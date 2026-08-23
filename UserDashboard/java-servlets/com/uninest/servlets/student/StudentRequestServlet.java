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

@WebServlet("/StudentRequestServlet")
public class StudentRequestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (!isStudent(session)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"Login required\"}");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"requests\":[],\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
                return;
            }

            Integer studentId = findStudentId(conn, userId);
            if (studentId == null) {
                out.print("{\"requests\":[]}");
                return;
            }

            String query = "SELECT i.inquiry_id, i.message, i.inquiry_date, i.reply_status, "
                    + "r.room_number, r.location, r.room_type "
                    + "FROM inquiries i "
                    + "JOIN rooms r ON r.room_id = i.room_id "
                    + "WHERE i.student_id = ? "
                    + "ORDER BY i.inquiry_date DESC LIMIT 50";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"requests\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        writeRequest(out, rs);
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"requests\":[],\"error\":\"Requests could not be loaded\"}");
            System.err.println("Database error in StudentRequestServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (!isStudent(session)) {
            sendUnauthenticated(request, response);
            return;
        }

        String action = trim(request.getParameter("action"));
        if ("delete".equalsIgnoreCase(action)) {
            deleteRequest(request, response, (Integer) session.getAttribute("userId"));
            return;
        }

        String roomIdValue = firstNonEmpty(request.getParameter("roomId"), request.getParameter("listingId"));
        String requestType = trim(request.getParameter("requestType"));

        if (!ValidationUtil.isRequired(roomIdValue) || !ValidationUtil.isRequired(requestType)) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Listing and request type are required");
            return;
        }

        Integer roomId = parseRoomId(roomIdValue);
        if (roomId == null) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Listing could not be found");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendResult(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            Integer studentId = ensureStudentRecord(conn, userId);
            RoomRecord room = findApprovedRoom(conn, roomId);
            if (room == null) {
                sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "This listing is not available for requests");
                return;
            }

            if (hasPendingRequest(conn, studentId, roomId)) {
                sendResult(request, response, HttpServletResponse.SC_CONFLICT, "error",
                        "You already have a pending request for this listing");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO inquiries (student_id, landlord_id, room_id, message, reply_status) "
                            + "VALUES (?, ?, ?, ?, 'pending')")) {
                stmt.setInt(1, studentId);
                stmt.setInt(2, room.landlordId);
                stmt.setInt(3, room.roomId);
                stmt.setString(4, requestType + " request for " + room.title);
                stmt.executeUpdate();
            }

            notifyLandlord(conn, room.landlordId, room.title);

            sendResult(request, response, HttpServletResponse.SC_OK, "success", "Request sent");
        } catch (SQLException e) {
            System.err.println("Database error in StudentRequestServlet: " + e.getMessage());
            e.printStackTrace();
            sendResult(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "Could not send request");
        }
    }

    private void deleteRequest(HttpServletRequest request, HttpServletResponse response, int userId)
            throws IOException {
        Integer requestId = parseRoomId(request.getParameter("requestId"));
        if (requestId == null) {
            sendResult(request, response, HttpServletResponse.SC_BAD_REQUEST, "error", "Request not found");
            return;
        }
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendResult(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "error", "Service is temporarily unavailable");
                return;
            }
            Integer studentId = findStudentId(conn, userId);
            if (studentId == null) {
                sendResult(request, response, HttpServletResponse.SC_NOT_FOUND, "error", "Request not found");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM inquiries WHERE inquiry_id = ? AND student_id = ? AND reply_status = 'pending'")) {
                stmt.setInt(1, requestId);
                stmt.setInt(2, studentId);
                int deleted = stmt.executeUpdate();
                sendResult(request, response, deleted == 1 ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CONFLICT,
                        deleted == 1 ? "success" : "error",
                        deleted == 1 ? "Request deleted" : "Only pending requests can be deleted");
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to delete student request", e);
            sendResult(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "error", "Request could not be deleted");
        }
    }

    private boolean hasPendingRequest(Connection conn, int studentId, int roomId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM inquiries WHERE student_id = ? AND room_id = ? "
                        + "AND reply_status = 'pending' LIMIT 1")) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void notifyLandlord(Connection conn, int landlordId, String title) throws SQLException {
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT user_id, 'New viewing request', ?, 'inquiry_response', "
                + "'Landlord/booking-requests.html' FROM landlords WHERE landlord_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, "A student requested a viewing for " + title);
            stmt.setInt(2, landlordId);
            stmt.executeUpdate();
        }
    }

    private boolean isStudent(HttpSession session) {
        return session != null && session.getAttribute("userId") instanceof Integer
                && "student".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private Integer findStudentId(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT student_id FROM students WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("student_id") : null;
            }
        }
    }

    private Integer ensureStudentRecord(Connection conn, int userId) throws SQLException {
        Integer studentId = findStudentId(conn, userId);
        if (studentId != null) return studentId;

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO students (user_id) VALUES (?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, userId);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : null;
            }
        }
    }

    private RoomRecord findApprovedRoom(Connection conn, int roomId) throws SQLException {
        String query = "SELECT room_id, landlord_id, room_number "
                + "FROM rooms WHERE room_id = ? AND approval_status = 'approved' AND availability_status = 'available'";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                RoomRecord room = new RoomRecord();
                room.roomId = rs.getInt("room_id");
                room.landlordId = rs.getInt("landlord_id");
                room.title = valueOr(rs.getString("room_number"), "student accommodation");
                return room;
            }
        }
    }

    private void writeRequest(PrintWriter out, ResultSet rs) throws SQLException {
        out.print("{");
        field(out, "id", String.valueOf(rs.getInt("inquiry_id")), true);
        field(out, "listingTitle", valueOr(rs.getString("room_number"), "Student accommodation"), false);
        field(out, "area", valueOr(rs.getString("location"), "Cape Town"), false);
        field(out, "requestType", requestTypeFromMessage(rs.getString("message")), false);
        field(out, "status", displayStatus(rs.getString("reply_status")), false);
        field(out, "createdAt", String.valueOf(rs.getTimestamp("inquiry_date")), false);
        out.print("}");
    }

    private String requestTypeFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "Viewing";
        int splitAt = message.indexOf(" request");
        return splitAt > 0 ? message.substring(0, splitAt).trim() : "Viewing";
    }

    private String displayStatus(String status) {
        if (status == null || status.trim().isEmpty()) return "Pending";
        String clean = status.replace('_', ' ').trim().toLowerCase();
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
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
        response.sendRedirect(request.getContextPath() + "/UserDashboard/requests.html?"
                + key + "=" + java.net.URLEncoder.encode(message, "UTF-8"));
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    private Integer parseRoomId(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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

    private static class RoomRecord {
        int roomId;
        int landlordId;
        String title;
    }
}
