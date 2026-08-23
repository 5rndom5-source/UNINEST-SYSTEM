package com.uninest.servlets.enquiries;

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

@WebServlet("/AdminEnquiriesServlet")
public class AdminEnquiriesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"enquiries\":[],\"error\":\"Admin login required\"}");
            return;
        }

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"enquiries\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }

            String query = "SELECT notification_id, title, message, created_at, is_read "
                    + "FROM notifications WHERE user_id = ? AND action_url = 'Enquiries.html' "
                    + "ORDER BY created_at DESC LIMIT 100";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, (Integer) session.getAttribute("userId"));
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"enquiries\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        out.print("{\"id\":" + rs.getInt("notification_id")
                                + ",\"title\":\"" + json(rs.getString("title")) + "\""
                                + ",\"message\":\"" + json(rs.getString("message")) + "\""
                                + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("created_at"))) + "\""
                                + ",\"read\":" + rs.getBoolean("is_read") + "}");
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load admin enquiries", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"enquiries\":[],\"error\":\"Enquiries could not be loaded\"}");
        }
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
