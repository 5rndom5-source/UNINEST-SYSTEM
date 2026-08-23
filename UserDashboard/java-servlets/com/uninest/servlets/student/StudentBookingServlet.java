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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

@WebServlet("/StudentBookingServlet")
public class StudentBookingServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        Integer userId = studentUserId(request.getSession(false));
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"bookings\":[],\"error\":\"Student login required\"}");
            return;
        }

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"bookings\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }
            String query = "SELECT b.booking_id, b.check_in, b.check_out, b.booking_date, "
                    + "b.booking_status, b.cancellation_reason, r.room_number, r.location, "
                    + "landlord.full_name AS landlord_name FROM bookings b "
                    + "JOIN students s ON s.student_id = b.student_id "
                    + "JOIN rooms r ON r.room_id = b.room_id "
                    + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                    + "JOIN users landlord ON landlord.user_id = l.user_id "
                    + "WHERE s.user_id = ? ORDER BY b.booking_date DESC LIMIT 50";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"bookings\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        out.print("{\"id\":" + rs.getInt("booking_id")
                                + ",\"listingTitle\":\"" + json(rs.getString("room_number")) + "\""
                                + ",\"area\":\"" + json(rs.getString("location")) + "\""
                                + ",\"landlord\":\"" + json(rs.getString("landlord_name")) + "\""
                                + ",\"checkIn\":\"" + json(String.valueOf(rs.getDate("check_in"))) + "\""
                                + ",\"checkOut\":\"" + json(String.valueOf(rs.getDate("check_out"))) + "\""
                                + ",\"status\":\"" + json(rs.getString("booking_status")) + "\""
                                + ",\"reason\":\"" + json(rs.getString("cancellation_reason")) + "\""
                                + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("booking_date"))) + "\"}");
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load student bookings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"bookings\":[],\"error\":\"Bookings could not be loaded\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer userId = studentUserId(request.getSession(false));
        if (userId == null) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, "error", "Student login required");
            return;
        }
        String action = valueOr(request.getParameter("action"), "create").toLowerCase();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                send(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error",
                        "Service is temporarily unavailable");
                return;
            }
            Integer studentId = findStudentId(conn, userId);
            if (studentId == null) {
                send(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Student profile is incomplete");
                return;
            }
            if ("cancel".equals(action)) cancelBooking(conn, studentId, request, response);
            else if ("update".equals(action)) updateBooking(conn, studentId, request, response);
            else createBooking(conn, studentId, request, response);
        } catch (SQLException e) {
            getServletContext().log("Unable to update student booking", e);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "Booking could not be saved");
        }
    }

    private void createBooking(Connection conn, int studentId, HttpServletRequest request,
                               HttpServletResponse response) throws SQLException, IOException {
        Integer roomId = positiveInteger(request.getParameter("roomId"));
        DateRange range = dateRange(request);
        if (roomId == null || range == null) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Choose valid booking dates");
            return;
        }
        if (!availableRoom(conn, roomId)) {
            send(response, HttpServletResponse.SC_CONFLICT, "error", "This listing is not available for booking");
            return;
        }
        if (hasActiveBooking(conn, studentId, roomId, null)) {
            send(response, HttpServletResponse.SC_CONFLICT, "error", "You already have an active booking for this listing");
            return;
        }
        int bookingId;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO bookings (student_id, room_id, check_in, check_out, booking_status) "
                        + "VALUES (?, ?, ?, ?, 'pending')", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, roomId);
            stmt.setDate(3, Date.valueOf(range.checkIn));
            if (range.checkOut == null) stmt.setNull(4, java.sql.Types.DATE);
            else stmt.setDate(4, Date.valueOf(range.checkOut));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Booking ID was not generated");
                bookingId = keys.getInt(1);
            }
        }
        notifyBookingCreated(conn, bookingId);
        send(response, HttpServletResponse.SC_OK, "success", "Booking request submitted");
    }

    private void updateBooking(Connection conn, int studentId, HttpServletRequest request,
                               HttpServletResponse response) throws SQLException, IOException {
        Integer bookingId = positiveInteger(request.getParameter("bookingId"));
        DateRange range = dateRange(request);
        if (bookingId == null || range == null) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Choose valid booking dates");
            return;
        }
        String query = "UPDATE bookings SET check_in = ?, check_out = ? "
                + "WHERE booking_id = ? AND student_id = ? AND booking_status = 'pending'";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setDate(1, Date.valueOf(range.checkIn));
            if (range.checkOut == null) stmt.setNull(2, java.sql.Types.DATE);
            else stmt.setDate(2, Date.valueOf(range.checkOut));
            stmt.setInt(3, bookingId);
            stmt.setInt(4, studentId);
            int updated = stmt.executeUpdate();
            send(response, updated == 1 ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CONFLICT,
                    updated == 1 ? "success" : "error",
                    updated == 1 ? "Booking dates updated" : "Only pending bookings can be changed");
        }
    }

    private void cancelBooking(Connection conn, int studentId, HttpServletRequest request,
                               HttpServletResponse response) throws SQLException, IOException {
        Integer bookingId = positiveInteger(request.getParameter("bookingId"));
        if (bookingId == null) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Booking not found");
            return;
        }
        String reason = valueOr(request.getParameter("reason"), "Cancelled by student");
        String query = "UPDATE bookings SET booking_status = 'cancelled', cancellation_reason = ? "
                + "WHERE booking_id = ? AND student_id = ? AND booking_status IN ('pending','confirmed')";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, reason);
            stmt.setInt(2, bookingId);
            stmt.setInt(3, studentId);
            int updated = stmt.executeUpdate();
            send(response, updated == 1 ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CONFLICT,
                    updated == 1 ? "success" : "error",
                    updated == 1 ? "Booking cancelled" : "This booking can no longer be cancelled");
        }
    }

    private void notifyBookingCreated(Connection conn, int bookingId) throws SQLException {
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT u.user_id, 'New booking request', CONCAT(student.full_name, ' requested ', r.room_number), "
                + "'booking_update', CASE WHEN u.role = 'landlord' THEN 'Landlord/booking-requests.html' "
                + "ELSE 'BookingRequests.html' END FROM bookings b "
                + "JOIN students s ON s.student_id = b.student_id "
                + "JOIN users student ON student.user_id = s.user_id "
                + "JOIN rooms r ON r.room_id = b.room_id "
                + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "JOIN users u ON (u.user_id = l.user_id OR (u.role = 'admin' AND u.status = 'active')) "
                + "WHERE b.booking_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, bookingId);
            stmt.executeUpdate();
        }
    }

    private Integer findStudentId(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT student_id FROM students WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private boolean availableRoom(Connection conn, int roomId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM rooms WHERE room_id = ? AND approval_status = 'approved' "
                        + "AND availability_status = 'available'")) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasActiveBooking(Connection conn, int studentId, int roomId, Integer excluded)
            throws SQLException {
        String query = "SELECT 1 FROM bookings WHERE student_id = ? AND room_id = ? "
                + "AND booking_status IN ('pending','confirmed')"
                + (excluded == null ? "" : " AND booking_id <> ?") + " LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, roomId);
            if (excluded != null) stmt.setInt(3, excluded);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private DateRange dateRange(HttpServletRequest request) {
        try {
            LocalDate checkIn = LocalDate.parse(request.getParameter("checkIn"));
            String checkOutValue = request.getParameter("checkOut");
            LocalDate checkOut = checkOutValue == null || checkOutValue.trim().isEmpty()
                    ? null : LocalDate.parse(checkOutValue);
            if (checkIn.isBefore(LocalDate.now()) || (checkOut != null && !checkOut.isAfter(checkIn))) return null;
            return new DateRange(checkIn, checkOut);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer studentUserId(HttpSession session) {
        return session != null && session.getAttribute("userId") instanceof Integer
                && "student".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))
                ? (Integer) session.getAttribute("userId") : null;
    }

    private Integer positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void send(HttpServletResponse response, int status, String result, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().print("{\"status\":\"" + result + "\",\"message\":\""
                + json(message) + "\"}");
    }

    private String json(String value) {
        if (value == null || "null".equals(value)) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static class DateRange {
        private final LocalDate checkIn;
        private final LocalDate checkOut;

        private DateRange(LocalDate checkIn, LocalDate checkOut) {
            this.checkIn = checkIn;
            this.checkOut = checkOut;
        }
    }
}
