package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebServlet("/LandlordAddPropertyServlet")
@MultipartConfig(maxFileSize = 5242880, maxRequestSize = 52428800)
public class AddPropertyServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String title = request.getParameter("propertyTitle");
        String description = request.getParameter("description");
        String location = request.getParameter("location");
        String price = request.getParameter("price");
        String[] amenities = request.getParameterValues("amenities");
        Integer roomId = parseRoomId(request.getParameter("roomId"));
        boolean areaOnly="1".equals(request.getParameter("areaOnly"));
        if(areaOnly && (roomId==null || request.getParameter("area")==null)){response.sendError(400);return;}

        if (!areaOnly && (!ValidationUtil.isRequired(title)
                || !ValidationUtil.isRequired(description)
                || !ValidationUtil.isRequired(location)
                || location.trim().length() > 255
                || !ValidationUtil.isValidPrice(price))) {
            if ("2".equals(request.getParameter("editorVersion"))) response.sendError(400, "Check required fields.");
            else response.sendRedirect(request.getContextPath() + "/Landlord/listings.html?error=missing");
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("landlordId") == null
                || !"landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            if ("2".equals(request.getParameter("editorVersion"))) response.sendError(401);
            else response.sendRedirect(request.getContextPath() + "/Landlord/login.html?error=session");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.sendError(503);
                return;
            }

            if (!(session.getAttribute("userId") instanceof Integer)
                    || !com.uninest.utils.LandlordApproval.eligible(conn, (Integer)session.getAttribute("userId"))) {
                response.sendError(403, "An approved, active landlord account is required");
                return;
            }
            conn.setAutoCommit(false);
            if ("2".equals(request.getParameter("editorVersion")) && conn.getNetworkTimeout()>0 && conn.getNetworkTimeout()<30000)
                conn.setNetworkTimeout(Runnable::run,30000);
            java.util.List<Path> editorFiles = new java.util.ArrayList<>();
            try {
            int landlordId = (Integer) session.getAttribute("landlordId");
            if(areaOnly) {
                ListingEditor.saveArea(request,conn,roomId,landlordId);
                try(PreparedStatement q=conn.prepareStatement("UPDATE rooms SET approval_status='pending',updated_at=CURRENT_TIMESTAMP WHERE room_id=? AND landlord_id=?")) {
                    q.setInt(1,roomId);q.setInt(2,landlordId);q.executeUpdate();
                }
                conn.commit();
                response.setContentType("application/json;charset=UTF-8");response.getWriter().print("{\"saved\":true}");
                return;
            }
            if (roomId == null) {
                roomId = insertRoom(conn, landlordId, title.trim(), description.trim(),
                        location.trim(), new BigDecimal(price), amenities);
            } else if (!updateOwnedRoom(conn, roomId, landlordId, title.trim(), description.trim(),
                    location.trim(), new BigDecimal(price), amenities)) {
                conn.rollback();
                response.sendError(403);
                return;
            }

            replaceAmenities(conn, roomId, amenities);
            if ("2".equals(request.getParameter("editorVersion"))) {
                ListingEditor.save(request, conn, roomId, landlordId, editorFiles);
            } else {
                saveImages(request, conn, roomId);
                ListingEditor.saveArea(request,conn,roomId,landlordId);
            }
            conn.commit();

            session.setAttribute("listingStatus", "Pending Approval");
            if ("2".equals(request.getParameter("editorVersion"))) {
                response.setContentType("application/json;charset=UTF-8"); response.getWriter().print("{\"saved\":true}");
            } else response.sendRedirect(request.getContextPath() + "/Landlord/listings.html?status=pending");
            } catch (SQLException | IOException | ServletException | RuntimeException e) {
                conn.rollback();
                for (Path path : editorFiles) Files.deleteIfExists(path);
                if ("2".equals(request.getParameter("editorVersion"))) {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(e instanceof IllegalArgumentException ? 400 : 503);
                    response.getWriter().print("{\"message\":" + ListingEditor.json(e instanceof IllegalArgumentException ? e.getMessage() : "Listing could not be saved. Please retry.") + "}");
                    return;
                }
                throw e;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException e) {
            System.err.println("Database error in Landlord AddPropertyServlet: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/Landlord/listings.html?error=save");
        }
    }

    private boolean updateOwnedRoom(Connection conn, int roomId, int landlordId, String title,
                                    String description, String location, BigDecimal price,
                                    String[] amenities) throws SQLException {
        String sql = "UPDATE rooms SET room_number = ?, description = ?, location = ?, price = ?, "
                + "furnishing = ?, approval_status = 'pending', admin_note = NULL, "
                + "reviewed_by = NULL, reviewed_at = NULL WHERE room_id = ? AND landlord_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setString(3, location);
            stmt.setBigDecimal(4, price);
            stmt.setString(5, hasAmenity(amenities, "Furnished") ? "fully_furnished" : "unfurnished");
            stmt.setInt(6, roomId);
            stmt.setInt(7, landlordId);
            return stmt.executeUpdate() == 1;
        }
    }

    private void replaceAmenities(Connection conn, int roomId, String[] amenities) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM room_amenities WHERE room_id = ?")) {
            stmt.setInt(1, roomId);
            stmt.executeUpdate();
        }
        insertAmenities(conn, roomId, amenities);
    }

    private Integer parseRoomId(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            int id = Integer.parseInt(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int insertRoom(Connection conn, int landlordId, String title, String description,
                           String location, BigDecimal price, String[] amenities)
            throws SQLException {
        String query = "INSERT INTO rooms "
                + "(landlord_id, room_number, room_type, location, price, capacity, description, "
                + "availability_status, furnishing, approval_status) "
                + "VALUES (?, ?, 'apartment', ?, ?, 1, ?, 'available', ?, 'pending')";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, landlordId);
        stmt.setString(2, title);
        stmt.setString(3, location);
        stmt.setBigDecimal(4, price);
        stmt.setString(5, description);
        stmt.setString(6, hasAmenity(amenities, "Furnished") ? "fully_furnished" : "unfurnished");
        stmt.executeUpdate();

        ResultSet keys = stmt.getGeneratedKeys();
        int roomId = 0;
        if (keys.next()) {
            roomId = keys.getInt(1);
        }
        stmt.close();
        return roomId;
    }

    private void insertAmenities(Connection conn, int roomId, String[] amenities) throws SQLException {
        if (amenities == null || roomId == 0) {
            return;
        }

        for (int i = 0; i < amenities.length; i++) {
            String amenity = amenities[i];
            if ("Furnished".equalsIgnoreCase(amenity)) {
                continue;
            }

            Integer amenityId = findAmenityId(conn, amenity);
            if (amenityId == null) {
                continue;
            }

            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT IGNORE INTO room_amenities (room_id, amenity_id) VALUES (?, ?)");
            stmt.setInt(1, roomId);
            stmt.setInt(2, amenityId);
            stmt.executeUpdate();
            stmt.close();
        }
    }

    private Integer findAmenityId(Connection conn, String amenityName) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT amenity_id FROM amenities WHERE amenity_name = ?");
        stmt.setString(1, amenityName);
        ResultSet rs = stmt.executeQuery();

        Integer amenityId = null;
        if (rs.next()) {
            amenityId = rs.getInt("amenity_id");
        }
        stmt.close();
        return amenityId;
    }

    private void saveImages(HttpServletRequest request, Connection conn, int roomId)
            throws IOException, ServletException, SQLException {
        Path uploadDir = com.uninest.utils.ImageStorage.directory("property-images");
        Files.createDirectories(uploadDir);

        int displayOrder = 0;
        try (PreparedStatement count = conn.prepareStatement("SELECT COALESCE(MAX(display_order),-1)+1 FROM room_images WHERE room_id=?")) {
            count.setInt(1, roomId);
            try (ResultSet result = count.executeQuery()) { if (result.next()) displayOrder = result.getInt(1); }
        }
        java.util.List<Path> written = new java.util.ArrayList<>();
        int count = 0;
        try {
        for (Part part : request.getParts()) {
            if (!("photos".equals(part.getName()) || part.getName().startsWith("photos_")) || part.getSize() == 0) {
                continue;
            }
            if (++count > 20) throw new IOException("Upload at most 20 images at a time");
            String category = "photos".equals(part.getName()) ? "building" : part.getName().substring(7);
            if (!java.util.Arrays.asList("building","single_room","double_room","kitchen","bathroom","shower","tub","toilet","other").contains(category))
                throw new IOException("Invalid image category");
            String fileName = java.util.UUID.randomUUID().toString() + ".png";
            Path destination = uploadDir.resolve(fileName).normalize();
            if (!destination.getParent().equals(uploadDir)) {
                throw new IOException("Invalid upload path");
            }
            com.uninest.utils.ImageStorage.write(com.uninest.utils.ImageStorage.decode(part), destination);
            written.add(destination);
            insertImage(conn, roomId, "/PropertyImageServlet/" + fileName, displayOrder == 0, displayOrder, category);
            displayOrder++;
        }
        } catch (IOException | ServletException | SQLException | RuntimeException e) {
            for (Path path : written) Files.deleteIfExists(path);
            throw e;
        }
    }

    private void insertImage(Connection conn, int roomId, String imageUrl,
                             boolean primary, int displayOrder, String category) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO room_images (room_id, image_url, is_primary, display_order, category) VALUES (?, ?, ?, ?, ?)");
        stmt.setInt(1, roomId);
        stmt.setString(2, imageUrl);
        stmt.setBoolean(3, primary);
        stmt.setInt(4, displayOrder);
        stmt.setString(5, category);
        stmt.executeUpdate();
        stmt.close();
    }

    private boolean hasAmenity(String[] amenities, String target) {
        if (amenities == null) {
            return false;
        }
        for (int i = 0; i < amenities.length; i++) {
            if (target.equalsIgnoreCase(amenities[i])) {
                return true;
            }
        }
        return false;
    }
}
