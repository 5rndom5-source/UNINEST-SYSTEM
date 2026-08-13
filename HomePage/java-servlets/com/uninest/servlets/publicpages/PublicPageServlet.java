package com.uninest.servlets.publicpages;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * PublicPageServlet - Optional servlet routes for static HomePage screens.
 */
@WebServlet({"/HomeServlet", "/SearchPageServlet", "/AboutPageServlet", "/PublicContactPageServlet"})
public class PublicPageServlet extends HttpServlet {

    private static final Map<String, String> PAGES = new HashMap<String, String>();

    static {
        PAGES.put("/HomeServlet", "/HomePage/home.html");
        PAGES.put("/SearchPageServlet", "/HomePage/search.html");
        PAGES.put("/AboutPageServlet", "/HomePage/about.html");
        PAGES.put("/PublicContactPageServlet", "/HomePage/contact.html");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String page = PAGES.get(request.getServletPath());
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.getRequestDispatcher(page).forward(request, response);
    }
}
