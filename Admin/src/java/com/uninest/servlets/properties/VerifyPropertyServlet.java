package com.uninest.servlets.properties;

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
 * VerifyPropertyServlet - Accepts or denies landlord-submitted listings.
 * Path: /VerifyPropertyServlet
 *
 * Expected form fields:
 * - propertyId: room/listing id
 * - decision: approve or deny
 * - adminNote: optional note explaining the decision
 */
@WebServlet("/VerifyPropertyServlet")
public class VerifyPropertyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminId") == null) {
            response.sendRedirect(request.getContextPath() + "/createAccount/login.html");
            return;
        }

        String propertyId = request.getParameter("propertyId");
        String decision = request.getParameter("decision");
        String adminNote = request.getParameter("adminNote");

        if (!ValidationUtil.isInteger(propertyId)) {
            redirect(response, request, "error", "Invalid property id");
            return;
        }

        if (!"approve".equalsIgnoreCase(decision) && !"deny".equalsIgnoreCase(decision)) {
            redirect(response, request, "error", "Decision must be approve or deny");
            return;
        }

        String approvalStatus = "approve".equalsIgnoreCase(decision) ? "approved" : "denied";
        int adminId = (Integer) session.getAttribute("adminId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, request, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            String query = "UPDATE rooms SET approval_status = ?, admin_note = ?, "
                    + "reviewed_by = ?, reviewed_at = NOW() WHERE room_id = ?";

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, approvalStatus);
            stmt.setString(2, adminNote);
            stmt.setInt(3, adminId);
            stmt.setInt(4, Integer.parseInt(propertyId));

            int updated = stmt.executeUpdate();
            stmt.close();

            if (updated == 0) {
                redirect(response, request, "error", "No matching property was found");
                return;
            }

            String message = "approved".equals(approvalStatus)
                    ? "Listing approved and ready for public pages"
                    : "Listing denied with admin note";
            redirect(response, request, "success", message);
        } catch (SQLException e) {
            System.err.println("Database error in VerifyPropertyServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(response, request, "error", "Could not save verification decision");
        }
    }

    private void redirect(HttpServletResponse response, HttpServletRequest request,
                          String key, String message) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/Admin/web/jsp/AddProperty.html?"
                + key
                + "="
                + java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
