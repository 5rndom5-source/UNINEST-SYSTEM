package com.uninest.servlets.properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * AddPropertyServlet is intentionally blocked in the Admin portal.
 *
 * Landlords submit properties from the landlord/student-facing workflow.
 * Admin users should verify submitted listings through VerifyPropertyServlet.
 */
@WebServlet("/AddPropertyServlet")
public class AddPropertyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath()
                + "/AddProperty.html?error="
                + java.net.URLEncoder.encode(
                    "Admins verify landlord-submitted listings and cannot add properties from this portal.",
                    "UTF-8"));
    }
}
