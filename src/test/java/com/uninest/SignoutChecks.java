package com.uninest;

import com.uninest.servlets.auth.LogoutServlet;
import com.uninest.servlets.landlord.LandlordLogoutServlet;
import com.uninest.servlets.system.RequestLifecycleFilter;
import com.uninest.utils.PortalAccess;
import java.lang.reflect.Proxy;
import java.util.*;
import javax.servlet.http.*;

/** Checks routing and session termination without touching application accounts. */
public class SignoutChecks {
    static int checks;
    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    @SuppressWarnings("unchecked")
    static <T> T mock(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    public static void main(String[] args) throws Exception {
        for (String context : new String[]{"", "/uninest"}) {
            for (String role : new String[]{"admin", "landlord", "student", "invalid"}) {
                boolean[] invalidated = {false};
                HttpSession session = mock(HttpSession.class, (p,m,a) -> {
                    if (m.getName().equals("invalidate")) invalidated[0] = true;
                    return null;
                });
                String[] target = {null};
                List<Cookie> cleared = new ArrayList<>();
                HttpServletRequest request = mock(HttpServletRequest.class, (p,m,a) -> {
                    switch(m.getName()) {
                        case "getMethod": return "POST";
                        case "getSession": return invalidated[0] ? null : session;
                        case "getContextPath": return context;
                        case "getParameter": return role;
                        case "getCookies": return new Cookie[]{new Cookie("JSESSIONID","old"), new Cookie("admin_token","old")};
                        default: return null;
                    }
                });
                HttpServletResponse response = mock(HttpServletResponse.class, (p,m,a) -> {
                    if(m.getName().equals("sendRedirect")) target[0]=(String)a[0];
                    if(m.getName().equals("addCookie")) cleared.add((Cookie)a[0]);
                    return null;
                });
                new LogoutServlet().service(request,response);
                check(invalidated[0],"session invalidated: "+role);
                check(target[0].startsWith(context+"/createAccount/login.html?signedOut=1&"),"neutral role chooser");
                check(cleared.size()==2 && cleared.stream().allMatch(c -> c.getMaxAge()==0),"cookies expired");
                new LandlordLogoutServlet().service(request,response);
                check(target[0].contains("signedOut=1"),"legacy and repeated logout");
            }
        }
        for(String path : new String[]{"/UserDashboard/index.html","/Landlord/listings.html","/Admin/web/jsp/Profile.html"}) {
            String[] target={null};
            HttpServletRequest request=mock(HttpServletRequest.class,(p,m,a)->{
                switch(m.getName()) {
                    case "getMethod": return "GET";
                    case "getServletPath": return path;
                    case "getContextPath": return "/uninest";
                    default: return null;
                }
            });
            HttpServletResponse response=mock(HttpServletResponse.class,(p,m,a)->{
                if(m.getName().equals("sendRedirect")) target[0]=(String)a[0];
                return null;
            });
            new RequestLifecycleFilter().doFilter(request,response,(q,s)->{throw new AssertionError("Anonymous portal access");});
            check(target[0].startsWith("/uninest/createAccount/login.html"),"anonymous page blocked");
        }
        check(PortalAccess.requiredRole("/Landlord/login.html")==null,"landlord login public");
        check(PortalAccess.requiredRole("/Admin/web/jsp/ForgotPassword.html")==null,"recovery public");
        check(PortalAccess.requiredRole("/createAccount/login.html")==null,"chooser public");
        check(PortalAccess.isLegacyLogout("/Admin/web/jsp/Logout.html"),"old admin logout intercepted");
        check(PortalAccess.home("student").equals("/UserDashboard/index.html"),"wrong-role home routing");
        System.out.println("PASS: "+checks+" sign-out and portal routing checks");
    }
}
