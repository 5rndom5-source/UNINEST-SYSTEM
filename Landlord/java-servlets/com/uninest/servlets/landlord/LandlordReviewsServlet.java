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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/LandlordReviewsServlet")
public class LandlordReviewsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"landlord".equals(session.getAttribute("role"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"Landlord login required\"}");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");
        String sql = "SELECT rv.review_id, rv.rating, rv.review_text, rv.created_at, "
                + "r.room_number, u.full_name "
                + "FROM reviews rv "
                + "JOIN rooms r ON r.room_id = rv.room_id "
                + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "JOIN users u ON u.user_id = rv.from_user_id "
                + "WHERE l.user_id = ? AND rv.review_type = 'room_review' "
                + "ORDER BY rv.created_at DESC LIMIT 100";

        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"reviews\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    out.print("{\"reviews\":[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.print(",");
                        first = false;
                        out.print("{\"id\":\"" + rs.getInt("review_id") + "\""
                                + ",\"property\":\"" + json(valueOr(rs.getString("room_number"), "Accommodation")) + "\""
                                + ",\"student\":\"" + json(valueOr(rs.getString("full_name"), "Student")) + "\""
                                + ",\"rating\":" + rs.getInt("rating")
                                + ",\"feedback\":\"" + json(valueOr(rs.getString("review_text"), "No written comment")) + "\""
                                + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("created_at"))) + "\"}");
                    }
                    out.print("]}");
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"reviews\":[],\"error\":\"Reviews could not be loaded\"}");
            System.err.println("Database error in LandlordReviewsServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
