package com.uninest.utils;

import java.sql.*;

/**
 * Accounts are deactivated, never deleted: bookings, payments and messages stay on record.
 * Deleting a users row would cascade into other people's bookings and payments.
 * Inactive accounts cannot sign in, and RequestLifecycleFilter ends their open sessions.
 */
public final class AccountStatus {
    private AccountStatus() { }

    public enum Result { CHANGED, NOT_ALLOWED, NOT_FOUND, WRONG_PASSWORD, LAST_ADMIN }

    /** An admin may deactivate any other active or pending account, and reactivate inactive ones. */
    public static boolean adminMayChange(int actorId, int targetId, String targetStatus, boolean activate) {
        if (actorId == targetId) return false;
        return activate
                ? "inactive".equals(targetStatus)
                : "active".equals(targetStatus) || "pending_verification".equals(targetStatus);
    }

    /** The last active admin cannot close their own account, or nobody could approve or support anyone. */
    public static boolean mayCloseOwn(String role, String status, int otherActiveAdmins) {
        if (!"active".equals(status) && !"pending_verification".equals(status)) return false;
        return !"admin".equals(role) || !"active".equals(status) || otherActiveAdmins > 0;
    }

    public static Result setByAdmin(Connection connection, int actorId, int targetId, boolean activate)
            throws SQLException {
        if (actorId == targetId) return Result.NOT_ALLOWED;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement q = connection.prepareStatement(
                    "SELECT u.user_id FROM users u JOIN admins a ON a.user_id=u.user_id "
                            + "WHERE u.user_id=? AND u.role='admin' AND u.status='active' FOR UPDATE")) {
                q.setInt(1, actorId);
                try (ResultSet rows = q.executeQuery()) {
                    if (!rows.next()) { connection.rollback(); return Result.NOT_ALLOWED; }
                }
            }
            String status;
            String email;
            try (PreparedStatement q = connection.prepareStatement(
                    "SELECT status, email FROM users WHERE user_id=? FOR UPDATE")) {
                q.setInt(1, targetId);
                try (ResultSet rows = q.executeQuery()) {
                    if (!rows.next()) { connection.rollback(); return Result.NOT_FOUND; }
                    status = rows.getString(1);
                    email = rows.getString(2);
                }
            }
            if (!adminMayChange(actorId, targetId, status, activate)) {
                connection.rollback();
                return Result.NOT_ALLOWED;
            }
            try (PreparedStatement q = connection.prepareStatement("UPDATE users SET status=? WHERE user_id=?")) {
                q.setString(1, activate ? "active" : "inactive");
                q.setInt(2, targetId);
                q.executeUpdate();
            }
            try (PreparedStatement q = connection.prepareStatement(
                    "INSERT INTO notifications(user_id,title,message,notification_type,action_url) "
                            + "VALUES(?,'Account status changed',?,'system_alert','Admin/web/jsp/Users.html')")) {
                q.setInt(1, actorId);
                q.setString(2, email + (activate ? " was reactivated" : " was deactivated") + " by admin user #" + actorId);
                q.executeUpdate();
            }
            connection.commit();
            return Result.CHANGED;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public static Result closeOwn(Connection connection, int userId, String password) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String role;
            String status;
            String hash;
            try (PreparedStatement q = connection.prepareStatement(
                    "SELECT role, status, password FROM users WHERE user_id=? FOR UPDATE")) {
                q.setInt(1, userId);
                try (ResultSet rows = q.executeQuery()) {
                    if (!rows.next()) { connection.rollback(); return Result.NOT_FOUND; }
                    role = rows.getString(1);
                    status = rows.getString(2);
                    hash = rows.getString(3);
                }
            }
            if (password == null || hash == null || !PasswordUtil.verifyPassword(password, hash)) {
                connection.rollback();
                return Result.WRONG_PASSWORD;
            }
            int otherAdmins = 0;
            if ("admin".equals(role)) {
                try (PreparedStatement q = connection.prepareStatement(
                        "SELECT COUNT(*) FROM users u JOIN admins a ON a.user_id=u.user_id "
                                + "WHERE u.role='admin' AND u.status='active' AND u.user_id<>? FOR UPDATE")) {
                    q.setInt(1, userId);
                    try (ResultSet rows = q.executeQuery()) { rows.next(); otherAdmins = rows.getInt(1); }
                }
            }
            if (!mayCloseOwn(role, status, otherAdmins)) {
                connection.rollback();
                return "admin".equals(role) ? Result.LAST_ADMIN : Result.NOT_ALLOWED;
            }
            try (PreparedStatement q = connection.prepareStatement("UPDATE users SET status='inactive' WHERE user_id=?")) {
                q.setInt(1, userId);
                q.executeUpdate();
            }
            connection.commit();
            return Result.CHANGED;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
