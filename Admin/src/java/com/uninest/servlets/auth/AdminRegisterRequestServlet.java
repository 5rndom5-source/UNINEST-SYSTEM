package com.uninest.servlets.auth;

import com.uninest.utils.DatabaseConnection;
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

/**
 * Creates a pending admin account and notifies existing active admins.
 * Path: /AdminRegisterRequestServlet
 */
@WebServlet("/AdminRegisterRequestServlet")
public class AdminRegisterRequestServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String reason = request.getParameter("reason");
        String password = request.getParameter("password");
        String confirmation = request.getParameter("confirmPassword");

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isRequired(reason)
                || fullName.trim().length() > 100 || email.trim().length() > 100 || reason.trim().length() > 1000) {
            redirect(response, "error", "Name, email, and reason are required");
            return;
        }
        if (password == null || !password.equals(confirmation)
                || !com.uninest.utils.PasswordUtil.isStrongPassword(password)) {
            redirect(response, "error", "Passwords must match and include 8+ characters, uppercase, lowercase, a number and a symbol");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            com.uninest.utils.AdminRegistration.createPending(conn, fullName.trim(), email.trim(), password, reason.trim());

            redirect(response, "success", "Admin account created pending approval. You cannot sign in until an authorised administrator approves access.");
        } catch (SQLException e) {
            System.err.println("Database error in AdminRegisterRequestServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(response, "error", "Could not complete registration. The email may already exist; contact the system owner if you already submitted a request.");
        }
    }

    private void redirect(HttpServletResponse response, String key, String message) throws IOException {
        response.sendRedirect("Admin/web/jsp/Register.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
