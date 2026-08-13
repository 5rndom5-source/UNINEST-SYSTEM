package com.uninest.servlets.publicpages;

import com.uninest.utils.DatabaseConnection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/PublicListingsServlet")
public class PublicListingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Access-Control-Allow-Origin", "*");

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"listings\":[],\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
                return;
            }

            int pageSize = clamp(parsePositiveInt(request.getParameter("size"), 6), 1, 24);
            int page = parsePositiveInt(request.getParameter("page"), 1);
            String search = valueOr(request.getParameter("search"), "");

            StringBuilder where = new StringBuilder("WHERE r.approval_status = 'approved' "
                    + "AND r.availability_status = 'available' ");
            List<String> params = new ArrayList<String>();

            if (!search.isEmpty()) {
                where.append("AND (LOWER(r.room_number) LIKE ? "
                        + "OR LOWER(r.location) LIKE ? "
                        + "OR LOWER(r.description) LIKE ? "
                        + "OR LOWER(REPLACE(r.room_type, '_', ' ')) LIKE ?) ");
                String term = "%" + search.toLowerCase() + "%";
                params.add(term);
                params.add(term);
                params.add(term);
                params.add(term);
            }

            int total = countListings(conn, where.toString(), params);
            int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
            if (totalPages > 0 && page > totalPages) {
                page = totalPages;
            }
            int offset = (page - 1) * pageSize;

            String query = "SELECT r.room_id, r.room_number, r.room_type, r.location, r.price, "
                    + "r.capacity, r.description, r.furnishing, r.bedrooms, r.bathrooms, "
                    + "COALESCE((SELECT ri.image_url FROM room_images ri "
                    + "WHERE ri.room_id = r.room_id "
                    + "ORDER BY ri.is_primary DESC, ri.display_order ASC, ri.image_id ASC LIMIT 1), '') AS image_url, "
                    + "GROUP_CONCAT(a.amenity_name ORDER BY a.amenity_name SEPARATOR ', ') AS amenities "
                    + "FROM rooms r "
                    + "LEFT JOIN room_amenities ra ON ra.room_id = r.room_id "
                    + "LEFT JOIN amenities a ON a.amenity_id = ra.amenity_id "
                    + where
                    + "GROUP BY r.room_id, r.room_number, r.room_type, r.location, r.price, "
                    + "r.capacity, r.description, r.furnishing, r.bedrooms, r.bathrooms "
                    + "ORDER BY r.updated_at DESC, r.created_at DESC LIMIT ? OFFSET ?";

            PreparedStatement stmt = conn.prepareStatement(query);
            int paramIndex = bindParams(stmt, params, 1);
            stmt.setInt(paramIndex++, pageSize);
            stmt.setInt(paramIndex, offset);
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
            response.getWriter().print("{\"listings\":[],\"error\":\"Could not load listings\"}");
            System.err.println("Database error in PublicListingsServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int countListings(Connection conn, String where, List<String> params) throws SQLException {
        String query = "SELECT COUNT(*) FROM rooms r " + where;
        PreparedStatement stmt = conn.prepareStatement(query);
        bindParams(stmt, params, 1);
        ResultSet rs = stmt.executeQuery();
        int total = 0;
        if (rs.next()) {
            total = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return total;
    }

    private int bindParams(PreparedStatement stmt, List<String> params, int startAt) throws SQLException {
        int index = startAt;
        for (int i = 0; i < params.size(); i++) {
            stmt.setString(index++, params.get(i));
        }
        return index;
    }

    private void writeListing(PrintWriter out, HttpServletRequest request, ResultSet rs)
            throws SQLException {

        int id = rs.getInt("room_id");
        BigDecimal price = rs.getBigDecimal("price");
        String title = valueOr(rs.getString("room_number"), "Student accommodation");
        String type = displayType(rs.getString("room_type"));
        String imageUrl = publicImageUrl(request, rs.getString("image_url"));
        List<String> tags = buildTags(rs);

        out.print("{");
        field(out, "id", String.valueOf(id), true);
        field(out, "title", title, false);
        field(out, "area", valueOr(rs.getString("location"), "Cape Town"), false);
        field(out, "type", type, false);
        field(out, "price", formatPrice(price), false);
        field(out, "priceAmount", price == null ? "" : price.toPlainString(), false);
        field(out, "image", imageUrl, false);
        field(out, "description", valueOr(rs.getString("description"), ""), false);
        out.print(",\"tags\":[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.print(",");
            }
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
            if (!rs.wasNull() && capacity > 1) {
                tags.add(capacity + " Students");
            }
        }

        String furnishing = rs.getString("furnishing");
        if ("fully_furnished".equalsIgnoreCase(furnishing)) {
            tags.add("Furnished");
        } else if ("semi_furnished".equalsIgnoreCase(furnishing)) {
            tags.add("Semi-furnished");
        }

        String amenities = rs.getString("amenities");
        if (amenities != null && !amenities.trim().isEmpty()) {
            String[] parts = amenities.split(",");
            for (int i = 0; i < parts.length; i++) {
                String item = parts[i].trim();
                if (!item.isEmpty() && !tags.contains(item)) {
                    tags.add(item);
                }
            }
        }

        return tags;
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

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "Price not set";
        }
        return "R" + String.format("%,.0f", price) + " / month";
    }

    private void field(PrintWriter out, String key, String value, boolean first) {
        if (!first) {
            out.print(",");
        }
        out.print("\"" + key + "\":\"" + json(value) + "\"");
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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
