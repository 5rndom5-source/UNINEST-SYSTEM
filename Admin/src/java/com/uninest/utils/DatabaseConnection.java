package com.uninest.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Central MySQL connection helper used by the UniNest servlets.
 */
public class DatabaseConnection {

    private static final String DB_URL = environment("UNINEST_DB_URL",
            "jdbc:mysql://localhost:3306/uninest_db"
                    + "?useSSL=false"
                    + "&serverTimezone=Africa/Johannesburg"
                    + "&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=5000"
                    + "&socketTimeout=5000");
    private static final String DB_USER = environment("UNINEST_DB_USER", "root");
    private static final String DB_PASSWORD = environment("UNINEST_DB_PASSWORD", "");
    private static final String DB_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static DatabaseConnection instance;
    private Connection connection;

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private DatabaseConnection() {
        try {
            Class.forName(DB_DRIVER);
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver was not found: " + e.getMessage());
        }
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                System.out.println("Database connection established");
            }
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
        return connection;
    }

    public synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }

    public synchronized boolean isAvailable() {
        try {
            Connection conn = getConnection();
            if (conn == null || conn.isClosed()) {
                return false;
            }

            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1");
                 ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            closeConnection();
            System.err.println("Database availability check failed: " + e.getMessage());
            return false;
        }
    }

    public static void testConnection() {
        try {
            Connection conn = getInstance().getConnection();
            if (conn != null && !conn.isClosed()) {
                System.out.println("Database connection test passed");
            } else {
                System.out.println("Database connection test failed");
            }
        } catch (SQLException e) {
            System.err.println("Connection test error: " + e.getMessage());
        }
    }
}
