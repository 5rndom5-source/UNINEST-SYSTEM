package com.uninest.servlets.auth;

import com.uninest.utils.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/LandlordDocumentServlet")
public class LandlordDocumentServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Cache-Control", "no-store, private");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        try {
            if (LandlordApprovalServlet.authorised(req, res) == null) return;
            int id = Integer.parseInt(req.getParameter("id"));
            Connection c = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement q = c.prepareStatement("SELECT d.file_url FROM documents d JOIN users u ON u.user_id=d.user_id JOIN landlords l ON l.user_id=u.user_id WHERE d.document_id=? AND u.role='landlord'")) {
                q.setInt(1, id);
                try (ResultSet r = q.executeQuery()) {
                    Path file = r.next() ? LandlordDocuments.resolve(r.getString(1), getServletContext()) : null;
                    if (file == null) { res.sendError(404); return; }
                    res.setContentType("application/octet-stream");
                    // Never use an uploaded filename in headers or render an untrusted document inline.
                    res.setHeader("Content-Disposition", "attachment; filename=\"verification-" + id + "." +
                            (file.toString().endsWith(".pdf") ? "pdf" : file.toString().endsWith(".png") ? "png" : "bin") + "\"");
                    res.setContentLengthLong(Files.size(file));
                    Files.copy(file, res.getOutputStream());
                }
            }
        } catch (NumberFormatException ex) { res.sendError(400); }
        catch (SQLException ex) { getServletContext().log("Document lookup failed", ex); res.sendError(503); }
    }
}
