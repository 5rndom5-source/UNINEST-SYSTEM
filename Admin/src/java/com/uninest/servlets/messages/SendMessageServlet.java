package com.uninest.servlets.messages;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.MessageAttachmentSupport;
import com.uninest.utils.MessageDelivery;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SendMessageServlet - Sends message to student
 * Path: /SendMessageServlet
 */
@WebServlet("/SendMessageServlet")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class SendMessageServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Check session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null
                || !"admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
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
        String subject = request.getParameter("subject");
        String inquiryId = request.getParameter("inquiryId");
        
        if (messageText == null || messageText.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/Messages.html?error=Message cannot be empty");
            return;
        }
        if (messageText.trim().length() > 4000 || (subject != null && subject.trim().length() > 120)) {
            response.sendRedirect(request.getContextPath() + "/Messages.html?error=Message is too long");
            return;
        }
        
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendRedirect(request.getContextPath() + "/Messages.html?error=Service is temporarily unavailable. Please try again later.");
                return;
            }

            String recipientRole = findRecipientRole(conn, recipientId);
            if (recipientRole == null || senderId == recipientId) {
                response.sendRedirect(request.getContextPath() + "/Messages.html?error=Invalid recipient");
                return;
            }

            try (MessageDelivery delivery = new MessageDelivery(conn)) {
            String storedMessage = messageText.trim();
            if (subject != null && !subject.trim().isEmpty()) {
                storedMessage = "Subject: " + subject.trim() + "\n" + storedMessage;
            }
            
            // Insert message
            String query = "INSERT INTO messages " +
                         "(sender_id, recipient_id, inquiry_id, message_text, sent_date, is_read) " +
                         "VALUES (?, ?, ?, ?, NOW(), FALSE)";
            
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
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
            
            stmt.setString(4, storedMessage);
            stmt.executeUpdate();
            int messageId;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Message ID was not generated");
                messageId = keys.getInt(1);
            }
            stmt.close();

            Part attachment = multipart(request) ? request.getPart("attachment") : null;
            String storedAttachment = null;
            try {
                storedAttachment = delivery.saveAttachment(attachment, messageId);
                if (storedAttachment != null) {
                    updateAttachment(conn, messageId, storedAttachment);
                }
            } catch (IOException | IllegalArgumentException e) {
                deleteMessage(conn, messageId);
                MessageAttachmentSupport.delete(storedAttachment);
                response.sendRedirect(request.getContextPath() + "/Messages.html?error="
                        + java.net.URLEncoder.encode(e.getMessage(), "UTF-8"));
                return;
            }
            
            // Create notification for recipient
            createNotification(conn, recipientId, 
                             "New Message", 
                             "You have received a new message",
                             "message_received",
                             "student".equals(recipientRole)
                                     ? "UserDashboard/messages.html"
                                     : "Landlord/contact-landlord.html");
            
            delivery.commit();
            }
            response.sendRedirect(request.getContextPath() + "/Messages.html?success=Message sent");
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/Messages.html?error=Error sending message");
        }
    }

    private boolean multipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private void updateAttachment(Connection conn, int messageId, String storedName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE messages SET attachment_url = ? WHERE message_id = ?")) {
            stmt.setString(1, storedName);
            stmt.setInt(2, messageId);
            stmt.executeUpdate();
        }
    }

    private void deleteMessage(Connection conn, int messageId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM messages WHERE message_id = ?")) {
            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        }
    }

    private String findRecipientRole(Connection conn, int recipientId) throws SQLException {
        String query = "SELECT role FROM users WHERE user_id = ? AND status NOT IN ('inactive', 'suspended') "
                + "AND role IN ('student', 'landlord')";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, recipientId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("role") : null;
            }
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
