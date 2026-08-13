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
        if (session == null || session.getAttribute("adminId") == null) {
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

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE bookings SET booking_status = ?, cancellation_reason = ? WHERE booking_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, status);
            stmt.setString(2, "cancelled".equals(status) ? reason : null);
            stmt.setInt(3, Integer.parseInt(bookingId));
            int updated = stmt.executeUpdate();
            stmt.close();

            redirect(request, response, updated > 0 ? "success" : "error",
                    updated > 0 ? "Booking updated" : "No matching booking was found");
        } catch (SQLException e) {
            System.err.println("Database error in BookingActionServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update booking");
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
