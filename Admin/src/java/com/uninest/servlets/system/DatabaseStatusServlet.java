package com.uninest.servlets.system;

import com.uninest.utils.DatabaseConnection;

import java.io.IOException;
import java.time.Instant;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/DatabaseStatusServlet")
public class DatabaseStatusServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        boolean connected = DatabaseConnection.getInstance().isAvailable();
        response.setStatus(connected ? 200 : 503);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        response.getWriter().print("{"
                + "\"connected\":" + connected + ","
                + "\"status\":\"" + (connected ? "online" : "offline") + "\","
                + "\"label\":\"" + (connected ? "Data services online" : "Data services unavailable") + "\","
                + "\"checkedAt\":\"" + Instant.now().toString() + "\""
                + "}");
    }
}
