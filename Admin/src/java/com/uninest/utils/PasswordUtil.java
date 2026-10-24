package com.uninest.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PasswordUtil - Secure password hashing and verification
 * New passwords use PBKDF2-HMAC-SHA256; legacy salted SHA-256 hashes remain readable.
 */
public class PasswordUtil {
    
    private static final int SALT_LENGTH = 16;
    private static final String HASH_ALGORITHM = "SHA-256";
    
    /**
     * Generate a random salt
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }
    
    /**
     * Hash password with salt
     */
    public static String hashPassword(String password) {
        try {
            byte[] salt = generateSalt();
            byte[] hash = derive(password, salt, 600000);
            return "pbkdf2$600000$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (java.security.GeneralSecurityException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    
    /**
     * Verify password against hash
     */
    public static boolean verifyPassword(String password, String hash) {
        try {
            if (hash.startsWith("pbkdf2$")) {
                String[] parts = hash.split("\\$");
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                if (iterations < 600000 || iterations > 1000000) return false;
                return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                        derive(password, Base64.getDecoder().decode(parts[2]), iterations));
            }
            byte[] saltAndHash = Base64.getDecoder().decode(hash);
            
            // Extract salt
            byte[] salt = new byte[SALT_LENGTH];
            System.arraycopy(saltAndHash, 0, salt, 0, SALT_LENGTH);
            
            // Hash the provided password with the extracted salt
            byte[] computedHash = hashPasswordWithSalt(password, salt);
            
            // Compare hashes
            return constantTimeEquals(
                saltAndHash, 
                SALT_LENGTH, 
                saltAndHash.length - SALT_LENGTH,
                computedHash, 
                0, 
                computedHash.length
            );
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws java.security.GeneralSecurityException {
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    
    /**
     * Hash password with given salt
     */
    private static byte[] hashPasswordWithSalt(String password, byte[] salt) 
            throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
        md.update(salt);
        return md.digest(password.getBytes());
    }
    
    /**
     * Constant time comparison to prevent timing attacks
     */
    private static boolean constantTimeEquals(
            byte[] a, int aOffset, int aLength,
            byte[] b, int bOffset, int bLength) {
        
        if (aLength != bLength) return false;
        
        int result = 0;
        for (int i = 0; i < aLength; i++) {
            result |= a[aOffset + i] ^ b[bOffset + i];
        }
        return result == 0;
    }
    
    /**
     * Validate password strength
     * Requirements:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character
     */
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUpperCase = password.matches(".*[A-Z].*");
        boolean hasLowerCase = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>?].*");
        
        return hasUpperCase && hasLowerCase && hasDigit && hasSpecial;
    }
    
    /**
     * Get password strength feedback
     */
    public static String getPasswordStrengthFeedback(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty";
        }
        
        StringBuilder feedback = new StringBuilder();
        
        if (password.length() < 8) {
            feedback.append("- At least 8 characters required\n");
        }
        if (!password.matches(".*[A-Z].*")) {
            feedback.append("- Add uppercase letters (A-Z)\n");
        }
        if (!password.matches(".*[a-z].*")) {
            feedback.append("- Add lowercase letters (a-z)\n");
        }
        if (!password.matches(".*\\d.*")) {
            feedback.append("- Add numbers (0-9)\n");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>?].*")) {
            feedback.append("- Add special characters (!@#$%^&*)\n");
        }
        
        return feedback.length() == 0 ? "Password strength: Strong" : feedback.toString();
    }
}
