package com.uninest.servlets.auth;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ForgotPasswordServlet - Records a password reset request without exposing
 * whether the email exists.
 * Path: /ForgotPasswordServlet
 */
@WebServlet("/ForgotPasswordServlet")
public class ForgotPasswordServlet extends HttpServlet {
    private final java.util.Map<String, long[]> attempts = new java.util.HashMap<>();
    private synchronized boolean allowed(String address) {
        long now=System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> now-entry.getValue()[0]>60000);
        if(!attempts.containsKey(address) && attempts.size()>=10000) return false;
        long[] bucket=attempts.computeIfAbsent(address,key -> new long[]{now,0});
        return ++bucket[1]<=5;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("email");
        if(!allowed(request.getRemoteAddr())) {
            response.setHeader("Retry-After","60");
            response.sendError(429,"Please wait a minute before trying again");
            return;
        }
        if (!ValidationUtil.isValidEmail(email)) {
            redirect(response, request.getContextPath()+"/ResetPasswordServlet", "message", "Enter a valid email address.");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) throw new IllegalStateException("Database unavailable");
            if (conn != null) {
                com.uninest.utils.RecoveryService.request(conn, email.trim());
            }
        } catch (Exception e) {
            System.err.println("Database error in ForgotPasswordServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(response, request.getContextPath() + "/ResetPasswordServlet", "message", "Password recovery is temporarily unavailable. Please try again later.");
            return;
        }

        redirect(response, request.getContextPath() + "/ResetPasswordServlet", "message", "If this account is eligible, a reset link will arrive shortly. Check your spam folder.");
    }

    private Integer findUserId(Connection conn, String email) throws SQLException {
        String query = "SELECT user_id FROM users WHERE email = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        Integer userId = null;
        if (rs.next()) {
            userId = rs.getInt("user_id");
        }

        stmt.close();
        return userId;
    }

    private void createNotification(Connection conn, int userId) throws SQLException {
        String query = "INSERT INTO notifications "
                + "(user_id, title, message, notification_type, action_url) "
                + "VALUES (?, 'Password reset requested', "
                + "'A password reset was requested for this account.', 'system_alert', 'Login.html')";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, userId);
        stmt.executeUpdate();
        stmt.close();
    }

    private void redirect(HttpServletResponse response, String page,
                          String key, String message) throws IOException {
        response.sendRedirect(page
                + "?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
