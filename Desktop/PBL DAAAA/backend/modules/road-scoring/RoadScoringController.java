package roadscoring;

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
 * RoadScoringController.java
 * Road scoring, flagging, and threshold-check logic.
 * Converted from backend/modules/road-scoring/controller.js
 *
 * Routes:
 *   GET  /api/road-scoring/all              → getAllRoadScores()
 *   GET  /api/road-scoring/flagged          → getFlaggedRoads()
 *   GET  /api/road-scoring/stats            → getRoadStats()
 *   POST /api/road-scoring/score            → scoreRoad()
 *   POST /api/road-scoring/threshold-check  → thresholdCheck()
 */
public class RoadScoringController {

    // ── Thresholds (mirrors JS THRESHOLDS object) ─────────────────────────────
    private static final double QUALITY_WARN     = 1.5,  QUALITY_CRIT     = 2.0;
    private static final double CONGESTION_WARN  = 0.6,  CONGESTION_CRIT  = 0.85;
    private static final double SPEED_WARN       = 20.0, SPEED_CRIT       = 10.0;
    private static final int    INCIDENT_WARN    = 3,    INCIDENT_CRIT    = 6;
    private static final int    COMPOSITE_WARN   = 60,   COMPOSITE_CRIT   = 80;

    // ── GET /api/road-scoring/all ─────────────────────────────────────────────
    public static void getAllRoadScores(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql =
                "SELECT rs.*, " +
                "  td.congestion_level, td.average_speed, rc.condition_type " +
                "FROM road_scores rs " +
                "LEFT JOIN (" +
                "  SELECT road_id, AVG(congestion_level) as congestion_level, AVG(average_speed) as average_speed " +
                "  FROM traffic_data " +
                "  WHERE timestamp > DATE_SUB(NOW(), INTERVAL 1 HOUR) " +
                "  GROUP BY road_id" +
                ") td ON rs.road_id = td.road_id " +
                "LEFT JOIN road_conditions rc ON rs.road_id = rc.road_id " +
                "ORDER BY rs.composite_score DESC";

            ResultSet rs = conn.createStatement().executeQuery(sql);
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(roadRowToJson(rs));
                first = false;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/road-scoring/flagged ─────────────────────────────────────────
    public static void getFlaggedRoads(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM road_scores WHERE flag_status IN ('warning','critical') " +
                "ORDER BY composite_score DESC, updated_at DESC"
            );
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            int count = 0;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(roadRowToJson(rs));
                first = false;
                count++;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200,
                "{\"success\":true,\"data\":" + arr + ",\"count\":" + count + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/road-scoring/stats ───────────────────────────────────────────
    public static void getRoadStats(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) as total, " +
                "SUM(flag_status='good') as good, " +
                "SUM(flag_status='warning') as warning, " +
                "SUM(flag_status='critical') as critical, " +
                "AVG(composite_score) as avgScore, " +
                "MAX(composite_score) as maxScore " +
                "FROM road_scores"
            );
            if (rs.next()) {
                String json = String.format(
                    "{\"total\":%d,\"good\":%d,\"warning\":%d,\"critical\":%d,\"avgScore\":%.2f,\"maxScore\":%d}",
                    rs.getInt("total"), rs.getInt("good"), rs.getInt("warning"),
                    rs.getInt("critical"), rs.getDouble("avgScore"), rs.getInt("maxScore")
                );
                ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + json + "}");
            }
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/road-scoring/score ──────────────────────────────────────────
    // JS: exports.scoreRoad = async (req, res) => { ... }
    public static void scoreRoad(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));

        String roadId   = body.get("roadId");
        String roadName = body.get("roadName");
        if (roadId == null || roadName == null) {
            ErrorHandler.sendError(ex, 400, "roadId and roadName are required");
            return;
        }

        double roadQuality       = parseDouble(body.get("roadQuality"),       1.0);
        double terrainDifficulty = parseDouble(body.get("terrainDifficulty"), 1.0);
        double congestionLevel   = parseDouble(body.get("congestionLevel"),   0.0);
        double averageSpeed      = parseDouble(body.get("averageSpeed"),      60.0);
        int    incidentCount     = parseInt(body.get("incidentCount"),        0);
        double weatherFactor     = parseDouble(body.get("weatherFactor"),     1.0);
        String reportedBy        = body.getOrDefault("reportedBy", "system");
        String latStr            = body.get("latitude");
        String lonStr            = body.get("longitude");

        int    compositeScore = calculateRoadScore(roadQuality, terrainDifficulty, congestionLevel, averageSpeed, incidentCount, weatherFactor);
        String flagStatus     = getFlagStatus(compositeScore, congestionLevel, averageSpeed);

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO road_scores " +
                "(road_id, road_name, latitude, longitude, road_quality, terrain_difficulty, " +
                " congestion_level, average_speed, incident_count, weather_factor, " +
                " composite_score, flag_status, reported_by, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "road_name=VALUES(road_name), latitude=VALUES(latitude), longitude=VALUES(longitude), " +
                "road_quality=VALUES(road_quality), terrain_difficulty=VALUES(terrain_difficulty), " +
                "congestion_level=VALUES(congestion_level), average_speed=VALUES(average_speed), " +
                "incident_count=VALUES(incident_count), weather_factor=VALUES(weather_factor), " +
                "composite_score=VALUES(composite_score), flag_status=VALUES(flag_status), " +
                "reported_by=VALUES(reported_by), updated_at=NOW()"
            );
            ps.setString(1, roadId);
            ps.setString(2, roadName);
            if (latStr != null) ps.setDouble(3, Double.parseDouble(latStr)); else ps.setNull(3, Types.DECIMAL);
            if (lonStr != null) ps.setDouble(4, Double.parseDouble(lonStr)); else ps.setNull(4, Types.DECIMAL);
            ps.setDouble(5, roadQuality);
            ps.setDouble(6, terrainDifficulty);
            ps.setDouble(7, congestionLevel);
            ps.setDouble(8, averageSpeed);
            ps.setInt(9, incidentCount);
            ps.setDouble(10, weatherFactor);
            ps.setInt(11, compositeScore);
            ps.setString(12, flagStatus);
            ps.setString(13, reportedBy);
            ps.executeUpdate();

            // Auto-create alert if critical (mirrors JS auto-alert)
            if ("critical".equals(flagStatus)) {
                PreparedStatement alert = conn.prepareStatement(
                    "INSERT INTO alerts (road_id, road_name, alert_type, severity, message, status) " +
                    "VALUES (?,?,'road_condition','critical',?,'active') " +
                    "ON DUPLICATE KEY UPDATE status='active', updated_at=NOW()"
                );
                alert.setString(1, roadId);
                alert.setString(2, roadName);
                alert.setString(3, "Critical road condition detected on " + roadName + ". Score: " + compositeScore + "/100");
                alert.executeUpdate();
            }

            int qualityScore    = (int) Math.min(((roadQuality - 1.0) / 1.5) * 100, 100);
            int congestionScore = (int) (congestionLevel * 100);
            int speedScore      = (int) Math.max(0, ((60 - averageSpeed) / 60) * 100);

            ErrorHandler.writeResponse(ex, 200, String.format(
                "{\"success\":true,\"roadId\":\"%s\",\"compositeScore\":%d,\"flagStatus\":\"%s\"," +
                "\"breakdown\":{\"qualityScore\":%d,\"congestionScore\":%d,\"speedScore\":%d}}",
                ErrorHandler.escapeJson(roadId), compositeScore, flagStatus,
                qualityScore, congestionScore, speedScore
            ));
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/road-scoring/threshold-check ────────────────────────────────
    // JS: exports.thresholdCheck = async (req, res) => { ... }
    public static void thresholdCheck(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String roadId = body.get("roadId");
        if (roadId == null) {
            ErrorHandler.sendError(ex, 400, "roadId is required");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM road_scores WHERE road_id = ?"
            );
            ps.setString(1, roadId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                ErrorHandler.sendError(ex, 404, "Road not found");
                return;
            }

            double rq  = rs.getDouble("road_quality");
            double cl  = rs.getDouble("congestion_level");
            double spd = rs.getDouble("average_speed");
            int    cs  = rs.getInt("composite_score");
            String fs  = rs.getString("flag_status");

            StringBuilder violations = new StringBuilder("[");
            boolean first = true;

            if (rq >= QUALITY_CRIT) {
                if (!first) violations.append(",");
                violations.append(violation("roadQuality", rq, QUALITY_CRIT, "critical"));
                first = false;
            } else if (rq >= QUALITY_WARN) {
                if (!first) violations.append(",");
                violations.append(violation("roadQuality", rq, QUALITY_WARN, "warning"));
                first = false;
            }
            if (cl >= CONGESTION_CRIT) {
                if (!first) violations.append(",");
                violations.append(violation("congestionLevel", cl, CONGESTION_CRIT, "critical"));
                first = false;
            } else if (cl >= CONGESTION_WARN) {
                if (!first) violations.append(",");
                violations.append(violation("congestionLevel", cl, CONGESTION_WARN, "warning"));
                first = false;
            }
            if (spd <= SPEED_CRIT) {
                if (!first) violations.append(",");
                violations.append(violation("averageSpeed", spd, SPEED_CRIT, "critical"));
                first = false;
            } else if (spd <= SPEED_WARN) {
                if (!first) violations.append(",");
                violations.append(violation("averageSpeed", spd, SPEED_WARN, "warning"));
                first = false;
            }
            violations.append("]");

            boolean requiresAction = violations.toString().contains("\"critical\"");

            ErrorHandler.writeResponse(ex, 200, String.format(
                "{\"success\":true,\"roadId\":\"%s\",\"compositeScore\":%d,\"flagStatus\":\"%s\"," +
                "\"violations\":%s,\"requiresAction\":%b}",
                ErrorHandler.escapeJson(roadId), cs, fs, violations, requiresAction
            ));
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── calculateRoadScore — mirrors JS calculateRoadScore() ─────────────────
    // Weighted composite 0-100 (higher = worse)
    static int calculateRoadScore(double roadQuality, double terrainDifficulty,
                                   double congestionLevel, double averageSpeed,
                                   int incidentCount, double weatherFactor) {
        double qualityScore    = Math.min(((roadQuality - 1.0) / 1.5) * 100, 100);
        double terrainScore    = Math.min(((terrainDifficulty - 1.0) / 1.5) * 100, 100);
        double congestionScore = congestionLevel * 100;
        double speedScore      = Math.max(0, ((60 - averageSpeed) / 60) * 100);
        double incidentScore   = Math.min((incidentCount / 10.0) * 100, 100);
        double weatherScore    = Math.min(((weatherFactor - 1.0) / 1.0) * 100, 100);

        double composite = qualityScore    * 0.30
                         + terrainScore    * 0.15
                         + congestionScore * 0.25
                         + speedScore      * 0.15
                         + incidentScore   * 0.10
                         + weatherScore    * 0.05;
        return (int) Math.round(composite);
    }

    // ── getFlagStatus — mirrors JS getFlagStatus() ────────────────────────────
    static String getFlagStatus(int score, double congestionLevel, double averageSpeed) {
        if (score >= COMPOSITE_CRIT || congestionLevel >= CONGESTION_CRIT || averageSpeed <= SPEED_CRIT)
            return "critical";
        if (score >= COMPOSITE_WARN || congestionLevel >= CONGESTION_WARN || averageSpeed <= SPEED_WARN)
            return "warning";
        return "good";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String violation(String field, double value, double threshold, String level) {
        return String.format("{\"field\":\"%s\",\"value\":%.4f,\"threshold\":%.4f,\"level\":\"%s\"}",
            field, value, threshold, level);
    }

    private static String roadRowToJson(ResultSet rs) throws SQLException {
        return String.format(
            "{\"id\":%d,\"road_id\":\"%s\",\"road_name\":\"%s\"," +
            "\"latitude\":%s,\"longitude\":%s," +
            "\"road_quality\":%s,\"terrain_difficulty\":%s," +
            "\"congestion_level\":%s,\"average_speed\":%s," +
            "\"incident_count\":%d,\"weather_factor\":%s," +
            "\"composite_score\":%d,\"flag_status\":\"%s\"," +
            "\"reported_by\":\"%s\",\"updated_at\":\"%s\"}",
            rs.getInt("id"),
            ErrorHandler.escapeJson(rs.getString("road_id")),
            ErrorHandler.escapeJson(rs.getString("road_name")),
            nullOrVal(rs, "latitude"), nullOrVal(rs, "longitude"),
            nullOrVal(rs, "road_quality"), nullOrVal(rs, "terrain_difficulty"),
            nullOrVal(rs, "congestion_level"), nullOrVal(rs, "average_speed"),
            rs.getInt("incident_count"),
            nullOrVal(rs, "weather_factor"),
            rs.getInt("composite_score"),
            rs.getString("flag_status"),
            ErrorHandler.escapeJson(rs.getString("reported_by")),
            rs.getString("updated_at")
        );
    }

    private static String nullOrVal(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v == null ? "null" : v;
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

    private static double parseDouble(String s, double def) {
        try { return s != null ? Double.parseDouble(s) : def; } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String s, int def) {
        try { return s != null ? Integer.parseInt(s) : def; } catch (NumberFormatException e) { return def; }
    }
}
