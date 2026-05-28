package hospital;

import config.DatabaseConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * HospitalRepository.java
 * All SQL operations for the hospitals table.
 * Uses the shared DatabaseConfig connection (config package).
 */
public class HospitalRepository {

    private static final String SELECT_COLS =
        "id, name, address, latitude, longitude, contact, facilities, " +
        "total_beds, available_beds, is_available, operating_hours, rating";

    // ── Fetch all hospitals with optional sort ────────────────────────────────
    // sortBy: "name" (default), "rating", "available_beds", "total_beds", "occupancy"
    public static List<Hospital> findAll() throws SQLException {
        return findAll("name");
    }

    public static List<Hospital> findAll(String sortBy) throws SQLException {
        List<Hospital> list = new ArrayList<>();
        String orderClause;
        switch (sortBy == null ? "name" : sortBy) {
            case "rating"         -> orderClause = "rating DESC, name";
            case "available_beds" -> orderClause = "available_beds DESC, name";
            case "total_beds"     -> orderClause = "total_beds DESC, name";
            case "occupancy"      -> orderClause = "(total_beds - available_beds) / NULLIF(total_beds,0) DESC, name";
            default               -> orderClause = "name";
        }
        String sql = "SELECT " + SELECT_COLS + " FROM hospitals ORDER BY " + orderClause;
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    // ── Fetch only available hospitals ────────────────────────────────────────
    public static List<Hospital> findAvailable() throws SQLException {
        List<Hospital> list = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLS +
                     " FROM hospitals WHERE is_available = TRUE ORDER BY available_beds DESC";
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    // ── Fetch hospitals that have a specific facility ─────────────────────────
    public static List<Hospital> findByFacility(String facility) throws SQLException {
        List<Hospital> list = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLS +
                     " FROM hospitals WHERE JSON_SEARCH(facilities, 'one', ?) IS NOT NULL ORDER BY name";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, facility);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    // ── Fetch single hospital by ID ───────────────────────────────────────────
    public static Hospital findById(int id) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM hospitals WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    // ── Insert a new hospital ─────────────────────────────────────────────────
    public static int insert(Hospital h) throws SQLException {
        String sql = "INSERT INTO hospitals " +
                     "(name, address, latitude, longitude, contact, facilities, " +
                     " total_beds, available_beds, is_available, operating_hours, rating) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, h.name);
            ps.setString(2, h.address);
            ps.setDouble(3, h.latitude);
            ps.setDouble(4, h.longitude);
            ps.setString(5, h.contact);
            ps.setString(6, h.facilities == null ? "[]" : h.facilities);
            ps.setInt(7, h.totalBeds);
            ps.setInt(8, h.availableBeds);
            ps.setBoolean(9, h.isAvailable);
            ps.setString(10, h.operatingHours == null ? "24/7" : h.operatingHours);
            ps.setDouble(11, h.rating);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    // ── Update an existing hospital ───────────────────────────────────────────
    public static boolean update(Hospital h) throws SQLException {
        String sql = "UPDATE hospitals SET name=?, address=?, latitude=?, longitude=?, " +
                     "contact=?, facilities=?, total_beds=?, available_beds=?, " +
                     "is_available=?, operating_hours=?, rating=? WHERE id=?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, h.name);
            ps.setString(2, h.address);
            ps.setDouble(3, h.latitude);
            ps.setDouble(4, h.longitude);
            ps.setString(5, h.contact);
            ps.setString(6, h.facilities == null ? "[]" : h.facilities);
            ps.setInt(7, h.totalBeds);
            ps.setInt(8, h.availableBeds);
            ps.setBoolean(9, h.isAvailable);
            ps.setString(10, h.operatingHours == null ? "24/7" : h.operatingHours);
            ps.setDouble(11, h.rating);
            ps.setInt(12, h.id);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Update bed availability only (lightweight endpoint) ───────────────────
    public static boolean updateBeds(int id, int availableBeds) throws SQLException {
        String sql = "UPDATE hospitals SET available_beds=?, " +
                     "is_available=(available_beds > 0) WHERE id=?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, availableBeds);
            ps.setInt(2, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) logHistory(id, "beds_updated", null, String.valueOf(availableBeds), null);
            return ok;
        }
    }

    // ── Update availability status only ──────────────────────────────────────
    public static boolean updateAvailability(int id, boolean isAvailable) throws SQLException {
        String sql = "UPDATE hospitals SET is_available=? WHERE id=?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setBoolean(1, isAvailable);
            ps.setInt(2, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) logHistory(id, "availability_changed", null, String.valueOf(isAvailable), null);
            return ok;
        }
    }

    // ── Bulk update beds for multiple hospitals ───────────────────────────────
    // updates: list of int[]{hospitalId, newAvailableBeds}
    public static int bulkUpdateBeds(List<int[]> updates) throws SQLException {
        String sql = "UPDATE hospitals SET available_beds=?, is_available=(? > 0) WHERE id=?";
        int count = 0;
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            for (int[] u : updates) {
                ps.setInt(1, u[1]);
                ps.setInt(2, u[1]);
                ps.setInt(3, u[0]);
                count += ps.executeUpdate();
                logHistory(u[0], "beds_updated", null, String.valueOf(u[1]), "bulk update");
            }
        }
        return count;
    }

    // ── Submit a rating and recompute running average ─────────────────────────
    public static double submitRating(int hospitalId, double rating) throws SQLException {
        // Insert individual rating
        String ins = "INSERT INTO hospital_ratings (hospital_id, rating) VALUES (?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(ins)) {
            ps.setInt(1, hospitalId);
            ps.setDouble(2, rating);
            ps.executeUpdate();
        }
        // Recompute average
        String avg = "SELECT ROUND(AVG(rating), 1) AS avg_r FROM hospital_ratings WHERE hospital_id=?";
        double newAvg = rating;
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(avg)) {
            ps.setInt(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) newAvg = rs.getDouble("avg_r");
            }
        }
        // Update hospital rating
        String upd = "UPDATE hospitals SET rating=? WHERE id=?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(upd)) {
            ps.setDouble(1, newAvg);
            ps.setInt(2, hospitalId);
            ps.executeUpdate();
        }
        logHistory(hospitalId, "rating_submitted", null, String.format("%.1f", newAvg), null);
        return newAvg;
    }

    // ── Get audit history for a hospital ─────────────────────────────────────
    public static String getHistory(int hospitalId) throws SQLException {
        String sql = "SELECT id, change_type, old_value, new_value, note, changed_at " +
                     "FROM hospital_history WHERE hospital_id=? ORDER BY changed_at DESC LIMIT 50";
        StringBuilder sb = new StringBuilder("[");
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append(String.format(
                        "{\"id\":%d,\"changeType\":\"%s\",\"oldValue\":%s,\"newValue\":%s," +
                        "\"note\":%s,\"changedAt\":\"%s\"}",
                        rs.getInt("id"),
                        Hospital.escapeJson(rs.getString("change_type")),
                        rs.getString("old_value") == null ? "null" : "\"" + Hospital.escapeJson(rs.getString("old_value")) + "\"",
                        rs.getString("new_value") == null ? "null" : "\"" + Hospital.escapeJson(rs.getString("new_value")) + "\"",
                        rs.getString("note") == null ? "null" : "\"" + Hospital.escapeJson(rs.getString("note")) + "\"",
                        rs.getTimestamp("changed_at")
                    ));
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Internal: write an audit log entry ───────────────────────────────────
    private static void logHistory(int hospitalId, String changeType,
                                   String oldVal, String newVal, String note) {
        try {
            // Fetch hospital name for the log
            String nameSql = "SELECT name FROM hospitals WHERE id=?";
            String hName = null;
            try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(nameSql)) {
                ps.setInt(1, hospitalId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) hName = rs.getString("name");
                }
            }
            String ins = "INSERT INTO hospital_history " +
                         "(hospital_id, hospital_name, change_type, old_value, new_value, note) " +
                         "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(ins)) {
                ps.setInt(1, hospitalId);
                ps.setString(2, hName);
                ps.setString(3, changeType);
                ps.setString(4, oldVal);
                ps.setString(5, newVal);
                ps.setString(6, note);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Warning: could not write hospital history: " + e.getMessage());
        }
    }

    // ── Delete a hospital by ID ───────────────────────────────────────────────
    public static boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM hospitals WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Aggregate stats ───────────────────────────────────────────────────────
    public static String getStats() throws SQLException {
        String sql = "SELECT COUNT(*) AS total, " +
                     "SUM(is_available) AS available_count, " +
                     "SUM(total_beds) AS total_beds, " +
                     "SUM(available_beds) AS available_beds, " +
                     "ROUND(AVG(rating), 1) AS avg_rating " +
                     "FROM hospitals";
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return String.format(
                    "{\"total\":%d,\"availableCount\":%d," +
                    "\"totalBeds\":%d,\"availableBeds\":%d,\"avgRating\":%.1f}",
                    rs.getInt("total"),
                    rs.getInt("available_count"),
                    rs.getInt("total_beds"),
                    rs.getInt("available_beds"),
                    rs.getDouble("avg_rating")
                );
            }
        }
        return "{\"total\":0,\"availableCount\":0,\"totalBeds\":0,\"availableBeds\":0,\"avgRating\":0.0}";
    }

    // ── Map a ResultSet row to a Hospital object ──────────────────────────────
    static Hospital mapRow(ResultSet rs) throws SQLException {
        return new Hospital(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getString("contact"),
            rs.getString("facilities"),
            rs.getInt("total_beds"),
            rs.getInt("available_beds"),
            rs.getBoolean("is_available"),
            rs.getString("operating_hours"),
            rs.getDouble("rating")
        );
    }
}
