package com.uninest.servlets.account;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.PasswordUtil;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * AccountRegisterServlet - Registers basic UniNest user accounts from
 * createAccount/index.html.
 * Path: /AccountRegisterServlet
 */
@WebServlet("/AccountRegisterServlet")
public class AccountRegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String fullName = firstNonEmpty(request.getParameter("fullName"), request.getParameter("fullname"));
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String role = request.getParameter("role");

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || !isValidRole(role)
                || !ValidationUtil.isRequired(password)
                || !password.equals(confirmPassword)) {
            redirect(request, response, "error", "Please complete all registration fields correctly");
            return;
        }

        if (!PasswordUtil.isStrongPassword(password)) {
            redirect(request, response, "error", "Password must be 8+ characters with uppercase, lowercase, number, and symbol");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            int userId = createUser(conn, fullName.trim(), email.trim(), password, role);
            createRoleRecord(conn, userId, fullName.trim(), role);

            response.sendRedirect(request.getContextPath() + "/createAccount/login.html?success="
                    + java.net.URLEncoder.encode("Account created. You can sign in now.", "UTF-8"));
        } catch (SQLException e) {
            System.err.println("Database error in AccountRegisterServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not create account. The email may already exist.");
        }
    }

    private int createUser(Connection conn, String fullName, String email,
                           String password, String role) throws SQLException {
        String query = "INSERT INTO users (full_name, email, password, role, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, fullName);
        stmt.setString(2, email);
        stmt.setString(3, PasswordUtil.hashPassword(password));
        stmt.setString(4, role);
        stmt.setString(5, "student".equals(role) ? "active" : "pending_verification");
        stmt.executeUpdate();

        java.sql.ResultSet keys = stmt.getGeneratedKeys();
        int userId = 0;
        if (keys.next()) {
            userId = keys.getInt(1);
        }
        stmt.close();
        return userId;
    }

    private void createRoleRecord(Connection conn, int userId, String fullName, String role) throws SQLException {
        if ("student".equals(role)) {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO students (user_id) VALUES (?)");
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            stmt.close();
        } else if ("landlord".equals(role)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO landlords (user_id, business_name, id_number, approval_status) "
                            + "VALUES (?, ?, ?, 'pending')");
            stmt.setInt(1, userId);
            stmt.setString(2, fullName);
            stmt.setString(3, "PENDING-" + userId);
            stmt.executeUpdate();
            stmt.close();
        } else if ("admin".equals(role)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admins (user_id, admin_position, access_level) VALUES (?, 'Pending approval', 'moderator')");
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            stmt.close();
        }
    }

    private boolean isValidRole(String role) {
        return "student".equals(role) || "landlord".equals(role) || "admin".equals(role);
    }

    private String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath() + "/createAccount/index.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
