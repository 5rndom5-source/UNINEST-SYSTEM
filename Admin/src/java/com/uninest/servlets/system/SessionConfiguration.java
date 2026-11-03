package com.uninest.servlets.system;

import java.util.EnumSet;
import javax.servlet.*;
import javax.servlet.annotation.WebListener;

/** Never expose session identifiers in URLs; secure cookies on configured HTTPS deployments. */
@WebListener
public class SessionConfiguration implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        context.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
        context.getSessionCookieConfig().setHttpOnly(true);
        String publicUrl = System.getenv("UNINEST_PUBLIC_URL");
        if (publicUrl != null && publicUrl.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            context.getSessionCookieConfig().setSecure(true);
        }
    }
}
