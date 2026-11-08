package com.uninest.servlets.auth;

import com.uninest.utils.AccountStatus;
import com.uninest.utils.DatabaseConnection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * CloseAccountServlet - Lets a signed-in student, landlord or admin close their own account.
 * Path: /CloseAccountServlet
 * The account is set to inactive after the current password is confirmed, then the session ends.
 */
@WebServlet("/CloseAccountServlet")
public class CloseAccountServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Integer)) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, false, "Please sign in again");
            return;
        }
        String password = request.getParameter("password");
        if (password == null || password.isEmpty() || password.length() > 128) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, false, "Enter your current password");
            return;
        }
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                send(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "Service is temporarily unavailable");
                return;
            }
            AccountStatus.Result result = AccountStatus.closeOwn(conn, (Integer) session.getAttribute("userId"), password);
            switch (result) {
                case CHANGED:
                    session.invalidate();
                    send(response, HttpServletResponse.SC_OK, true, "Your account has been closed");
                    break;
                case WRONG_PASSWORD:
                    send(response, HttpServletResponse.SC_FORBIDDEN, false, "That password is not correct");
                    break;
                case LAST_ADMIN:
                    send(response, HttpServletResponse.SC_CONFLICT, false,
                            "You are the only active admin. Approve another admin before closing this account.");
                    break;
                default:
                    send(response, HttpServletResponse.SC_CONFLICT, false, "This account cannot be closed");
            }
        } catch (SQLException e) {
            getServletContext().log("Unable to close account", e);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Your account could not be closed");
        }
    }

    private static void send(HttpServletResponse response, int status, boolean success, String message)
            throws IOException {
        response.setStatus(status);
        response.getWriter().print("{\"success\":" + success + ",\"message\":" + AdminUsersServlet.json(message) + "}");
    }
}
