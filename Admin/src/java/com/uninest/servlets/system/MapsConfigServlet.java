package com.uninest.servlets.system;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

/** Only the dedicated, website-restricted Embed browser key is published. */
@WebServlet("/MapsConfigServlet")
public class MapsConfigServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        String key=System.getenv("UNINEST_GOOGLE_MAPS_EMBED_KEY");
        boolean enabled=key!=null&&key.matches("[A-Za-z0-9_-]{20,100}");
        response.getWriter().print("{\"enabled\":"+enabled+",\"key\":\""+(enabled?key:"")+"\"}");
    }
}
