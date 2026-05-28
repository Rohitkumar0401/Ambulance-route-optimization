package alerts;

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
 * AlertController.java
 * Alert management and government report generation.
 * Converted from backend/modules/alerts/controller.js
 *
 * Routes:
 *   GET   /api/alerts/active              → getActiveAlerts()
 *   GET   /api/alerts/all                 → getAllAlerts()
 *   GET   /api/alerts/stats               → getAlertStats()
 *   GET   /api/alerts/government-report   → generateGovernmentReport()
 *   POST  /api/alerts/create              → createAlert()
 *   PATCH /api/alerts/:id/acknowledge     → acknowledgeAlert()
 *   PATCH /api/alerts/:id/resolve         → resolveAlert()
 */
public class AlertController {

    private static final List<String> VALID_TYPES      = List.of("road_condition","traffic","emergency","weather","infrastructure");
    private static final List<String> VALID_SEVERITIES = List.of("low","medium","high","critical");

    // ── GET /api/alerts/active ────────────────────────────────────────────────
    public static void getActiveAlerts(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM alerts WHERE status='active' " +
                "ORDER BY CASE severity WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END, created_at DESC"
            );
            String arr = resultToArray(rs);
            int count = arr.split("\\{").length - 1;
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + ",\"count\":" + count + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/alerts/all ───────────────────────────────────────────────────
    public static void getAllAlerts(HttpExchange ex) throws IOException {
        String status   = getQueryParam(ex.getRequestURI(), "status");
        String severity = getQueryParam(ex.getRequestURI(), "severity");
        String limitStr = getQueryParam(ex.getRequestURI(), "limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 50;

        try (Connection conn = DatabaseConfig.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM alerts WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (status   != null) { sql.append(" AND status = ?");   params.add(status); }
            if (severity != null) { sql.append(" AND severity = ?"); params.add(severity); }
            sql.append(" ORDER BY created_at DESC LIMIT ?");
            params.add(limit);

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i) instanceof Integer) ps.setInt(i + 1, (Integer) params.get(i));
                else ps.setString(i + 1, (String) params.get(i));
            }
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + resultToArray(ps.executeQuery()) + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/alerts/stats ─────────────────────────────────────────────────
    public static void getAlertStats(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) as total, " +
                "SUM(status='active') as active, " +
                "SUM(status='acknowledged') as acknowledged, " +
                "SUM(status='resolved') as resolved, " +
                "SUM(severity='critical') as critical, " +
                "SUM(severity='high') as high " +
                "FROM alerts"
            );
            if (rs.next()) {
                String json = String.format(
                    "{\"total\":%d,\"active\":%d,\"acknowledged\":%d,\"resolved\":%d,\"critical\":%d,\"high\":%d}",
                    rs.getInt("total"), rs.getInt("active"), rs.getInt("acknowledged"),
                    rs.getInt("resolved"), rs.getInt("critical"), rs.getInt("high")
                );
                ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + json + "}");
            }
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/alerts/create ───────────────────────────────────────────────
    public static void createAlert(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String alertType = body.get("alertType");
        String severity  = body.get("severity");
        String message   = body.get("message");

        if (alertType == null || !VALID_TYPES.contains(alertType)) {
            ErrorHandler.sendError(ex, 400, "alertType must be one of: " + String.join(", ", VALID_TYPES));
            return;
        }
        if (severity == null || !VALID_SEVERITIES.contains(severity)) {
            ErrorHandler.sendError(ex, 400, "severity must be one of: " + String.join(", ", VALID_SEVERITIES));
            return;
        }
        if (message == null || message.isEmpty()) {
            ErrorHandler.sendError(ex, 400, "message is required");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO alerts (road_id, road_name, alert_type, severity, message, latitude, longitude, status) " +
                "VALUES (?,?,?,?,?,?,?,'active')",
                Statement.RETURN_GENERATED_KEYS
            );
            String roadId   = body.get("roadId");
            String roadName = body.get("roadName");
            String latStr   = body.get("latitude");
            String lonStr   = body.get("longitude");

            if (roadId   != null) ps.setString(1, roadId);   else ps.setNull(1, Types.VARCHAR);
            if (roadName != null) ps.setString(2, roadName); else ps.setNull(2, Types.VARCHAR);
            ps.setString(3, alertType);
            ps.setString(4, severity);
            ps.setString(5, message);
            if (latStr != null) ps.setDouble(6, Double.parseDouble(latStr)); else ps.setNull(6, Types.DECIMAL);
            if (lonStr != null) ps.setDouble(7, Double.parseDouble(lonStr)); else ps.setNull(7, Types.DECIMAL);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int alertId = keys.next() ? keys.getInt(1) : -1;
            ErrorHandler.writeResponse(ex, 200,
                "{\"success\":true,\"alertId\":" + alertId + ",\"message\":\"Alert created\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── PATCH /api/alerts/:id/acknowledge ─────────────────────────────────────
    public static void acknowledgeAlert(HttpExchange ex, int id) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String acknowledgedBy = body.getOrDefault("acknowledgedBy", "system");

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM alerts WHERE id = ?");
            check.setInt(1, id);
            if (!check.executeQuery().next()) {
                ErrorHandler.sendError(ex, 404, "Alert not found");
                return;
            }
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE alerts SET status='acknowledged', acknowledged_by=?, acknowledged_at=NOW(), updated_at=NOW() WHERE id=?"
            );
            ps.setString(1, acknowledgedBy);
            ps.setInt(2, id);
            ps.executeUpdate();
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"Alert acknowledged\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── PATCH /api/alerts/:id/resolve ─────────────────────────────────────────
    public static void resolveAlert(HttpExchange ex, int id) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM alerts WHERE id = ?");
            check.setInt(1, id);
            if (!check.executeQuery().next()) {
                ErrorHandler.sendError(ex, 404, "Alert not found");
                return;
            }
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE alerts SET status='resolved', resolved_at=NOW(), updated_at=NOW() WHERE id=?"
            );
            ps.setInt(1, id);
            ps.executeUpdate();
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"Alert resolved\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/alerts/government-report ─────────────────────────────────────
    // JS: exports.generateGovernmentReport = async (req, res) => { ... }
    public static void generateGovernmentReport(HttpExchange ex) throws IOException {
        String fromDate = getQueryParam(ex.getRequestURI(), "from");
        String toDate   = getQueryParam(ex.getRequestURI(), "to");
        if (fromDate == null) fromDate = java.time.LocalDate.now().minusDays(7).toString();
        if (toDate   == null) toDate   = java.time.LocalDate.now().toString();

        try (Connection conn = DatabaseConfig.getConnection()) {
            // Alert summary
            PreparedStatement alertPs = conn.prepareStatement(
                "SELECT alert_type, severity, COUNT(*) as count, " +
                "SUM(status='resolved') as resolved, SUM(status='active') as active " +
                "FROM alerts WHERE DATE(created_at) BETWEEN ? AND ? " +
                "GROUP BY alert_type, severity " +
                "ORDER BY CASE severity WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END"
            );
            alertPs.setString(1, fromDate);
            alertPs.setString(2, toDate);
            String alertSummary = resultToArray(alertPs.executeQuery());

            // Critical roads
            String criticalRoads = resultToArray(conn.createStatement().executeQuery(
                "SELECT road_id, road_name, composite_score, flag_status, updated_at " +
                "FROM road_scores WHERE flag_status IN ('warning','critical') " +
                "ORDER BY composite_score DESC LIMIT 20"
            ));

            // Emergency summary
            PreparedStatement emPs = conn.prepareStatement(
                "SELECT severity, COUNT(*) as total, SUM(status='completed') as completed, SUM(status='pending') as pending " +
                "FROM emergency_requests WHERE DATE(created_at) BETWEEN ? AND ? GROUP BY severity"
            );
            emPs.setString(1, fromDate);
            emPs.setString(2, toDate);
            String emergencySummary = resultToArray(emPs.executeQuery());

            // Route stats
            PreparedStatement routePs = conn.prepareStatement(
                "SELECT COUNT(*) as totalRoutes, AVG(distance) as avgDistance, AVG(estimated_time) as avgTime " +
                "FROM routes WHERE DATE(created_at) BETWEEN ? AND ?"
            );
            routePs.setString(1, fromDate);
            routePs.setString(2, toDate);
            ResultSet routeRs = routePs.executeQuery();
            String routeStats = routeRs.next()
                ? String.format("{\"totalRoutes\":%d,\"avgDistance\":%.2f,\"avgTime\":%.2f}",
                    routeRs.getInt("totalRoutes"), routeRs.getDouble("avgDistance"), routeRs.getDouble("avgTime"))
                : "{}";

            String report = String.format(
                "{\"generatedAt\":\"%s\",\"period\":{\"from\":\"%s\",\"to\":\"%s\"}," +
                "\"title\":\"Ambulance Route Optimization - Government Report\"," +
                "\"team\":\"Team Visitors\"," +
                "\"alertBreakdown\":%s,\"criticalRoads\":%s," +
                "\"emergencyBreakdown\":%s,\"routeStatistics\":%s," +
                "\"recommendations\":[\"Immediate road repair required on critically flagged roads\"," +
                "\"Regular road condition monitoring recommended every 48 hours\"," +
                "\"Coordinate with local authorities for emergency route clearance protocols\"]}",
                java.time.Instant.now().toString(), fromDate, toDate,
                alertSummary, criticalRoads, emergencySummary, routeStats
            );

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"report\":" + report + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String resultToArray(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder("[");
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        boolean first = true;
        while (rs.next()) {
            if (!first) sb.append(",");
            sb.append("{");
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(",");
                String col = meta.getColumnLabel(i);
                String val = rs.getString(i);
                sb.append("\"").append(col).append("\":");
                if (val == null) sb.append("null");
                else {
                    try { Double.parseDouble(val); sb.append(val); }
                    catch (NumberFormatException e) {
                        sb.append("\"").append(ErrorHandler.escapeJson(val)).append("\"");
                    }
                }
            }
            sb.append("}");
            first = false;
        }
        sb.append("]");
        return sb.toString();
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
