package emergency;

import com.sun.net.httpserver.HttpExchange;
import config.DatabaseConfig;
import utils.ErrorHandler;
import emergency.RequestQueue;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * EmergencyController.java
 * HTTP handlers for emergency request endpoints.
 * Converted from backend/modules/emergency-request/controller.js
 *
 * Routes:
 *   POST /api/emergency/create        → createEmergencyRequest()
 *   GET  /api/emergency/next          → getNextRequest()
 *   POST /api/emergency/update-status → updateRequestStatus()
 *   GET  /api/emergency/all           → getAllRequests()
 */
public class EmergencyController {

    private static final List<String> VALID_SEVERITIES = List.of("critical", "high", "medium", "low");
    private static final List<String> VALID_STATUSES   = List.of("pending", "assigned", "in_progress", "completed", "cancelled");
    private static final Pattern      PHONE_REGEX       = Pattern.compile("^[0-9+\\-\\s()]{10,20}$");

    // ── POST /api/emergency/create ────────────────────────────────────────────
    // JS: exports.createEmergencyRequest = async (req, res) => { ... }
    public static void createEmergencyRequest(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));

        String severity    = body.get("severity");
        String contact     = body.get("contact");
        String patientName = body.getOrDefault("patientName", "Unknown");
        String description = body.getOrDefault("description", "");
        String location    = buildLocation(body);

        // Validation
        if (location == null || severity == null || contact == null) {
            ErrorHandler.sendError(ex, 400, "Location, severity, and contact are required fields");
            return;
        }
        if (!VALID_SEVERITIES.contains(severity)) {
            ErrorHandler.sendError(ex, 400, "Invalid severity level. Must be: critical, high, medium, or low");
            return;
        }
        if (!PHONE_REGEX.matcher(contact).matches()) {
            ErrorHandler.sendError(ex, 400, "Invalid contact number format");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO emergency_requests (patient_name, location, severity, contact, description, status) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, patientName);
            ps.setString(2, location);
            ps.setString(3, severity);
            ps.setString(4, contact);
            ps.setString(5, description);
            ps.setString(6, "pending");
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int requestId = keys.next() ? keys.getInt(1) : -1;

            // Enqueue in FIFO queue
            RequestQueue.getInstance().enqueue(requestId, severity);

            ErrorHandler.writeResponse(ex, 200,
                "{\"success\":true,\"requestId\":" + requestId + ",\"message\":\"Emergency request created\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/emergency/next ───────────────────────────────────────────────
    // JS: exports.getNextRequest = async (req, res) => { ... }
    public static void getNextRequest(HttpExchange ex) throws IOException {
        RequestQueue.QueueEntry next = RequestQueue.getInstance().dequeue();
        if (next == null) {
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"No pending requests\"}");
            return;
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM emergency_requests WHERE id = ?"
            );
            ps.setInt(1, next.id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ErrorHandler.writeResponse(ex, 200,
                    "{\"success\":true,\"data\":" + rowToJson(rs) + "}");
            } else {
                ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":null}");
            }
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/emergency/update-status ────────────────────────────────────
    // JS: exports.updateRequestStatus = async (req, res) => { ... }
    public static void updateRequestStatus(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String requestIdStr = body.get("requestId");
        String status       = body.get("status");
        String ambulanceId  = body.get("ambulanceId");

        if (requestIdStr == null || status == null) {
            ErrorHandler.sendError(ex, 400, "Request ID and status are required");
            return;
        }
        if (!VALID_STATUSES.contains(status)) {
            ErrorHandler.sendError(ex, 400, "Invalid status. Must be: pending, assigned, in_progress, completed, or cancelled");
            return;
        }

        int requestId;
        try { requestId = Integer.parseInt(requestIdStr); }
        catch (NumberFormatException e) {
            ErrorHandler.sendError(ex, 400, "requestId must be a number");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            // Check exists
            PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM emergency_requests WHERE id = ?"
            );
            check.setInt(1, requestId);
            if (!check.executeQuery().next()) {
                ErrorHandler.sendError(ex, 404, "Emergency request not found");
                return;
            }

            PreparedStatement ps = conn.prepareStatement(
                "UPDATE emergency_requests SET status = ?, ambulance_id = ?, updated_at = NOW() WHERE id = ?"
            );
            ps.setString(1, status);
            if (ambulanceId != null) {
                try {
                    ps.setInt(2, Integer.parseInt(ambulanceId));
                } catch (NumberFormatException e) {
                    ErrorHandler.sendError(ex, 400, "ambulanceId must be a number");
                    return;
                }
            } else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, requestId);
            ps.executeUpdate();

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"Request status updated\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/emergency/all ────────────────────────────────────────────────
    // JS: exports.getAllRequests = async (req, res) => { ... }
    public static void getAllRequests(HttpExchange ex) throws IOException {
        String statusFilter = getQueryParam(ex.getRequestURI(), "status");
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql = "SELECT * FROM emergency_requests";
            if (statusFilter != null) sql += " WHERE status = ?";
            sql += " ORDER BY created_at DESC";

            PreparedStatement ps = conn.prepareStatement(sql);
            if (statusFilter != null) ps.setString(1, statusFilter);

            ResultSet rs = ps.executeQuery();
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(rowToJson(rs));
                first = false;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Build location JSON from lat/lon fields in body
    private static String buildLocation(Map<String, String> body) {
        String lat = body.get("latitude");
        String lon = body.get("longitude");
        // Also accept nested location.latitude / location.longitude
        if (lat == null) lat = body.get("location_latitude");
        if (lon == null) lon = body.get("location_longitude");
        if (lat == null) lat = body.get("locationLat");
        if (lon == null) lon = body.get("locationLon");
        if (lat == null || lon == null) return null;
        return "{\"latitude\":" + lat + ",\"longitude\":" + lon + "}";
    }

    private static String rowToJson(ResultSet rs) throws SQLException {
        return String.format(
            "{\"id\":%d,\"patient_name\":\"%s\",\"location\":%s,\"severity\":\"%s\"," +
            "\"contact\":\"%s\",\"description\":\"%s\",\"status\":\"%s\"," +
            "\"created_at\":\"%s\"}",
            rs.getInt("id"),
            ErrorHandler.escapeJson(rs.getString("patient_name")),
            rs.getString("location"),
            rs.getString("severity"),
            ErrorHandler.escapeJson(rs.getString("contact")),
            ErrorHandler.escapeJson(rs.getString("description")),
            rs.getString("status"),
            rs.getString("created_at")
        );
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        Pattern nestedLoc = Pattern.compile("\"location\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher loc = nestedLoc.matcher(json);
        if (loc.find()) {
            String inner = loc.group(1);
            Pattern lp = Pattern.compile("\"latitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
            Pattern lop = Pattern.compile("\"longitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
            Matcher lm = lp.matcher(inner);
            Matcher lom = lop.matcher(inner);
            if (lm.find()) map.put("location_latitude", lm.group(1));
            if (lom.find()) map.put("location_longitude", lom.group(1));
        }
        Pattern strPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = strPat.matcher(json);
        while (m.find()) map.put(m.group(1), m.group(2));
        Pattern numPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher n = numPat.matcher(json);
        while (n.find()) map.putIfAbsent(n.group(1), n.group(2));
        return map;
    }

    private static String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key))
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
