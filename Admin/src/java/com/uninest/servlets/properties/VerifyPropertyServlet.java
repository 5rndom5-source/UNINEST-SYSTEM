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
        if (session == null || session.getAttribute("userId") == null
                || !"admin".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.sendRedirect(request.getContextPath() + "/Admin/web/jsp/Login.html");
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
        if ("deny".equalsIgnoreCase(decision) && !ValidationUtil.isRequired(adminNote)) {
            redirect(response, request, "error", "An admin note is required when denying a listing");
            return;
        }

        String approvalStatus = "approve".equalsIgnoreCase(decision) ? "approved" : "denied";
        int adminUserId = (Integer) session.getAttribute("userId");

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                redirect(response, request, "error", "Service is temporarily unavailable. Please try again later.");
                return;
            }

            // The decision and the landlord's notification are saved together or not at all.
            int roomId = Integer.parseInt(propertyId);
            boolean autoCommit = conn.getAutoCommit();
            int updated;
            conn.setAutoCommit(false);
            try {
                String query = "UPDATE rooms SET approval_status = ?, admin_note = ?, "
                        + "reviewed_by = ?, reviewed_at = NOW() WHERE room_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, approvalStatus);
                    stmt.setString(2, adminNote);
                    stmt.setInt(3, adminUserId);
                    stmt.setInt(4, roomId);
                    updated = stmt.executeUpdate();
                }
                if (updated == 0) {
                    conn.rollback();
                } else {
                    notifyLandlord(conn, roomId, approvalStatus, adminNote);
                    conn.commit();
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }

            if (updated == 0) {
                redirect(response, request, "error", "No matching property was found");
                return;
            }

            String message = "approved".equals(approvalStatus)
                    ? "Listing approved and published"
                    : "Listing denied with admin note";
            redirect(response, request, "success", message);
        } catch (SQLException e) {
            System.err.println("Database error in VerifyPropertyServlet: " + e.getMessage());
            e.printStackTrace();
            redirect(response, request, "error", "Could not save verification decision");
        }
    }

    private void notifyLandlord(Connection conn, int roomId, String status, String note)
            throws SQLException {
        String message = "Listing status changed to " + status
                + (note == null || note.trim().isEmpty() ? "" : ": " + note.trim());
        String query = "INSERT INTO notifications (user_id, title, message, notification_type, action_url) "
                + "SELECT l.user_id, 'Listing review completed', ?, 'document_update', "
                + "'Landlord/listings.html' FROM rooms r JOIN landlords l ON l.landlord_id = r.landlord_id "
                + "WHERE r.room_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, message);
            stmt.setInt(2, roomId);
            stmt.executeUpdate();
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
