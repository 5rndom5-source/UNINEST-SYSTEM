package com.uninest.servlets.account;

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
 * ProfileServlet - Updates the signed-in admin user's basic account details.
 * Path: /ProfileServlet
 */
@WebServlet("/ProfileServlet")
public class ProfileServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isValidPhone(phone)) {
            redirect(request, response, "error", "Please enter a valid name, email, and phone number");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE users SET full_name = ?, email = ?, phone = ? WHERE user_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, fullName.trim());
            stmt.setString(2, email.trim());
            stmt.setString(3, phone.trim());
            stmt.setInt(4, (Integer) session.getAttribute("userId"));

            int updated = stmt.executeUpdate();
            stmt.close();

            if (updated == 0) {
                redirect(request, response, "error", "No matching admin profile was found");
                return;
            }

            session.setAttribute("adminName", fullName.trim());
            session.setAttribute("email", email.trim());
            redirect(request, response, "success", "Profile updated");
        } catch (SQLException e) {
            System.err.println("Database error in ProfileServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update profile");
        }
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/Profile.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
