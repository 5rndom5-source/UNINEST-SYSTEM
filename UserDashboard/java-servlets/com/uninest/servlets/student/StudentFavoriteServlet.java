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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/StudentFavoriteServlet")
public class StudentFavoriteServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"Login required\"}");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"listings\":[],\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
                return;
            }

            Integer studentId = findStudentId(conn, userId);
            if (studentId == null) {
                out.print("{\"listings\":[]}");
                return;
            }

            String query = "SELECT r.room_id, r.room_number, r.room_type, r.location, r.price, "
                    + "r.capacity, r.furnishing, r.bedrooms, r.bathrooms, "
                    + "COALESCE((SELECT ri.image_url FROM room_images ri "
                    + "WHERE ri.room_id = r.room_id "
                    + "ORDER BY ri.is_primary DESC, ri.display_order ASC, ri.image_id ASC LIMIT 1), '') AS image_url, "
                    + "GROUP_CONCAT(a.amenity_name ORDER BY a.amenity_name SEPARATOR ', ') AS amenities "
                    + "FROM favorites f "
                    + "JOIN rooms r ON r.room_id = f.room_id "
                    + "LEFT JOIN room_amenities ra ON ra.room_id = r.room_id "
                    + "LEFT JOIN amenities a ON a.amenity_id = ra.amenity_id "
                    + "WHERE f.student_id = ? AND r.approval_status = 'approved' "
                    + "AND r.availability_status = 'available' "
                    + "GROUP BY r.room_id, r.room_number, r.room_type, r.location, r.price, "
                    + "r.capacity, r.furnishing, r.bedrooms, r.bathrooms "
                    + "ORDER BY f.saved_date DESC";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"listings\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        writeListing(out, request, rs);
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"listings\":[],\"error\":\"Saved listings could not be loaded\"}");
            System.err.println("Database error in StudentFavoriteServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().print("{\"status\":\"error\",\"message\":\"Login required\"}");
            return;
        }

        String action = trim(request.getParameter("action"));
        String roomIdValue = request.getParameter("roomId");

        if (!ValidationUtil.isRequired(roomIdValue)) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Listing could not be found");
            return;
        }

        Integer roomId = parseRoomId(roomIdValue);
        if (roomId == null) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Listing could not be found");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            int studentId = ensureStudentRecord(conn, userId);
            if ("remove".equalsIgnoreCase(action)) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM favorites WHERE student_id = ? AND room_id = ?")) {
                    stmt.setInt(1, studentId);
                    stmt.setInt(2, roomId);
                    stmt.executeUpdate();
                }
                sendJson(response, HttpServletResponse.SC_OK, "success", "Saved listing removed");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT IGNORE INTO favorites (student_id, room_id) "
                            + "SELECT ?, room_id FROM rooms "
                            + "WHERE room_id = ? AND approval_status = 'approved' AND availability_status = 'available'")) {
                stmt.setInt(1, studentId);
                stmt.setInt(2, roomId);
                stmt.executeUpdate();
            }

            sendJson(response, HttpServletResponse.SC_OK, "success", "Listing saved");
        } catch (SQLException e) {
            System.err.println("Database error in StudentFavoriteServlet: " + e.getMessage());
            e.printStackTrace();
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "Saved listing could not be updated");
        }
    }

    private Integer findStudentId(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT student_id FROM students WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("student_id") : null;
            }
        }
    }

    private int ensureStudentRecord(Connection conn, int userId) throws SQLException {
        Integer existing = findStudentId(conn, userId);
        if (existing != null) return existing;

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO students (user_id) VALUES (?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, userId);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private void writeListing(PrintWriter out, HttpServletRequest request, ResultSet rs) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        List<String> tags = buildTags(rs);

        out.print("{");
        field(out, "id", String.valueOf(rs.getInt("room_id")), true);
        field(out, "title", valueOr(rs.getString("room_number"), "Student accommodation"), false);
        field(out, "area", valueOr(rs.getString("location"), "Cape Town"), false);
        field(out, "type", displayType(rs.getString("room_type")), false);
        field(out, "price", formatPrice(price), false);
        field(out, "image", publicImageUrl(request, rs.getString("image_url")), false);
        out.print(",\"tags\":[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) out.print(",");
            out.print("\"" + json(tags.get(i)) + "\"");
        }
        out.print("]}");
    }

    private List<String> buildTags(ResultSet rs) throws SQLException {
        List<String> tags = new ArrayList<String>();
        int bedrooms = rs.getInt("bedrooms");
        if (!rs.wasNull() && bedrooms > 0) {
            tags.add(bedrooms == 1 ? "1 Bed" : bedrooms + " Beds");
        } else {
            int capacity = rs.getInt("capacity");
            if (!rs.wasNull() && capacity > 1) tags.add(capacity + " Students");
        }

        String furnishing = rs.getString("furnishing");
        if ("fully_furnished".equalsIgnoreCase(furnishing)) tags.add("Furnished");
        if ("semi_furnished".equalsIgnoreCase(furnishing)) tags.add("Semi-furnished");

        String amenities = rs.getString("amenities");
        if (amenities != null && !amenities.trim().isEmpty()) {
            String[] parts = amenities.split(",");
            for (int i = 0; i < parts.length; i++) {
                String item = parts[i].trim();
                if (!item.isEmpty() && !tags.contains(item)) tags.add(item);
            }
        }
        return tags;
    }

    private String publicImageUrl(HttpServletRequest request, String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return "";
        String cleanUrl = imageUrl.trim();
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) return cleanUrl;
        if (cleanUrl.startsWith("/")) return request.getContextPath() + cleanUrl;
        return request.getContextPath() + "/" + cleanUrl;
    }

    private String displayType(String type) {
        if (type == null || type.trim().isEmpty()) return "Accommodation";
        String clean = type.replace('_', ' ').trim().toLowerCase();
        String[] words = clean.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) builder.append(words[i].substring(1));
        }
        return builder.length() == 0 ? "Accommodation" : builder.toString();
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "Price not set";
        return "R" + String.format("%,.0f", price) + " / month";
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

    private void sendJson(HttpServletResponse response, int status, String key, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().print("{\"status\":\"" + key + "\",\"message\":\"" + json(message) + "\"}");
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
}
