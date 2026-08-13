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
import java.sql.SQLException;

/**
 * AdminRegisterRequestServlet - Stores an admin access request as notifications
 * for existing active admins.
 * Path: /AdminRegisterRequestServlet
 */
@WebServlet("/AdminRegisterRequestServlet")
public class AdminRegisterRequestServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String reason = request.getParameter("reason");

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isRequired(reason)) {
            redirect(response, "error", "Name, email, and reason are required");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String message = fullName.trim()
                    + " requested admin access using "
                    + email.trim()
                    + ". Reason: "
                    + ValidationUtil.sanitizeXSS(reason.trim());

            String query = "INSERT INTO notifications "
                    + "(user_id, title, message, notification_type, action_url) "
                    + "SELECT user_id, 'Admin access request', ?, 'system_alert', 'Register.html' "
                    + "FROM users WHERE role = 'admin' AND status = 'active'";

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, message);
            stmt.executeUpdate();
            stmt.close();

            redirect(response, "success", "Admin access request submitted");
        } catch (SQLException e) {
            System.err.println("Database error in AdminRegisterRequestServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(response, "error", "Could not submit access request");
        }
    }

    private void redirect(HttpServletResponse response, String key, String message) throws IOException {
        response.sendRedirect("Register.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
