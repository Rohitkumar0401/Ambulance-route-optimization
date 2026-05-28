package utils;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * ErrorHandler.java
 * Centralised HTTP error/not-found responses.
 * Converted from backend/utils/errorHandler.js
 *
 * JS:
 *   exports.errorHandler   = (err, req, res, next) => { ... }
 *   exports.notFoundHandler = (req, res) => { ... }
 */
public class ErrorHandler {

    // ── Send a JSON error response ────────────────────────────────────────────
    // Mirrors JS errorHandler middleware
    public static void sendError(HttpExchange ex, int status, String message) throws IOException {
        // Map known error codes (mirrors JS ER_DUP_ENTRY / JWT checks)
        if (message != null && message.contains("Duplicate entry")) {
            status  = 409;
            message = "Duplicate entry - resource already exists";
        } else if (message != null && message.contains("ER_NO_REFERENCED_ROW")) {
            status  = 400;
            message = "Invalid reference - related resource not found";
        }

        String json = "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
        writeResponse(ex, status, json);
    }

    // ── 404 handler ───────────────────────────────────────────────────────────
    // Mirrors JS notFoundHandler
    public static void notFound(HttpExchange ex) throws IOException {
        writeResponse(ex, 404, "{\"success\":false,\"error\":\"Route not found\"}");
    }

    // ── Write raw JSON response ───────────────────────────────────────────────
    public static void writeResponse(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── Escape special characters for JSON strings ────────────────────────────
    public static String escapeJson(String s) {
        if (s == null) return "Internal server error";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
