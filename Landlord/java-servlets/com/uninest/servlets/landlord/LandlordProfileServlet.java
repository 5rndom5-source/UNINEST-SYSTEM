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

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String mainArea = request.getParameter("mainArea");
        String bio = request.getParameter("bio");

        if (!ValidationUtil.isRequired(fullName)
                || !ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isRequired(phone)
                || !ValidationUtil.isRequired(mainArea)) {
            response.sendRedirect("Landlord/profile.html?error=missing");
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"landlord".equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/Landlord/login.html");
            return;
        }
        session.setAttribute("landlordName", fullName.trim());
        session.setAttribute("landlordEmail", email.trim());
        session.setAttribute("landlordPhone", phone.trim());
        session.setAttribute("landlordArea", mainArea.trim());
        session.setAttribute("landlordBio", bio);

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect("Landlord/profile.html?error=db");
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET full_name = ?, email = ?, phone = ? WHERE user_id = ?");
            stmt.setString(1, fullName.trim());
            stmt.setString(2, email.trim());
            stmt.setString(3, phone.trim());
            stmt.setInt(4, (Integer) session.getAttribute("userId"));
            stmt.executeUpdate();
            stmt.close();

            session.setAttribute("landlordName", fullName.trim());
            session.setAttribute("landlordEmail", email.trim());
            session.setAttribute("email", email.trim());

            response.sendRedirect("Landlord/profile.html?saved=true");
        } catch (SQLException e) {
            System.err.println("Database error in LandlordProfileServlet: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("Landlord/profile.html?error=save");
        }
    }
}
