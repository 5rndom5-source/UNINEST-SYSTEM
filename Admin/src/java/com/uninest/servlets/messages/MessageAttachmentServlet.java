package com.uninest.servlets.messages;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.MessageAttachmentSupport;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/MessageAttachmentServlet/*")
public class MessageAttachmentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Integer)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Integer messageId = pathId(request.getPathInfo());
        if (messageId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            AttachmentRecord record = findAttachment(conn, messageId);
            int userId = (Integer) session.getAttribute("userId");
            String role = String.valueOf(session.getAttribute("role"));
            if (record == null || !canAccess(record, userId, role)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            Path file = MessageAttachmentSupport.resolve(record.storedName);
            if (file == null || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String contentType = Files.probeContentType(file);
            response.setContentType(contentType == null ? "application/octet-stream" : contentType);
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + MessageAttachmentSupport.displayName(record.storedName).replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
            response.setContentLengthLong(Files.size(file));
            Files.copy(file, response.getOutputStream());
        } catch (SQLException e) {
            getServletContext().log("Unable to load message attachment", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private AttachmentRecord findAttachment(Connection conn, int messageId) throws SQLException {
        String query = "SELECT m.sender_id, m.recipient_id, m.attachment_url, "
                + "sender.role AS sender_role, recipient.role AS recipient_role "
                + "FROM messages m JOIN users sender ON sender.user_id = m.sender_id "
                + "JOIN users recipient ON recipient.user_id = m.recipient_id "
                + "WHERE m.message_id = ? AND m.attachment_url IS NOT NULL";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new AttachmentRecord(rs.getInt("sender_id"), rs.getInt("recipient_id"),
                        rs.getString("sender_role"), rs.getString("recipient_role"),
                        rs.getString("attachment_url"));
            }
        }
    }

    private boolean canAccess(AttachmentRecord record, int userId, String role) {
        if (record.senderId == userId || record.recipientId == userId) return true;
        return "admin".equalsIgnoreCase(role)
                && ("admin".equalsIgnoreCase(record.senderRole)
                || "admin".equalsIgnoreCase(record.recipientRole));
    }

    private Integer pathId(String path) {
        if (path == null || path.length() < 2) return null;
        try {
            int id = Integer.parseInt(path.substring(1));
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class AttachmentRecord {
        private final int senderId;
        private final int recipientId;
        private final String senderRole;
        private final String recipientRole;
        private final String storedName;

        private AttachmentRecord(int senderId, int recipientId, String senderRole,
                                 String recipientRole, String storedName) {
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.senderRole = senderRole;
            this.recipientRole = recipientRole;
            this.storedName = storedName;
        }
    }
}
