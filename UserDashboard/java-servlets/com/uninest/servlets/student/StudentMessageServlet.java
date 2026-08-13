package com.uninest.servlets.student;

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

@WebServlet("/StudentMessageServlet")
public class StudentMessageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
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

            String query = "SELECT m.message_id, m.message_text, m.sent_date, "
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
                        writeMessage(out, rs);
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
        if (session == null || session.getAttribute("userId") == null) {
            sendUnauthenticated(request, response);
            return;
        }

        String subject = trim(request.getParameter("subject"));
        String message = trim(request.getParameter("message"));

        if (!ValidationUtil.isRequired(subject) || !ValidationUtil.isRequired(message)) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Subject and message are required");
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

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO messages (sender_id, recipient_id, message_text) VALUES (?, ?, ?)")) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, recipientId);
                stmt.setString(3, body);
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
                + "WHERE u.role = 'admin' "
                + "ORDER BY CASE WHEN u.status = 'active' THEN 0 ELSE 1 END, u.user_id ASC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt("user_id") : null;
        }
    }

    private void writeMessage(PrintWriter out, ResultSet rs) throws SQLException {
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
        out.print("}");
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
