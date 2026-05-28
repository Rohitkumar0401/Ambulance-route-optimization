package hospital;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.*;

/**
 * HospitalController.java
 * HTTP request handlers for all /api/hospitals/* routes.
 *
 * Routes:
 *   GET    /api/hospitals                          → getAllHospitals
 *   GET    /api/hospitals/available                → getAvailableHospitals
 *   GET    /api/hospitals/stats                    → getStats
 *   GET    /api/hospitals/search?name=             → searchHospital
 *   GET    /api/hospitals/nearest?lat=&lon=&count= → getNearestHospitals
 *   GET    /api/hospitals/filter?facility=         → filterByFacility
 *   GET    /api/hospitals/:id                      → getHospitalById
 *   GET    /api/hospitals/:id/history              → getHistory
 *   POST   /api/hospitals/add                      → addHospital
 *   POST   /api/hospitals/bulk-beds                → bulkUpdateBeds
 *   POST   /api/hospitals/:id/rate                 → rateHospital
 *   PUT    /api/hospitals/:id                      → updateHospital
 *   PATCH  /api/hospitals/:id/beds                 → updateBeds
 *   PATCH  /api/hospitals/:id/availability         → updateAvailability
 *   DELETE /api/hospitals/:id                      → deleteHospital
 */
public class HospitalController {

    // ── GET /api/hospitals ────────────────────────────────────────────────────
    // Optional query param: ?sort=name|rating|available_beds|total_beds|occupancy
    public static void getAllHospitals(HttpExchange ex) throws IOException {
        try {
            String sort = getQueryParam(ex.getRequestURI(), "sort");
            List<Hospital> hospitals = HospitalRepository.findAll(sort);
            sendJson(ex, 200, "{\"success\":true,\"data\":" + toJsonArray(hospitals) +
                              ",\"count\":" + hospitals.size() + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/available ──────────────────────────────────────────
    public static void getAvailableHospitals(HttpExchange ex) throws IOException {
        try {
            List<Hospital> hospitals = HospitalRepository.findAvailable();
            sendJson(ex, 200, "{\"success\":true,\"data\":" + toJsonArray(hospitals) +
                              ",\"count\":" + hospitals.size() + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/stats ──────────────────────────────────────────────
    public static void getStats(HttpExchange ex) throws IOException {
        try {
            String stats = HospitalRepository.getStats();
            sendJson(ex, 200, "{\"success\":true,\"data\":" + stats + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/filter?facility= ───────────────────────────────────
    public static void filterByFacility(HttpExchange ex) throws IOException {
        String facility = getQueryParam(ex.getRequestURI(), "facility");
        if (facility == null || facility.isEmpty()) {
            sendError(ex, 400, "Query parameter 'facility' is required");
            return;
        }
        try {
            List<Hospital> hospitals = HospitalRepository.findByFacility(facility);
            sendJson(ex, 200, "{\"success\":true,\"data\":" + toJsonArray(hospitals) +
                              ",\"count\":" + hospitals.size() +
                              ",\"facility\":\"" + Hospital.escapeJson(facility) + "\"}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/:id ────────────────────────────────────────────────
    public static void getHospitalById(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }
            sendJson(ex, 200, "{\"success\":true,\"data\":" + h.toJson() + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/hospitals/add ───────────────────────────────────────────────
    public static void addHospital(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        Map<String, String> data = parseJson(body);

        String name     = data.get("name");
        String address  = data.get("address");
        String latStr   = data.get("latitude");
        String lonStr   = data.get("longitude");

        if (name == null || name.isEmpty() || address == null || address.isEmpty()
                || latStr == null || lonStr == null) {
            sendError(ex, 400, "name, address, latitude, and longitude are required");
            return;
        }

        double lat, lon;
        try {
            lat = Double.parseDouble(latStr);
            lon = Double.parseDouble(lonStr);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "latitude and longitude must be valid numbers");
            return;
        }

        // Validate coordinate ranges
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            sendError(ex, 400, "latitude must be -90 to 90, longitude -180 to 180");
            return;
        }

        String contact        = data.getOrDefault("contact", "");
        String facilities     = normaliseFacilities(data.getOrDefault("facilities", "[]"));
        String operatingHours = data.getOrDefault("operatingHours", "24/7");
        int    totalBeds      = parseIntSafe(data.get("totalBeds"), 0);
        int    availableBeds  = parseIntSafe(data.get("availableBeds"), totalBeds);
        double rating         = parseDoubleSafe(data.get("rating"), 0.0);
        boolean isAvailable   = !"false".equalsIgnoreCase(data.getOrDefault("isAvailable", "true"));

        // Clamp rating
        rating = Math.max(0.0, Math.min(5.0, rating));
        // availableBeds cannot exceed totalBeds
        availableBeds = Math.min(availableBeds, totalBeds);

        try {
            Hospital h = new Hospital(0, name, address, lat, lon, contact, facilities,
                                      totalBeds, availableBeds, isAvailable, operatingHours, rating);
            int newId = HospitalRepository.insert(h);
            sendJson(ex, 201,
                "{\"success\":true,\"hospitalId\":" + newId +
                ",\"message\":\"Hospital added successfully\"}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── PUT /api/hospitals/:id ────────────────────────────────────────────────
    public static void updateHospital(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }

            String body = readBody(ex);
            Map<String, String> data = parseJson(body);

            h.name           = data.getOrDefault("name",           h.name);
            h.address        = data.getOrDefault("address",        h.address);
            h.contact        = data.getOrDefault("contact",        h.contact);
            h.operatingHours = data.getOrDefault("operatingHours", h.operatingHours);

            if (data.containsKey("facilities"))
                h.facilities = normaliseFacilities(data.get("facilities"));
            if (data.containsKey("latitude"))
                h.latitude = Double.parseDouble(data.get("latitude"));
            if (data.containsKey("longitude"))
                h.longitude = Double.parseDouble(data.get("longitude"));
            if (data.containsKey("totalBeds"))
                h.totalBeds = parseIntSafe(data.get("totalBeds"), h.totalBeds);
            if (data.containsKey("availableBeds"))
                h.availableBeds = Math.min(parseIntSafe(data.get("availableBeds"), h.availableBeds), h.totalBeds);
            if (data.containsKey("isAvailable"))
                h.isAvailable = !"false".equalsIgnoreCase(data.get("isAvailable"));
            if (data.containsKey("rating"))
                h.rating = Math.max(0.0, Math.min(5.0, parseDoubleSafe(data.get("rating"), h.rating)));

            if (HospitalRepository.update(h)) {
                sendJson(ex, 200, "{\"success\":true,\"message\":\"Hospital updated successfully\"}");
            } else {
                sendError(ex, 500, "Update failed");
            }
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── PATCH /api/hospitals/:id/beds ─────────────────────────────────────────
    public static void updateBeds(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }

            String body = readBody(ex);
            Map<String, String> data = parseJson(body);
            String availStr = data.get("availableBeds");
            if (availStr == null) { sendError(ex, 400, "availableBeds is required"); return; }

            int avail = parseIntSafe(availStr, -1);
            if (avail < 0) { sendError(ex, 400, "availableBeds must be a non-negative integer"); return; }
            if (avail > h.totalBeds) {
                sendError(ex, 400, "availableBeds cannot exceed totalBeds (" + h.totalBeds + ")");
                return;
            }

            HospitalRepository.updateBeds(id, avail);
            sendJson(ex, 200,
                "{\"success\":true,\"message\":\"Bed count updated\"," +
                "\"availableBeds\":" + avail + ",\"totalBeds\":" + h.totalBeds + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── PATCH /api/hospitals/:id/availability ─────────────────────────────────
    public static void updateAvailability(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }

            String body = readBody(ex);
            Map<String, String> data = parseJson(body);
            String val = data.get("isAvailable");
            if (val == null) { sendError(ex, 400, "isAvailable (true/false) is required"); return; }

            boolean avail = !"false".equalsIgnoreCase(val);
            HospitalRepository.updateAvailability(id, avail);
            sendJson(ex, 200,
                "{\"success\":true,\"message\":\"Availability updated\"," +
                "\"isAvailable\":" + avail + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── DELETE /api/hospitals/:id ─────────────────────────────────────────────
    public static void deleteHospital(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }
            HospitalRepository.delete(id);
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Hospital deleted successfully\"}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/hospitals/:id/rate ──────────────────────────────────────────
    // Body: { "rating": 4.5 }
    public static void rateHospital(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }

            String body = readBody(ex);
            Map<String, String> data = parseJson(body);
            String ratingStr = data.get("rating");
            if (ratingStr == null) { sendError(ex, 400, "rating (0.0–5.0) is required"); return; }

            double rating = parseDoubleSafe(ratingStr, -1);
            if (rating < 0 || rating > 5) {
                sendError(ex, 400, "rating must be between 0.0 and 5.0");
                return;
            }

            double newAvg = HospitalRepository.submitRating(id, rating);
            sendJson(ex, 200,
                "{\"success\":true,\"message\":\"Rating submitted\"," +
                "\"newAverageRating\":" + String.format("%.1f", newAvg) + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/hospitals/bulk-beds ─────────────────────────────────────────
    // Body: { "updates": [{ "id": 1, "availableBeds": 30 }, ...] }
    public static void bulkUpdateBeds(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        // Parse array of {id, availableBeds} objects
        List<int[]> updates = new ArrayList<>();
        Pattern entryPat = Pattern.compile("\\{[^}]*\"id\"\\s*:\\s*(\\d+)[^}]*\"availableBeds\"\\s*:\\s*(\\d+)[^}]*\\}");
        Matcher m = entryPat.matcher(body);
        while (m.find()) {
            updates.add(new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) });
        }
        // Also try reversed field order
        Pattern entryPat2 = Pattern.compile("\\{[^}]*\"availableBeds\"\\s*:\\s*(\\d+)[^}]*\"id\"\\s*:\\s*(\\d+)[^}]*\\}");
        Matcher m2 = entryPat2.matcher(body);
        while (m2.find()) {
            int id = Integer.parseInt(m2.group(2));
            int beds = Integer.parseInt(m2.group(1));
            boolean already = updates.stream().anyMatch(u -> u[0] == id);
            if (!already) updates.add(new int[]{ id, beds });
        }

        if (updates.isEmpty()) {
            sendError(ex, 400, "updates array with {id, availableBeds} entries is required");
            return;
        }

        // Validate each hospital exists and beds don't exceed total
        for (int[] u : updates) {
            try {
                Hospital h = HospitalRepository.findById(u[0]);
                if (h == null) { sendError(ex, 404, "Hospital id " + u[0] + " not found"); return; }
                if (u[1] < 0)  { sendError(ex, 400, "availableBeds must be >= 0 for id " + u[0]); return; }
                if (u[1] > h.totalBeds) u[1] = h.totalBeds; // clamp silently
            } catch (SQLException e) {
                sendError(ex, 500, e.getMessage()); return;
            }
        }

        try {
            int updated = HospitalRepository.bulkUpdateBeds(updates);
            sendJson(ex, 200,
                "{\"success\":true,\"message\":\"Bulk bed update complete\"," +
                "\"updatedCount\":" + updated + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/:id/history ────────────────────────────────────────
    public static void getHistory(HttpExchange ex, int id) throws IOException {
        try {
            Hospital h = HospitalRepository.findById(id);
            if (h == null) { sendError(ex, 404, "Hospital not found"); return; }
            String history = HospitalRepository.getHistory(id);
            sendJson(ex, 200,
                "{\"success\":true,\"hospitalId\":" + id +
                ",\"hospitalName\":\"" + Hospital.escapeJson(h.name) + "\"" +
                ",\"data\":" + history + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/search?name= ──────────────────────────────────────
    public static void searchHospital(HttpExchange ex) throws IOException {
        String query = getQueryParam(ex.getRequestURI(), "name");
        if (query == null || query.isEmpty()) {
            sendError(ex, 400, "Query parameter 'name' is required");
            return;
        }
        try {
            List<Hospital> all = HospitalRepository.findAll();
            Hospital result = HospitalSearch.binarySearch(all, query);
            if (result == null) {
                sendJson(ex, 200, "{\"success\":true,\"data\":null,\"message\":\"No hospital found\"}");
            } else {
                sendJson(ex, 200, "{\"success\":true,\"data\":" + result.toJson() + "}");
            }
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/hospitals/nearest?lat=&lon=&count=&availableOnly= ────────────
    public static void getNearestHospitals(HttpExchange ex) throws IOException {
        URI uri = ex.getRequestURI();
        String latStr        = getQueryParam(uri, "lat");
        String lonStr        = getQueryParam(uri, "lon");
        String countStr      = getQueryParam(uri, "count");
        String availableOnly = getQueryParam(uri, "availableOnly");

        if (latStr == null || lonStr == null) {
            sendError(ex, 400, "Query parameters 'lat' and 'lon' are required");
            return;
        }

        double userLat, userLon;
        int count = 5;
        try {
            userLat = Double.parseDouble(latStr);
            userLon = Double.parseDouble(lonStr);
            if (countStr != null) count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "lat, lon, and count must be valid numbers");
            return;
        }

        try {
            List<Hospital> all = "true".equalsIgnoreCase(availableOnly)
                ? HospitalRepository.findAvailable()
                : HospitalRepository.findAll();

            List<Hospital> nearest = HospitalSearch.findNearestHospitals(userLat, userLon, all, count);

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < nearest.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(nearest.get(i).toJsonWithDistance());
            }
            sb.append("]");
            sendJson(ex, 200, "{\"success\":true,\"data\":" + sb + ",\"count\":" + nearest.size() + "}");
        } catch (SQLException e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String toJsonArray(List<Hospital> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    private static String normaliseFacilities(String raw) {
        if (raw == null || raw.isBlank()) return "[]";
        raw = raw.trim();
        if (raw.startsWith("[")) return raw;
        // comma-separated string → JSON array
        String[] parts = raw.split(",");
        StringBuilder arr = new StringBuilder("[");
        for (String p : parts) {
            String f = p.trim();
            if (!f.isEmpty()) {
                if (arr.length() > 1) arr.append(",");
                arr.append("\"").append(Hospital.escapeJson(f)).append("\"");
            }
        }
        arr.append("]");
        return arr.toString();
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDoubleSafe(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, "{\"success\":false,\"error\":\"" + Hospital.escapeJson(message) + "\"}");
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        Pattern strPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher strMat = strPat.matcher(json);
        while (strMat.find()) map.put(strMat.group(1), strMat.group(2));
        Pattern numPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher numMat = numPat.matcher(json);
        while (numMat.find()) map.putIfAbsent(numMat.group(1), numMat.group(2));
        Pattern arrPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\\[[^\\]]*\\])");
        Matcher arrMat = arrPat.matcher(json);
        while (arrMat.find()) map.put(arrMat.group(1), arrMat.group(2));
        Pattern boolPat = Pattern.compile("\"(\\w+)\"\\s*:\\s*(true|false)");
        Matcher boolMat = boolPat.matcher(json);
        while (boolMat.find()) map.putIfAbsent(boolMat.group(1), boolMat.group(2));
        return map;
    }

    static String getQueryParam(URI uri, String key) {
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
