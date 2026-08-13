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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("email");
        if (!ValidationUtil.isValidEmail(email)) {
            redirect(response, "Login.html", "message", "If the email exists, reset instructions will be prepared.");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn != null) {
                Integer userId = findUserId(conn, email.trim());
                if (userId != null) {
                    createNotification(conn, userId);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error in ForgotPasswordServlet: " + e.getMessage());
            e.printStackTrace();
        }

        redirect(response, "Login.html", "message", "If the email exists, reset instructions will be prepared.");
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
