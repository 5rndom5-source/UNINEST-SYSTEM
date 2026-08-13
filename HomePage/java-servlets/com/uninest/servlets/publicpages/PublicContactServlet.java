package com.uninest.servlets.publicpages;

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
 * PublicContactServlet - Captures HomePage/contact.html messages for admin follow-up.
 * Path: /PublicContactServlet
 */
@WebServlet("/PublicContactServlet")
public class PublicContactServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String subject = request.getParameter("subject");
        String message = request.getParameter("message");

        if (!ValidationUtil.isRequired(name)
                || !ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isRequired(subject)
                || !ValidationUtil.isRequired(message)) {
            redirect(request, response, "error", "Please complete the contact form");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String body = "From: " + name.trim() + " <" + email.trim() + ">. "
                    + "Subject: " + subject.trim() + ". "
                    + "Message: " + ValidationUtil.sanitizeXSS(message.trim());

            String query = "INSERT INTO notifications "
                    + "(user_id, title, message, notification_type, action_url) "
                    + "SELECT user_id, 'Public contact message', ?, 'system_alert', 'Enquiries.html' "
                    + "FROM users WHERE role = 'admin' AND status = 'active'";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, body);
            stmt.executeUpdate();
            stmt.close();

            response.sendRedirect(request.getContextPath() + "/HomePage/contact.html?sent=true");
        } catch (SQLException e) {
            System.err.println("Database error in PublicContactServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not send message");
        }
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath() + "/HomePage/contact.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
