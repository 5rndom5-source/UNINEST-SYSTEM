package com.uninest.servlets.auth;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.PasswordUtil;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
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

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String role = request.getParameter("role");
        String rememberMe = request.getParameter("remember");

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

        if (ValidationUtil.containsSQLInjectionPatterns(email)
                || ValidationUtil.containsSQLInjectionPatterns(password)) {
            redirectWithError(response, "Invalid input detected");
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

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                String userStatus = rs.getString("status");

                if ("suspended".equals(userStatus)) {
                    redirectWithError(response, "Your admin account has been suspended");
                    return;
                }

                if ("pending_verification".equals(userStatus)) {
                    redirectWithError(response, "Your admin account is pending verification");
                    return;
                }

                if (PasswordUtil.verifyPassword(password, storedPassword)) {
                    int userId = rs.getInt("user_id");
                    String fullName = rs.getString("full_name");

                    HttpSession session = request.getSession();
                    session.setAttribute("userId", userId);
                    session.setAttribute("adminId", userId);
                    session.setAttribute("adminName", fullName);
                    session.setAttribute("email", email);
                    session.setAttribute("role", "admin");
                    session.setMaxInactiveInterval(1800);

                    updateLastLogin(conn, userId);

                    if ("on".equals(rememberMe)) {
                        Cookie cookie = new Cookie("admin_token", userId + ":" + System.currentTimeMillis());
                        cookie.setMaxAge(30 * 24 * 60 * 60);
                        cookie.setHttpOnly(true);
                        response.addCookie(cookie);
                    }

                    response.sendRedirect(request.getContextPath() + "/Dashboard.html");
                } else {
                    redirectWithError(response, "Invalid email or password");
                }
            } else {
                redirectWithError(response, "Invalid email or password");
            }

            stmt.close();
        } catch (SQLException e) {
            System.err.println("Database error in LoginServlet: " + e.getMessage());
            e.printStackTrace();
            redirectWithError(response, "Database error occurred. Please try again later.");
        }
    }

    private void updateLastLogin(Connection conn, int userId) {
        try {
            String query = "UPDATE users SET last_login = NOW() WHERE user_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Error updating last login: " + e.getMessage());
        }
    }

    private void redirectWithError(HttpServletResponse response, String errorMessage)
            throws IOException {
        response.sendRedirect("Login.html?error="
                + java.net.URLEncoder.encode(errorMessage, "UTF-8"));
    }
}
