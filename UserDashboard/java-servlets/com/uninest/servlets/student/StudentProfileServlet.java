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

@WebServlet("/StudentProfileServlet")
public class StudentProfileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"student".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
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

            String query = "SELECT u.full_name, u.email, u.phone, s.institution, s.program, "
                    + "s.year_level, s.emergency_contact "
                    + "FROM users u LEFT JOIN students s ON s.user_id = u.user_id "
                    + "WHERE u.user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"profile\":");
                    if (rs.next()) {
                        out.print("{");
                        field(out, "fullName", rs.getString("full_name"), true);
                        field(out, "email", rs.getString("email"), false);
                        field(out, "phone", rs.getString("phone"), false);
                        field(out, "institution", rs.getString("institution"), false);
                        field(out, "program", rs.getString("program"), false);
                        field(out, "yearLevel", rs.getString("year_level"), false);
                        field(out, "emergencyContact", rs.getString("emergency_contact"), false);
                        out.print("}");
                    } else {
                        out.print("{}");
                    }
                    out.print("}");
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"Profile could not be loaded\"}");
            System.err.println("Database error in StudentProfileServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"student".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            sendUnauthenticated(request, response);
            return;
        }

        String fullName = trim(request.getParameter("fullName"));
        String email = trim(request.getParameter("email"));
        String phone = trim(request.getParameter("phone"));
        String institution = trim(request.getParameter("institution"));
        String program = trim(request.getParameter("program"));
        String yearLevel = trim(request.getParameter("yearLevel"));
        String emergencyContact = trim(request.getParameter("emergencyContact"));

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || (!phone.isEmpty() && !ValidationUtil.isValidPhone(phone))) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Enter a valid name, email, and phone number");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendResult(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET full_name = ?, email = ?, phone = ? WHERE user_id = ?")) {
                stmt.setString(1, fullName);
                stmt.setString(2, email);
                stmt.setString(3, phone);
                stmt.setInt(4, userId);
                stmt.executeUpdate();
            }

            ensureStudentRecord(conn, userId);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE students SET institution = ?, program = ?, year_level = ?, emergency_contact = ? "
                            + "WHERE user_id = ?")) {
                stmt.setString(1, institution);
                stmt.setString(2, program);
                stmt.setString(3, yearLevel);
                stmt.setString(4, emergencyContact);
                stmt.setInt(5, userId);
                stmt.executeUpdate();
            }

            session.setAttribute("studentName", fullName);
            session.setAttribute("fullName", fullName);
            session.setAttribute("email", email);
            sendResult(request, response, HttpServletResponse.SC_OK, "success", "Profile saved");
        } catch (SQLException e) {
            System.err.println("Database error in StudentProfileServlet: " + e.getMessage());
            e.printStackTrace();
            sendResult(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "Could not save profile");
        }
    }

    private void ensureStudentRecord(Connection conn, int userId) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement("SELECT student_id FROM students WHERE user_id = ?")) {
            check.setInt(1, userId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return;
            }
        }

        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO students (user_id) VALUES (?)")) {
            insert.setInt(1, userId);
            insert.executeUpdate();
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
        response.sendRedirect(request.getContextPath() + "/UserDashboard/profile.html?"
                + key + "=" + java.net.URLEncoder.encode(message, "UTF-8"));
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
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
