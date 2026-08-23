package com.uninest.servlets.payments;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PaymentsServlet - Updates payment status from the admin records page.
 * Path: /PaymentsServlet
 */
@WebServlet("/PaymentsServlet")
public class PaymentsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/Payments.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request)) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String paymentId = request.getParameter("paymentId");
        String status = normalizeStatus(request.getParameter("status"));
        String receiptNumber = request.getParameter("receiptNumber");

        if (!ValidationUtil.isInteger(paymentId) || status == null) {
            redirect(request, response, "error", "Invalid payment update");
            return;
        }
        if ("completed".equals(status) && !ValidationUtil.isRequired(receiptNumber)) {
            redirect(request, response, "error", "A receipt number is required for completed payments");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE payments SET payment_status = ?, receipt_number = ?, "
                    + "payment_date = CASE WHEN ? = 'completed' THEN NOW() ELSE payment_date END "
                    + "WHERE payment_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, status);
            stmt.setString(2, emptyToNull(receiptNumber));
            stmt.setString(3, status);
            stmt.setInt(4, Integer.parseInt(paymentId));
            int updated = stmt.executeUpdate();
            stmt.close();

            if (updated > 0) {
                recordTransaction(conn, Integer.parseInt(paymentId), status, emptyToNull(receiptNumber));
                notifyParties(conn, Integer.parseInt(paymentId), status);
            }

            redirect(request, response, updated > 0 ? "success" : "error",
                    updated > 0 ? "Payment updated" : "No matching payment was found");
        } catch (SQLException e) {
            System.err.println("Database error in PaymentsServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update payment");
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") != null
                && "admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")));
    }

    private void recordTransaction(Connection conn, int paymentId, String status, String receipt)
            throws SQLException {
        if (!"completed".equals(status)) return;
        String query = "INSERT INTO transaction_history "
                + "(payment_id, transaction_type, amount, reference_number, notes) "
                + "SELECT p.payment_id, 'credit', p.amount, ?, 'Payment completed by admin' "
                + "FROM payments p WHERE p.payment_id = ? AND NOT EXISTS "
                + "(SELECT 1 FROM transaction_history t WHERE t.payment_id = p.payment_id "
                + "AND t.transaction_type = 'credit')";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, receipt);
            stmt.setInt(2, paymentId);
            stmt.executeUpdate();
        }
    }

    private void notifyParties(Connection conn, int paymentId, String status) throws SQLException {
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT u.user_id, 'Payment updated', ?, 'payment_due', "
                + "CASE WHEN u.role = 'student' THEN 'UserDashboard/requests.html' "
                + "ELSE 'Landlord/index.html' END FROM payments p "
                + "JOIN students s ON s.student_id = p.student_id "
                + "JOIN landlords l ON l.landlord_id = p.landlord_id "
                + "JOIN users u ON u.user_id IN (s.user_id, l.user_id) WHERE p.payment_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, "Payment status changed to " + status);
            stmt.setInt(2, paymentId);
            stmt.executeUpdate();
        }
    }

    private String normalizeStatus(String value) {
        if ("pending".equalsIgnoreCase(value)) return "pending";
        if ("completed".equalsIgnoreCase(value)) return "completed";
        if ("failed".equalsIgnoreCase(value)) return "failed";
        if ("overdue".equalsIgnoreCase(value)) return "overdue";
        return null;
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/Payments.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
