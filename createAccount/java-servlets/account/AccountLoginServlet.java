package com.uninest.servlets.account;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.PasswordUtil;
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
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AccountLoginServlet - Role-aware login for the shared createAccount/login.html page.
 * Path: /AccountLoginServlet
 */
@WebServlet("/AccountLoginServlet")
public class AccountLoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = normalizeEmail(request.getParameter("email"));
        String password = request.getParameter("password");
        String role = normalizeRole(request.getParameter("role"));

        if (!ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isRequired(password)
                || !isValidRole(role)) {
            redirect(request, response, "createAccount/login.html", "error", "Invalid login details");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "createAccount/login.html", "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "SELECT user_id, full_name, password, role, status "
                    + "FROM users WHERE email = ? AND role = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, role);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next() || !PasswordUtil.verifyPassword(password, rs.getString("password"))) {
                stmt.close();
                redirect(request, response, "createAccount/login.html", "error", "Invalid email or password");
                return;
            }

            if ("suspended".equalsIgnoreCase(rs.getString("status"))
                    || "inactive".equalsIgnoreCase(rs.getString("status"))) {
                stmt.close();
                redirect(request, response, "createAccount/login.html", "error", "This account is not active");
                return;
            }

            HttpSession session = createFreshSession(request);
            int userId = rs.getInt("user_id");
            String fullName = rs.getString("full_name");
            session.setAttribute("userId", userId);
            session.setAttribute("fullName", fullName);
            session.setAttribute("email", email);
            session.setAttribute("role", role);
            session.setMaxInactiveInterval(1800);

            if ("admin".equals(role)) {
                session.setAttribute("adminId", userId);
            } else if ("landlord".equals(role)) {
                Integer landlordId = findLandlordId(conn, userId, fullName);
                if (landlordId == null) {
                    stmt.close();
                    redirect(request, response, "createAccount/login.html", "error", "Landlord profile is not ready");
                    return;
                }
                session.setAttribute("landlordId", landlordId);
                session.setAttribute("landlordName", fullName);
            } else {
                session.setAttribute("studentName", fullName);
            }

            stmt.close();
            response.sendRedirect(request.getContextPath() + "/" + destinationFor(role));
        } catch (SQLException e) {
            System.err.println("Database error in AccountLoginServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "createAccount/login.html", "error", "Could not sign in");
        }
    }

    private boolean isValidRole(String role) {
        return "student".equals(role) || "landlord".equals(role) || "admin".equals(role);
    }

    private HttpSession createFreshSession(HttpServletRequest request) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        return request.getSession(true);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeRole(String role) {
        return role == null ? null : role.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String destinationFor(String role) {
        if ("landlord".equals(role)) return "Landlord/index.html";
        if ("admin".equals(role)) return "Admin/web/jsp/Dashboard.html";
        return "UserDashboard/index.html";
    }

    private Integer findLandlordId(Connection conn, int userId, String fullName) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT landlord_id FROM landlords WHERE user_id = ?");
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            int landlordId = rs.getInt("landlord_id");
            stmt.close();
            return landlordId;
        }
        stmt.close();

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO landlords (user_id, business_name, id_number, approval_status) "
                        + "VALUES (?, ?, ?, 'pending')",
                java.sql.Statement.RETURN_GENERATED_KEYS);
        insert.setInt(1, userId);
        insert.setString(2, fullName);
        insert.setString(3, "PENDING-" + userId);
        insert.executeUpdate();

        ResultSet keys = insert.getGeneratedKeys();
        Integer landlordId = null;
        if (keys.next()) {
            landlordId = keys.getInt(1);
        }
        insert.close();
        return landlordId;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String page,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath() + "/" + page
                + "?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
