package com.uninest.servlets.landlord;
import com.uninest.utils.DatabaseConnection;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/PropertyGalleryServlet")
public class PropertyGalleryServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        int roomId;
        try { roomId = Integer.parseInt(request.getParameter("roomId")); }
        catch (Exception e) { response.sendError(400); return; }
        HttpSession session = request.getSession(false);
        boolean admin = session != null && "admin".equals(session.getAttribute("role"));
        int owner = session != null && session.getAttribute("landlordId") instanceof Integer ? (Integer)session.getAttribute("landlordId") : -1;
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) { response.sendError(503); return; }
            StringBuilder json = new StringBuilder("{\"images\":[");
            try (PreparedStatement query = conn.prepareStatement("SELECT i.image_id,i.image_url,i.category,i.is_primary FROM room_images i JOIN rooms r ON r.room_id=i.room_id WHERE r.room_id=? AND (r.approval_status='approved' OR r.landlord_id=? OR ?=1) ORDER BY i.is_primary DESC,i.display_order,i.image_id")) {
                query.setInt(1, roomId); query.setInt(2, owner); query.setInt(3, admin ? 1 : 0);
                try (ResultSet rows = query.executeQuery()) {
                    boolean first = true;
                    while (rows.next()) {
                        String url = rows.getString("image_url");
                        if (url == null || !url.startsWith("/PropertyImageServlet/")) continue;
                        if (!first) json.append(','); first = false;
                        json.append("{\"id\":").append(rows.getInt("image_id"))
                            .append(",\"url\":\"").append(escape(request.getContextPath()+url))
                            .append("\",\"category\":\"").append(escape(rows.getString("category"))).append("\"}");
                    }
                }
            }
            response.getWriter().print(json.append("],\"listing\":").append(ListingEditor.read(conn, roomId, owner, admin, request.getContextPath())).append('}'));
        } catch (SQLException e) { getServletContext().log("Gallery unavailable",e); response.sendError(503); }
    }
    private String escape(String s) { return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r"); }
}
