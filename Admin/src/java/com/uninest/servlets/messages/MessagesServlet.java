package com.uninest.servlets.messages;

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
 * MessagesServlet - Message utility actions such as marking a message read.
 * Path: /MessagesServlet
 */
@WebServlet("/MessagesServlet")
public class MessagesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/Messages.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String messageId = request.getParameter("messageId");
        if (!ValidationUtil.isInteger(messageId)) {
            redirect(request, response, "error", "Invalid message ID");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE messages SET is_read = TRUE, read_date = NOW() "
                    + "WHERE message_id = ? AND recipient_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, Integer.parseInt(messageId));
            stmt.setInt(2, (Integer) session.getAttribute("userId"));
            int updated = stmt.executeUpdate();
            stmt.close();

            redirect(request, response, updated > 0 ? "success" : "error",
                    updated > 0 ? "Message marked as read" : "No matching message was found");
        } catch (SQLException e) {
            System.err.println("Database error in MessagesServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update message");
        }
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/Messages.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
