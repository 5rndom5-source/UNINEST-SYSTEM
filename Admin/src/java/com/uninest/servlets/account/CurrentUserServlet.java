package com.uninest.servlets.account;

import com.uninest.utils.DatabaseConnection;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/CurrentUserServlet")
public class CurrentUserServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(response.getWriter(), false, 0, "", "", "", "");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");
        try {
            Connection connection = DatabaseConnection.getInstance().getConnection();
            if (connection == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                writeJson(response.getWriter(), false, userId, "", "", "", "");
                return;
            }

            String sql = "SELECT full_name, email, phone, role FROM users WHERE user_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        session.invalidate();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        writeJson(response.getWriter(), false, 0, "", "", "", "");
                        return;
                    }

                    String name = result.getString("full_name");
                    String email = result.getString("email");
                    String phone = result.getString("phone");
                    String role = result.getString("role");
                    session.setAttribute("fullName", name);
                    session.setAttribute("email", email);
                    session.setAttribute("role", role);
                    if ("admin".equalsIgnoreCase(role)) {
                        session.setAttribute("adminName", name);
                    } else if ("landlord".equalsIgnoreCase(role)) {
                        session.setAttribute("landlordName", name);
                        session.setAttribute("landlordEmail", email);
                    } else if ("student".equalsIgnoreCase(role)) {
                        session.setAttribute("studentName", name);
                    }
                    writeJson(response.getWriter(), true, userId, name, email, phone, role);
                }
            }
        } catch (SQLException exception) {
            getServletContext().log("Unable to load the signed-in user", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeJson(response.getWriter(), false, userId, "", "", "", "");
        }
    }

    private void writeJson(PrintWriter writer, boolean authenticated, int userId,
                           String name, String email, String phone, String role) {
        writer.print("{\"authenticated\":" + authenticated
                + ",\"userId\":" + userId
                + ",\"demoPayments\":" + "true".equalsIgnoreCase(System.getenv("UNINEST_DEMO_PAYMENTS"))
                + ",\"fullName\":\"" + escape(name) + "\""
                + ",\"email\":\"" + escape(email) + "\""
                + ",\"phone\":\"" + escape(phone) + "\""
                + ",\"role\":\"" + escape(role) + "\"}");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
