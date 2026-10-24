package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ImageStorage;
import java.io.*;
import java.sql.*;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.*;
import javax.servlet.http.*;

@WebServlet("/ListingPhotoUploadServlet")
@MultipartConfig(maxFileSize=5242880, maxRequestSize=5500000)
public class ListingPhotoUploadServlet extends HttpServlet {
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        if (session == null || !"landlord".equals(session.getAttribute("role")) || !(session.getAttribute("landlordId") instanceof Integer)) {
            res.sendError(403); return;
        }
        String token = req.getParameter("token");
        if (token == null || !token.matches("[a-f0-9-]{36}")) { res.sendError(400); return; }
        int owner = (Integer) session.getAttribute("landlordId");
        try {
            Connection c = DatabaseConnection.getInstance().getConnection();
            if (c == null) { res.sendError(503); return; }
            if(c.getNetworkTimeout()>0&&c.getNetworkTimeout()<30000)c.setNetworkTimeout(Runnable::run,30000);
            // Lock the owner to enforce the staging quota across simultaneous uploads.
            c.setAutoCommit(false);
            try {
                try (PreparedStatement q = c.prepareStatement("SELECT landlord_id FROM landlords WHERE landlord_id=? FOR UPDATE")) {
                    q.setInt(1,owner); try (ResultSet r=q.executeQuery()) { if (!r.next()) throw new IOException("Account unavailable"); }
                }
                try (PreparedStatement q=c.prepareStatement("DELETE FROM listing_photo_uploads WHERE created_at < CURRENT_TIMESTAMP - INTERVAL 1 DAY")) { q.executeUpdate(); }
                boolean exists;
                try (PreparedStatement q=c.prepareStatement("SELECT token FROM listing_photo_uploads WHERE token=? AND landlord_id=?")) {
                    q.setString(1,token); q.setInt(2,owner); try(ResultSet r=q.executeQuery()) { exists=r.next(); }
                }
                if (!exists) {
                    try (PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM listing_photo_uploads WHERE landlord_id=?")) {
                        q.setInt(1,owner); try(ResultSet r=q.executeQuery()) { r.next(); if(r.getInt(1)>=60) throw new IOException("Upload limit reached. Save your listing or try again tomorrow."); }
                    }
                    java.awt.image.BufferedImage image=ImageStorage.decode(req.getPart("photo"));
                    if (image.getWidth()>1600 || image.getHeight()>1600) throw new IOException("Resize photos to 1600 pixels or smaller.");
                    java.awt.image.BufferedImage rgb=new java.awt.image.BufferedImage(image.getWidth(),image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D graphics=rgb.createGraphics();
                    try { graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,rgb.getWidth(),rgb.getHeight());graphics.drawImage(image,0,0,null); } finally { graphics.dispose(); }
                    ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(rgb,"jpeg",out);
                    try(PreparedStatement q=c.prepareStatement("INSERT INTO listing_photo_uploads(token,landlord_id,image_data) VALUES(?,?,?)")) {
                        q.setString(1,token);q.setInt(2,owner);q.setBytes(3,out.toByteArray());q.executeUpdate();
                    }
                }
                c.commit(); res.getWriter().print("{\"token\":\""+token+"\"}");
            } catch(Exception e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch(IOException e) { res.setStatus(400);res.getWriter().print("{\"message\":"+ListingEditor.json(e.getMessage())+"}"); }
        catch(Exception e) { getServletContext().log("Photo staging failed",e);res.setStatus(503);res.getWriter().print("{\"message\":\"Upload unavailable. Please retry.\"}"); }
    }
}
