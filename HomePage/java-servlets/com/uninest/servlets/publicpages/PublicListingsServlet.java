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
            String location = valueOr(request.getParameter("location"), "");
            String type = normalizedType(request.getParameter("type"));
            BigDecimal minPrice = parseMoney(request.getParameter("minPrice"));
            BigDecimal maxPrice = parseMoney(request.getParameter("maxPrice"));
            String sort = valueOr(request.getParameter("sort"), "recent");
            String[] facilities = request.getParameterValues("facility");

            StringBuilder where = new StringBuilder("WHERE r.approval_status = 'approved' "
                    + "AND r.availability_status = 'available' ");
            List<Object> params = new ArrayList<Object>();

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

            if (!location.isEmpty()) {
                where.append("AND LOWER(r.location) = ? ");
                params.add(location.toLowerCase());
            }
            if (!type.isEmpty()) {
                where.append("AND r.room_type = ? ");
                params.add(type);
            }
            if (minPrice != null) {
                where.append("AND r.price >= ? ");
                params.add(minPrice);
            }
            if (maxPrice != null) {
                where.append("AND r.price <= ? ");
                params.add(maxPrice);
            }
            if (facilities != null) {
                for (String facility : facilities) {
                    String clean = valueOr(facility, "");
                    if (clean.isEmpty()) continue;
                    if ("furnished".equalsIgnoreCase(clean)) {
                        where.append("AND r.furnishing IN ('semi_furnished', 'fully_furnished') ");
                    } else {
                        where.append("AND EXISTS (SELECT 1 FROM room_amenities raf "
                                + "JOIN amenities af ON af.amenity_id = raf.amenity_id "
                                + "WHERE raf.room_id = r.room_id AND LOWER(af.amenity_name) = ?) ");
                        params.add(clean.toLowerCase());
                    }
                }
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
                    + "COALESCE((SELECT AVG(rv.rating) FROM reviews rv "
                    + "WHERE rv.room_id = r.room_id AND rv.review_type = 'room_review'), 0) AS rating_average, "
                    + "(SELECT COUNT(*) FROM reviews rv "
                    + "WHERE rv.room_id = r.room_id AND rv.review_type = 'room_review') AS rating_count, "
                    + "GROUP_CONCAT(a.amenity_name ORDER BY a.amenity_name SEPARATOR ', ') AS amenities "
                    + "FROM rooms r "
                    + "LEFT JOIN room_amenities ra ON ra.room_id = r.room_id "
                    + "LEFT JOIN amenities a ON a.amenity_id = ra.amenity_id "
                    + where
                    + "GROUP BY r.room_id, r.room_number, r.room_type, r.location, r.price, "
                    + "r.capacity, r.description, r.furnishing, r.bedrooms, r.bathrooms "
                    + orderBy(sort) + " LIMIT ? OFFSET ?";

            PreparedStatement stmt = conn.prepareStatement(query);
            int paramIndex = bindParams(stmt, params, 1);
            stmt.setInt(paramIndex++, pageSize);
            stmt.setInt(paramIndex, offset);
            ResultSet rs = stmt.executeQuery();

            out.print("{\"page\":" + page
                    + ",\"pageSize\":" + pageSize
                    + ",\"total\":" + total
                    + ",\"totalPages\":" + totalPages + ",");
            writeFilterOptions(out, conn);
            out.print(",\"listings\":[");
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

    private int countListings(Connection conn, String where, List<Object> params) throws SQLException {
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

    private int bindParams(PreparedStatement stmt, List<Object> params, int startAt) throws SQLException {
        int index = startAt;
        for (Object value : params) {
            if (value instanceof BigDecimal) {
                stmt.setBigDecimal(index++, (BigDecimal) value);
            } else {
                stmt.setString(index++, String.valueOf(value));
            }
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
        field(out, "ratingAverage", String.format("%.1f", rs.getBigDecimal("rating_average")), false);
        field(out, "ratingCount", String.valueOf(rs.getInt("rating_count")), false);
        out.print(",\"tags\":[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.print(",");
            }
            out.print("\"" + json(tags.get(i)) + "\"");
        }
        out.print("]}");
    }

    private void writeFilterOptions(PrintWriter out, Connection conn) throws SQLException {
        out.print("\"locations\":[");
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT DISTINCT location FROM rooms WHERE approval_status = 'approved' "
                        + "AND availability_status = 'available' AND location IS NOT NULL ORDER BY location");
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                String location = valueOr(rs.getString(1), "");
                if (location.isEmpty()) continue;
                if (!first) out.print(",");
                first = false;
                out.print("\"" + json(location) + "\"");
            }
        }
        out.print("],\"types\":[");
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT DISTINCT room_type FROM rooms WHERE approval_status = 'approved' "
                        + "AND availability_status = 'available' ORDER BY room_type");
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                String type = valueOr(rs.getString(1), "");
                if (type.isEmpty()) continue;
                if (!first) out.print(",");
                first = false;
                out.print("\"" + json(type) + "\"");
            }
        }
        out.print("]");
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

    private String normalizedType(String value) {
        String clean = valueOr(value, "").toLowerCase().replace(' ', '_');
        switch (clean) {
            case "single_room":
            case "shared_room":
            case "studio":
            case "apartment":
            case "house":
            case "residence":
                return clean;
            default:
                return "";
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            return amount.signum() >= 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String orderBy(String sort) {
        if ("price_asc".equalsIgnoreCase(sort)) return "ORDER BY r.price ASC, r.updated_at DESC";
        if ("price_desc".equalsIgnoreCase(sort)) return "ORDER BY r.price DESC, r.updated_at DESC";
        if ("rating".equalsIgnoreCase(sort)) return "ORDER BY rating_average DESC, rating_count DESC, r.updated_at DESC";
        return "ORDER BY r.updated_at DESC, r.created_at DESC";
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
