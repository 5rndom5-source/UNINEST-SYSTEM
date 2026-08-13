package com.uninest.servlets.auth;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.PasswordUtil;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * RegisterServlet - Handles landlord registration
 * Path: /RegisterServlet
 */
@WebServlet("/RegisterServlet")
@MultipartConfig(
    maxFileSize = 5242880, // 5MB
    maxRequestSize = 10485760 // 10MB
)
public class RegisterServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Get form parameters
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String businessName = request.getParameter("businessName");
        String businessAddress = request.getParameter("businessAddress");
        String idNumber = request.getParameter("idNumber");
        String bankAccount = request.getParameter("bankAccount");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        Part idFilePart = request.getPart("idFile");
        
        // Validation
        if (!validateInput(fullName, email, phone, businessName, businessAddress, 
                          idNumber, password, confirmPassword, response)) {
            return;
        }
        
        // Check if passwords match
        if (!password.equals(confirmPassword)) {
            sendError(response, "Passwords do not match");
            return;
        }
        
        // Check password strength
        if (!PasswordUtil.isStrongPassword(password)) {
            sendError(response, "Password is not strong enough. " + 
                     PasswordUtil.getPasswordStrengthFeedback(password));
            return;
        }
        
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendError(response, "Service is temporarily unavailable. Please try again later.");
                return;
            }
            
            // Check if email already exists
            if (emailExists(conn, email)) {
                sendError(response, "Email already registered");
                return;
            }
            
            // Check if ID number already exists
            if (idExists(conn, idNumber)) {
                sendError(response, "ID number already registered");
                return;
            }
            
            // Hash password
            String hashedPassword = PasswordUtil.hashPassword(password);
            
            // Upload file and get path
            String idFilePath = uploadFile(idFilePart);
            
            // Insert into users table
            String userQuery = "INSERT INTO users (full_name, email, password, phone, role, status) " +
                             "VALUES (?, ?, ?, ?, 'landlord', 'pending_verification')";
            
            PreparedStatement userStmt = conn.prepareStatement(userQuery, 
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            userStmt.setString(1, fullName);
            userStmt.setString(2, email);
            userStmt.setString(3, hashedPassword);
            userStmt.setString(4, phone);
            
            userStmt.executeUpdate();
            
            // Get generated user ID
            java.sql.ResultSet generatedKeys = userStmt.getGeneratedKeys();
            int userId = 0;
            if (generatedKeys.next()) {
                userId = generatedKeys.getInt(1);
            }
            userStmt.close();
            
            // Insert into landlords table
            String landlordQuery = "INSERT INTO landlords " +
                                 "(user_id, business_name, id_number, business_address, " +
                                 "bank_account, approval_status) " +
                                 "VALUES (?, ?, ?, ?, ?, 'pending')";
            
            PreparedStatement landlordStmt = conn.prepareStatement(landlordQuery);
            landlordStmt.setInt(1, userId);
            landlordStmt.setString(2, businessName);
            landlordStmt.setString(3, idNumber);
            landlordStmt.setString(4, businessAddress);
            landlordStmt.setString(5, bankAccount);
            
            landlordStmt.executeUpdate();
            landlordStmt.close();
            
            // Insert into documents table
            String docQuery = "INSERT INTO documents " +
                            "(user_id, document_type, file_url, verification_status) " +
                            "VALUES (?, 'ownership_proof', ?, 'pending')";
            
            PreparedStatement docStmt = conn.prepareStatement(docQuery);
            docStmt.setInt(1, userId);
            docStmt.setString(2, idFilePath);
            docStmt.executeUpdate();
            docStmt.close();
            
            // Redirect to login page with success message
            response.sendRedirect("Login.html?success=" + 
                                java.net.URLEncoder.encode(
                                "Registration successful! Awaiting admin verification.", "UTF-8"));
            
        } catch (SQLException e) {
            System.err.println("Database error in RegisterServlet: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Database error occurred. Please try again later.");
        }
    }
    
    /**
     * Validate all input
     */
    private boolean validateInput(String fullName, String email, String phone, 
                                  String businessName, String businessAddress,
                                  String idNumber, String password, String confirmPassword,
                                  HttpServletResponse response) throws IOException {
        
        if (!ValidationUtil.isRequired(fullName)) {
            sendError(response, "Full name is required");
            return false;
        }
        
        if (!ValidationUtil.isValidEmail(email)) {
            sendError(response, "Invalid email format");
            return false;
        }
        
        if (!ValidationUtil.isValidPhone(phone)) {
            sendError(response, "Invalid phone number format");
            return false;
        }
        
        if (!ValidationUtil.isRequired(businessName)) {
            sendError(response, "Business name is required");
            return false;
        }
        
        if (!ValidationUtil.isRequired(businessAddress)) {
            sendError(response, "Business address is required");
            return false;
        }
        
        if (!ValidationUtil.isRequired(idNumber)) {
            sendError(response, "ID number is required");
            return false;
        }
        
        if (!ValidationUtil.isRequired(password) || !ValidationUtil.isRequired(confirmPassword)) {
            sendError(response, "Password and confirmation are required");
            return false;
        }
        
        // Check for SQL injection
        if (ValidationUtil.containsSQLInjectionPatterns(email) ||
            ValidationUtil.containsSQLInjectionPatterns(fullName)) {
            sendError(response, "Invalid input detected");
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if email exists
     */
    private boolean emailExists(Connection conn, String email) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM users WHERE email = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, email);
        java.sql.ResultSet rs = stmt.executeQuery();
        
        boolean exists = false;
        if (rs.next() && rs.getInt("count") > 0) {
            exists = true;
        }
        
        stmt.close();
        return exists;
    }
    
    /**
     * Check if ID already exists
     */
    private boolean idExists(Connection conn, String idNumber) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM landlords WHERE id_number = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, idNumber);
        java.sql.ResultSet rs = stmt.executeQuery();
        
        boolean exists = false;
        if (rs.next() && rs.getInt("count") > 0) {
            exists = true;
        }
        
        stmt.close();
        return exists;
    }
    
    /**
     * Upload file and return path
     */
    private String uploadFile(Part part) throws IOException {
        if (part == null) {
            return null;
        }
        
        String fileName = UUID.randomUUID().toString() + "_" + 
                         part.getSubmittedFileName();
        String uploadPath = getServletContext().getRealPath("") + "/uploads/" + fileName;
        
        java.nio.file.Files.createDirectories(
            java.nio.file.Paths.get(
                getServletContext().getRealPath("") + "/uploads/"
            )
        );
        
        part.write(uploadPath);
        return "/uploads/" + fileName;
    }
    
    /**
     * Send error response
     */
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect("Register.html?error=" + 
                            java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
