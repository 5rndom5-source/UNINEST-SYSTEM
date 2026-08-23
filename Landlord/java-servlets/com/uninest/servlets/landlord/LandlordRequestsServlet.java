package com.uninest.servlets.landlord;

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

@WebServlet("/LandlordRequestsServlet")
public class LandlordRequestsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (!isLandlord(session)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"requests\":[],\"error\":\"Landlord login required\"}");
            return;
        }

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"requests\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }

            String query = "SELECT i.inquiry_id, i.message, i.inquiry_date, i.reply_status, "
                    + "u.full_name AS student_name, u.email AS student_email, "
                    + "r.room_number, r.location "
                    + "FROM inquiries i "
                    + "JOIN students s ON s.student_id = i.student_id "
                    + "JOIN users u ON u.user_id = s.user_id "
                    + "JOIN rooms r ON r.room_id = i.room_id "
                    + "WHERE i.landlord_id = ? ORDER BY i.inquiry_date DESC LIMIT 100";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, (Integer) session.getAttribute("landlordId"));
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"requests\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        out.print("{\"id\":" + rs.getInt("inquiry_id")
                                + ",\"studentName\":\"" + json(rs.getString("student_name")) + "\""
                                + ",\"studentEmail\":\"" + json(rs.getString("student_email")) + "\""
                                + ",\"property\":\"" + json(valueOr(rs.getString("room_number"), "Student accommodation")) + "\""
                                + ",\"location\":\"" + json(valueOr(rs.getString("location"), "")) + "\""
                                + ",\"message\":\"" + json(rs.getString("message")) + "\""
                                + ",\"status\":\"" + json(rs.getString("reply_status")) + "\""
                                + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("inquiry_date"))) + "\"}");
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load landlord requests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"requests\":[],\"error\":\"Requests could not be loaded\"}");
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

        String inquiryId = request.getParameter("inquiryId");
        String action = request.getParameter("action");
        String status = "reply".equalsIgnoreCase(action) ? "replied"
                : "resolve".equalsIgnoreCase(action) ? "resolved" : null;
        if (!ValidationUtil.isInteger(inquiryId) || status == null) {
            redirect(response, "error", "Invalid request update");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, "error", "Service is temporarily unavailable");
                return;
            }

            Integer studentUserId = null;
            String property = "accommodation request";
            String lookup = "SELECT s.user_id, r.room_number FROM inquiries i "
                    + "JOIN students s ON s.student_id = i.student_id "
                    + "JOIN rooms r ON r.room_id = i.room_id "
                    + "WHERE i.inquiry_id = ? AND i.landlord_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(lookup)) {
                stmt.setInt(1, Integer.parseInt(inquiryId));
                stmt.setInt(2, (Integer) session.getAttribute("landlordId"));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        studentUserId = rs.getInt("user_id");
                        property = valueOr(rs.getString("room_number"), property);
                    }
                }
            }

            if (studentUserId == null) {
                redirect(response, "error", "Request was not found");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE inquiries SET reply_status = ? WHERE inquiry_id = ? AND landlord_id = ?")) {
                stmt.setString(1, status);
                stmt.setInt(2, Integer.parseInt(inquiryId));
                stmt.setInt(3, (Integer) session.getAttribute("landlordId"));
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                            + "VALUES (?, 'Request updated', ?, 'inquiry_response', 'UserDashboard/requests.html')")) {
                stmt.setInt(1, studentUserId);
                stmt.setString(2, "Your request for " + property + " is now " + status + ".");
                stmt.executeUpdate();
            }

            redirect(response, "success", "Request updated");
        } catch (SQLException e) {
            getServletContext().log("Unable to update landlord request", e);
            redirect(response, "error", "Request could not be updated");
        }
    }

    private boolean isLandlord(HttpSession session) {
        return session != null
                && session.getAttribute("landlordId") != null
                && "landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private void redirect(HttpServletResponse response, String key, String message) throws IOException {
        response.sendRedirect("Landlord/booking-requests.html?" + key + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
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
