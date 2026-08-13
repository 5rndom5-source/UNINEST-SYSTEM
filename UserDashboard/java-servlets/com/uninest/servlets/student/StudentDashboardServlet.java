package com.uninest.servlets.student;

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

@WebServlet("/StudentDashboardServlet")
public class StudentDashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!wantsJson(request)) {
            request.getRequestDispatcher("/UserDashboard/index.html").forward(request, response);
            return;
        }

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
                out.print("{\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
                return;
            }

            Integer studentId = findStudentId(conn, userId);
            int savedCount = studentId == null ? 0 : count(conn, "SELECT COUNT(*) FROM favorites WHERE student_id = ?", studentId);
            int requestCount = studentId == null ? 0 : count(conn, "SELECT COUNT(*) FROM inquiries WHERE student_id = ?", studentId);
            int messageCount = count(conn, "SELECT COUNT(*) FROM messages WHERE sender_id = ? OR recipient_id = ?", userId, userId);

            out.print("{");
            out.print("\"savedCount\":" + savedCount + ",");
            out.print("\"requestCount\":" + requestCount + ",");
            out.print("\"messageCount\":" + messageCount);
            out.print("}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"Dashboard details could not be loaded\"}");
            System.err.println("Database error in StudentDashboardServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean wantsJson(HttpServletRequest request) {
        String format = request.getParameter("format");
        String accept = request.getHeader("Accept");
        return "json".equalsIgnoreCase(format) || (accept != null && accept.contains("application/json"));
    }

    private Integer findStudentId(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT student_id FROM students WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("student_id") : null;
            }
        }
    }

    private int count(Connection conn, String query, int... params) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
