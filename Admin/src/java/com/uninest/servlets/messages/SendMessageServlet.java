package com.uninest.servlets.messages;

import com.uninest.utils.DatabaseConnection;

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
import java.time.LocalDateTime;

/**
 * SendMessageServlet - Sends message to student
 * Path: /SendMessageServlet
 */
@WebServlet("/SendMessageServlet")
public class SendMessageServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Check session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }
        
        int senderId = (Integer) session.getAttribute("userId");
        int recipientId;
        try {
            recipientId = Integer.parseInt(request.getParameter("recipientId"));
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/Messages.html?error=Invalid recipient");
            return;
        }
        String messageText = request.getParameter("message");
        String inquiryId = request.getParameter("inquiryId");
        
        if (messageText == null || messageText.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/Messages.html?error=Message cannot be empty");
            return;
        }
        
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect(request.getContextPath() + "/Messages.html?error=Service is temporarily unavailable. Please try again later.");
                return;
            }
            
            // Insert message
            String query = "INSERT INTO messages " +
                         "(sender_id, recipient_id, inquiry_id, message_text, sent_date, is_read) " +
                         "VALUES (?, ?, ?, ?, NOW(), FALSE)";
            
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, senderId);
            stmt.setInt(2, recipientId);
            
            if (inquiryId != null && !inquiryId.isEmpty()) {
                try {
                    stmt.setInt(3, Integer.parseInt(inquiryId));
                } catch (NumberFormatException e) {
                    stmt.setNull(3, java.sql.Types.INTEGER);
                }
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            
            stmt.setString(4, messageText);
            stmt.executeUpdate();
            stmt.close();
            
            // Create notification for recipient
            createNotification(conn, recipientId, 
                             "New Message", 
                             "You have received a new message",
                             "message_received",
                             "Messages.html");
            
            response.sendRedirect(request.getContextPath() + "/Messages.html?success=Message sent");
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/Messages.html?error=Error sending message");
        }
    }
    
    /**
     * Create notification
     */
    private void createNotification(Connection conn, int userId, String title, 
                                   String message, String type, String actionUrl) 
            throws SQLException {
        
        String query = "INSERT INTO notifications " +
                     "(user_id, title, message, notification_type, action_url) " +
                     "VALUES (?, ?, ?, ?, ?)";
        
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, userId);
        stmt.setString(2, title);
        stmt.setString(3, message);
        stmt.setString(4, type);
        stmt.setString(5, actionUrl);
        stmt.executeUpdate();
        stmt.close();
    }
}
