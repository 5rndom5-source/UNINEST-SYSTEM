package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/BookingDecisionServlet")
public class BookingDecisionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String requestId = firstNonEmpty(request.getParameter("bookingId"), request.getParameter("requestId"));
        String decision = request.getParameter("decision");
        String status = normalizeDecision(decision);

        if (!ValidationUtil.isInteger(requestId) || status == null) {
            response.sendRedirect("Landlord/booking-requests.html?error=missing");
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("landlordId") == null
                || !"landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.sendRedirect("Landlord/login.html?error=session");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect("Landlord/booking-requests.html?error=db");
                return;
            }

            int updated = com.uninest.utils.BookingTransitions.update(conn,Integer.parseInt(requestId),
                    (Integer)session.getAttribute("landlordId"),status,"Declined by landlord");

            response.sendRedirect("Landlord/booking-requests.html?"
                    + (updated > 0 ? "decision=" + decision : "error=notfound"));
        } catch (SQLException e) {
            System.err.println("Database error in BookingDecisionServlet: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("Landlord/booking-requests.html?error=save");
        }
    }

    private void notifyStudent(Connection conn, int bookingId, String status) throws SQLException {
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT u.user_id, 'Booking updated', CONCAT('Your booking for ', r.room_number, "
                + "' is now ', ?), 'booking_update', 'UserDashboard/requests.html' FROM bookings b "
                + "JOIN students s ON s.student_id = b.student_id "
                + "JOIN users u ON u.user_id = s.user_id "
                + "JOIN rooms r ON r.room_id = b.room_id WHERE b.booking_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, status);
            stmt.setInt(2, bookingId);
            stmt.executeUpdate();
        }
    }

    private String normalizeDecision(String decision) {
        if ("approve".equalsIgnoreCase(decision) || "confirmed".equalsIgnoreCase(decision)) {
            return "confirmed";
        }
        if ("reject".equalsIgnoreCase(decision) || "deny".equalsIgnoreCase(decision)
                || "cancel".equalsIgnoreCase(decision)) {
            return "cancelled";
        }
        return null;
    }

    private String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }
}
