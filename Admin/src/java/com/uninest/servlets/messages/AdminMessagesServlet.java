package com.uninest.servlets.messages;

import com.uninest.utils.DatabaseConnection;

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

@WebServlet("/AdminMessagesServlet")
public class AdminMessagesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (!isAdmin(session)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"messages\":[],\"error\":\"Admin login required\"}");
            return;
        }

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"messages\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }

            String query = "SELECT m.message_id, m.sender_id, m.recipient_id, m.message_text, "
                    + "m.sent_date, m.is_read, sender.full_name AS sender_name, "
                    + "sender.role AS sender_role, recipient.full_name AS recipient_name, "
                    + "recipient.role AS recipient_role "
                    + "FROM messages m "
                    + "JOIN users sender ON sender.user_id = m.sender_id "
                    + "JOIN users recipient ON recipient.user_id = m.recipient_id "
                    + "WHERE (sender.role IN ('student', 'landlord') AND recipient.role = 'admin') "
                    + "OR (sender.role = 'admin' AND recipient.role IN ('student', 'landlord')) "
                    + "ORDER BY m.sent_date DESC LIMIT 100";

            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                out.print("{\"messages\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.print(",");
                    first = false;
                    writeMessage(out, rs);
                }
                out.print("]}");
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load admin messages", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"messages\":[],\"error\":\"Messages could not be loaded\"}");
        }
    }

    private boolean isAdmin(HttpSession session) {
        return session != null
                && session.getAttribute("userId") != null
                && "admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private void writeMessage(PrintWriter out, ResultSet rs) throws SQLException {
        String body = valueOr(rs.getString("message_text"), "");
        String subject = "Message";
        if (body.startsWith("Subject: ")) {
            int splitAt = body.indexOf('\n');
            if (splitAt >= 0) {
                subject = body.substring(9, splitAt).trim();
                body = body.substring(splitAt + 1).trim();
            }
        }

        boolean senderIsAdmin = "admin".equalsIgnoreCase(rs.getString("sender_role"));
        int contactId = senderIsAdmin ? rs.getInt("recipient_id") : rs.getInt("sender_id");
        String contactName = senderIsAdmin ? rs.getString("recipient_name") : rs.getString("sender_name");
        String contactRole = senderIsAdmin ? rs.getString("recipient_role") : rs.getString("sender_role");

        out.print("{");
        numberField(out, "id", rs.getInt("message_id"), true);
        numberField(out, "contactId", contactId, false);
        stringField(out, "contactName", valueOr(contactName, "UniNest user"), false);
        stringField(out, "contactRole", valueOr(contactRole, "user"), false);
        stringField(out, "senderName", valueOr(rs.getString("sender_name"), "UniNest user"), false);
        stringField(out, "subject", subject, false);
        stringField(out, "body", body, false);
        stringField(out, "sentAt", String.valueOf(rs.getTimestamp("sent_date")), false);
        booleanField(out, "read", rs.getBoolean("is_read"), false);
        out.print("}");
    }

    private void numberField(PrintWriter out, String key, int value, boolean first) {
        if (!first) out.print(",");
        out.print("\"" + key + "\":" + value);
    }

    private void booleanField(PrintWriter out, String key, boolean value, boolean first) {
        if (!first) out.print(",");
        out.print("\"" + key + "\":" + value);
    }

    private void stringField(PrintWriter out, String key, String value, boolean first) {
        if (!first) out.print(",");
        out.print("\"" + key + "\":\"" + json(value) + "\"");
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
