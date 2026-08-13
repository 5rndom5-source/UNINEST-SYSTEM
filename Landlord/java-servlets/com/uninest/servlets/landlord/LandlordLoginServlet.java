package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.PasswordUtil;
import com.uninest.utils.ValidationUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/LandlordLoginServlet")
public class LandlordLoginServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("landlordEmail");
        String password = request.getParameter("landlordPassword");

        if (!ValidationUtil.isValidEmail(email) || !ValidationUtil.isRequired(password)) {
            response.sendRedirect(request.getContextPath() + "/Landlord/login.html?error=invalid");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn != null && authenticateFromDatabase(conn, request, email.trim(), password)) {
                response.sendRedirect(request.getContextPath() + "/Landlord/index.html");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Database error in LandlordLoginServlet: " + e.getMessage());
            e.printStackTrace();
        }

        response.sendRedirect(request.getContextPath() + "/Landlord/login.html?error=invalid");
    }

    private boolean authenticateFromDatabase(Connection conn, HttpServletRequest request,
                                             String email, String password) throws SQLException {
        String query = "SELECT u.user_id, u.full_name, u.password, u.status, l.landlord_id "
                + "FROM users u LEFT JOIN landlords l ON u.user_id = l.user_id "
                + "WHERE u.email = ? AND u.role = 'landlord'";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        if (!rs.next()) {
            stmt.close();
            return false;
        }

        String storedPassword = rs.getString("password");
        String status = rs.getString("status");
        if (!PasswordUtil.verifyPassword(password, storedPassword)
                || "suspended".equalsIgnoreCase(status)
                || "inactive".equalsIgnoreCase(status)) {
            stmt.close();
            return false;
        }

        HttpSession session = request.getSession();
        session.setAttribute("userId", rs.getInt("user_id"));
        session.setAttribute("fullName", rs.getString("full_name"));
        int landlordId = rs.getInt("landlord_id");
        if (!rs.wasNull()) {
            session.setAttribute("landlordId", landlordId);
        }
        session.setAttribute("landlordName", rs.getString("full_name"));
        session.setAttribute("landlordEmail", email);
        session.setAttribute("role", "landlord");

        stmt.close();
        return true;
    }
}
