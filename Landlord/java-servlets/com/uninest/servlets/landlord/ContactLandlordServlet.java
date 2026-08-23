package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.MessageAttachmentSupport;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@WebServlet("/ContactLandlordServlet")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class ContactLandlordServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (!isLandlord(session)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"messages\":[],\"error\":\"Landlord login required\"}");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");
        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"messages\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }

            markIncomingRead(conn, userId);

            String query = "SELECT m.message_id, m.sender_id, m.recipient_id, m.message_text, "
                    + "m.attachment_url, m.sent_date, m.is_read, "
                    + "sender.full_name AS sender_name, recipient.full_name AS recipient_name "
                    + "FROM messages m "
                    + "JOIN users sender ON sender.user_id = m.sender_id "
                    + "JOIN users recipient ON recipient.user_id = m.recipient_id "
                    + "WHERE m.sender_id = ? OR m.recipient_id = ? "
                    + "ORDER BY m.sent_date DESC LIMIT 50";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"messages\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        writeMessage(out, rs, userId);
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load landlord support messages", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"messages\":[],\"error\":\"Messages could not be loaded\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (!isLandlord(session)) {
            response.sendRedirect(request.getContextPath() + "/Landlord/login.html");
            return;
        }

        String subject = trim(request.getParameter("subject"));
        String message = trim(request.getParameter("message"));
        if (!ValidationUtil.isRequired(subject) || !ValidationUtil.isRequired(message)) {
            redirect(response, "error", "Subject and message are required");
            return;
        }
        if (subject.length() > 120 || message.length() > 4000) {
            redirect(response, "error", "Subject or message is too long");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, "error", "Service is temporarily unavailable");
                return;
            }

            Integer recipientId = findSupportUser(conn);
            if (recipientId == null) {
                redirect(response, "error", "Support account is not available");
                return;
            }

            String body = "Subject: " + ValidationUtil.sanitizeXSS(subject)
                    + "\n" + ValidationUtil.sanitizeXSS(message);
            int messageId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO messages (sender_id, recipient_id, message_text) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, (Integer) session.getAttribute("userId"));
                stmt.setInt(2, recipientId);
                stmt.setString(3, body);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Message ID was not generated");
                    messageId = keys.getInt(1);
                }
            }

            String storedAttachment = null;
            try {
                Part attachment = multipart(request) ? request.getPart("attachment") : null;
                storedAttachment = MessageAttachmentSupport.save(attachment, messageId);
                if (storedAttachment != null) updateAttachment(conn, messageId, storedAttachment);
            } catch (IOException | IllegalArgumentException e) {
                deleteMessage(conn, messageId);
                MessageAttachmentSupport.delete(storedAttachment);
                redirect(response, "error", e.getMessage());
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                            + "SELECT u.user_id, 'New landlord message', ?, 'message_received', 'Messages.html' "
                            + "FROM users u JOIN admins a ON a.user_id = u.user_id "
                            + "WHERE u.role = 'admin' AND u.status = 'active'")) {
                stmt.setString(1, subject);
                stmt.executeUpdate();
            }

            redirect(response, "success", "Message sent");
        } catch (SQLException e) {
            getServletContext().log("Unable to send landlord support message", e);
            redirect(response, "error", "Could not send message");
        }
    }

    private boolean isLandlord(HttpSession session) {
        return session != null
                && session.getAttribute("userId") != null
                && "landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private Integer findSupportUser(Connection conn) throws SQLException {
        String query = "SELECT u.user_id FROM users u JOIN admins a ON a.user_id = u.user_id "
                + "WHERE u.role = 'admin' AND u.status = 'active' ORDER BY u.user_id LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt("user_id") : null;
        }
    }

    private void writeMessage(PrintWriter out, ResultSet rs, int userId) throws SQLException {
        String body = valueOr(rs.getString("message_text"), "");
        String subject = "Message";
        if (body.startsWith("Subject: ")) {
            int splitAt = body.indexOf('\n');
            if (splitAt >= 0) {
                subject = body.substring(9, splitAt).trim();
                body = body.substring(splitAt + 1).trim();
            }
        }
        out.print("{\"id\":" + rs.getInt("message_id")
                + ",\"sender\":\"" + json(rs.getString("sender_name")) + "\""
                + ",\"recipient\":\"" + json(rs.getString("recipient_name")) + "\""
                + ",\"subject\":\"" + json(subject) + "\""
                + ",\"body\":\"" + json(body) + "\""
                + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("sent_date"))) + "\""
                + ",\"attachmentUrl\":\"" + (rs.getString("attachment_url") == null ? ""
                        : "/MessageAttachmentServlet/" + rs.getInt("message_id")) + "\""
                + ",\"attachmentName\":\"" + json(MessageAttachmentSupport.displayName(
                        rs.getString("attachment_url"))) + "\""
                + ",\"mine\":" + (rs.getInt("sender_id") == userId)
                + ",\"read\":" + rs.getBoolean("is_read") + "}");
    }

    private void markIncomingRead(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE messages SET is_read = TRUE, read_date = COALESCE(read_date, NOW()) "
                        + "WHERE recipient_id = ? AND is_read = FALSE")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE notifications SET is_read = TRUE, read_at = COALESCE(read_at, NOW()) "
                        + "WHERE user_id = ? AND notification_type = 'message_received' AND is_read = FALSE")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    private boolean multipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private void updateAttachment(Connection conn, int messageId, String storedName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE messages SET attachment_url = ? WHERE message_id = ?")) {
            stmt.setString(1, storedName);
            stmt.setInt(2, messageId);
            stmt.executeUpdate();
        }
    }

    private void deleteMessage(Connection conn, int messageId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM messages WHERE message_id = ?")) {
            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        }
    }

    private void redirect(HttpServletResponse response, String key, String message) throws IOException {
        response.sendRedirect("Landlord/contact-landlord.html?" + key + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
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
