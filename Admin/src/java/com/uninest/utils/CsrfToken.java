package com.uninest.utils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.*;

public final class CsrfToken {
    private static final String ATTRIBUTE="uninest.csrf";
    private static final SecureRandom RANDOM=new SecureRandom();
    private CsrfToken() { }
    public static String issue(HttpServletRequest request) {
        HttpSession session=request.getSession(true);
        synchronized(session) {
            String token=(String)session.getAttribute(ATTRIBUTE);
            if(token==null) {
                byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
                token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(ATTRIBUTE,token);
            }
            return token;
        }
    }
    public static boolean valid(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        if(session==null)return false;
        Object expected=session.getAttribute(ATTRIBUTE);
        String supplied=request.getHeader("X-CSRF-Token");
        // Tokens in URLs leak through logs and must never authorize a request.
        if(request.getQueryString()!=null) {
            try {
                for(String parameter:request.getQueryString().split("&")) {
                    if("_csrf".equalsIgnoreCase(java.net.URLDecoder.decode(parameter.split("=",2)[0],StandardCharsets.UTF_8)))return false;
                }
            } catch(IllegalArgumentException e) {return false;}
        }
        if(supplied==null && "POST".equals(request.getMethod())) {
            try { supplied=request.getParameter("_csrf"); }
            catch(IllegalStateException e) { return false; }
        }
        return expected instanceof String && supplied!=null && supplied.length()==43
                && MessageDigest.isEqual(((String)expected).getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII));
    }
    public static String field(HttpServletRequest request) {
        return "<input type='hidden' name='_csrf' value='"+issue(request)+"'>";
    }
    public static boolean unsafe(String method) {
        return !("GET".equals(method)||"HEAD".equals(method)||"OPTIONS".equals(method));
    }
}
