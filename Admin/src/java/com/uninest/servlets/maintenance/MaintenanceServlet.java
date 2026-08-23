package com.uninest.servlets.maintenance;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * MaintenanceServlet - Updates maintenance request status and resolution notes.
 * Path: /MaintenanceServlet
 */
@WebServlet("/MaintenanceServlet")
public class MaintenanceServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/Maintenance.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request)) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String requestId = request.getParameter("requestId");
        String status = normalizeStatus(request.getParameter("status"));
        String notes = request.getParameter("resolutionNotes");

        if (!ValidationUtil.isInteger(requestId) || status == null) {
            redirect(request, response, "error", "Invalid maintenance update");
            return;
        }
        if ("resolved".equals(status) && !ValidationUtil.isRequired(notes)) {
            redirect(request, response, "error", "Resolution notes are required when resolving a request");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE maintenance_requests SET request_status = ?, "
                    + "resolution_notes = ?, resolved_date = CASE WHEN ? = 'resolved' THEN NOW() ELSE resolved_date END "
                    + "WHERE request_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, status);
            stmt.setString(2, notes);
            stmt.setString(3, status);
            stmt.setInt(4, Integer.parseInt(requestId));
            int updated = stmt.executeUpdate();
            stmt.close();

            if (updated > 0) {
                notifyStudent(conn, Integer.parseInt(requestId), status, notes);
            }

            redirect(request, response, updated > 0 ? "success" : "error",
                    updated > 0 ? "Maintenance request updated" : "No matching maintenance request was found");
        } catch (SQLException e) {
            System.err.println("Database error in MaintenanceServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update maintenance request");
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") != null
                && "admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private void notifyStudent(Connection conn, int requestId, String status, String notes)
            throws SQLException {
        String message = "Maintenance request status changed to " + status
                + (notes == null || notes.trim().isEmpty() ? "" : ": " + notes.trim());
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT u.user_id, 'Maintenance updated', ?, 'maintenance_update', "
                + "'UserDashboard/requests.html' FROM maintenance_requests m "
                + "JOIN students s ON s.student_id = m.student_id "
                + "JOIN users u ON u.user_id = s.user_id WHERE m.request_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, message);
            stmt.setInt(2, requestId);
            stmt.executeUpdate();
        }
    }

    private String normalizeStatus(String value) {
        if ("pending".equalsIgnoreCase(value)) return "pending";
        if ("in_progress".equalsIgnoreCase(value)) return "in_progress";
        if ("resolved".equalsIgnoreCase(value)) return "resolved";
        if ("cancelled".equalsIgnoreCase(value)) return "cancelled";
        return null;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/Maintenance.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
