package auth;

import com.sun.net.httpserver.HttpExchange;
import config.DatabaseConfig;
import utils.ErrorHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * AuthController.java
 * User registration, login, and admin user management.
 * Converted from backend/modules/user-authentication/controller.js
 *
 * Routes:
 *   POST   /api/auth/register           → register()
 *   POST   /api/auth/login              → login()
 *   GET    /api/auth/me                 → getMe()
 *   GET    /api/auth/users              → getAllUsers()
 *   POST   /api/auth/users              → adminCreateUser()
 *   PATCH  /api/auth/users/:id/role     → updateUserRole()
 *   DELETE /api/auth/users/:id          → deleteUser()
 *   GET    /api/auth/activity-log       → getActivityLog()
 *
 * Password hashing uses Spring Security BCryptPasswordEncoder (spring-security-crypto).
 */
public class AuthController {

    private static final List<String> VALID_ROLES = List.of("admin", "dispatcher", "driver", "user");

    // ── POST /api/auth/register ───────────────────────────────────────────────
    // JS: exports.register = async (req, res) => { ... }
    public static void register(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");
        String role     = body.getOrDefault("role", "user");

        if (username == null || email == null || password == null) {
            ErrorHandler.sendError(ex, 400, "Username, email and password are required");
            return;
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            ErrorHandler.sendError(ex, 400, "Invalid email format");
            return;
        }
        if (password.length() < 8) {
            ErrorHandler.sendError(ex, 400, "Password must be at least 8 characters");
            return;
        }

        // Public registration only allows user/driver roles
        List<String> allowed = List.of("user", "driver");
        String assignedRole = allowed.contains(role) ? role : "user";

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE email = ?");
            check.setString(1, email);
            if (check.executeQuery().next()) {
                ErrorHandler.sendError(ex, 409, "Email already registered");
                return;
            }

            String hash = hashPassword(password);
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setString(4, assignedRole);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int userId = keys.next() ? keys.getInt(1) : -1;
            ErrorHandler.writeResponse(ex, 200,
                "{\"success\":true,\"userId\":" + userId + ",\"message\":\"Account created successfully\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────
    // JS: exports.login = async (req, res) => { ... }
    public static void login(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String email    = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            ErrorHandler.sendError(ex, 400, "Email and password are required");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                ErrorHandler.sendError(ex, 401, "Invalid email or password");
                return;
            }

            String storedHash = rs.getString("password");
            if (!verifyPassword(password, storedHash)) {
                ErrorHandler.sendError(ex, 401, "Invalid email or password");
                return;
            }

            int    userId   = rs.getInt("id");
            String username = rs.getString("username");
            String userRole = rs.getString("role");

            String token = generateJwt(userId, email, userRole, username);

            // Log login (non-fatal)
            try {
                PreparedStatement log = conn.prepareStatement(
                    "INSERT INTO activity_log (user_id, action, details) VALUES (?, ?, ?)"
                );
                log.setInt(1, userId);
                log.setString(2, "login");
                log.setString(3, "User " + username + " logged in");
                log.executeUpdate();
            } catch (SQLException ignored) {}

            ErrorHandler.writeResponse(ex, 200, String.format(
                "{\"success\":true,\"token\":\"%s\",\"user\":{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"role\":\"%s\"}}",
                token, userId,
                ErrorHandler.escapeJson(username),
                ErrorHandler.escapeJson(email),
                userRole
            ));
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────
    public static void getMe(HttpExchange ex, AuthMiddleware.JwtPayload user) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT id, username, email, role, created_at FROM users WHERE id = ?"
            );
            ps.setInt(1, user.userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                ErrorHandler.sendError(ex, 404, "User not found");
                return;
            }
            ErrorHandler.writeResponse(ex, 200, String.format(
                "{\"success\":true,\"user\":{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"created_at\":\"%s\"}}",
                rs.getInt("id"), ErrorHandler.escapeJson(rs.getString("username")),
                ErrorHandler.escapeJson(rs.getString("email")), rs.getString("role"),
                rs.getString("created_at")
            ));
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/auth/users ───────────────────────────────────────────────────
    public static void getAllUsers(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT id, username, email, role, created_at FROM users ORDER BY created_at DESC"
            );
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(userRowToJson(rs));
                first = false;
            }
            arr.append("]");
            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"data\":" + arr + "}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/auth/users (admin create) ───────────────────────────────────
    public static void adminCreateUser(HttpExchange ex, AuthMiddleware.JwtPayload admin) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");
        String role     = body.getOrDefault("role", "user");

        if (username == null || email == null || password == null) {
            ErrorHandler.sendError(ex, 400, "Username, email and password are required");
            return;
        }
        if (password.length() < 8) {
            ErrorHandler.sendError(ex, 400, "Password must be at least 8 characters");
            return;
        }

        String assignedRole = VALID_ROLES.contains(role) ? role : "user";

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE email = ?");
            check.setString(1, email);
            if (check.executeQuery().next()) {
                ErrorHandler.sendError(ex, 409, "Email already registered");
                return;
            }

            String hash = hashPassword(password);
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, username); ps.setString(2, email);
            ps.setString(3, hash);     ps.setString(4, assignedRole);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : -1;

            logActivity(conn, admin.userId, "create_user",
                "Admin created user " + username + " with role " + assignedRole);

            ErrorHandler.writeResponse(ex, 200,
                "{\"success\":true,\"userId\":" + newId + ",\"message\":\"User created\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── PATCH /api/auth/users/:id/role ────────────────────────────────────────
    public static void updateUserRole(HttpExchange ex, int id, AuthMiddleware.JwtPayload admin) throws IOException {
        Map<String, String> body = parseJson(readBody(ex));
        String role = body.get("role");

        if (!VALID_ROLES.contains(role)) {
            ErrorHandler.sendError(ex, 400, "Role must be one of: " + String.join(", ", VALID_ROLES));
            return;
        }
        if (id == admin.userId) {
            ErrorHandler.sendError(ex, 400, "Cannot change your own role");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE id = ?");
            check.setInt(1, id);
            if (!check.executeQuery().next()) {
                ErrorHandler.sendError(ex, 404, "User not found");
                return;
            }
            PreparedStatement ps = conn.prepareStatement("UPDATE users SET role = ? WHERE id = ?");
            ps.setString(1, role); ps.setInt(2, id);
            ps.executeUpdate();

            logActivity(conn, admin.userId, "role_change",
                "Changed user #" + id + " role to " + role);

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"Role updated\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── DELETE /api/auth/users/:id ────────────────────────────────────────────
    public static void deleteUser(HttpExchange ex, int id, AuthMiddleware.JwtPayload admin) throws IOException {
        if (id == admin.userId) {
            ErrorHandler.sendError(ex, 400, "Cannot delete your own account");
            return;
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement check = conn.prepareStatement("SELECT id, username FROM users WHERE id = ?");
            check.setInt(1, id);
            ResultSet rs = check.executeQuery();
            if (!rs.next()) {
                ErrorHandler.sendError(ex, 404, "User not found");
                return;
            }
            String deletedName = rs.getString("username");

            PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();

            logActivity(conn, admin.userId, "delete_user",
                "Deleted user " + deletedName + " (#" + id + ")");

            ErrorHandler.writeResponse(ex, 200, "{\"success\":true,\"message\":\"User deleted\"}");
        } catch (SQLException e) {
            ErrorHandler.sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/auth/activity-log ────────────────────────────────────────────
    public static void getActivityLog(HttpExchange ex) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT al.*, u.username, u.email, u.role " +
                "FROM activity_log al LEFT JOIN users u ON al.user_id = u.id " +
                "ORDER BY al.created_at DESC LIMIT 100"
            );
            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) arr.append(",");
                arr.append(String.format(
                    "{\"id\":%d,\"user_id\":%s,\"action\":\"%s\",\"details\":\"%s\"," +
                    "\"created_at\":\"%s\",\"username\":\"%s\",\"email\":\"%s\",\"role\":\"%s\"}",
                    rs.getInt("id"),
                    rs.getString("user_id") != null ? rs.getString("user_id") : "null",
                    ErrorHandler.escapeJson(rs.getString("action")),
                    ErrorHandler.escapeJson(rs.getString("details")),
                    rs.getString("created_at"),
                    ErrorHandler.escapeJson(rs.getString("username")),
                    ErrorHandler.escapeJson(rs.getString("email")),
                    rs.getString("role") != null ? rs.getString("role") : ""
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

    private static String userRowToJson(ResultSet rs) throws SQLException {
        return String.format(
            "{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"created_at\":\"%s\"}",
            rs.getInt("id"),
            ErrorHandler.escapeJson(rs.getString("username")),
            ErrorHandler.escapeJson(rs.getString("email")),
            rs.getString("role"),
            rs.getString("created_at")
        );
    }

    private static void logActivity(Connection conn, int userId, String action, String details) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO activity_log (user_id, action, details) VALUES (?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ── Minimal JWT generator (HS256) ─────────────────────────────────────────
    private static String generateJwt(int userId, String email, String role, String username) {
        String secret = getProp("JWT_SECRET", "your_jwt_secret_key");
        long   exp    = System.currentTimeMillis() / 1000 + 86400; // 24h

        String header  = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(String.format(
            "{\"userId\":%d,\"email\":\"%s\",\"role\":\"%s\",\"username\":\"%s\",\"exp\":%d}",
            userId, ErrorHandler.escapeJson(email), role,
            ErrorHandler.escapeJson(username), exp
        ));

        String sig = hmacSha256(header + "." + payload, secret);
        return header + "." + payload + "." + sig;
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            ));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ── Password hashing using Spring Security BCrypt ────────────────────────
    // Singleton — BCryptPasswordEncoder is thread-safe; cost 8 is ~4x faster
    // than default 10 while remaining secure.
    private static final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
        PASSWORD_ENCODER = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(8);

    private static String hashPassword(String password) {
        return PASSWORD_ENCODER.encode(password);
    }

    private static boolean verifyPassword(String password, String stored) {
        if (stored == null) return false;
        try {
            return PASSWORD_ENCODER.matches(password, stored);
        } catch (Exception e) {
            return false;
        }
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

    private static String getProp(String key, String def) {
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
}
