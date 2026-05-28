package config;

import java.io.*;
import java.nio.file.*;
import java.sql.*;

/**
 * DbInit.java
 * Creates the database, runs schema.sql, and inserts sample data.
 * Converted from backend/config/dbInit.js
 *
 * JS:
 *   async function initializeDatabase() { ... }
 *   async function insertSampleData(connection) { ... }
 *
 * Run: java -cp out:mysql-connector-j.jar config.DbInit
 */
public class DbInit {

    public static void main(String[] args) {
        try {
            initializeDatabase();
        } catch (Exception e) {
            System.err.println("❌ Database initialization error: " + e.getMessage());
            System.exit(1);
        }
    }

    // ── Main init flow ────────────────────────────────────────────────────────
    // Mirrors JS initializeDatabase()
    public static void initializeDatabase() throws Exception {
        String host     = getProp("DB_HOST",     "localhost");
        String user     = getProp("DB_USER",     "root");
        String password = getProp("DB_PASSWORD", "");
        String dbName   = getProp("DB_NAME",     "ambulance_optimization");

        // Connect without specifying a database first (mirrors JS multipleStatements connection)
        String url = "jdbc:mysql://" + host + ":3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("✅ Connected to MySQL server");

            // CREATE DATABASE IF NOT EXISTS
            conn.createStatement().executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `" + dbName + "`"
            );
            conn.createStatement().executeUpdate("USE `" + dbName + "`");
            System.out.println("✅ Database '" + dbName + "' ready");

            // Run schema.sql
            runSchema(conn);
            System.out.println("✅ Schema initialized");

            // Insert sample data
            insertSampleData(conn);
            System.out.println("✅ Database initialization complete!");
        }
    }

    // ── Execute schema.sql statement by statement ─────────────────────────────
    // Mirrors JS: schema.split(';').forEach(stmt => connection.query(stmt))
    private static void runSchema(Connection conn) throws Exception {
        // Resolve schema path relative to this class file location
        String schemaPath = System.getProperty("schema.path",
            "backend/database/schema.sql");

        String schema = new String(Files.readAllBytes(Paths.get(schemaPath)));

        // Remove SQL comments (mirrors JS .replace(/--[^\n]*/g,''))
        schema = schema.replaceAll("--[^\n]*", "")
                       .replaceAll("/\\*[\\s\\S]*?\\*/", "");

        String[] statements = schema.split(";");
        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.length() <= 5) continue;
            try {
                conn.createStatement().executeUpdate(stmt);
            } catch (SQLException e) {
                // Ignore "already exists" / "Duplicate" errors (mirrors JS catch)
                if (!e.getMessage().contains("already exists") &&
                    !e.getMessage().contains("Duplicate")) {
                    throw e;
                }
            }
        }
    }

    // ── Insert sample data ────────────────────────────────────────────────────
    // Mirrors JS insertSampleData()
    private static void insertSampleData(Connection conn) throws SQLException {
        // Hospitals
        ResultSet hCount = conn.createStatement()
            .executeQuery("SELECT COUNT(*) as c FROM hospitals");
        hCount.next();
        if (hCount.getInt("c") == 0) {
            conn.createStatement().executeUpdate(
                "INSERT INTO hospitals (name, address, latitude, longitude, contact, facilities, total_beds, available_beds, is_available, operating_hours, rating) VALUES " +
                "('City General Hospital',    '123 Main St, City Center',    28.6139, 77.2090, '+91-1234567890', '[\"Emergency\",\"ICU\",\"Surgery\",\"Trauma Center\"]',    200, 45, TRUE,  '24/7',        4.5)," +
                "('Rural Health Center',      '456 Village Rd, Remote Area', 28.5355, 77.3910, '+91-0987654321', '[\"Emergency\",\"Basic Care\",\"Maternity\"]',             60,  12, TRUE,  '08:00-20:00', 3.8)," +
                "('District Medical College', '789 College Rd, District HQ', 28.7041, 77.1025, '+91-1122334455', '[\"Emergency\",\"ICU\",\"Surgery\",\"Trauma Center\",\"Neurology\"]', 500, 120, TRUE, '24/7', 4.7)," +
                "('North City Hospital',      '12 North Ave, Sector 5',      28.6800, 77.1500, '+91-9988776655', '[\"Emergency\",\"ICU\",\"Cardiology\"]',                   150, 0,  FALSE, '24/7',        4.2)," +
                "('South Care Clinic',        '34 South Rd, Sector 12',      28.5700, 77.2500, '+91-8877665544', '[\"Emergency\",\"Basic Care\",\"Pediatrics\"]',            80,  30, TRUE,  '07:00-22:00', 4.0)"
            );
            System.out.println("  → Sample hospitals inserted");
        }

        // Seed users: admin, dispatcher, driver
        ResultSet uCount = conn.createStatement()
            .executeQuery("SELECT COUNT(*) as c FROM users");
        uCount.next();
        if (uCount.getInt("c") == 0) {
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder enc =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(8);

            String[][] users = {
                {"admin",      "admin@ambulance.com",      enc.encode("admin123"),    "admin"},
                {"dispatcher", "dispatcher@ambulance.com", enc.encode("dispatch123"), "dispatcher"},
                {"driver",     "driver@ambulance.com",     enc.encode("driver123"),   "driver"},
            };

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)"
            );
            for (String[] u : users) {
                ps.setString(1, u[0]); ps.setString(2, u[1]);
                ps.setString(3, u[2]); ps.setString(4, u[3]);
                ps.executeUpdate();
            }
            System.out.println("  → Users created: admin / dispatcher / driver");
        }

        // Road scores
        ResultSet rCount = conn.createStatement()
            .executeQuery("SELECT COUNT(*) as c FROM road_scores");
        rCount.next();
        if (rCount.getInt("c") == 0) {
            conn.createStatement().executeUpdate(
                "INSERT INTO road_scores (road_id, road_name, latitude, longitude, road_quality, terrain_difficulty, congestion_level, average_speed, incident_count, weather_factor, composite_score, flag_status) VALUES " +
                "('RD001','NH-48 Main Highway',        28.6200,77.2100,1.1,1.0,0.30,55,1,1.0,18,'good')," +
                "('RD002','Village Road Sector-7',     28.5400,77.3800,1.8,1.4,0.20,30,3,1.1,62,'warning')," +
                "('RD003','Mountain Pass Route',       28.4900,77.4500,2.0,1.9,0.10,15,5,1.3,85,'critical')," +
                "('RD004','City Ring Road',            28.6500,77.1800,1.2,1.0,0.70,25,2,1.0,58,'warning')," +
                "('RD005','District Highway NH-58',    28.7100,77.0900,1.1,1.1,0.25,50,0,1.0,20,'good')," +
                "('RD006','Rural Dirt Track',          28.5100,77.4200,2.1,1.7,0.05,12,7,1.2,90,'critical')," +
                "('RD007','Expressway Bypass',         28.6300,77.2400,1.0,1.0,0.40,80,0,1.0,15,'good')," +
                "('RD008','Flood-Prone Road Sector-3', 28.5800,77.3200,1.6,1.3,0.50,20,4,1.5,72,'critical')"
            );
            System.out.println("  → Sample road scores inserted");
        }

        // Alerts
        ResultSet aCount = conn.createStatement()
            .executeQuery("SELECT COUNT(*) as c FROM alerts");
        aCount.next();
        if (aCount.getInt("c") == 0) {
            conn.createStatement().executeUpdate(
                "INSERT INTO alerts (road_id, road_name, alert_type, severity, message, status) VALUES " +
                "('RD003','Mountain Pass Route','road_condition','critical','Critical road condition: Mountain Pass Route has composite score 85/100.','active')," +
                "('RD006','Rural Dirt Track','road_condition','critical','Critical road condition: Rural Dirt Track has composite score 90/100.','active')," +
                "('RD004','City Ring Road','traffic','high','Heavy congestion on City Ring Road. Congestion level 70%.','active')," +
                "('RD008','Flood-Prone Road Sector-3','weather','critical','Flood risk on Sector-3 Road. Weather factor 1.5x.','active')," +
                "('RD002','Village Road Sector-7','infrastructure','medium','Village Road Sector-7 requires maintenance.','acknowledged')," +
                "(NULL,NULL,'emergency','high','Multiple emergency requests in remote area.','active')"
            );
            System.out.println("  → Sample alerts inserted");
        }

        // Emergency requests
        ResultSet eCount = conn.createStatement()
            .executeQuery("SELECT COUNT(*) as c FROM emergency_requests");
        eCount.next();
        if (eCount.getInt("c") == 0) {
            conn.createStatement().executeUpdate(
                "INSERT INTO emergency_requests (patient_name, location, severity, contact, description, status) VALUES " +
                "('Rajesh Kumar','{\"latitude\":28.6139,\"longitude\":77.2090}','critical','+91-9876543210','Cardiac arrest, needs immediate attention','completed')," +
                "('Priya Sharma', '{\"latitude\":28.5355,\"longitude\":77.3910}','high',    '+91-8765432109','Road accident, multiple injuries','in_progress')," +
                "('Amit Singh',   '{\"latitude\":28.7041,\"longitude\":77.1025}','medium',  '+91-7654321098','Severe fever, remote location','pending')," +
                "('Sunita Devi',  '{\"latitude\":28.5800,\"longitude\":77.3200}','critical','+91-6543210987','Pregnancy emergency, flood-prone area','assigned')," +
                "('Mohan Lal',    '{\"latitude\":28.6500,\"longitude\":77.1800}','low',     '+91-5432109876','Minor injury, needs transport','pending')"
            );
            System.out.println("  → Sample emergency requests inserted");
        }
    }

    private static String getProp(String key, String def) {
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
}
