package backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import alerts.AlertController;
import auth.AuthController;
import auth.AuthMiddleware;
import auth.AuthMiddleware.JwtPayload;
import config.DatabaseConfig;
import emergency.EmergencyController;
import hospital.HospitalController;
import roadscoring.RoadScoringController;
import routeopt.RouteOptimizationController;
import traffic.TrafficController;
import utils.ErrorHandler;
import mdvrp.Dijkstra;
import mdvrp.Graph;
import mdvrp.MapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * MainServer.java
 * Unified HTTP server — replaces backend/server.js (Express app).
 * Listens on port 5001 (configurable via PORT env/system property).
 *
 * JS routes mapped:
 *   /api/auth/*                → AuthController
 *   /api/hospitals/*           → HospitalController
 *   /api/route-optimization/*  → RouteOptimizationController
 *   /api/traffic-analysis/*    → TrafficController
 *   /api/emergency/*           → EmergencyController
 *   /api/road-scoring/*        → RoadScoringController
 *   /api/alerts/*              → AlertController
 *   /health                    → health check
 *   /                          → info
 *
 * Run:
 *   java -cp out:mysql-connector-j.jar \
 *     -DDB_HOST=localhost -DDB_USER=root -DDB_PASSWORD=root12345 \
 *     -DDB_NAME=ambulance_optimization -DJWT_SECRET=your_secret \
 *     backend.MainServer
 */
public class MainServer {
    private static final Graph DELIVERY_MAP = MapFactory.buildDeliveryMap();
    private static final Graph TRANSPORT_MAP = MapFactory.buildTransportMap();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(getProp("PORT", "5001"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/",        MainServer::dispatch);
        server.setExecutor(Executors.newFixedThreadPool(20));
        server.start();

        System.out.println("🚑 Java Server running on port " + port);
        System.out.println("   Health: http://localhost:" + port + "/health");
        System.out.println("   Auth:   http://localhost:" + port + "/api/auth/login");
    }

    // ── Main dispatcher — mirrors Express app.use() routing ──────────────────
    private static void dispatch(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path   = ex.getRequestURI().getPath();
        if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);

        // CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
            ex.sendResponseHeaders(204, -1);
            return;
        }

        // Request logging (mirrors JS app.use logger)
        System.out.println(java.time.Instant.now() + " [" + method + "] " + path);

        try {
            // ── Health / root ─────────────────────────────────────────────────
            if (path.equals("/health")) {
                ErrorHandler.writeResponse(ex, 200,
                    "{\"status\":\"ok\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
                return;
            }
            if (path.equals("") || path.equals("/")) {
                ErrorHandler.writeResponse(ex, 200,
                    "{\"message\":\"Ambulance Route Optimization API v2.0\",\"team\":\"Team Visitors\"}");
                return;
            }

            // ── /api/auth/* ───────────────────────────────────────────────────
            if (path.startsWith("/api/auth")) {
                routeAuth(ex, method, path);
                return;
            }

            // ── /api/hospitals/* ──────────────────────────────────────────────
            if (path.startsWith("/api/hospitals")) {
                routeHospitals(ex, method, path);
                return;
            }

            // ── /api/route-optimization/* ─────────────────────────────────────
            if (path.startsWith("/api/route-optimization")) {
                JwtPayload user = AuthMiddleware.verifyToken(ex);
                if (user == null) return;
                routeOptimization(ex, method, path, user);
                return;
            }

            // ── /api/delivery/* and /api/transport/* (Dijkstra graph APIs) ───
            if (path.startsWith("/api/delivery")) {
                routeDelivery(ex, method, path);
                return;
            }
            if (path.startsWith("/api/transport")) {
                routeTransport(ex, method, path);
                return;
            }

            // ── /api/traffic-analysis/* ───────────────────────────────────────
            if (path.startsWith("/api/traffic-analysis")) {
                JwtPayload user = AuthMiddleware.verifyToken(ex);
                if (user == null) return;
                routeTraffic(ex, method, path, user);
                return;
            }

            // ── /api/emergency/* ──────────────────────────────────────────────
            if (path.startsWith("/api/emergency")) {
                routeEmergency(ex, method, path);
                return;
            }

            // ── /api/road-scoring/* ───────────────────────────────────────────
            if (path.startsWith("/api/road-scoring")) {
                JwtPayload user = AuthMiddleware.verifyToken(ex);
                if (user == null) return;
                routeRoadScoring(ex, method, path, user);
                return;
            }

            // ── /api/alerts/* ─────────────────────────────────────────────────
            if (path.startsWith("/api/alerts")) {
                JwtPayload user = AuthMiddleware.verifyToken(ex);
                if (user == null) return;
                routeAlerts(ex, method, path, user);
                return;
            }

            ErrorHandler.notFound(ex);

        } catch (Exception e) {
            System.err.println("Unhandled error: " + e.getMessage());
            e.printStackTrace();
            ErrorHandler.sendError(ex, 500, "Internal server error");
        }
    }

    // ── /api/auth/* ───────────────────────────────────────────────────────────
    private static void routeAuth(HttpExchange ex, String method, String path) throws IOException {
        if ("POST".equals(method) && path.equals("/api/auth/register")) {
            AuthController.register(ex);
        } else if ("POST".equals(method) && path.equals("/api/auth/login")) {
            AuthController.login(ex);
        } else if ("GET".equals(method) && path.equals("/api/auth/me")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user != null) AuthController.getMe(ex, user);
        } else if ("GET".equals(method) && path.equals("/api/auth/users")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user != null && AuthMiddleware.adminOnly(ex, user)) AuthController.getAllUsers(ex);
        } else if ("POST".equals(method) && path.equals("/api/auth/users")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user != null && AuthMiddleware.adminOnly(ex, user)) AuthController.adminCreateUser(ex, user);
        } else if ("GET".equals(method) && path.equals("/api/auth/activity-log")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user != null && AuthMiddleware.adminOnly(ex, user)) AuthController.getActivityLog(ex);
        } else if (path.matches("/api/auth/users/\\d+/role") && "PATCH".equals(method)) {
            int id = extractId(path, 3);
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user != null && AuthMiddleware.adminOnly(ex, user)) AuthController.updateUserRole(ex, id, user);
        } else if (path.matches("/api/auth/users/\\d+") && "DELETE".equals(method)) {
            int id = extractId(path, 3);
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user != null && AuthMiddleware.adminOnly(ex, user)) AuthController.deleteUser(ex, id, user);
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── /api/hospitals/* ──────────────────────────────────────────────────────
    private static void routeHospitals(HttpExchange ex, String method, String path) throws IOException {
        if ("GET".equals(method) && path.equals("/api/hospitals")) {
            HospitalController.getAllHospitals(ex);
        } else if ("GET".equals(method) && path.equals("/api/hospitals/available")) {
            HospitalController.getAvailableHospitals(ex);
        } else if ("GET".equals(method) && path.equals("/api/hospitals/stats")) {
            HospitalController.getStats(ex);
        } else if ("POST".equals(method) && path.equals("/api/hospitals/add")) {
            HospitalController.addHospital(ex);
        } else if ("POST".equals(method) && path.equals("/api/hospitals/bulk-beds")) {
            HospitalController.bulkUpdateBeds(ex);
        } else if ("GET".equals(method) && path.equals("/api/hospitals/search")) {
            HospitalController.searchHospital(ex);
        } else if ("GET".equals(method) && path.equals("/api/hospitals/nearest")) {
            HospitalController.getNearestHospitals(ex);
        } else if ("GET".equals(method) && path.equals("/api/hospitals/filter")) {
            HospitalController.filterByFacility(ex);
        } else if (path.matches("/api/hospitals/\\d+/beds")) {
            int id = Integer.parseInt(path.split("/")[3]);
            if ("PATCH".equals(method)) HospitalController.updateBeds(ex, id);
            else ErrorHandler.notFound(ex);
        } else if (path.matches("/api/hospitals/\\d+/availability")) {
            int id = Integer.parseInt(path.split("/")[3]);
            if ("PATCH".equals(method)) HospitalController.updateAvailability(ex, id);
            else ErrorHandler.notFound(ex);
        } else if (path.matches("/api/hospitals/\\d+/rate")) {
            int id = Integer.parseInt(path.split("/")[3]);
            if ("POST".equals(method)) HospitalController.rateHospital(ex, id);
            else ErrorHandler.notFound(ex);
        } else if (path.matches("/api/hospitals/\\d+/history")) {
            int id = Integer.parseInt(path.split("/")[3]);
            if ("GET".equals(method)) HospitalController.getHistory(ex, id);
            else ErrorHandler.notFound(ex);
        } else if (path.matches("/api/hospitals/\\d+")) {
            int id = extractId(path, 2);
            switch (method) {
                case "GET"    -> HospitalController.getHospitalById(ex, id);
                case "PUT"    -> HospitalController.updateHospital(ex, id);
                case "DELETE" -> HospitalController.deleteHospital(ex, id);
                default       -> ErrorHandler.notFound(ex);
            }
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── /api/route-optimization/* ─────────────────────────────────────────────
    private static void routeOptimization(HttpExchange ex, String method, String path, JwtPayload user) throws IOException {
        if ("POST".equals(method) && path.equals("/api/route-optimization/calculate")) {
            RouteOptimizationController.calculateOptimalRoute(ex);
        } else if ("POST".equals(method) && path.equals("/api/route-optimization/reroute")) {
            RouteOptimizationController.dynamicReroute(ex);
        } else if ("POST".equals(method) && path.equals("/api/route-optimization/simulate")) {
            RouteOptimizationController.simulate(ex);
        } else if ("GET".equals(method) && path.equals("/api/route-optimization/history")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) RouteOptimizationController.getRouteHistory(ex);
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── /api/delivery/* (Dijkstra.java + Graph.java style) ───────────────────
    private static void routeDelivery(HttpExchange ex, String method, String path) throws IOException {
        if ("GET".equals(method) && path.equals("/api/delivery/map")) {
            String json = "{\"nodes\":" + DELIVERY_MAP.nodesToJson() +
                    ",\"edges\":" + DELIVERY_MAP.edgesToJson() + "}";
            ErrorHandler.writeResponse(ex, 200, json);
            return;
        }

        if ("POST".equals(method) && path.equals("/api/delivery/route")) {
            String body = readBody(ex);
            try {
                int start = extractInt(body, "start");
                List<Integer> stops = extractIntList(body, "stops");
                if (!DELIVERY_MAP.hasNode(start)) {
                    ErrorHandler.sendError(ex, 400, "Invalid start node: " + start);
                    return;
                }
                for (int s : stops) {
                    if (!DELIVERY_MAP.hasNode(s)) {
                        ErrorHandler.sendError(ex, 400, "Invalid stop node: " + s);
                        return;
                    }
                }
                Dijkstra.RouteResult result = Dijkstra.greedyRoute(DELIVERY_MAP, start, stops);
                ErrorHandler.writeResponse(ex, 200, result.toJson());
                return;
            } catch (Exception e) {
                ErrorHandler.sendError(ex, 400, "Bad request: " + e.getMessage());
                return;
            }
        }

        ErrorHandler.notFound(ex);
    }

    private static void routeTransport(HttpExchange ex, String method, String path) throws IOException {
        if ("GET".equals(method) && path.equals("/api/transport/map")) {
            String json = "{\"nodes\":" + TRANSPORT_MAP.nodesToJson() +
                    ",\"edges\":" + TRANSPORT_MAP.edgesToJson() + "}";
            ErrorHandler.writeResponse(ex, 200, json);
            return;
        }

        if ("POST".equals(method) && path.equals("/api/transport/route")) {
            String body = readBody(ex);
            try {
                int start = extractInt(body, "start");
                List<Integer> stops = extractIntList(body, "stops");
                if (!TRANSPORT_MAP.hasNode(start)) {
                    ErrorHandler.sendError(ex, 400, "Invalid start node: " + start);
                    return;
                }
                for (int s : stops) {
                    if (!TRANSPORT_MAP.hasNode(s)) {
                        ErrorHandler.sendError(ex, 400, "Invalid stop node: " + s);
                        return;
                    }
                }
                Dijkstra.RouteResult result = Dijkstra.greedyRoute(TRANSPORT_MAP, start, stops);
                ErrorHandler.writeResponse(ex, 200, result.toJson());
                return;
            } catch (Exception e) {
                ErrorHandler.sendError(ex, 400, "Bad request: " + e.getMessage());
                return;
            }
        }

        ErrorHandler.notFound(ex);
    }

    // ── /api/traffic-analysis/* ───────────────────────────────────────────────
    private static void routeTraffic(HttpExchange ex, String method, String path, JwtPayload user) throws IOException {
        if ("GET".equals(method) && path.startsWith("/api/traffic-analysis/traffic/")) {
            String roadId = path.substring("/api/traffic-analysis/traffic/".length());
            TrafficController.getTrafficData(ex, roadId);
        } else if ("GET".equals(method) && path.startsWith("/api/traffic-analysis/road-conditions/")) {
            String roadId = path.substring("/api/traffic-analysis/road-conditions/".length());
            TrafficController.getRoadConditions(ex, roadId);
        } else if ("POST".equals(method) && path.equals("/api/traffic-analysis/roadblock/report")) {
            TrafficController.reportRoadblock(ex);
        } else if ("POST".equals(method) && path.equals("/api/traffic-analysis/traffic/update")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) TrafficController.updateTrafficCondition(ex);
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── /api/emergency/* ──────────────────────────────────────────────────────
    private static void routeEmergency(HttpExchange ex, String method, String path) throws IOException {
        if ("POST".equals(method) && path.equals("/api/emergency/create")) {
            // Anyone (including unauthenticated) can create an emergency request
            EmergencyController.createEmergencyRequest(ex);
        } else if ("GET".equals(method) && path.equals("/api/emergency/next")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user == null) return;
            if (AuthMiddleware.requireRole(ex, user, "admin", "dispatcher", "driver"))
                EmergencyController.getNextRequest(ex);
        } else if (("POST".equals(method) || "PUT".equals(method)) && path.equals("/api/emergency/update-status")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user == null) return;
            if (AuthMiddleware.requireRole(ex, user, "admin", "dispatcher", "driver"))
                EmergencyController.updateRequestStatus(ex);
        } else if ("GET".equals(method) && path.equals("/api/emergency/all")) {
            JwtPayload user = AuthMiddleware.verifyToken(ex);
            if (user == null) return;
            // All authenticated roles can view emergency list
            EmergencyController.getAllRequests(ex);
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── /api/road-scoring/* ───────────────────────────────────────────────────
    private static void routeRoadScoring(HttpExchange ex, String method, String path, JwtPayload user) throws IOException {
        if ("GET".equals(method) && path.equals("/api/road-scoring/all")) {
            RoadScoringController.getAllRoadScores(ex);
        } else if ("GET".equals(method) && path.equals("/api/road-scoring/flagged")) {
            RoadScoringController.getFlaggedRoads(ex);
        } else if ("GET".equals(method) && path.equals("/api/road-scoring/stats")) {
            RoadScoringController.getRoadStats(ex);
        } else if ("POST".equals(method) && path.equals("/api/road-scoring/score")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) RoadScoringController.scoreRoad(ex);
        } else if ("POST".equals(method) && path.equals("/api/road-scoring/threshold-check")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) RoadScoringController.thresholdCheck(ex);
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── /api/alerts/* ─────────────────────────────────────────────────────────
    private static void routeAlerts(HttpExchange ex, String method, String path, JwtPayload user) throws IOException {
        if ("GET".equals(method) && path.equals("/api/alerts/active")) {
            AlertController.getActiveAlerts(ex);
        } else if ("GET".equals(method) && path.equals("/api/alerts/stats")) {
            AlertController.getAlertStats(ex);
        } else if ("GET".equals(method) && path.equals("/api/alerts/all")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) AlertController.getAllAlerts(ex);
        } else if ("GET".equals(method) && path.equals("/api/alerts/government-report")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) AlertController.generateGovernmentReport(ex);
        } else if ("POST".equals(method) && path.equals("/api/alerts/create")) {
            if (AuthMiddleware.adminOrDispatch(ex, user)) AlertController.createAlert(ex);
        } else if ("PATCH".equals(method) && path.matches("/api/alerts/\\d+/acknowledge")) {
            if (AuthMiddleware.adminOrDispatch(ex, user))
                AlertController.acknowledgeAlert(ex, extractId(path, 2));
        } else if ("PATCH".equals(method) && path.matches("/api/alerts/\\d+/resolve")) {
            if (AuthMiddleware.adminOnly(ex, user))
                AlertController.resolveAlert(ex, extractId(path, 2));
        } else {
            ErrorHandler.notFound(ex);
        }
    }

    // ── Extract numeric ID from path segment ─────────────────────────────────
    // e.g. /api/alerts/42/resolve → segment 2 → 42
    private static int extractId(String path, int segmentFromEnd) {
        String[] parts = path.split("/");
        return Integer.parseInt(parts[parts.length - segmentFromEnd]);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int extractInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) throw new IllegalArgumentException("Missing field: " + key);
        idx += pattern.length();
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return Integer.parseInt(json.substring(idx, end).trim());
    }

    private static List<Integer> extractIntList(String json, String key) {
        String pattern = "\"" + key + "\":[";
        int start = json.indexOf(pattern);
        if (start < 0) return Collections.emptyList();
        start += pattern.length();
        int end = json.indexOf("]", start);
        if (end < 0) return Collections.emptyList();
        String inner = json.substring(start, end).trim();
        if (inner.isEmpty()) return Collections.emptyList();
        List<Integer> list = new ArrayList<>();
        for (String s : inner.split(",")) {
            String v = s.trim();
            if (!v.isEmpty()) list.add(Integer.parseInt(v));
        }
        return list;
    }

    private static String getProp(String key, String def) {
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
}
