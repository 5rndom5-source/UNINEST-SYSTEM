package com.uninest.servlets.auth;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/** Ends any UniNest portal session and returns to the shared role chooser. */
@WebServlet("/LogoutServlet")
public class LogoutServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().print("<!doctype html><html lang='en'><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Sign out | UniNest</title>"
                +"<main style='max-width:32rem;margin:4rem auto;padding:1rem;font-family:Arial,sans-serif'><h1>Sign out</h1>"
                +"<form method='post' action='"+request.getContextPath()+"/LogoutServlet'>"
                +com.uninest.utils.CsrfToken.field(request)+"<button type='submit'>Sign out and choose a role</button></form></main></html>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("admin_token".equals(cookie.getName()) || "JSESSIONID".equals(cookie.getName())) {
                    cookie.setMaxAge(0);
                    cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
                    response.addCookie(cookie);
                }
            }
        }

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.sendRedirect(request.getContextPath() + "/createAccount/login.html?signedOut=1&success="
                + java.net.URLEncoder.encode("You have signed out.", "UTF-8"));
    }
    
}
