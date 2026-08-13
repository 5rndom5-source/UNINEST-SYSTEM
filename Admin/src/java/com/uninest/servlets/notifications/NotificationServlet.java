package com.uninest.servlets.notifications;

import com.uninest.utils.DatabaseConnection;
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
import java.sql.SQLException;

/**
 * NotificationServlet - Marks one or all notifications as read.
 * Path: /NotificationServlet
 */
@WebServlet("/NotificationServlet")
public class NotificationServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String notificationId = request.getParameter("notificationId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            int userId = (Integer) session.getAttribute("userId");
            int updated = ValidationUtil.isInteger(notificationId)
                    ? markOne(conn, Integer.parseInt(notificationId), userId)
                    : markAll(conn, userId);

            redirect(request, response, "success", updated + " notification(s) marked as read");
        } catch (SQLException e) {
            System.err.println("Database error in NotificationServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update notifications");
        }
    }

    private int markOne(Connection conn, int notificationId, int userId) throws SQLException {
        String query = "UPDATE notifications SET is_read = TRUE, read_at = NOW() "
                + "WHERE notification_id = ? AND user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, notificationId);
        stmt.setInt(2, userId);
        int updated = stmt.executeUpdate();
        stmt.close();
        return updated;
    }

    private int markAll(Connection conn, int userId) throws SQLException {
        String query = "UPDATE notifications SET is_read = TRUE, read_at = NOW() "
                + "WHERE user_id = ? AND is_read = FALSE";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, userId);
        int updated = stmt.executeUpdate();
        stmt.close();
        return updated;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        String referer = request.getHeader("Referer");
        String page = referer != null && !referer.trim().isEmpty()
                ? referer
                : request.getContextPath() + "/Dashboard.html";
        response.sendRedirect(page
                + (page.contains("?") ? "&" : "?")
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
