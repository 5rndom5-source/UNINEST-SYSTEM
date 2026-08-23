package com.uninest.servlets.system;

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

@WebServlet("/AdminOperationsServlet")
public class AdminOperationsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        if (!isAdmin(request.getSession(false))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"Admin login required\"}");
            return;
        }

        String view = valueOr(request.getParameter("view"), "dashboard").toLowerCase();
        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"error\":\"Service is temporarily unavailable\"}");
                return;
            }
            if ("bookings".equals(view)) writeBookings(conn, out);
            else if ("payments".equals(view)) writePayments(conn, out);
            else if ("maintenance".equals(view)) writeMaintenance(conn, out);
            else writeDashboard(conn, out);
        } catch (SQLException e) {
            getServletContext().log("Unable to load admin operations", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"Admin records could not be loaded\"}");
        }
    }

    private void writeDashboard(Connection conn, PrintWriter out) throws SQLException {
        int pendingListings = count(conn,
                "SELECT COUNT(*) FROM rooms WHERE approval_status = 'pending'");
        int approvedListings = count(conn,
                "SELECT COUNT(*) FROM rooms WHERE approval_status = 'approved'");
        int pendingBookings = count(conn,
                "SELECT COUNT(*) FROM bookings WHERE booking_status = 'pending'");
        int openMaintenance = count(conn,
                "SELECT COUNT(*) FROM maintenance_requests WHERE request_status IN ('pending','in_progress')");
        int unreadMessages = count(conn,
                "SELECT COUNT(*) FROM messages m JOIN users u ON u.user_id = m.recipient_id "
                        + "WHERE u.role = 'admin' AND m.is_read = FALSE");
        int unreadEnquiries = count(conn,
                "SELECT COUNT(*) FROM notifications n JOIN users u ON u.user_id = n.user_id "
                        + "WHERE u.role = 'admin' AND n.action_url = 'Enquiries.html' AND n.is_read = FALSE");

        out.print("{\"counts\":{\"pendingListings\":" + pendingListings
                + ",\"approvedListings\":" + approvedListings
                + ",\"pendingBookings\":" + pendingBookings
                + ",\"openMaintenance\":" + openMaintenance
                + ",\"unreadMessages\":" + unreadMessages
                + ",\"unreadEnquiries\":" + unreadEnquiries + "},\"listings\":[");

        String query = "SELECT r.room_id, r.room_number, r.location, r.price, r.created_at, "
                + "u.full_name AS landlord_name FROM rooms r "
                + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "JOIN users u ON u.user_id = l.user_id "
                + "WHERE r.approval_status = 'pending' ORDER BY r.created_at DESC LIMIT 8";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) out.print(",");
                first = false;
                out.print("{\"id\":" + rs.getInt("room_id")
                        + ",\"property\":\"" + json(rs.getString("room_number")) + "\""
                        + ",\"landlord\":\"" + json(rs.getString("landlord_name")) + "\""
                        + ",\"location\":\"" + json(rs.getString("location")) + "\""
                        + ",\"price\":\"R" + json(String.format("%,.2f", rs.getBigDecimal("price"))) + "\""
                        + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("created_at"))) + "\"}");
            }
        }
        out.print("]}");
    }

    private void writeBookings(Connection conn, PrintWriter out) throws SQLException {
        String query = "SELECT b.booking_id, b.booking_date, b.check_in, b.check_out, "
                + "b.booking_status, b.cancellation_reason, r.room_number, r.location, "
                + "student.full_name AS student_name, student.email AS student_email, "
                + "landlord.full_name AS landlord_name FROM bookings b "
                + "JOIN students s ON s.student_id = b.student_id "
                + "JOIN users student ON student.user_id = s.user_id "
                + "JOIN rooms r ON r.room_id = b.room_id "
                + "JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "JOIN users landlord ON landlord.user_id = l.user_id "
                + "ORDER BY CASE b.booking_status WHEN 'pending' THEN 1 WHEN 'confirmed' THEN 2 ELSE 3 END, "
                + "b.booking_date DESC LIMIT 100";
        out.print("{\"bookings\":[");
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) out.print(",");
                first = false;
                out.print("{\"id\":" + rs.getInt("booking_id")
                        + ",\"student\":\"" + json(rs.getString("student_name")) + "\""
                        + ",\"studentEmail\":\"" + json(rs.getString("student_email")) + "\""
                        + ",\"property\":\"" + json(rs.getString("room_number")) + "\""
                        + ",\"location\":\"" + json(rs.getString("location")) + "\""
                        + ",\"landlord\":\"" + json(rs.getString("landlord_name")) + "\""
                        + ",\"checkIn\":\"" + json(String.valueOf(rs.getDate("check_in"))) + "\""
                        + ",\"checkOut\":\"" + json(String.valueOf(rs.getDate("check_out"))) + "\""
                        + ",\"status\":\"" + json(rs.getString("booking_status")) + "\""
                        + ",\"reason\":\"" + json(rs.getString("cancellation_reason")) + "\""
                        + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("booking_date"))) + "\"}");
            }
        }
        out.print("]}");
    }

    private void writePayments(Connection conn, PrintWriter out) throws SQLException {
        String query = "SELECT p.payment_id, p.amount, p.payment_date, p.due_date, p.payment_method, "
                + "p.payment_status, p.receipt_number, student.full_name AS student_name, "
                + "landlord.full_name AS landlord_name, r.room_number FROM payments p "
                + "JOIN students s ON s.student_id = p.student_id "
                + "JOIN users student ON student.user_id = s.user_id "
                + "JOIN landlords l ON l.landlord_id = p.landlord_id "
                + "JOIN users landlord ON landlord.user_id = l.user_id "
                + "JOIN tenancy_records t ON t.tenancy_id = p.tenancy_id "
                + "JOIN bookings b ON b.booking_id = t.booking_id "
                + "JOIN rooms r ON r.room_id = b.room_id "
                + "ORDER BY p.due_date DESC, p.payment_id DESC LIMIT 100";
        out.print("{\"payments\":[");
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) out.print(",");
                first = false;
                out.print("{\"id\":" + rs.getInt("payment_id")
                        + ",\"student\":\"" + json(rs.getString("student_name")) + "\""
                        + ",\"landlord\":\"" + json(rs.getString("landlord_name")) + "\""
                        + ",\"property\":\"" + json(rs.getString("room_number")) + "\""
                        + ",\"amount\":\"R" + json(String.format("%,.2f", rs.getBigDecimal("amount"))) + "\""
                        + ",\"dueDate\":\"" + json(String.valueOf(rs.getDate("due_date"))) + "\""
                        + ",\"paidAt\":\"" + json(String.valueOf(rs.getTimestamp("payment_date"))) + "\""
                        + ",\"method\":\"" + json(rs.getString("payment_method")) + "\""
                        + ",\"status\":\"" + json(rs.getString("payment_status")) + "\""
                        + ",\"receipt\":\"" + json(rs.getString("receipt_number")) + "\"}");
            }
        }
        out.print("]}");
    }

    private void writeMaintenance(Connection conn, PrintWriter out) throws SQLException {
        String query = "SELECT m.request_id, m.issue_description, m.priority, m.request_date, "
                + "m.request_status, m.resolved_date, m.resolution_notes, r.room_number, r.location, "
                + "student.full_name AS student_name FROM maintenance_requests m "
                + "JOIN students s ON s.student_id = m.student_id "
                + "JOIN users student ON student.user_id = s.user_id "
                + "JOIN rooms r ON r.room_id = m.room_id "
                + "ORDER BY CASE m.priority WHEN 'urgent' THEN 1 WHEN 'high' THEN 2 "
                + "WHEN 'medium' THEN 3 ELSE 4 END, m.request_date DESC LIMIT 100";
        out.print("{\"requests\":[");
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) out.print(",");
                first = false;
                out.print("{\"id\":" + rs.getInt("request_id")
                        + ",\"student\":\"" + json(rs.getString("student_name")) + "\""
                        + ",\"property\":\"" + json(rs.getString("room_number")) + "\""
                        + ",\"location\":\"" + json(rs.getString("location")) + "\""
                        + ",\"issue\":\"" + json(rs.getString("issue_description")) + "\""
                        + ",\"priority\":\"" + json(rs.getString("priority")) + "\""
                        + ",\"status\":\"" + json(rs.getString("request_status")) + "\""
                        + ",\"notes\":\"" + json(rs.getString("resolution_notes")) + "\""
                        + ",\"createdAt\":\"" + json(String.valueOf(rs.getTimestamp("request_date"))) + "\"}");
            }
        }
        out.print("]}");
    }

    private int count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private boolean isAdmin(HttpSession session) {
        return session != null && session.getAttribute("userId") instanceof Integer
                && "admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String json(String value) {
        if (value == null || "null".equals(value)) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
