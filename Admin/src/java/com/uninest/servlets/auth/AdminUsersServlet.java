package com.uninest.servlets.auth;

import com.uninest.utils.AccountStatus;
import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.ValidationUtil;

import javax.servlet.ServletException;
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

/**
 * AdminUsersServlet - Lists accounts and lets admins deactivate or reactivate them.
 * Path: /AdminUsersServlet
 * GET returns the accounts as JSON; POST takes userId and action (deactivate or reactivate).
 */
@WebServlet("/AdminUsersServlet")
public class AdminUsersServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        HttpSession session = request.getSession(false);
        if (!isAdmin(session)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"users\":[],\"error\":\"Admin login required\"}");
            return;
        }
        int adminId = (Integer) session.getAttribute("userId");
        try (PrintWriter out = response.getWriter()) {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                out.print("{\"users\":[],\"error\":\"Service is temporarily unavailable\"}");
                return;
            }
            String query = "SELECT user_id, full_name, email, role, status, created_at, last_login "
                    + "FROM users ORDER BY created_at DESC LIMIT 500";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                out.print("{\"users\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.print(",");
                    first = false;
                    int id = rs.getInt("user_id");
                    out.print("{\"id\":" + id
                            + ",\"fullName\":" + json(rs.getString("full_name"))
                            + ",\"email\":" + json(rs.getString("email"))
                            + ",\"role\":" + json(rs.getString("role"))
                            + ",\"status\":" + json(rs.getString("status"))
                            + ",\"createdAt\":" + json(String.valueOf(rs.getTimestamp("created_at")))
                            + ",\"lastLogin\":" + json(rs.getTimestamp("last_login") == null ? "" : String.valueOf(rs.getTimestamp("last_login")))
                            + ",\"self\":" + (id == adminId) + "}");
                }
                out.print("]}");
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to load accounts", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"users\":[],\"error\":\"Accounts could not be loaded\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        HttpSession session = request.getSession(false);
        if (!isAdmin(session)) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, false, "Admin login required");
            return;
        }
        String userId = request.getParameter("userId");
        String action = request.getParameter("action");
        if (!ValidationUtil.isInteger(userId) || (!"deactivate".equals(action) && !"reactivate".equals(action))) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, false, "Choose an account and an action");
            return;
        }
        boolean activate = "reactivate".equals(action);
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                send(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "Service is temporarily unavailable");
                return;
            }
            AccountStatus.Result result = AccountStatus.setByAdmin(conn,
                    (Integer) session.getAttribute("userId"), Integer.parseInt(userId), activate);
            switch (result) {
                case CHANGED:
                    send(response, HttpServletResponse.SC_OK, true, activate ? "Account reactivated" : "Account deactivated");
                    break;
                case NOT_FOUND:
                    send(response, HttpServletResponse.SC_NOT_FOUND, false, "Account not found");
                    break;
                default:
                    send(response, HttpServletResponse.SC_CONFLICT, false, activate
                            ? "Only inactive accounts can be reactivated"
                            : "This account cannot be deactivated (your own account, or it is already inactive or suspended)");
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to change account status", e);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Account status could not be changed");
        }
    }

    private static void prepare(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
    }

    private static boolean isAdmin(HttpSession session) {
        return session != null && session.getAttribute("userId") instanceof Integer
                && "admin".equals(session.getAttribute("role"));
    }

    private static void send(HttpServletResponse response, int status, boolean success, String message)
            throws IOException {
        response.setStatus(status);
        response.getWriter().print("{\"success\":" + success + ",\"message\":" + json(message) + "}");
    }

    static String json(String value) {
        if (value == null) return "\"\"";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32 || c == '<' || c == '>') out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
