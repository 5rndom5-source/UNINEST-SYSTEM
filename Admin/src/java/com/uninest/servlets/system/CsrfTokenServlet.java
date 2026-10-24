package com.uninest.servlets.system;
import com.uninest.utils.CsrfToken;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/CsrfTokenServlet")
public class CsrfTokenServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        if("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {response.sendError(403);return;}
        response.setHeader("Cache-Control","no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print("{\"token\":\""+CsrfToken.issue(request)+"\"}");
    }
}
