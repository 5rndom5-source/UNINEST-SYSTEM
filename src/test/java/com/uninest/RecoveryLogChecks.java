package com.uninest;

import com.uninest.utils.RecoveryService;

public class RecoveryLogChecks {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }

    static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-AbCdE";
    static final String API_KEY = "re_Abc123XyZ_secretKeyValue987";
    static final String EMAIL = "owner@example.com";
    static final String LINK = "https://x.onrender.com/uninest/ResetPasswordServlet?token=" + TOKEN;
    // Fixed output shape: " (code: description)" with no digits, "@", "/", "=", "%" or "&".
    static final java.util.regex.Pattern SAFE = java.util.regex.Pattern.compile("^ \\([a-z_]+: [A-Za-z ,.]+\\)$");

    static String reason(int status, String body) {
        String logged = RecoveryService.providerReason(status, body);
        check(logged != null && SAFE.matcher(logged).matches(), "fixed-shape reason for: " + body);
        return logged;
    }
    static void noLeak(String logged, String label, String... secrets) {
        String lower = logged.toLowerCase(java.util.Locale.ROOT);
        for (String secret : secrets) check(!lower.contains(secret.toLowerCase(java.util.Locale.ROOT)), label + " leaks " + secret);
        check(!logged.contains("@") && !logged.contains("://") && !logged.contains("token")
                && !logged.contains("re_") && !logged.contains("example"), label + " has no secret markers");
    }

    public static void main(String[] args) {
        // Known provider codes map to fixed descriptions; the provider's message is never used.
        String validation = reason(422, "{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"The example.com domain is not verified.\"}");
        check(validation.startsWith(" (validation_error: "), "known code is named");
        check(!validation.contains("domain is not verified") && !validation.contains("example.com"), "provider message is not logged");
        check(reason(403, "{\"name\":\"invalid_from_address\"}").startsWith(" (invalid_from_address: "), "sender code is named");
        check(reason(401, "{\"name\":\"invalid_api_key\"}").startsWith(" (invalid_api_key: "), "API key code is named");
        check(reason(429, "{\"name\":\"rate_limit_exceeded\"}").startsWith(" (rate_limit_exceeded: "), "rate limit code is named");
        check(reason(429, "{\"name\":\"daily_quota_exceeded\"}").startsWith(" (daily_quota_exceeded: "), "quota code is named");
        check(reason(500, "{\"name\":\"application_error\"}").startsWith(" (application_error: "), "provider error code is named");
        check(reason(422, "{ \"name\" : \"VALIDATION_ERROR\" }").startsWith(" (validation_error: "), "code case and spacing are normalised");

        // Missing, empty, non-JSON or unknown bodies fall back to a category from the HTTP status.
        check(reason(401, null).startsWith(" (unauthorized: "), "401 fallback");
        check(reason(403, "").startsWith(" (forbidden: "), "403 fallback");
        check(reason(422, "not json").startsWith(" (invalid_request: "), "422 fallback");
        check(reason(429, "<html>Too Many</html>").startsWith(" (rate_limited: "), "429 fallback");
        check(reason(502, "Bad Gateway").startsWith(" (provider_unavailable: "), "5xx fallback");
        check(reason(400, "{}").startsWith(" (unrecognised_error: "), "other status fallback");
        String unknown = reason(422, "{\"name\":\"owner_example_com\",\"message\":\"x\"}");
        check(unknown.startsWith(" (invalid_request: ") && !unknown.contains("owner"), "unknown code is never echoed");

        // Long or truncated messages: nothing from the body appears, however long or cut off.
        String longBody = "{\"name\":\"validation_error\",\"message\":\"" + "x".repeat(5000) + " " + EMAIL + "\"}";
        noLeak(reason(422, longBody), "long message", EMAIL, "xxxx");
        String truncated = "{\"name\":\"validation_error\",\"message\":\"Deliver to " + EMAIL.substring(0, 9);
        noLeak(reason(422, truncated), "truncated message", "owner@", "Deliver");
        String cutName = "{\"message\":\"" + LINK + "\",\"name\":\"validation_err";
        noLeak(reason(422, cutName), "truncated code", TOKEN, "onrender");
        check(reason(422, cutName).startsWith(" (invalid_request: "), "cut-off code falls back to status");
        check(reason(422, "{\"name\":\"" + "a".repeat(41) + "\"}").startsWith(" (invalid_request: "), "oversized code falls back");

        // Escaped content: escaped quotes and encoded "@" cannot smuggle text through the code field.
        String injected = "{\"name\":\"validation_error\\\" " + EMAIL + "\",\"message\":\"m\"}";
        noLeak(reason(422, injected), "escaped quote in code", EMAIL);
        check(reason(422, injected).startsWith(" (invalid_request: "), "escaped code is not trusted");
        String[] encoded = { "owner\\u0040example.com", "owner%40example.com", "owner&#64;example.com", "\\\"owner@example.com\\\"" };
        for (String value : encoded)
            noLeak(reason(422, "{\"name\":\"validation_error\",\"message\":\"Send to " + value + "\"}"), "encoded " + value, "owner", value);
        noLeak(reason(422, "{\"name\":\"validation\\u005ferror\",\"message\":\"" + EMAIL + "\"}"), "unicode-escaped code", EMAIL);

        // Secret-bearing errors: emails, API keys, reset links and tokens never reach the log.
        String[] secretBodies = {
            "{\"name\":\"validation_error\",\"message\":\"You can only send testing emails to your own email address (" + EMAIL + ").\"}",
            "{\"name\":\"invalid_api_key\",\"message\":\"API key " + API_KEY + " is invalid\"}",
            "{\"name\":\"validation_error\",\"message\":\"Bad text " + LINK + "\"}",
            "{\"name\":\"validation_error\",\"message\":\"token=" + TOKEN + "\"}",
            "{\"name\":\"" + TOKEN + "\",\"message\":\"x\"}",
            "{\"text\":\"Reset your password " + LINK + "\",\"to\":[\"" + EMAIL + "\"],\"from\":\"UniNest <noreply@mail.uninest.co.za>\"}",
            "Authorization: Bearer " + API_KEY + " " + LINK
        };
        for (String body : secretBodies) {
            for (int status : new int[]{ 400, 401, 403, 422, 429, 500 })
                noLeak(reason(status, body), "status " + status + " body " + body.substring(0, 20), EMAIL, API_KEY, TOKEN, LINK, "onrender", "uninest.co.za");
        }
        System.out.println("PASS: " + checks + " password-recovery log checks");
    }
}
