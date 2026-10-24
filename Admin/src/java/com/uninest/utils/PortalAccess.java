package com.uninest.utils;

/** Routing policy for static portal pages and legacy logout bookmarks. */
public final class PortalAccess {
    private PortalAccess() { }
    public static boolean isLogout(String path) {
        return "/LogoutServlet".equals(path) || "/LandlordLogoutServlet".equals(path);
    }
    public static boolean isLegacyLogout(String path) {
        return "/Admin/web/jsp/Logout.html".equals(path)
                || "/Landlord/logout.html".equals(path)
                || "/Logout.html".equals(path) || "/LogoutPageServlet".equals(path);
    }
    public static String requiredRole(String path) {
        if (!path.endsWith(".html") && !path.endsWith("/")) return null;
        if (path.startsWith("/UserDashboard/")) return "student";
        if (path.startsWith("/Landlord/") && !path.equals("/Landlord/login.html")) return "landlord";
        if (path.startsWith("/Admin/web/jsp/")
                && !path.equals("/Admin/web/jsp/Login.html")
                && !path.equals("/Admin/web/jsp/Register.html")
                && !path.equals("/Admin/web/jsp/ForgotPassword.html")) return "admin";
        return null;
    }
    public static String home(String role) {
        if ("admin".equals(role)) return "/Admin/web/jsp/Dashboard.html";
        if ("landlord".equals(role)) return "/Landlord/index.html";
        if ("student".equals(role)) return "/UserDashboard/index.html";
        return "/createAccount/login.html";
    }
}
