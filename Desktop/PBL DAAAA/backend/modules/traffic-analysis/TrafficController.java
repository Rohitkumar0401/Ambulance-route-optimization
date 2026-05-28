package traffic;

import com.sun.net.httpserver.HttpExchange;
import config.DatabaseConfig;
import utils.ErrorHandler;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * TrafficController.java
 * HTTP handlers for traffic-analysis endpoints.
 * Converted from backend/modules/traffic-analysis/controller.js
 *
 * Routes:
 *   GET  /api/traffic-analysis/traffic/:roadId          → getTrafficData()
 *   GET  /api/traffic-analysis/road-conditions/:roadId  → getRoadConditions()
 *   POST /api/traffic-analysis/roadblock/report         → reportRoadblock()
 *   POST /api/traffic-analysis/traffic/update           → updateTrafficCondition()
 */
public class TrafficController {

    // ── GET /api/traffic-analysis/traffic/:roadId ─────────────────────────────
    // JS: exports.getTrafficData = async (req, res) => {
    //       const [rows] = await db.query('SELECT * FROM traffic_data WHERE road_id = ?', [roadId]);
    //     }
    public static void getTrafficData(HttpExchange ex, String roadId) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM traffic_data WHERE road_id = ?"
            );
            ps.setString(1, roadId);
            ResultSet rs = ps.executeQuery();
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(trafficRowToJson(rs));
                first = false;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/traffic-analysis/traffic/update ─────────────────────────────
    // JS: exports.updateTrafficCondition = async (req, res) => { ... }
    public static void updateTrafficCondition(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String roadId        = body.get("roadId");
        String congestionStr = body.get("congestionLevel");
        String speedStr      = body.get("averageSpeed");

        if (roadId == null || roadId.isEmpty()) {
            ErrorHandler.sendError(ex, 400, "Road ID is required");
            return;
        }

        // Validate congestion level (0-1)
        if (congestionStr != null) {
            double c = Double.parseDouble(congestionStr);
            if (c < 0 || c > 1) {
                ErrorHandler.sendError(ex, 400, "Congestion level must be between 0 and 1");
                return;
            }
        }

        // Validate average speed (positive)
        if (speedStr != null) {
            double s = Double.parseDouble(speedStr);
            if (s < 0) {
                ErrorHandler.sendError(ex, 400, "Average speed must be a positive number");
                return;
            }
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO traffic_data (road_id, congestion_level, average_speed, timestamp) VALUES (?, ?, ?, NOW())"
            );
            ps.setString(1, roadId);
            if (congestionStr != null) ps.setDouble(2, Double.parseDouble(congestionStr));
            else ps.setNull(2, Types.DECIMAL);
            if (speedStr != null) ps.setDouble(3, Double.parseDouble(speedStr));
            else ps.setNull(3, Types.DECIMAL);
            ps.executeUpdate();

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"Traffic data updated\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/traffic-analysis/road-conditions/:roadId ────────────────────
    // JS: exports.getRoadConditions = async (req, res) => { ... }
    public static void getRoadConditions(HttpExchange ex, String roadId) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM road_conditions WHERE road_id = ?"
            );
            ps.setString(1, roadId);
            ResultSet rs = ps.executeQuery();
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(conditionRowToJson(rs));
                first = false;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/traffic-analysis/roadblock/report ───────────────────────────
    // JS: exports.reportRoadblock = async (req, res) => { ... }
    public static void reportRoadblock(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String roadId      = body.get("roadId");
        String location    = body.get("location");
        String severity    = body.get("severity");
        String description = body.getOrDefault("description", "");

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO roadblocks (road_id, location, severity, description, status) VALUES (?, ?, ?, ?, ?)"
            );
            ps.setString(1, roadId);
            ps.setString(2, location);
            ps.setString(3, severity);
            ps.setString(4, description);
            ps.setString(5, "active");
            ps.executeUpdate();

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"Roadblock reported\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String trafficRowToJson(ResultSet rs) throws SQLException {
        return String.format(
            "{\"id\":%d,\"road_id\":\"%s\",\"congestion_level\":%s,\"average_speed\":%s,\"timestamp\":\"%s\"}",
            rs.getInt("id"),
            ErrorHandler.escapeJson(rs.getString("road_id")),
            rs.getString("congestion_level") != null ? rs.getString("congestion_level") : "null",
            rs.getString("average_speed")    != null ? rs.getString("average_speed")    : "null",
            rs.getString("timestamp")
        );
    }

    private static String conditionRowToJson(ResultSet rs) throws SQLException {
        return String.format(
            "{\"id\":%d,\"road_id\":\"%s\",\"condition_type\":\"%s\",\"description\":\"%s\",\"updated_at\":\"%s\"}",
            rs.getInt("id"),
            ErrorHandler.escapeJson(rs.getString("road_id")),
            rs.getString("condition_type"),
            ErrorHandler.escapeJson(rs.getString("description")),
            rs.getString("updated_at")
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
