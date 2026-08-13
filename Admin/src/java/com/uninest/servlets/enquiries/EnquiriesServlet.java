package com.uninest.servlets.enquiries;

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
 * EnquiriesServlet - Updates inquiry status from the admin enquiries page.
 * Path: /EnquiriesServlet
 */
@WebServlet("/EnquiriesServlet")
public class EnquiriesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/Enquiries.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request)) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }

        String inquiryId = request.getParameter("inquiryId");
        String status = normalizeStatus(request.getParameter("status"));

        if (!ValidationUtil.isInteger(inquiryId) || status == null) {
            redirect(request, response, "error", "Invalid inquiry update");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(request, response, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE inquiries SET reply_status = ? WHERE inquiry_id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, status);
            stmt.setInt(2, Integer.parseInt(inquiryId));
            int updated = stmt.executeUpdate();
            stmt.close();

            redirect(request, response, updated > 0 ? "success" : "error",
                    updated > 0 ? "Inquiry updated" : "No matching inquiry was found");
        } catch (SQLException e) {
            System.err.println("Database error in EnquiriesServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(request, response, "error", "Could not update inquiry");
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("adminId") != null;
    }

    private String normalizeStatus(String value) {
        if ("pending".equalsIgnoreCase(value)) return "pending";
        if ("replied".equalsIgnoreCase(value)) return "replied";
        if ("resolved".equalsIgnoreCase(value)) return "resolved";
        return null;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/Enquiries.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
