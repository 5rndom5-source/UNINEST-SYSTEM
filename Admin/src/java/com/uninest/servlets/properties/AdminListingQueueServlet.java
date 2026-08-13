package com.uninest.servlets.properties;

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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/AdminListingQueueServlet")
public class AdminListingQueueServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"listings\":[],\"error\":\"Please sign in as an admin\"}");
            return;
        }

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"listings\":[],\"error\":\"Service is temporarily unavailable. Please try again later.\"}");
                return;
            }

            int pageSize = clamp(parsePositiveInt(request.getParameter("size"), 5), 1, 25);
            int page = parsePositiveInt(request.getParameter("page"), 1);
            String status = valueOr(request.getParameter("status"), "all").toLowerCase();
            String search = valueOr(request.getParameter("search"), "");

            StringBuilder where = new StringBuilder("WHERE 1 = 1 ");
            List<String> params = new ArrayList<String>();

            if ("pending".equals(status) || "approved".equals(status) || "denied".equals(status)) {
                where.append("AND r.approval_status = ? ");
                params.add(status);
            }

            if (!search.isEmpty()) {
                where.append("AND (LOWER(r.room_number) LIKE ? "
                        + "OR LOWER(r.location) LIKE ? "
                        + "OR LOWER(u.full_name) LIKE ? "
                        + "OR LOWER(u.email) LIKE ?) ");
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
                    + "r.capacity, r.description, r.availability_status, r.approval_status, "
                    + "r.admin_note, r.reviewed_at, r.created_at, "
                    + "u.full_name AS landlord_name, u.email AS landlord_email "
                    + "FROM rooms r "
                    + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                    + "JOIN users u ON u.user_id = l.user_id "
                    + where
                    + "ORDER BY CASE r.approval_status "
                    + "WHEN 'pending' THEN 1 WHEN 'approved' THEN 2 ELSE 3 END, r.created_at DESC "
                    + "LIMIT ? OFFSET ?";

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
                writeListing(out, rs);
            }
            out.print("]}");

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"listings\":[],\"error\":\"Could not load listing queue\"}");
            System.err.println("Database error in AdminListingQueueServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int countListings(Connection conn, String where, List<String> params) throws SQLException {
        String query = "SELECT COUNT(*) "
                + "FROM rooms r "
                + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "JOIN users u ON u.user_id = l.user_id "
                + where;
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

    private void writeListing(PrintWriter out, ResultSet rs) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");

        out.print("{");
        field(out, "id", String.valueOf(rs.getInt("room_id")), true);
        field(out, "title", valueOr(rs.getString("room_number"), "Student accommodation"), false);
        field(out, "type", displayType(rs.getString("room_type")), false);
        field(out, "location", valueOr(rs.getString("location"), "Cape Town"), false);
        field(out, "price", price == null ? "Price not set" : "R" + String.format("%,.0f", price), false);
        field(out, "capacity", String.valueOf(rs.getInt("capacity")), false);
        field(out, "description", valueOr(rs.getString("description"), ""), false);
        field(out, "availability", valueOr(rs.getString("availability_status"), "available"), false);
        field(out, "status", valueOr(rs.getString("approval_status"), "pending"), false);
        field(out, "adminNote", valueOr(rs.getString("admin_note"), ""), false);
        field(out, "createdAt", valueOr(String.valueOf(rs.getTimestamp("created_at")), ""), false);
        field(out, "reviewedAt", valueOr(String.valueOf(rs.getTimestamp("reviewed_at")), ""), false);
        field(out, "landlordName", valueOr(rs.getString("landlord_name"), "Landlord"), false);
        field(out, "landlordEmail", valueOr(rs.getString("landlord_email"), "Email not saved"), false);
        out.print("}");
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
