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
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/NotificationServlet")
public class NotificationServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        Integer userId = authenticatedUserId(request);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"notifications\":[],\"unreadCount\":0,\"error\":\"Login required\"}");
            return;
        }

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"notifications\":[],\"unreadCount\":0,\"error\":\"Service is temporarily unavailable\"}");
                return;
            }

            int unreadCount = countUnread(conn, userId);
            String query = "SELECT notification_id, title, message, notification_type, "
                    + "created_at, is_read, action_url FROM notifications "
                    + "WHERE user_id = ? ORDER BY created_at DESC LIMIT 20";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"unreadCount\":" + unreadCount + ",\"notifications\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        out.print("{\"id\":" + rs.getInt("notification_id")
                                + ",\"title\":\"" + json(rs.getString("title")) + "\""
                                + ",\"message\":\"" + json(rs.getString("message")) + "\""
                                + ",\"type\":\"" + json(rs.getString("notification_type")) + "\""
                                + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("created_at"))) + "\""
                                + ",\"read\":" + rs.getBoolean("is_read")
                                + ",\"actionUrl\":\"" + json(rs.getString("action_url")) + "\"}");
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load notifications", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"notifications\":[],\"unreadCount\":0,\"error\":\"Notifications could not be loaded\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer userId = authenticatedUserId(request);
        if (userId == null) {
            if (wantsJson(request)) {
                jsonResult(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required", 0);
            } else {
                response.sendRedirect(request.getContextPath() + "/createAccount/login.html");
            }
            return;
        }

        String notificationId = request.getParameter("notificationId");
        String action = valueOr(request.getParameter("action"),
                ValidationUtil.isInteger(notificationId) ? "read" : "readAll");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                finish(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Service is temporarily unavailable", 0, "error");
                return;
            }

            int updated;
            String message;
            if ("delete".equalsIgnoreCase(action) && ValidationUtil.isInteger(notificationId)) {
                updated = deleteOne(conn, Integer.parseInt(notificationId), userId);
                message = updated == 1 ? "Notification deleted" : "Notification not found";
            } else if ("read".equalsIgnoreCase(action) && ValidationUtil.isInteger(notificationId)) {
                updated = markOne(conn, Integer.parseInt(notificationId), userId);
                message = updated == 1 ? "Notification marked as read" : "Notification not found";
            } else if ("readAll".equalsIgnoreCase(action)) {
                updated = markAll(conn, userId);
                message = updated + " notification(s) marked as read";
            } else {
                finish(request, response, HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid notification action", 0, "error");
                return;
            }

            finish(request, response, updated == 0 && !"readAll".equalsIgnoreCase(action)
                            ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_OK,
                    message, updated, updated == 0 && !"readAll".equalsIgnoreCase(action)
                            ? "error" : "success");
        } catch (SQLException e) {
            getServletContext().log("Unable to update notifications", e);
            finish(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not update notifications", 0, "error");
        }
    }

    private int countUnread(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int markOne(Connection conn, int notificationId, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE notifications SET is_read = TRUE, read_at = COALESCE(read_at, NOW()) "
                        + "WHERE notification_id = ? AND user_id = ?")) {
            stmt.setInt(1, notificationId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate();
        }
    }

    private int markAll(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE notifications SET is_read = TRUE, read_at = COALESCE(read_at, NOW()) "
                        + "WHERE user_id = ? AND is_read = FALSE")) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate();
        }
    }

    private int deleteOne(Connection conn, int notificationId, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM notifications WHERE notification_id = ? AND user_id = ?")) {
            stmt.setInt(1, notificationId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate();
        }
    }

    private Integer authenticatedUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") instanceof Integer
                ? (Integer) session.getAttribute("userId") : null;
    }

    private void finish(HttpServletRequest request, HttpServletResponse response, int status,
                        String message, int updated, String key) throws IOException {
        if (wantsJson(request)) {
            jsonResult(response, status, message, updated);
            return;
        }
        String referer = request.getHeader("Referer");
        String page = referer != null && !referer.trim().isEmpty()
                ? referer : request.getContextPath() + "/HomePage/home.html";
        response.sendRedirect(page + (page.contains("?") ? "&" : "?") + key + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }

    private void jsonResult(HttpServletResponse response, int status, String message, int updated)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().print("{\"message\":\"" + json(message)
                + "\",\"updated\":" + updated + "}");
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
