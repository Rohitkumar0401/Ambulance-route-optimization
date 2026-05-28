package routeopt;

import com.sun.net.httpserver.HttpExchange;
import config.DatabaseConfig;
import utils.ErrorHandler;
import routeopt.DijkstraAlgorithm;
import routeopt.AStarAlgorithm;
import routeopt.AmbulanceGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * RouteOptimizationController.java
 * HTTP handlers for route-optimization endpoints.
 * Converted from backend/modules/route-optimization/controller.js
 *
 * Routes:
 *   POST /api/route-optimization/calculate → calculateOptimalRoute()
 *   POST /api/route-optimization/reroute   → dynamicReroute()
 *   GET  /api/route-optimization/history   → getRouteHistory()
 */
public class RouteOptimizationController {

    // ── POST /api/route-optimization/calculate ────────────────────────────────
    // JS: exports.calculateOptimalRoute = async (req, res) => { ... }
    public static void calculateOptimalRoute(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));

        String algorithm = body.getOrDefault("algorithm", "dijkstra");
        double startLat  = parseDouble(body.get("startLat"),  Double.NaN);
        double startLon  = parseDouble(body.get("startLon"),  Double.NaN);
        double destLat   = parseDouble(body.get("destLat"),   Double.NaN);
        double destLon   = parseDouble(body.get("destLon"),   Double.NaN);

        // Validation
        if (Double.isNaN(startLat) || Double.isNaN(startLon)) {
            ErrorHandler.sendError(ex, 400, "Start must have latitude and longitude");
            return;
        }
        if (Double.isNaN(destLat) || Double.isNaN(destLon)) {
            ErrorHandler.sendError(ex, 400, "Destination must have latitude and longitude");
            return;
        }
        if (!List.of("dijkstra", "astar").contains(algorithm)) {
            ErrorHandler.sendError(ex, 400, "Invalid algorithm. Use \"dijkstra\" or \"astar\"");
            return;
        }
        if (startLat < -90 || startLat > 90 || destLat < -90 || destLat > 90) {
            ErrorHandler.sendError(ex, 400, "Latitude must be between -90 and 90");
            return;
        }
        if (startLon < -180 || startLon > 180 || destLon < -180 || destLon > 180) {
            ErrorHandler.sendError(ex, 400, "Longitude must be between -180 and 180");
            return;
        }

        List<double[]>        hospitals      = new ArrayList<>();
        Map<String, double[]> roadConditions = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection()) {
            // Fetch hospitals
            try {
                ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT latitude, longitude, name FROM hospitals"
                );
                while (rs.next()) {
                    hospitals.add(new double[]{
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude")
                    });
                }
            } catch (SQLException ignored) {}

            // Fetch road conditions
            try {
                ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT road_id, road_quality, terrain_difficulty, congestion_level FROM road_scores"
                );
                while (rs.next()) {
                    roadConditions.put(rs.getString("road_id"), new double[]{
                        rs.getDouble("road_quality"),
                        rs.getDouble("terrain_difficulty"),
                        1.0 + rs.getDouble("congestion_level")
                    });
                }
            } catch (SQLException ignored) {}

            // Run algorithm
            DijkstraAlgorithm.RouteResult route;
            try {
                if ("astar".equals(algorithm)) {
                    route = AStarAlgorithm.findPath(startLat, startLon, destLat, destLon, hospitals, roadConditions);
                } else {
                    route = DijkstraAlgorithm.findPath(startLat, startLon, destLat, destLon, hospitals, roadConditions);
                }
            } catch (RuntimeException e) {
                ErrorHandler.sendError(ex, 500, e.getMessage());
                return;
            }

            // Persist route (non-fatal)
            try {
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO routes (start_location, end_location, path, distance, estimated_time, algorithm_used) VALUES (?,?,?,?,?,?)"
                );
                ps.setString(1, String.format("{\"latitude\":%.6f,\"longitude\":%.6f}", startLat, startLon));
                ps.setString(2, String.format("{\"latitude\":%.6f,\"longitude\":%.6f}", destLat, destLon));
                ps.setString(3, "[]"); // simplified
                ps.setDouble(4, route.distance);
                ps.setInt(5, route.estimatedTime);
                ps.setString(6, algorithm);
                ps.executeUpdate();
            } catch (SQLException ignored) {}

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"route\":" + route.toJson() + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/route-optimization/reroute ─────────────────────────────────
    // JS: exports.dynamicReroute = async (req, res) => { ... }
    public static void dynamicReroute(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));

        double curLat  = parseDouble(body.get("currentLat"),  Double.NaN);
        double curLon  = parseDouble(body.get("currentLon"),  Double.NaN);
        double destLat = parseDouble(body.get("destLat"),     Double.NaN);
        double destLon = parseDouble(body.get("destLon"),     Double.NaN);

        if (Double.isNaN(curLat) || Double.isNaN(curLon) || Double.isNaN(destLat) || Double.isNaN(destLon)) {
            ErrorHandler.sendError(ex, 400, "Current location and destination are required");
            return;
        }

        List<double[]>        hospitals      = new ArrayList<>();
        Map<String, double[]> roadConditions = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection()) {
            try {
                ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT latitude, longitude FROM hospitals"
                );
                while (rs.next()) {
                    hospitals.add(new double[]{rs.getDouble("latitude"), rs.getDouble("longitude")});
                }
            } catch (SQLException ignored) {}

            DijkstraAlgorithm.RouteResult route;
            try {
                route = DijkstraAlgorithm.findPath(curLat, curLon, destLat, destLon, hospitals, roadConditions);
            } catch (RuntimeException e) {
                ErrorHandler.sendError(ex, 500, e.getMessage());
                return;
            }

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"route\":" + route.toJson() + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/route-optimization/history ──────────────────────────────────
    public static void getRouteHistory(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM routes ORDER BY created_at DESC LIMIT 20"
            );
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(String.format(
                    "{\"id\":%d,\"start_location\":%s,\"end_location\":%s," +
                    "\"distance\":%s,\"estimated_time\":%s,\"algorithm_used\":\"%s\",\"created_at\":\"%s\"}",
                    rs.getInt("id"),
                    rs.getString("start_location"),
                    rs.getString("end_location"),
                    rs.getString("distance"),
                    rs.getString("estimated_time"),
                    rs.getString("algorithm_used"),
                    rs.getString("created_at")
                ));
                first = false;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        // Parse nested start/destination objects
        Pattern nested = Pattern.compile("\"(start|destination|currentLocation)\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher nm = nested.matcher(json);
        while (nm.find()) {
            String prefix = nm.group(1).equals("start") ? "start" :
                            nm.group(1).equals("destination") ? "dest" : "current";
            String inner = nm.group(2);
            Pattern lp = Pattern.compile("\"latitude\"\\s*:\\s*(-?\\d+\\.?\\d*)");
            Pattern lop = Pattern.compile("\"longitude\"\\s*:\\s*(-?\\d+\\.?\\d*)");
            Matcher lm = lp.matcher(inner);
            Matcher lom = lop.matcher(inner);
            if (lm.find())  map.put(prefix + "Lat", lm.group(1));
            if (lom.find()) map.put(prefix + "Lon", lom.group(1));
        }
        Pattern strPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = strPat.matcher(json);
        while (m.find()) map.putIfAbsent(m.group(1), m.group(2));
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

    // ── POST /api/route-optimization/simulate ─────────────────────────────────
    // Builds an ambulance graph from real hospital DB data using Graph.java +
    // MapFactory-style nearest-neighbour edges + Dijkstra.java for routing.
    // Returns nodes, edges, and computed path for canvas-based simulation.
    //
    // Body: { ambulanceLat, ambulanceLon, destHospitalId, waypoints? }
    public static void simulate(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));

        double ambulanceLat   = parseDouble(body.get("ambulanceLat"),  Double.NaN);
        double ambulanceLon   = parseDouble(body.get("ambulanceLon"),  Double.NaN);
        int    destHospitalId = parseInt(body.get("destHospitalId"),   -1);
        int    waypoints      = parseInt(body.get("waypoints"),         8);

        if (Double.isNaN(ambulanceLat) || Double.isNaN(ambulanceLon)) {
            ErrorHandler.sendError(ex, 400, "ambulanceLat and ambulanceLon are required");
            return;
        }
        if (destHospitalId < 0) {
            ErrorHandler.sendError(ex, 400, "destHospitalId is required");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            List<AmbulanceGraph.HospitalData> hospitals = new ArrayList<>();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT id, name, latitude, longitude FROM hospitals ORDER BY id"
            );
            while (rs.next()) {
                hospitals.add(new AmbulanceGraph.HospitalData(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude")
                ));
            }

            if (hospitals.isEmpty()) {
                ErrorHandler.sendError(ex, 404, "No hospitals found in database");
                return;
            }

            AmbulanceGraph.SimulationResult result;
            try {
                result = AmbulanceGraph.buildAndRoute(
                    ambulanceLat, ambulanceLon,
                    destHospitalId, hospitals, waypoints
                );
            } catch (RuntimeException e) {
                ErrorHandler.sendError(ex, 500, e.getMessage());
                return;
            }

            ErrorHandler.writeResponse(ex, 200,
                "{\"success\":true,\"simulation\":" + result.toJson() + "}");

        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }
}
