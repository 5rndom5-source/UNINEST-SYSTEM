package com.uninest.servlets.system;

import com.uninest.utils.DatabaseConnection;
import com.uninest.utils.PortalAccess;
import com.uninest.utils.LoginThrottle;
import com.uninest.utils.CsrfToken;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;
import java.io.IOException;

@WebFilter("/*")
public class RequestLifecycleFilter implements Filter {
    private final LoginThrottle loginThrottle;
    public RequestLifecycleFilter() { this(new LoginThrottle()); }
    public RequestLifecycleFilter(LoginThrottle loginThrottle) { this.loginThrottle=loginThrottle; }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "same-origin");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())
                && "cross-site".equals(req.getHeader("Sec-Fetch-Site"))) {
            res.sendError(403, "Cross-site request rejected");
            return;
        }
        try {
            String path = req.getServletPath();
            if(CsrfToken.unsafe(req.getMethod()) && !CsrfToken.valid(req)) {
                res.setStatus(403);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().print("{\"error\":\"Your security token is missing or expired. Reload the page and try again.\",\"message\":\"Reload the page before submitting again.\",\"code\":\"CSRF_INVALID\"}");
                return;
            }
            if ("POST".equals(req.getMethod()) && LoginThrottle.isLoginPath(path)) {
                String email=req.getParameter("email");
                if ((email==null || email.isEmpty()) && "/LandlordLoginServlet".equals(path)) email=req.getParameter("landlordEmail");
                // Only trust the connector's address; arbitrary forwarded headers are attacker-controlled.
                long retry=loginThrottle.retryAfterSeconds(req.getRemoteAddr(),email);
                if(retry>0) {
                    res.setStatus(429);
                    res.setHeader("Retry-After",Long.toString(retry));
                    res.setContentType("text/html;charset=UTF-8");
                    res.getWriter().print("<!doctype html><html lang='en'><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>Sign-in temporarily limited | UniNest</title>"
                            +"<main style='max-width:36rem;margin:4rem auto;padding:1rem;font-family:Arial,sans-serif;line-height:1.6'>"
                            +"<h1>Too many sign-in attempts</h1><p>Please wait "+retry+" seconds before trying again. Your account has not been permanently locked.</p>"
                            +"<p><a href='"+req.getContextPath()+"/createAccount/login.html'>Return to sign in</a></p>"
                            +"<p><a href='"+req.getContextPath()+"/ResetPasswordServlet'>Recover your password</a></p></main></html>");
                    return;
                }
            }
            if (PortalAccess.isLegacyLogout(path)) {
                res.sendRedirect(req.getContextPath() + "/LogoutServlet");
                return;
            }
            // Signing out must work even when the database or account is unavailable.
            if (PortalAccess.isLogout(path)) { chain.doFilter(request, response); return; }
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute("userId") instanceof Integer) {
                java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
                if (conn == null) { res.sendError(503); return; }
                try (java.sql.PreparedStatement query = conn.prepareStatement("SELECT password_changed_at,status,role FROM users WHERE user_id=?")) {
                    query.setInt(1,(Integer)session.getAttribute("userId"));
                    try(java.sql.ResultSet rows=query.executeQuery()) {
                        boolean invalid=!rows.next();
                        if(!invalid) {
                            java.sql.Timestamp changed=rows.getTimestamp(1);
                            invalid=(changed!=null && changed.getTime()>=session.getCreationTime())
                                    || "suspended".equals(rows.getString(2)) || "inactive".equals(rows.getString(2))
                                    || !rows.getString(3).equals(session.getAttribute("role"))
                                    || ("admin".equals(rows.getString(3)) && !"active".equals(rows.getString(2)));
                        }
                        if(invalid){
                            session.invalidate();
                            if (path.endsWith(".html") || path.endsWith("/")) {
                                res.sendRedirect(req.getContextPath()+"/createAccount/login.html?error=Please+sign+in+again");
                            } else { res.sendError(401,"Please sign in again"); }
                            return;
                        }
                    }
                } catch(java.sql.SQLException e){throw new ServletException("Session validation unavailable",e);}
            }
            String requiredRole = PortalAccess.requiredRole(path);
            if (requiredRole != null) {
                if (session == null || !(session.getAttribute("userId") instanceof Integer)) {
                    res.sendRedirect(req.getContextPath()+"/createAccount/login.html?error=Please+sign+in+to+continue");
                    return;
                }
                if (!requiredRole.equals(session.getAttribute("role"))) {
                    res.sendRedirect(req.getContextPath()+PortalAccess.home((String)session.getAttribute("role")));
                    return;
                }
            }
            chain.doFilter(request, response);
        }
        finally { DatabaseConnection.getInstance().closeConnection(); }
    }
}
