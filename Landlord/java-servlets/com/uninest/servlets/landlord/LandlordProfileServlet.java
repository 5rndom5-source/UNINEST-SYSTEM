package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/LandlordProfileServlet")
public class LandlordProfileServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.sendRedirect(request.getContextPath() + "/Landlord/login.html");
            return;
        }

        String fullName = trim(request.getParameter("fullName"));
        String email = trim(request.getParameter("email"));
        String phone = trim(request.getParameter("phone"));
        String mainArea = trim(request.getParameter("mainArea"));
        String bio = trim(request.getParameter("bio"));

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || (!phone.isEmpty() && !ValidationUtil.isValidPhone(phone))
                || !ValidationUtil.isRequired(mainArea)) {
            redirect(response, "error", "Enter a valid name, email, phone number, and main area");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            int updated;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET full_name = ?, email = ?, phone = ? WHERE user_id = ?")) {
                stmt.setString(1, fullName);
                stmt.setString(2, email);
                stmt.setString(3, phone);
                stmt.setInt(4, (Integer) session.getAttribute("userId"));
                updated = stmt.executeUpdate();
            }

            if (updated == 0) {
                redirect(response, "error", "No matching landlord profile was found");
                return;
            }

            session.setAttribute("landlordName", fullName);
            session.setAttribute("fullName", fullName);
            session.setAttribute("landlordEmail", email);
            session.setAttribute("email", email);
            session.setAttribute("landlordPhone", phone);
            session.setAttribute("landlordArea", mainArea);
            session.setAttribute("landlordBio", bio);

            redirect(response, "success", "Profile updated");
        } catch (SQLException e) {
            System.err.println("Database error in LandlordProfileServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(response, "error", "Could not update profile");
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void redirect(HttpServletResponse response, String key, String message) throws IOException {
        response.sendRedirect("Landlord/profile.html?" + key + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
