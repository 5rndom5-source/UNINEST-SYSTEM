
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

@WebServlet("/StudentTransactionHistoryServlet")
public class StudentTransactionHistoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        HttpSession session = request.getSession(false);

        // Check whether the student is logged in.
        if (session == null || session.getAttribute("userId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print(
                    "{\"error\":\"Please log in to view your transactions\"}");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");

        String query =
                "SELECT th.transaction_id, th.payment_id, " +
                "th.transaction_type, th.amount, " +
                "th.balance_before, th.balance_after, " +
                "th.transaction_date, th.reference_number, th.notes, " +
                "p.payment_method, p.payment_status, " +
                "p.due_date, p.receipt_number " +
                "FROM transaction_history th " +
                "INNER JOIN payments p ON th.payment_id = p.payment_id " +
                "WHERE p.student_id = ? " +
                "ORDER BY th.transaction_date DESC";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();

            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.getWriter().print(
                        "{\"transactions\":[],\"error\":\"Database service unavailable\"}");
                return;
            }

            // Find the student linked to the logged-in user.
            String studentQuery =
                    "SELECT student_id FROM students WHERE user_id = ?";

            try (PreparedStatement studentStmt =
                         conn.prepareStatement(studentQuery)) {

                studentStmt.setInt(1, userId);

                try (ResultSet studentRs = studentStmt.executeQuery()) {

                    if (!studentRs.next()) {
                        response.getWriter().print(
                                "{\"transactions\":[]}");
                        return;
                    }
                }
            }

            // Retrieve only transactions belonging to this student.
            try (PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setInt(1, getStudentId(conn, userId));

                try (ResultSet rs = stmt.executeQuery();
                     PrintWriter out = response.getWriter()) {

                    out.print("{\"transactions\":[");
                    boolean first = true;

                    while (rs.next()) {

                        if (!first) {
                            out.print(",");
                        }

                        first = false;

                        out.print("{");

                        addField(out, "transactionId",
                                String.valueOf(rs.getInt("transaction_id")), true);

                        addField(out, "paymentId",
                                String.valueOf(rs.getInt("payment_id")), false);

                        addField(out, "type",
                                rs.getString("transaction_type"), false);

                        addField(out, "amount",
                                rs.getBigDecimal("amount") == null ? null :
                                rs.getBigDecimal("amount").toPlainString(), false);

                        addField(out, "balanceBefore",
                                rs.getBigDecimal("balance_before") == null ? null :
                                rs.getBigDecimal("balance_before").toPlainString(), false);

                        addField(out, "balanceAfter",
                                rs.getBigDecimal("balance_after") == null ? null :
                                rs.getBigDecimal("balance_after").toPlainString(), false);

                        addField(out, "date",
                                rs.getString("transaction_date"), false);

                        addField(out, "reference",
                                rs.getString("reference_number"), false);

                        addField(out, "notes",
                                rs.getString("notes"), false);

                        addField(out, "paymentMethod",
                                rs.getString("payment_method"), false);

                        addField(out, "paymentStatus",
                                rs.getString("payment_status"), false);

                        addField(out, "dueDate",
                                rs.getString("due_date"), false);

                        addField(out, "receiptNumber",
                                rs.getString("receipt_number"), false);

                        out.print("}");
                    }

                    out.print("]}");
                }
            }

        } catch (SQLException e) {

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            response.getWriter().print(
                    "{\"transactions\":[],\"error\":\"Transaction history could not be loaded\"}");

            System.err.println(
                    "Database error in StudentTransactionHistoryServlet: "
                    + e.getMessage());

            e.printStackTrace();
        }
    }

    private int getStudentId(Connection conn, int userId)
            throws SQLException {

        String query =
                "SELECT student_id FROM students WHERE user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt("student_id");
                }
            }
        }

        return 0;
    }

    private void addField(PrintWriter out, String key,
                          String value, boolean first) {

        if (!first) {
            out.print(",");
        }

        out.print("\"" + key + "\":");

        if (value == null) {
            out.print("null");
        } else {
            out.print("\"" + escapeJson(value) + "\"");
        }
    }

    private String escapeJson(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
