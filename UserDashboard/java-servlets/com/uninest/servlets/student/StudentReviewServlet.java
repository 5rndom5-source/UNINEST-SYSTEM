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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/StudentReviewServlet")
public class StudentReviewServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"student".equals(session.getAttribute("role"))) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, "Login with a student account to leave a review", null);
            return;
        }

        Integer roomId = parseInteger(request.getParameter("roomId"));
        Integer rating = parseInteger(request.getParameter("rating"));
        String reviewText = clean(request.getParameter("reviewText"));

        if (roomId == null || rating == null || rating < 1 || rating > 5) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, "Choose a rating from 1 to 5 stars", null);
            return;
        }
        if (reviewText.length() > 1000) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, "Review comments must be 1000 characters or fewer", null);
            return;
        }

        int userId = (Integer) session.getAttribute("userId");
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                send(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service is temporarily unavailable", null);
                return;
            }

            Integer landlordUserId = findLandlordUserId(conn, roomId);
            if (landlordUserId == null) {
                send(response, HttpServletResponse.SC_BAD_REQUEST, "This accommodation is not available for reviews", null);
                return;
            }

            Integer reviewId = findReviewId(conn, userId, roomId);
            if (reviewId == null) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO reviews (from_user_id, to_user_id, room_id, rating, review_text, review_type) "
                                + "VALUES (?, ?, ?, ?, ?, 'room_review')")) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, landlordUserId);
                    stmt.setInt(3, roomId);
                    stmt.setInt(4, rating);
                    stmt.setString(5, reviewText.isEmpty() ? null : reviewText);
                    stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE reviews SET rating = ?, review_text = ?, created_at = CURRENT_TIMESTAMP "
                                + "WHERE review_id = ? AND from_user_id = ?")) {
                    stmt.setInt(1, rating);
                    stmt.setString(2, reviewText.isEmpty() ? null : reviewText);
                    stmt.setInt(3, reviewId);
                    stmt.setInt(4, userId);
                    stmt.executeUpdate();
                }
            }

            RatingSummary summary = ratingSummary(conn, roomId);
            send(response, HttpServletResponse.SC_OK, "Review saved", summary);
        } catch (SQLException e) {
            System.err.println("Database error in StudentReviewServlet: " + e.getMessage());
            e.printStackTrace();
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Review could not be saved", null);
        }
    }

    private Integer findLandlordUserId(Connection conn, int roomId) throws SQLException {
        String sql = "SELECT l.user_id FROM rooms r JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "WHERE r.room_id = ? AND r.approval_status = 'approved' AND r.availability_status = 'available'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private Integer findReviewId(Connection conn, int userId, int roomId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT review_id FROM reviews WHERE from_user_id = ? AND room_id = ? AND review_type = 'room_review' LIMIT 1")) {
            stmt.setInt(1, userId);
            stmt.setInt(2, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private RatingSummary ratingSummary(Connection conn, int roomId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COALESCE(AVG(rating), 0), COUNT(*) FROM reviews WHERE room_id = ? AND review_type = 'room_review'")) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return new RatingSummary(rs.getDouble(1), rs.getInt(2));
            }
        }
    }

    private void send(HttpServletResponse response, int status, String message, RatingSummary summary)
            throws IOException {
        response.setStatus(status);
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"status\":\"" + (status < 400 ? "success" : "error") + "\","
                    + "\"message\":\"" + json(message) + "\"");
            if (summary != null) {
                out.print(",\"ratingAverage\":\"" + String.format("%.1f", summary.average) + "\""
                        + ",\"ratingCount\":\"" + summary.count + "\""
                        + ",\"redirectUrl\":\"HomePage/home.html?review=success\"");
            }
            out.print("}");
        }
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static class RatingSummary {
        final double average;
        final int count;

        RatingSummary(double average, int count) {
            this.average = average;
            this.count = count;
        }
    }
}
