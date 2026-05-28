package auth;

import com.sun.net.httpserver.HttpExchange;
import utils.ErrorHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AuthMiddleware.java
 * JWT verification and role-based access control.
 * Converted from backend/modules/user-authentication/middleware/auth.js
 *
 * JS:
 *   exports.verifyToken  = (req, res, next) => { jwt.verify(token, JWT_SECRET); next(); }
 *   exports.requireRole  = (...roles) => (req, res, next) => { ... }
 *   exports.adminOnly    = requireRole('admin')
 *   exports.adminOrDispatch = requireRole('admin','dispatcher')
 *
 * NOTE: Uses a minimal HS256 JWT verifier (no external library needed).
 *       The JWT_SECRET must match the one used when tokens were issued.
 */
public class AuthMiddleware {

    // ── JWT payload record ────────────────────────────────────────────────────
    public static class JwtPayload {
        public final int    userId;
        public final String email;
        public final String role;
        public final String username;

        public JwtPayload(int userId, String email, String role, String username) {
            this.userId   = userId;
            this.email    = email;
            this.role     = role;
            this.username = username;
        }
    }

    // ── verifyToken — mirrors JS verifyToken middleware ───────────────────────
    // Returns the decoded payload, or null and sends 401 if invalid.
    public static JwtPayload verifyToken(HttpExchange ex) throws IOException {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        String token = null;
        if (authHeader != null) {
            if (authHeader.startsWith("Bearer ")) token = authHeader.substring(7);
            else token = authHeader;
        }

        if (token == null || token.isEmpty()) {
            ErrorHandler.sendError(ex, 401, "Authentication required");
            return null;
        }

        try {
            JwtPayload payload = decodeJwt(token);
            if (payload == null) throw new Exception("Invalid token");
            return payload;
        } catch (Exception e) {
            String msg = e.getMessage() != null && e.getMessage().contains("expired")
                ? "Session expired, please login again"
                : "Invalid token";
            ErrorHandler.sendError(ex, 401, msg);
            return null;
        }
    }

    // ── requireRole — mirrors JS requireRole(...roles) ────────────────────────
    // Returns true if allowed, false (and sends 403) if not.
    public static boolean requireRole(HttpExchange ex, JwtPayload user, String... roles) throws IOException {
        if (user == null) {
            ErrorHandler.sendError(ex, 401, "Authentication required");
            return false;
        }
        for (String r : roles) {
            if (r.equals(user.role)) return true;
        }
        ErrorHandler.sendError(ex, 403,
            "Access denied. Required role: " + String.join(" or ", roles));
        return false;
    }

    // ── Shorthand guards ──────────────────────────────────────────────────────
    public static boolean adminOnly(HttpExchange ex, JwtPayload user) throws IOException {
        return requireRole(ex, user, "admin");
    }

    public static boolean adminOrDispatch(HttpExchange ex, JwtPayload user) throws IOException {
        return requireRole(ex, user, "admin", "dispatcher");
    }

    // ── Minimal HS256 JWT decoder ─────────────────────────────────────────────
    // Decodes the payload without verifying the signature (signature verification
    // requires javax.crypto which is available in standard JDK).
    // For production, replace with a proper JWT library (e.g., jjwt).
    private static JwtPayload decodeJwt(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new Exception("Invalid JWT format");

        // Verify signature using HS256
        String secret = getProp("JWT_SECRET", "your_jwt_secret_key");
        verifyHmacSha256(parts[0] + "." + parts[1], parts[2], secret);

        // Decode payload (Base64URL)
        byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
        String payloadJson  = new String(payloadBytes, StandardCharsets.UTF_8);

        // Parse fields
        int    userId   = parseInt(extractField(payloadJson, "userId"),   0);
        String email    = extractField(payloadJson, "email");
        String role     = extractField(payloadJson, "role");
        String username = extractField(payloadJson, "username");

        // Check expiry
        String expStr = extractField(payloadJson, "exp");
        if (expStr != null) {
            long exp = Long.parseLong(expStr);
            if (System.currentTimeMillis() / 1000 > exp)
                throw new Exception("Token expired");
        }

        return new JwtPayload(userId, email, role, username);
    }

    private static void verifyHmacSha256(String data, String signature, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
        ));
        byte[] expected = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String expectedB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(expected);
        if (!expectedB64.equals(signature))
            throw new Exception("Invalid token signature");
    }

    private static String extractField(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))"
        );
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) return m.group(1) != null ? m.group(1) : m.group(2);
        return null;
    }

    private static String padBase64(String s) {
        int pad = (4 - s.length() % 4) % 4;
        return s + "=".repeat(pad);
    }

    private static int parseInt(String s, int def) {
        try { return s != null ? Integer.parseInt(s) : def; } catch (NumberFormatException e) { return def; }
    }

    private static String getProp(String key, String def) {
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
}
