package com.uninest.utils;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.Duration;
import java.util.Base64;

public final class RecoveryService {
    private RecoveryService() {}
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte b : digest) value.append(String.format("%02x", b & 255));
            return value.toString();
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is not configured");
        return value;
    }
    public static void request(Connection connection, String email) throws Exception {
        String apiKey = required("UNINEST_RESEND_API_KEY");
        String from = required("UNINEST_EMAIL_FROM");
        String base = required("UNINEST_PUBLIC_URL").replaceAll("/+$", "");
        if (!base.startsWith("https://") && !base.startsWith("http://localhost:"))
            throw new IllegalStateException("Public URL must use HTTPS");
        int userId;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT user_id FROM users WHERE email = ? AND status NOT IN ('inactive','suspended')")) {
            query.setString(1, email);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return;
                userId = result.getInt(1);
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM password_reset_tokens WHERE user_id=? AND created_at > DATE_SUB(NOW(), INTERVAL 1 MINUTE)")) {
            query.setInt(1, userId);
            try (ResultSet result = query.executeQuery()) { if (result.next()) return; }
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO password_reset_tokens(token_hash,user_id,expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 20 MINUTE))")) {
            insert.setString(1, hash(token)); insert.setInt(2, userId); insert.executeUpdate();
        }
        String text = "Reset your UniNest password using this link (expires in 20 minutes):\n"
                + base + "/ResetPasswordServlet?token=" + token
                + "\nIf you did not request this, ignore this email. Never email your password.";
        String json = "{\"from\":" + quote(from) + ",\"to\":[" + quote(email)
                + "],\"subject\":\"Reset your UniNest password\",\"text\":" + quote(text) + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        try {
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new java.io.IOException("Email provider returned HTTP " + response.statusCode()
                        + providerReason(response.body()));
        } catch (Exception e) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM password_reset_tokens WHERE token_hash=?")) {
                delete.setString(1, hash(token)); delete.executeUpdate();
            }
            throw e;
        }
    }
    public static boolean reset(Connection connection, String token, String password) throws SQLException {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return false;
        connection.setAutoCommit(false);
        try {
            int userId;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT user_id FROM password_reset_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at>NOW() FOR UPDATE")) {
                query.setString(1, hash(token));
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) { connection.rollback(); return false; }
                    userId = result.getInt(1);
                }
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE users SET password=?,password_changed_at=NOW(3) WHERE user_id=?")) {
                update.setString(1, PasswordUtil.hashPassword(password)); update.setInt(2, userId); update.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE password_reset_tokens SET used_at=NOW() WHERE user_id=? AND used_at IS NULL")) {
                update.setInt(1, userId); update.executeUpdate();
            }
            connection.commit(); return true;
        } catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }
    // Logs only Resend's short "message" field (e.g. unverified domain). Email addresses, links and
    // tokens are removed, so personal data and reset links never reach the logs even if echoed back.
    // Emails are matched with escaped/encoded "@" forms and without a TLD, so truncated ones are caught.
    public static String providerReason(String body) {
        if (body == null) return "";
        java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){0,200})").matcher(body);
        if (!match.find()) return "";
        String reason = match.group(1)
                .replaceAll("(?i)[A-Za-z0-9._%+'-]*(?:@|\\\\u0040|%40|&#64;)[A-Za-z0-9.-]*", "[email removed]")
                .replaceAll("https?://\\S+", "[link removed]")
                .replaceAll("token=[A-Za-z0-9_-]+", "token=[removed]")
                .replaceAll("[A-Za-z0-9_-]{40,}", "[removed]");
        return ": " + reason;
    }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format("\\u%04x", (int)c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
