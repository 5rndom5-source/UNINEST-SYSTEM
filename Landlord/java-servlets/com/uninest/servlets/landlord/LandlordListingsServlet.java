package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;

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

@WebServlet("/LandlordListingsServlet")
public class LandlordListingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("landlordId") == null
                || !"landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"listings\":[],\"error\":\"Please sign in as a landlord\"}");
            return;
        }

        int landlordId = (Integer) session.getAttribute("landlordId");
        int pageSize = clamp(parsePositiveInt(request.getParameter("size"), 4), 1, 20);
        int page = parsePositiveInt(request.getParameter("page"), 1);

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"listings\":[],\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
                return;
            }

            int total = countListings(conn, landlordId);
            int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
            if (totalPages > 0 && page > totalPages) {
                page = totalPages;
            }
            int offset = (page - 1) * pageSize;

            String query = "SELECT r.room_id, r.room_number, r.room_type, r.location, r.price, "
                    + "r.description, r.approval_status, r.admin_note, r.created_at, r.updated_at, "
                    + "COALESCE((SELECT ri.image_url FROM room_images ri "
                    + "WHERE ri.room_id = r.room_id "
                    + "ORDER BY ri.is_primary DESC, ri.display_order ASC, ri.image_id ASC LIMIT 1), '') AS image_url "
                    + "FROM rooms r "
                    + "WHERE r.landlord_id = ? "
                    + "ORDER BY r.updated_at DESC, r.created_at DESC "
                    + "LIMIT ? OFFSET ?";

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, landlordId);
            stmt.setInt(2, pageSize);
            stmt.setInt(3, offset);
            ResultSet rs = stmt.executeQuery();

            out.print("{\"page\":" + page
                    + ",\"pageSize\":" + pageSize
                    + ",\"total\":" + total
                    + ",\"totalPages\":" + totalPages
                    + ",\"listings\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) {
                    out.print(",");
                }
                first = false;
                writeListing(out, request, rs);
            }
            out.print("]}");

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"listings\":[],\"error\":\"Could not load landlord listings\"}");
            System.err.println("Database error in LandlordListingsServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int countListings(Connection conn, int landlordId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM rooms WHERE landlord_id = ?");
        stmt.setInt(1, landlordId);
        ResultSet rs = stmt.executeQuery();
        int total = 0;
        if (rs.next()) {
            total = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return total;
    }

    private void writeListing(PrintWriter out, HttpServletRequest request, ResultSet rs)
            throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");

        out.print("{");
        field(out, "id", String.valueOf(rs.getInt("room_id")), true);
        field(out, "title", valueOr(rs.getString("room_number"), "Student accommodation"), false);
        field(out, "type", displayType(rs.getString("room_type")), false);
        field(out, "location", valueOr(rs.getString("location"), "Cape Town"), false);
        field(out, "price", price == null ? "" : price.toPlainString(), false);
        field(out, "priceText", price == null ? "Price not set" : "R" + String.format("%,.0f", price) + "/month", false);
        field(out, "description", valueOr(rs.getString("description"), ""), false);
        field(out, "status", valueOr(rs.getString("approval_status"), "pending"), false);
        field(out, "adminNote", valueOr(rs.getString("admin_note"), ""), false);
        field(out, "image", publicImageUrl(request, rs.getString("image_url")), false);
        field(out, "createdAt", valueOr(String.valueOf(rs.getTimestamp("created_at")), ""), false);
        out.print("}");
    }

    private String publicImageUrl(HttpServletRequest request, String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return "";
        }
        String cleanUrl = imageUrl.trim();
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            return cleanUrl;
        }
        if (cleanUrl.startsWith("/")) {
            return request.getContextPath() + cleanUrl;
        }
        return request.getContextPath() + "/" + cleanUrl;
    }

    private String displayType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "Accommodation";
        }
        String clean = type.replace('_', ' ').trim().toLowerCase();
        String[] words = clean.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) {
                builder.append(words[i].substring(1));
            }
        }
        return builder.length() == 0 ? "Accommodation" : builder.toString();
    }

    private void field(PrintWriter out, String key, String value, boolean first) {
        if (!first) {
            out.print(",");
        }
        out.print("\"" + key + "\":\"" + json(value) + "\"");
    }

    private String valueOr(String value, String fallback) {
        return value == null || "null".equals(value) || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
