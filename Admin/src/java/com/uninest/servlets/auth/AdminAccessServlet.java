package com.uninest.servlets.auth;

import com.uninest.utils.AdminRegistration;
import com.uninest.utils.CsrfToken;
import com.uninest.utils.DatabaseConnection;
import java.io.IOException;
import java.sql.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/AdminAccessServlet")
public class AdminAccessServlet extends HttpServlet {
    private Integer authorised(HttpServletRequest req, HttpServletResponse res) throws IOException, SQLException {
        HttpSession session = req.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Integer)) { res.sendError(401); return null; }
        Connection c = DatabaseConnection.getInstance().getConnection();
        if (c == null) { res.sendError(503); return null; }
        int id = (Integer) session.getAttribute("userId");
        try (PreparedStatement q = c.prepareStatement("SELECT u.user_id FROM users u JOIN admins a ON a.user_id=u.user_id WHERE u.user_id=? AND u.role='admin' AND u.status='active'")) {
            q.setInt(1, id);
            try (ResultSet rows = q.executeQuery()) {
                if (rows.next()) return id;
            }
        }
        res.sendError(403); return null;
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            if (authorised(req, res) == null) return;
            String root = req.getContextPath();
            StringBuilder html = new StringBuilder("<!doctype html><html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Admin access | UniNest</title><style>body{font:16px Arial,sans-serif;background:#f6f8fa;color:#202c35;margin:0}main{max-width:760px;margin:auto;padding:24px}article{padding:20px 0;border-bottom:1px solid #ccd3da}h1{font-size:28px}h2{font-size:20px;overflow-wrap:anywhere}p{overflow-wrap:anywhere}button,a{font:inherit}button{padding:12px 20px;cursor:pointer;margin:8px 8px 0 0;border:1px solid #19634b;border-radius:4px}button[value=approve]{background:#19634b;color:white}a{color:#145d86}</style></head><body><main><a href='")
                .append(root).append("/Admin/web/jsp/Dashboard.html'>Back to dashboard</a><h1>Admin access requests</h1><p>Approve only team members whose identity you have confirmed. Approved accounts can access administrative records and review other requests.</p>");
            if ("saved".equals(req.getParameter("result"))) html.append("<p role='status'>Decision saved. Approved members can now sign in.</p>");
            if ("changed".equals(req.getParameter("result"))) html.append("<p role='alert'>This request is no longer pending. Refresh before trying again.</p>");
            int count = 0;
            try (PreparedStatement q = DatabaseConnection.getInstance().getConnection().prepareStatement("SELECT u.user_id,u.full_name,u.email,u.created_at FROM users u JOIN admins a ON a.user_id=u.user_id WHERE u.role='admin' AND u.status='pending_verification' ORDER BY u.created_at,u.user_id"); ResultSet rows = q.executeQuery()) {
                while (rows.next()) {
                    count++;
                    html.append("<article><h2>").append(escape(rows.getString("full_name"))).append("</h2><p>")
                        .append(escape(rows.getString("email"))).append("</p><p>Requested: ").append(escape(rows.getString("created_at")))
                        .append("</p><form method='post' action='").append(root).append("/AdminAccessServlet'>")
                        .append(CsrfToken.field(req)).append("<input type='hidden' name='userId' value='").append(rows.getInt("user_id"))
                        .append("'><button name='decision' value='approve'>Approve</button><button name='decision' value='decline'>Decline</button></form></article>");
                }
            }
            if (count == 0) html.append("<p>No pending admin requests.</p>");
            html.append("</main></body></html>");
            res.setContentType("text/html;charset=UTF-8"); res.getWriter().print(html);
        } catch (SQLException e) { getServletContext().log("Admin request list failed", e); res.sendError(503); }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            Integer actor = authorised(req, res);
            if (actor == null) return;
            if (!CsrfToken.valid(req)) { res.sendError(403); return; }
            String decision = req.getParameter("decision");
            if (!"approve".equals(decision) && !"decline".equals(decision)) { res.sendError(400); return; }
            int target = Integer.parseInt(req.getParameter("userId"));
            boolean saved = AdminRegistration.decide(DatabaseConnection.getInstance().getConnection(), actor, target, "approve".equals(decision));
            res.sendRedirect(req.getContextPath() + "/AdminAccessServlet?result=" + (saved ? "saved" : "changed"));
        } catch (NumberFormatException e) { res.sendError(400); }
        catch (SQLException e) { getServletContext().log("Admin access decision failed", e); res.sendError(503); }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
