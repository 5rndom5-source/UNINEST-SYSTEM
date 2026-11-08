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
                        + providerReason(response.statusCode(), response.body()));
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
    // Known Resend error names mapped to fixed, safe descriptions.
    private static final java.util.Map<String, String> PROVIDER_ERRORS = java.util.Map.ofEntries(
            java.util.Map.entry("validation_error", "request rejected, often an unverified sender domain or a recipient not allowed in testing mode"),
            java.util.Map.entry("invalid_from_address", "sender address is not allowed"),
            java.util.Map.entry("missing_required_field", "request is missing a required field"),
            java.util.Map.entry("invalid_parameter", "request has an invalid parameter"),
            java.util.Map.entry("missing_api_key", "API key was not sent"),
            java.util.Map.entry("invalid_api_key", "API key is not valid"),
            java.util.Map.entry("restricted_api_key", "API key is not allowed to send email"),
            java.util.Map.entry("invalid_access", "API key is not allowed for this action"),
            java.util.Map.entry("rate_limit_exceeded", "rate limit reached"),
            java.util.Map.entry("daily_quota_exceeded", "daily sending quota reached"),
            java.util.Map.entry("monthly_quota_exceeded", "monthly sending quota reached"),
            java.util.Map.entry("security_error", "blocked by provider security checks"),
            java.util.Map.entry("not_found", "provider endpoint not found"),
            java.util.Map.entry("method_not_allowed", "request method not allowed"),
            java.util.Map.entry("application_error", "provider internal error"),
            java.util.Map.entry("internal_server_error", "provider internal error"));
    private static final java.util.regex.Pattern PROVIDER_ERROR_NAME =
            java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z_]{1,40})\"");

    /**
     * Describes a refused send using only fixed text from this class: a known provider error code,
     * or a category derived from the HTTP status. The provider's message and body are never logged,
     * so email addresses, keys, reset links and tokens cannot reach the logs.
     */
    public static String providerReason(int status, String body) {
        if (body != null) {
            java.util.regex.Matcher match = PROVIDER_ERROR_NAME.matcher(body);
            if (match.find()) {
                String code = match.group(1).toLowerCase(java.util.Locale.ROOT);
                for (java.util.Map.Entry<String, String> known : PROVIDER_ERRORS.entrySet()) {
                    if (known.getKey().equals(code)) return " (" + known.getKey() + ": " + known.getValue() + ")";
                }
            }
        }
        if (status == 401) return " (unauthorized: API key missing or not valid)";
        if (status == 403) return " (forbidden: check the sender domain and API key)";
        if (status == 422) return " (invalid_request: the provider rejected the request)";
        if (status == 429) return " (rate_limited: too many requests)";
        if (status >= 500 && status <= 599) return " (provider_unavailable: try again later)";
        return " (unrecognised_error: see the provider dashboard)";
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
