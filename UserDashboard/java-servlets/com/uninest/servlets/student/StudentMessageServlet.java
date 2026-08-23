package com.uninest.servlets.student;

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

@WebServlet("/StudentMessageServlet")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class StudentMessageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (!isStudent(session)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"Login required\"}");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"messages\":[],\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
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
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"messages\":[],\"error\":\"Messages could not be loaded\"}");
            System.err.println("Database error in StudentMessageServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (!isStudent(session)) {
            sendUnauthenticated(request, response);
            return;
        }

        String subject = trim(request.getParameter("subject"));
        String message = trim(request.getParameter("message"));

        if (!ValidationUtil.isRequired(subject) || !ValidationUtil.isRequired(message)) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Subject and message are required");
            return;
        }
        if (subject.length() > 120 || message.length() > 4000) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST,
                    "error", "Subject or message is too long");
            return;
        }

        int senderId = (Integer) session.getAttribute("userId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendResult(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            Integer recipientId = findSupportUser(conn);
            if (recipientId == null) {
                sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Support account is not available");
                return;
            }

            String body = "Subject: " + ValidationUtil.sanitizeXSS(subject)
                    + "\n" + ValidationUtil.sanitizeXSS(message);

            int messageId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO messages (sender_id, recipient_id, message_text) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, senderId);
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
                sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST,
                        "error", e.getMessage());
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                            + "SELECT u.user_id, 'New student message', ?, 'message_received', 'Messages.html' "
                            + "FROM users u JOIN admins a ON a.user_id = u.user_id "
                            + "WHERE u.role = 'admin' AND u.status = 'active'")) {
                stmt.setString(1, subject);
                stmt.executeUpdate();
            }

            sendResult(request, response, HttpServletResponse.SC_OK, "success", "Message sent");
        } catch (SQLException e) {
            System.err.println("Database error in StudentMessageServlet: " + e.getMessage());
            e.printStackTrace();
            sendResult(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "Could not send message");
        }
    }

    private Integer findSupportUser(Connection conn) throws SQLException {
        String query = "SELECT u.user_id FROM users u "
                + "JOIN admins a ON a.user_id = u.user_id "
                + "WHERE u.role = 'admin' AND u.status = 'active' "
                + "ORDER BY u.user_id ASC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt("user_id") : null;
        }
    }

    private boolean isStudent(HttpSession session) {
        return session != null
                && session.getAttribute("userId") != null
                && "student".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private void writeMessage(PrintWriter out, ResultSet rs, int userId) throws SQLException {
        String body = valueOr(rs.getString("message_text"), "");
        String subject = "Message";
        String message = body;
        if (body.startsWith("Subject: ")) {
            int splitAt = body.indexOf('\n');
            if (splitAt > -1) {
                subject = body.substring(9, splitAt).trim();
                message = body.substring(splitAt + 1).trim();
            }
        }

        out.print("{");
        field(out, "id", String.valueOf(rs.getInt("message_id")), true);
        field(out, "sender", valueOr(rs.getString("sender_name"), "Student"), false);
        field(out, "recipient", valueOr(rs.getString("recipient_name"), "UniNest Support"), false);
        field(out, "subject", subject, false);
        field(out, "body", message, false);
        field(out, "createdAt", String.valueOf(rs.getTimestamp("sent_date")), false);
        field(out, "attachmentUrl", rs.getString("attachment_url") == null
                ? "" : "/MessageAttachmentServlet/" + rs.getInt("message_id"), false);
        field(out, "attachmentName", MessageAttachmentSupport.displayName(
                rs.getString("attachment_url")), false);
        out.print(",\"mine\":" + (rs.getInt("sender_id") == userId));
        out.print(",\"read\":" + rs.getBoolean("is_read"));
        out.print("}");
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

    private void sendUnauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().print("{\"status\":\"error\",\"message\":\"Login required\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/createAccount/login.html");
    }

    private void sendResult(HttpServletRequest request, HttpServletResponse response, int status,
                            String key, String message) throws IOException {
        if (wantsJson(request)) {
            response.setStatus(status);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().print("{\"status\":\"" + key + "\",\"message\":\"" + json(message) + "\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/UserDashboard/messages.html?"
                + key + "=" + java.net.URLEncoder.encode(message, "UTF-8"));
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void field(PrintWriter out, String key, String value, boolean first) {
        if (!first) out.print(",");
        out.print("\"" + key + "\":\"" + json(value) + "\"");
    }

    private String json(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
