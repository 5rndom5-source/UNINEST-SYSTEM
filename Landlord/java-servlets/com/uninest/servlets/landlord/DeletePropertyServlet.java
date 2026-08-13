package com.uninest.servlets.landlord;

import com.uninest.utils.DatabaseConnection;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@WebServlet("/LandlordDeletePropertyServlet")
public class DeletePropertyServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("landlordId") == null
                || !"landlord".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"status\":\"error\",\"message\":\"Landlord login required\"}");
            return;
        }

        int roomId;
        try {
            roomId = Integer.parseInt(request.getParameter("roomId"));
        } catch (Exception exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"status\":\"error\",\"message\":\"Invalid listing\"}");
            return;
        }

        try {
            Connection connection = DatabaseConnection.getInstance().getConnection();
            if (connection == null) throw new SQLException("Database unavailable");
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE r FROM rooms r WHERE r.room_id = ? AND r.landlord_id = ? "
                            + "AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id = r.room_id)")) {
                statement.setInt(1, roomId);
                statement.setInt(2, (Integer) session.getAttribute("landlordId"));
                if (statement.executeUpdate() != 1) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().print("{\"status\":\"error\",\"message\":\"Listing not found or has booking history\"}");
                    return;
                }
            }
            response.getWriter().print("{\"status\":\"success\",\"message\":\"Listing deleted\"}");
        } catch (SQLException exception) {
            getServletContext().log("Unable to delete landlord listing", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"status\":\"error\",\"message\":\"Listing could not be deleted\"}");
        }
    }
}
