package com.uninest.servlets.auth;

import com.uninest.utils.*;
import java.io.IOException;
import java.sql.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/LandlordApprovalServlet")
public class LandlordApprovalServlet extends HttpServlet {
    private Integer authorised(HttpServletRequest req,HttpServletResponse res) throws SQLException,IOException {
        HttpSession s=req.getSession(false);
        if(s==null || !(s.getAttribute("userId") instanceof Integer)){res.sendError(401);return null;}
        if(!"admin".equals(s.getAttribute("role"))){res.sendError(403);return null;}
        Connection c=DatabaseConnection.getInstance().getConnection();
        if(c==null){res.sendError(503);return null;}
        int id=(Integer)s.getAttribute("userId");
        try(PreparedStatement q=c.prepareStatement("SELECT u.user_id FROM users u JOIN admins a ON a.user_id=u.user_id WHERE u.user_id=? AND u.role='admin' AND u.status='active'")) {
            q.setInt(1,id);
            try(ResultSet r=q.executeQuery()){if(r.next())return id;}
        }
        res.sendError(403);return null;
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws IOException {
        try {
            if(authorised(req,res)==null)return;
            Connection c=DatabaseConnection.getInstance().getConnection();
            String root=req.getContextPath();
            StringBuilder h=new StringBuilder("<!doctype html><html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Landlord account review | UniNest</title><style>body{margin:0;font:16px Arial,sans-serif;color:#202c35;background:#f6f8fa}main{max-width:960px;margin:auto;padding:24px}article{border-bottom:1px solid #ccd3da;padding:20px 0}h1{font-size:26px}h2{font-size:20px}p,td{overflow-wrap:anywhere}textarea{display:block;box-sizing:border-box;width:100%;min-height:80px;margin:8px 0}button{min-height:44px;padding:8px 16px;margin:8px 8px 0 0;cursor:pointer}button[value=approve]{background:#176b51;color:white;border:1px solid #176b51}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:10px;border-bottom:1px solid #ccc}</style></head><body><main><a href='")
                    .append(root).append("/Admin/web/jsp/Users.html'>Back to users</a><h1>Landlord account review</h1>");
            String result=req.getParameter("result");
            if("approved".equals(result))h.append("<p role='status'>Landlord approved. The active account can now sign in and submit listings.</p>");
            if("rejected".equals(result))h.append("<p role='status'>Landlord rejected. Sign-in remains blocked.</p>");
            if("changed".equals(result))h.append("<p role='alert'>No change made. The request is no longer pending or the account is ineligible. Refresh the queue.</p>");
            int count=0;
            try(PreparedStatement q=c.prepareStatement("SELECT u.user_id,u.full_name,u.email,u.status,l.business_name,l.business_address FROM users u JOIN landlords l ON l.user_id=u.user_id WHERE u.role='landlord' AND u.status IN ('active','pending_verification') AND l.approval_status='pending' ORDER BY u.created_at,u.user_id");ResultSet r=q.executeQuery()) {
                while(r.next()) {
                    count++;
                    h.append("<article><h2>").append(e(r.getString("full_name"))).append("</h2><p>").append(e(r.getString("email")))
                     .append("</p><p>Business: ").append(e(r.getString("business_name"))).append("</p><p>Address: ").append(e(r.getString("business_address")))
                     .append("</p><p>Account: ").append(e(r.getString("status"))).append(" | Approval: pending</p><form method='post'>")
                     .append(CsrfToken.field(req)).append("<input type='hidden' name='userId' value='").append(r.getInt("user_id"))
                     .append("'><label>Decision reason<textarea name='reason' required maxlength='500'></textarea></label><button name='decision' value='approve'>Approve landlord</button><button name='decision' value='reject'>Reject landlord</button></form></article>");
                }
            }
            if(count==0)h.append("<p>No pending landlord accounts.</p>");
            h.append("<h2>Recent decisions</h2><table><thead><tr><th>Date</th><th>Decision record</th></tr></thead><tbody>");
            try(PreparedStatement q=c.prepareStatement("SELECT n.created_at,n.message FROM notifications n JOIN users u ON u.user_id=n.user_id WHERE n.title='Landlord account decision' AND n.action_url='/LandlordApprovalServlet' AND u.role='admin' ORDER BY n.notification_id DESC LIMIT 50");ResultSet r=q.executeQuery()) {
                while(r.next())h.append("<tr><td>").append(e(r.getString(1))).append("</td><td>").append(e(r.getString(2))).append("</td></tr>");
            }
            res.setContentType("text/html;charset=UTF-8");res.setHeader("Cache-Control","no-store");
            res.getWriter().print(h.append("</tbody></table></main></body></html>"));
        }catch(SQLException ex){getServletContext().log("Landlord review unavailable",ex);res.sendError(503);}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws IOException {
        try {
            Integer actor=authorised(req,res);if(actor==null)return;
            if(!CsrfToken.valid(req)){res.sendError(403);return;}
            String decision=req.getParameter("decision"),reason=req.getParameter("reason");
            if(!("approve".equals(decision)||"reject".equals(decision)) || reason==null || reason.trim().isEmpty() || reason.length()>500){res.sendError(400,"Choose a decision and enter a reason (maximum 500 characters)");return;}
            int target=Integer.parseInt(req.getParameter("userId"));
            boolean saved=LandlordApproval.decide(DatabaseConnection.getInstance().getConnection(),actor,target,"approve".equals(decision),reason);
            res.sendRedirect(req.getContextPath()+"/LandlordApprovalServlet?result="+(saved?("approve".equals(decision)?"approved":"rejected"):"changed"));
        }catch(NumberFormatException ex){res.sendError(400);}
        catch(SQLException ex){getServletContext().log("Landlord decision failed",ex);res.sendError(503);}
    }
    private static String e(String value){return value==null?"Not supplied":ValidationUtil.sanitizeXSS(value);}
}
