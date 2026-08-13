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
        if (session == null || session.getAttribute("landlordId") == null) {
            response.sendRedirect("Landlord/login.html?error=session");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect("Landlord/booking-requests.html?error=db");
                return;
            }

            String query = "UPDATE bookings b JOIN rooms r ON b.room_id = r.room_id "
                    + "SET b.booking_status = ? "
                    + "WHERE b.booking_id = ? AND r.landlord_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, status);
            stmt.setInt(2, Integer.parseInt(requestId));
            stmt.setInt(3, (Integer) session.getAttribute("landlordId"));
            int updated = stmt.executeUpdate();
            stmt.close();

            response.sendRedirect("Landlord/booking-requests.html?"
                    + (updated > 0 ? "decision=" + decision : "error=notfound"));
        } catch (SQLException e) {
            System.err.println("Database error in BookingDecisionServlet: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("Landlord/booking-requests.html?error=save");
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
