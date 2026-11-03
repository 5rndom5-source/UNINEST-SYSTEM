package com.uninest.servlets.messages;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.MessageAttachmentSupport;
import com.uninest.utils.MessagePermissions;

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

@WebServlet("/MessageActionServlet")
public class MessageActionServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Integer)) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        Integer messageId = positiveInteger(request.getParameter("messageId"));
        String action = value(request.getParameter("action"));
        if (messageId == null || !("delete".equals(action) || "read".equals(action))) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid message action");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");
        String role = value(String.valueOf(session.getAttribute("role")));
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                send(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Service is temporarily unavailable");
                return;
            }

            MessageRecord record = findMessage(conn, messageId);
            if (record == null) {
                send(response, HttpServletResponse.SC_NOT_FOUND, "Message not found");
                return;
            }
            if (!canAccess(record, userId, role)) {
                send(response, HttpServletResponse.SC_FORBIDDEN,
                        "You do not have permission to change this message");
                return;
            }

            if ("read".equals(action)) {
                if (!MessagePermissions.canMarkRead(userId, role, record.recipientId, record.recipientRole)) {
                    send(response, HttpServletResponse.SC_FORBIDDEN, "Only the recipient can mark this message as read");
                    return;
                }
                markRead(conn, messageId);
                send(response, HttpServletResponse.SC_OK, "Message marked as read");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM messages WHERE message_id = ?")) {
                stmt.setInt(1, messageId);
                if (stmt.executeUpdate() != 1) {
                    send(response, HttpServletResponse.SC_NOT_FOUND, "Message not found");
                    return;
                }
            }
            MessageAttachmentSupport.delete(record.attachment);
            send(response, HttpServletResponse.SC_OK, "Message deleted");
        } catch (SQLException e) {
            getServletContext().log("Unable to update message", e);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Message could not be updated");
        }
    }

    private MessageRecord findMessage(Connection conn, int messageId) throws SQLException {
        String query = "SELECT m.sender_id, m.recipient_id, m.attachment_url, "
                + "sender.role AS sender_role, recipient.role AS recipient_role "
                + "FROM messages m JOIN users sender ON sender.user_id = m.sender_id "
                + "JOIN users recipient ON recipient.user_id = m.recipient_id "
                + "WHERE m.message_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new MessageRecord(rs.getInt("sender_id"), rs.getInt("recipient_id"),
                        rs.getString("sender_role"), rs.getString("recipient_role"),
                        rs.getString("attachment_url"));
            }
        }
    }

    private boolean canAccess(MessageRecord record, int userId, String role) {
        if (record.senderId == userId || record.recipientId == userId) return true;
        return "admin".equals(role)
                && ("admin".equalsIgnoreCase(record.senderRole)
                || "admin".equalsIgnoreCase(record.recipientRole));
    }

    private void markRead(Connection conn, int messageId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE messages SET is_read = TRUE, read_date = COALESCE(read_date, NOW()) "
                        + "WHERE message_id = ?")) {
            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        }
    }

    private void send(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().print("{\"message\":\"" + json(message) + "\"}");
    }

    private Integer positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String value(String input) {
        return input == null ? "" : input.trim().toLowerCase();
    }

    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class MessageRecord {
        private final int senderId;
        private final int recipientId;
        private final String senderRole;
        private final String recipientRole;
        private final String attachment;

        private MessageRecord(int senderId, int recipientId, String senderRole,
                              String recipientRole, String attachment) {
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.senderRole = senderRole;
            this.recipientRole = recipientRole;
            this.attachment = attachment;
        }
    }
}
