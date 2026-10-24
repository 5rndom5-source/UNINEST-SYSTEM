package com.uninest.servlets.bookings;

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
 * BookingActionServlet - Handles approve, reject, cancel, and complete actions.
 * Path: /BookingActionServlet
 */
@WebServlet("/BookingActionServlet")
public class BookingActionServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String bookingId = request.getParameter("bookingId");
        String status = normalizeStatus(firstNonEmpty(request.getParameter("status"), request.getParameter("decision")));
        String reason = request.getParameter("reason");

        if (!ValidationUtil.isInteger(bookingId) || status == null) {
            redirect(request, response, "error", "Invalid booking action");
            return;
        }
        if ("cancelled".equals(status) && !ValidationUtil.isRequired(reason)) {
            redirect(request, response, "error", "A cancellation reason is required");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            int updated = com.uninest.utils.BookingTransitions.update(conn,Integer.parseInt(bookingId),null,status,reason);

            redirect(request, response, updated > 0 ? "success" : "error",
                    updated > 0 ? "Booking updated" : "Booking transition is not allowed or room capacity is unavailable");
        } catch (SQLException e) {
            System.err.println("Database error in BookingActionServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update booking");
        }
    }

    private void notifyStudent(Connection conn, int bookingId, String status, String reason)
            throws SQLException {
        String message = "Your booking is now " + status
                + ("cancelled".equals(status) && reason != null ? ": " + reason.trim() : "");
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT u.user_id, 'Booking updated', ?, 'booking_update', 'UserDashboard/requests.html' "
                + "FROM bookings b JOIN students s ON s.student_id = b.student_id "
                + "JOIN users u ON u.user_id = s.user_id WHERE b.booking_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, message);
            stmt.setInt(2, bookingId);
            stmt.executeUpdate();
        }
    }

    private String normalizeStatus(String value) {
        if ("approve".equalsIgnoreCase(value) || "approved".equalsIgnoreCase(value)
                || "confirmed".equalsIgnoreCase(value)) return "confirmed";
        if ("deny".equalsIgnoreCase(value) || "reject".equalsIgnoreCase(value)
                || "cancel".equalsIgnoreCase(value) || "cancelled".equalsIgnoreCase(value)) return "cancelled";
        if ("completed".equalsIgnoreCase(value)) return "completed";
        if ("pending".equalsIgnoreCase(value)) return "pending";
        return null;
    }

    private String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/BookingRequests.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
