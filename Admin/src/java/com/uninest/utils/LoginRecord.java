package com.uninest.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Records users.last_login after a successful sign-in on any login route (shown on the admin Users page). */
public final class LoginRecord {
    private LoginRecord() { }

    /** Never blocks a sign-in: a failure to record the time is logged and ignored. */
    public static void recordLastLogin(Connection connection, int userId) {
        try (PreparedStatement stmt = connection.prepareStatement("UPDATE users SET last_login = NOW() WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not record last login for user " + userId + ": " + e.getMessage());
        }
    }
}
