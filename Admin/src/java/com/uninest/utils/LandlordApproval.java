package com.uninest.utils;

import java.sql.*;

public final class LandlordApproval {
    private LandlordApproval() { }
    public static boolean eligible(String status, String approval) {
        return "active".equals(status) && "approved".equals(approval);
    }
    public static boolean eligible(Connection c, int userId) throws SQLException {
        try (PreparedStatement q=c.prepareStatement("SELECT u.status,l.approval_status FROM users u JOIN landlords l ON l.user_id=u.user_id WHERE u.user_id=? AND u.role='landlord'")) {
            q.setInt(1,userId);
            try(ResultSet r=q.executeQuery()){return r.next() && eligible(r.getString(1),r.getString(2));}
        }
    }
    public static boolean decide(Connection c,int actor,int target,boolean approve,String reason,boolean independentlyVerified) throws SQLException {
        if(actor==target || reason==null || reason.trim().isEmpty() || reason.length()>500) return false;
        if(approve && !independentlyVerified) return false;
        if(!c.getAutoCommit()) throw new SQLException("Approval requires its own transaction");
        c.setAutoCommit(false);
        try {
            try(PreparedStatement q=c.prepareStatement("SELECT u.user_id FROM users u JOIN admins a ON a.user_id=u.user_id WHERE u.user_id=? AND u.role='admin' AND u.status='active' FOR UPDATE")) {
                q.setInt(1,actor);
                try(ResultSet r=q.executeQuery()){if(!r.next()){c.rollback();return false;}}
            }
            try(PreparedStatement q=c.prepareStatement("SELECT u.status,l.approval_status FROM users u JOIN landlords l ON l.user_id=u.user_id WHERE u.user_id=? AND u.role='landlord' FOR UPDATE")) {
                q.setInt(1,target);
                try(ResultSet r=q.executeQuery()) {
                    if(!r.next() || !("pending_verification".equals(r.getString(1)) || "active".equals(r.getString(1))) || !"pending".equals(r.getString(2))) {c.rollback();return false;}
                }
            }
            try(PreparedStatement q=c.prepareStatement("UPDATE users SET status=? WHERE user_id=?")) {
                q.setString(1,approve?"active":"inactive");q.setInt(2,target);q.executeUpdate();
            }
            try(PreparedStatement q=c.prepareStatement("UPDATE landlords SET approval_status=?,verification_date=NOW() WHERE user_id=?")) {
                q.setString(1,approve?"approved":"rejected");q.setInt(2,target);q.executeUpdate();
            }
            String message="Landlord user #"+target+(approve?" approved":" rejected")+" by admin user #"+actor
                    +(approve?". Independent identity and letting-authority checks confirmed":"")+". Reason: "+reason.trim();
            try(PreparedStatement q=c.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,action_url) VALUES(?,'Landlord account decision',?,'system_alert',?)")) {
                q.setInt(1,actor);q.setString(2,message);q.setString(3,"/LandlordApprovalServlet");q.executeUpdate();
                q.setInt(1,target);q.setString(3,"/createAccount/login.html?role=landlord");q.executeUpdate();
            }
            c.commit();return true;
        } catch(SQLException | RuntimeException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
}
