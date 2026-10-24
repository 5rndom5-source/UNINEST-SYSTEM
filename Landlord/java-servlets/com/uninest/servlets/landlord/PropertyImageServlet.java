package com.uninest.servlets.landlord;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet("/PropertyImageServlet/*")
public class PropertyImageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        String fileName = pathInfo == null ? "" : Path.of(pathInfo).getFileName().toString();
        if (fileName.isEmpty() || !fileName.matches("[A-Za-z0-9._-]+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Path root = com.uninest.utils.ImageStorage.directory("property-images");
        javax.servlet.http.HttpSession session=request.getSession(false);
        int owner=session!=null&&session.getAttribute("landlordId") instanceof Integer?(Integer)session.getAttribute("landlordId"):-1;
        boolean admin=session!=null&&"admin".equals(session.getAttribute("role"));
        try {
            java.sql.Connection conn=com.uninest.utils.DatabaseConnection.getInstance().getConnection();
            if(conn==null){response.sendError(503);return;}
            try(java.sql.PreparedStatement query=conn.prepareStatement("SELECT i.image_id FROM room_images i JOIN rooms r ON r.room_id=i.room_id WHERE i.image_url=? AND (r.approval_status='approved' OR r.landlord_id=? OR ?=1) LIMIT 1")) {
                query.setString(1,"/PropertyImageServlet/"+fileName);query.setInt(2,owner);query.setInt(3,admin?1:0);
                try(java.sql.ResultSet rows=query.executeQuery()){if(!rows.next()){response.sendError(404);return;}}
            }
        }catch(java.sql.SQLException e){getServletContext().log("Photo authorization unavailable",e);response.sendError(503);return;}
        Path image = root.resolve(fileName).normalize();
        if (!image.getParent().equals(root) || !Files.isRegularFile(image)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String type = Files.probeContentType(image);
        if (type == null || !type.startsWith("image/")) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        response.setContentType(type);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentLengthLong(Files.size(image));
        Files.copy(image, response.getOutputStream());
    }
}
