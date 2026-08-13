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

@WebServlet("/ReviewResponseServlet")
public class ReviewResponseServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String reviewId = request.getParameter("reviewId");
        String responseText = request.getParameter("response");

        if (!ValidationUtil.isRequired(responseText)) {
            response.sendRedirect("Landlord/reviews.html?error=missing");
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect("Landlord/login.html?error=session");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect("Landlord/reviews.html?error=db");
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                            + "SELECT user_id, 'Landlord review response', ?, 'system_alert', 'Landlord/reviews.html' "
                            + "FROM users WHERE role = 'admin' AND status = 'active'");
            stmt.setString(1, "Review "
                    + (ValidationUtil.isInteger(reviewId) ? reviewId : "without id")
                    + " response from landlord user "
                    + session.getAttribute("userId")
                    + ": "
                    + ValidationUtil.sanitizeXSS(responseText.trim()));
            stmt.executeUpdate();
            stmt.close();

            response.sendRedirect("Landlord/reviews.html?response=sent");
        } catch (SQLException e) {
            System.err.println("Database error in ReviewResponseServlet: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("Landlord/reviews.html?error=save");
        }
    }
}
