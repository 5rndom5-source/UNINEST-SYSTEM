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
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ApproveBookingServlet - Confirms a student booking from the admin portal.
 * Path: /ApproveBookingServlet
 */
@WebServlet("/ApproveBookingServlet")
public class ApproveBookingServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminId") == null) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String bookingId = request.getParameter("bookingId");
        if (!ValidationUtil.isInteger(bookingId)) {
            redirect(request, response, "error", "Invalid booking ID");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            int updated = updateBookingStatus(conn, Integer.parseInt(bookingId), "confirmed");
            if (updated == 0) {
                redirect(request, response, "error", "No matching booking was found");
                return;
            }

            Integer studentUserId = findStudentUserId(conn, Integer.parseInt(bookingId));
            if (studentUserId != null) {
                createNotification(
                        conn,
                        studentUserId,
                        "Booking Confirmed",
                        "Your UniNest booking request has been approved.",
                        "booking_confirmed",
                        "BookingDetails.html?bookingId=" + bookingId);
            }

            redirect(request, response, "success", "Booking approved");
        } catch (SQLException e) {
            System.err.println("Database error in ApproveBookingServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Error approving booking");
        }
    }

    private int updateBookingStatus(Connection conn, int bookingId, String status) throws SQLException {
        String query = "UPDATE bookings SET booking_status = ? WHERE booking_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, status);
        stmt.setInt(2, bookingId);
        int updated = stmt.executeUpdate();
        stmt.close();
        return updated;
    }

    private Integer findStudentUserId(Connection conn, int bookingId) throws SQLException {
        String query = "SELECT u.user_id FROM bookings b "
                + "JOIN students s ON b.student_id = s.student_id "
                + "JOIN users u ON s.user_id = u.user_id "
                + "WHERE b.booking_id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, bookingId);
        ResultSet rs = stmt.executeQuery();

        Integer userId = null;
        if (rs.next()) {
            userId = rs.getInt("user_id");
        }

        stmt.close();
        return userId;
    }

    private void createNotification(Connection conn, int userId, String title,
                                    String message, String type, String actionUrl)
            throws SQLException {
        String query = "INSERT INTO notifications "
                + "(user_id, title, message, notification_type, action_url) "
                + "VALUES (?, ?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, userId);
        stmt.setString(2, title);
        stmt.setString(3, message);
        stmt.setString(4, type);
        stmt.setString(5, actionUrl);
        stmt.executeUpdate();
        stmt.close();
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
