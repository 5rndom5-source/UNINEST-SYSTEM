package com.uninest.utils;

import java.sql.*;

/** Public registration never grants active administrative access. */
public final class AdminRegistration {
    private AdminRegistration() { }
    public static boolean decide(Connection connection, int actor, int target, boolean approve) throws SQLException {
        if (actor == target) return false;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement q = connection.prepareStatement("SELECT u.user_id FROM users u JOIN admins a ON a.user_id=u.user_id WHERE u.user_id=? AND u.role='admin' AND u.status='active' FOR UPDATE")) {
                q.setInt(1, actor);
                try (ResultSet rows = q.executeQuery()) {
                    if (!rows.next()) { connection.rollback(); return false; }
                }
            }
            try (PreparedStatement q = connection.prepareStatement("UPDATE users u JOIN admins a ON a.user_id=u.user_id SET u.status=?,a.admin_position=? WHERE u.user_id=? AND u.role='admin' AND u.status='pending_verification'")) {
                q.setString(1, approve ? "active" : "inactive");
                q.setString(2, approve ? "Administrator" : "Access declined");
                q.setInt(3, target);
                if (q.executeUpdate() == 0) { connection.rollback(); return false; }
            }
            try (PreparedStatement q = connection.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,action_url) VALUES(?,'Admin access decision',?,'system_alert','/AdminAccessServlet')")) {
                q.setInt(1, actor);
                q.setString(2, "Admin user #" + target + (approve ? " approved" : " declined") + " by admin user #" + actor);
                q.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(autoCommit); }
    }
    public static int createPending(Connection connection,String name,String email,String password,String reason) throws SQLException {
        boolean autoCommit=connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int id;
            try(PreparedStatement statement=connection.prepareStatement(
                    "INSERT INTO users(full_name,email,password,role,status) VALUES(?,?,?,'admin','pending_verification')",Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1,name);statement.setString(2,email);statement.setString(3,PasswordUtil.hashPassword(password));
                statement.executeUpdate();
                try(ResultSet keys=statement.getGeneratedKeys()) {
                    if(!keys.next())throw new SQLException("Missing generated account ID");
                    id=keys.getInt(1);
                }
            }
            try(PreparedStatement statement=connection.prepareStatement(
                    "INSERT INTO admins(user_id,admin_position,access_level) VALUES(?,'Pending approval','moderator')")) {
                statement.setInt(1,id);statement.executeUpdate();
            }
            try(PreparedStatement statement=connection.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,action_url) SELECT user_id,'Admin access request',?,'system_alert','/AdminAccessServlet' FROM users WHERE role='admin' AND status='active'")) {
                statement.setString(1,name+" requested admin access using "+email+". Reason: "+reason);
                statement.executeUpdate();
            }
            connection.commit();return id;
        } catch(SQLException | RuntimeException error) {
            connection.rollback();throw error;
        } finally { connection.setAutoCommit(autoCommit); }
    }
}
