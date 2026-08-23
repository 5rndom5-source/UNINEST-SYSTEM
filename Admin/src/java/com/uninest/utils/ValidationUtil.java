package com.uninest.utils;

import java.util.regex.Pattern;

/**
 * ValidationUtil - Input validation and sanitization
 * Prevents SQL injection, XSS, and other common vulnerabilities
 */
public class ValidationUtil {
    
    // Email regex pattern (RFC 5322 simplified)
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    // Accept international numbers and common 10-digit South African local numbers.
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^(?:\\+?[1-9]\\d{7,14}|0\\d{9})$");
    
    // URL pattern
    private static final Pattern URL_PATTERN = 
        Pattern.compile("^(https?://.+)$");
    
    // Username pattern (alphanumeric, underscore, hyphen)
    private static final Pattern USERNAME_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9_-]{3,20}$");
    
    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Validate phone number format
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.replaceAll("[\\s-()]", "")).matches();
    }
    
    /**
     * Validate URL format
     */
    public static boolean isValidURL(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(url).matches();
    }
    
    /**
     * Validate username format
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Check if string is numeric
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Check if string is integer
     */
    public static boolean isInteger(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Sanitize string to prevent XSS
     * Escapes HTML special characters
     */
    public static String sanitizeXSS(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Check for SQL injection patterns
     */
    public static boolean containsSQLInjectionPatterns(String input) {
        if (input == null) {
            return false;
        }
        
        String[] sqlPatterns = {
            "(?i).*('|(\\-\\-)|(;)|(\\|\\|)|(\\*)|(xp_)|(sp_)).*",
            "(?i).*(union|select|insert|update|delete|drop|create).*",
            "(?i).*(exec|execute|script|javascript).*"
        };
        
        for (String pattern : sqlPatterns) {
            if (input.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Validate required field
     */
    public static boolean isRequired(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Validate string length
     */
    public static boolean isValidLength(String value, int minLength, int maxLength) {
        if (value == null) {
            return minLength == 0;
        }
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * Validate decimal/price format
     */
    public static boolean isValidPrice(String price) {
        if (price == null || price.isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(price);
            return value >= 0 && value <= 999999.99;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate date format (YYYY-MM-DD)
     */
    public static boolean isValidDate(String date) {
        if (date == null || date.isEmpty()) {
            return false;
        }
        return date.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }
    
    /**
     * Get validation error message
     */
    public static String getValidationError(String fieldName, String value, String rule) {
        switch (rule) {
            case "required":
                return fieldName + " is required";
            case "email":
                return "Invalid email format";
            case "phone":
                return "Invalid phone number format";
            case "url":
                return "Invalid URL format";
            case "username":
                return "Username must be 3-20 characters (alphanumeric, underscore, hyphen)";
            case "price":
                return "Invalid price format";
            case "date":
                return "Invalid date format (YYYY-MM-DD)";
            case "length":
                return fieldName + " length is invalid";
            default:
                return "Invalid input";
        }
    }
}
