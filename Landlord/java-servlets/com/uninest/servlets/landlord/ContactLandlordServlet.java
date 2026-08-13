package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
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

@WebServlet("/ContactLandlordServlet")
public class ContactLandlordServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String propertyTitle = request.getParameter("propertyTitle");
        String studentName = request.getParameter("studentName");
        String studentEmail = request.getParameter("studentEmail");
        String message = request.getParameter("message");

        if (!ValidationUtil.isRequired(propertyTitle)
                || !ValidationUtil.isRequired(studentName)
                || !ValidationUtil.isValidEmail(studentEmail)
                || !ValidationUtil.isRequired(message)) {
            response.sendRedirect("Landlord/contact-landlord.html?error=missing");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect("Landlord/contact-landlord.html?error=db");
                return;
            }

            Integer landlordUserId = findLandlordUserId(conn, request.getParameter("landlordEmail"));
            if (landlordUserId != null) {
                createNotification(
                        conn,
                        landlordUserId,
                        "New property contact",
                        studentName.trim() + " asked about " + propertyTitle.trim()
                                + ". Email: " + studentEmail.trim()
                                + ". Message: " + ValidationUtil.sanitizeXSS(message.trim()),
                        "message_received",
                        "Landlord/contact-landlord.html");
            }

            response.sendRedirect("Landlord/contact-landlord.html?sent=true");
        } catch (SQLException e) {
            System.err.println("Database error in ContactLandlordServlet: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("Landlord/contact-landlord.html?error=save");
        }
    }

    private Integer findLandlordUserId(Connection conn, String landlordEmail) throws SQLException {
        String email = ValidationUtil.isValidEmail(landlordEmail)
                ? landlordEmail
                : "molebogeng.ramushu@uninest.co.za";

        PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_id FROM users WHERE email = ? AND role = 'landlord'");
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        Integer userId = null;
        if (rs.next()) {
            userId = rs.getInt("user_id");
        }
        stmt.close();
        return userId;
    }

    private void createNotification(Connection conn, int userId, String title,
                                    String message, String type, String actionUrl)
            throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                        + "VALUES (?, ?, ?, ?, ?)");
        stmt.setInt(1, userId);
        stmt.setString(2, title);
        stmt.setString(3, message);
        stmt.setString(4, type);
        stmt.setString(5, actionUrl);
        stmt.executeUpdate();
        stmt.close();
    }
}
