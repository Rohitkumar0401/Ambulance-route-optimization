package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseConfig.java
 * Lightweight JDBC connection pool (up to 10 connections).
 * Replaces the single-connection approach that caused serialized request handling.
 *
 * JS equivalent:
 *   const pool = mysql.createPool({ host, user, password, database, connectionLimit: 10 });
 */
public class DatabaseConfig {

    private static final int POOL_SIZE = 10;
    private static final List<Connection> pool = new ArrayList<>();
    private static boolean initialized = false;

    // ── Borrow a connection from the pool ─────────────────────────────────────
    public static synchronized Connection getConnection() throws SQLException {
        ensurePool();

        // Return the first valid (open) connection
        for (Connection c : pool) {
            try {
                if (!c.isClosed() && c.isValid(1)) return c;
            } catch (SQLException ignored) {}
        }

        // All connections were stale — rebuild the pool
        pool.clear();
        initialized = false;
        ensurePool();
        return pool.get(0);
    }

    // ── Build the pool on first use ───────────────────────────────────────────
    private static void ensurePool() throws SQLException {
        if (initialized) return;

        String host     = getProp("DB_HOST",     "localhost");
        String user     = getProp("DB_USER",     "root");
        String password = getProp("DB_PASSWORD", "");
        String dbName   = getProp("DB_NAME",     "ambulance_optimization");

        String url = "jdbc:mysql://" + host + ":3306/" + dbName
                   + "?useSSL=false&allowPublicKeyRetrieval=true"
                   + "&serverTimezone=UTC"
                   + "&autoReconnect=true"
                   + "&cachePrepStmts=true"
                   + "&useServerPrepStmts=true";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found: " + e.getMessage());
        }

        for (int i = 0; i < POOL_SIZE; i++) {
            pool.add(DriverManager.getConnection(url, user, password));
        }
        initialized = true;
        System.out.println("✅ DB pool ready (" + POOL_SIZE + " connections)");
    }

    public static synchronized void close() {
        for (Connection c : pool) {
            try { c.close(); } catch (SQLException ignored) {}
        }
        pool.clear();
        initialized = false;
    }

    private static String getProp(String key, String def) {
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
}
