package com.uninest.servlets.auth;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.LoginRecord;
import com.uninest.utils.PasswordUtil;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * LoginServlet - Handles admin login authentication.
 * Path: /LoginServlet
 */
@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = normalizeEmail(request.getParameter("email"));
        String password = request.getParameter("password");
        String role = request.getParameter("role");

        if (!ValidationUtil.isRequired(email) || !ValidationUtil.isRequired(password)) {
            redirectWithError(response, "Email and password are required");
            return;
        }

        if (ValidationUtil.isRequired(role) && !"admin".equalsIgnoreCase(role)) {
            redirectWithError(response, "Only admin users can access this portal");
            return;
        }

        if (!ValidationUtil.isValidEmail(email)) {
            redirectWithError(response, "Invalid email format");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirectWithError(response, "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "SELECT user_id, full_name, password, status "
                    + "FROM users WHERE email = ? AND role = 'admin'";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, email);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("password");
                        String userStatus = rs.getString("status");

                        if (!PasswordUtil.verifyPassword(password, storedPassword) || !"active".equalsIgnoreCase(userStatus)) {
                            redirectWithError(response, "Invalid email or password");
                            return;
                        }

                        int userId = rs.getInt("user_id");
                        String fullName = rs.getString("full_name");
                        HttpSession session = createFreshSession(request);
                        session.setAttribute("userId", userId);
                        session.setAttribute("adminId", userId);
                        session.setAttribute("adminName", fullName);
                        session.setAttribute("fullName", fullName);
                        session.setAttribute("email", email);
                        session.setAttribute("role", "admin");
                        session.setMaxInactiveInterval(1800);

                        LoginRecord.recordLastLogin(conn, userId);
                        response.sendRedirect(request.getContextPath() + "/Dashboard.html");
                        return;
                    }

                    redirectWithError(response, "Invalid email or password");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error in LoginServlet: " + e.getMessage());
            e.printStackTrace();
            redirectWithError(response, "Database error occurred. Please try again later.");
        }
    }

    private HttpSession createFreshSession(HttpServletRequest request) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        return request.getSession(true);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void redirectWithError(HttpServletResponse response, String errorMessage)
            throws IOException {
        response.sendRedirect("Admin/web/jsp/Login.html?error="
                + java.net.URLEncoder.encode(errorMessage, "UTF-8"));
    }
}
