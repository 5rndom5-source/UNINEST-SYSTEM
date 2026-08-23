package com.uninest.servlets.auth;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/** Ends any UniNest portal session and returns to the appropriate login page. */
@WebServlet("/LogoutServlet")
public class LogoutServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("admin_token".equals(cookie.getName())) {
                    cookie.setMaxAge(0);
                    cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
                    response.addCookie(cookie);
                }
            }
        }

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        String loginPage = loginPage(request.getParameter("portal"));
        String separator = loginPage.contains("?") ? "&" : "?";
        response.sendRedirect(request.getContextPath() + loginPage
                + separator + "success="
                + java.net.URLEncoder.encode("You have signed out.", "UTF-8"));
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }

    private String loginPage(String portal) {
        if ("admin".equalsIgnoreCase(portal)) {
            return "/Admin/web/jsp/Login.html";
        }
        if ("landlord".equalsIgnoreCase(portal)) {
            return "/Landlord/login.html";
        }
        return "/createAccount/login.html?role=student";
    }
}
