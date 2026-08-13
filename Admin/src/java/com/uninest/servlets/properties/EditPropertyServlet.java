package com.uninest.servlets.properties;

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
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * EditPropertyServlet - Updates property listing
 * Path: /EditPropertyServlet
 */
@WebServlet("/EditPropertyServlet")
public class EditPropertyServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Check session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("landlordId") == null) {
            response.sendRedirect(request.getContextPath() + "/Login.html");
            return;
        }
        
        int landlordId = (Integer) session.getAttribute("landlordId");
        int roomId;
        try {
            String roomIdParam = firstNonEmpty(request.getParameter("roomId"), request.getParameter("propertyId"));
            roomId = Integer.parseInt(roomIdParam);
        } catch (NumberFormatException e) {
            sendError(request, response, "Invalid property ID");
            return;
        }
        
        String propertyTitle = request.getParameter("propertyTitle");
        String roomType = request.getParameter("roomType");
        String location = request.getParameter("location");
        String price = request.getParameter("price");
        String availability = normalizeAvailability(firstNonEmpty(request.getParameter("availability"), request.getParameter("status")));
        String furnishing = normalizeFurnishing(request.getParameter("furnishing"));
        String propertySize = firstNonEmpty(request.getParameter("propertySize"), request.getParameter("size"));
        String bedrooms = request.getParameter("bedrooms");
        String bathrooms = request.getParameter("bathrooms");
        String description = request.getParameter("description");
        
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendError(request, response, "Service is temporarily unavailable. Please try again later.");
                return;
            }
            
            // Verify property belongs to this landlord
            if (!verifyOwnership(conn, roomId, landlordId)) {
                sendError(request, response, "Unauthorized access");
                return;
            }
            
            // Update room
            String query = "UPDATE rooms SET room_number=?, room_type=?, location=?, price=?, " +
                         "availability_status=?, furnishing=?, property_size=?, bedrooms=?, bathrooms=?, " +
                         "description=? WHERE room_id=?";
            
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, propertyTitle);
            stmt.setString(2, roomType);
            stmt.setString(3, location);
            stmt.setBigDecimal(4, new java.math.BigDecimal(price));
            stmt.setString(5, availability);
            stmt.setString(6, furnishing);
            stmt.setString(7, propertySize);
            stmt.setInt(8, parseOptionalInt(bedrooms));
            stmt.setInt(9, parseOptionalInt(bathrooms));
            stmt.setString(10, description);
            stmt.setInt(11, roomId);
            
            stmt.executeUpdate();
            stmt.close();
            
            response.sendRedirect(request.getContextPath() + "/MyProperties.html?success=" +
                                java.net.URLEncoder.encode("Property updated successfully!", "UTF-8"));
            
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Database error: " + e.getMessage());
            sendError(request, response, "Error updating property");
        }
    }
    
    /**
     * Verify property ownership
     */
    private boolean verifyOwnership(Connection conn, int roomId, int landlordId) 
            throws SQLException {
        
        String query = "SELECT landlord_id FROM rooms WHERE room_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, roomId);
        ResultSet rs = stmt.executeQuery();
        
        boolean owns = false;
        if (rs.next() && rs.getInt("landlord_id") == landlordId) {
            owns = true;
        }
        
        stmt.close();
        return owns;
    }
    
    /**
     * Send error response
     */
    private String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private int parseOptionalInt(String value) {
        return value == null || value.trim().isEmpty() ? 0 : Integer.parseInt(value);
    }

    private String normalizeAvailability(String availability) {
        if ("active".equals(availability)) {
            return "available";
        }
        if (availability == null || availability.trim().isEmpty()) {
            return "available";
        }
        return availability;
    }

    private String normalizeFurnishing(String furnishing) {
        if ("furnished".equals(furnishing)) {
            return "fully_furnished";
        }
        if (furnishing == null || furnishing.trim().isEmpty()) {
            return "unfurnished";
        }
        return furnishing;
    }

    private void sendError(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        response.sendRedirect(request.getContextPath() + "/MyProperties.html?error=" +
                            java.net.URLEncoder.encode(message, "UTF-8"));
    }
}
