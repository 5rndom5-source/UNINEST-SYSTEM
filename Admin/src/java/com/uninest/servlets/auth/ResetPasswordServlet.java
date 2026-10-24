package com.uninest.servlets.auth;
import com.uninest.utils.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;

@WebServlet("/ResetPasswordServlet")
public class ResetPasswordServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        String token = request.getParameter("token");
        boolean reset = token != null && token.matches("[A-Za-z0-9_-]{43}");
        String base = request.getContextPath();
        response.getWriter().print("<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Password recovery | UniNest</title><style>body{font:16px system-ui;margin:0;background:#f4f7f8;color:#20372f}main{max-width:420px;margin:10vh auto;padding:24px}img{width:80px}label,input,button{display:block;box-sizing:border-box;width:100%;margin:12px 0}input,button{padding:12px;border:1px solid #81938e;border-radius:6px}button{background:#176b51;color:white;cursor:pointer}a{color:#176b51}</style><main>"
            + "<img alt='UniNest' src='" + base + "/Admin/web/uninest-logo.png'><h1>" + (reset ? "Choose a new password" : "Password recovery") + "</h1><p role='status'>"
            + escape(request.getParameter("message")) + "</p><form method='post' action='" + base
            + (reset ? "/ResetPasswordServlet'" : "/ForgotPasswordServlet'") + ">"
            + CsrfToken.field(request)
            + (reset ? "<input type='hidden' name='token' value='" + token + "'><label>New password<input type='password' name='password' minlength='8' maxlength='128' autocomplete='new-password' required></label><label>Confirm password<input type='password' name='confirm' minlength='8' maxlength='128' autocomplete='new-password' required></label><p>Use at least 8 characters, including uppercase, lowercase, a number and a symbol.</p>"
                : "<label>Email address<input type='email' name='email' autocomplete='email' required></label>")
            + "<button type='submit'>" + (reset ? "Reset password" : "Send recovery email") + "</button></form><a href='" + base + "/createAccount/login.html'>Back to login</a></main></html>");
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = request.getParameter("password"), token = request.getParameter("token");
        String message;
        if (password == null || password.length()>128 || !password.equals(request.getParameter("confirm")) || !PasswordUtil.isStrongPassword(password)) {
            message = "Passwords must match and meet the stated requirements.";
        } else {
            try {
                Connection conn = DatabaseConnection.getInstance().getConnection();
                if (conn == null) throw new IllegalStateException("Database unavailable");
                boolean success = RecoveryService.reset(conn, token, password);
                message = success ? "Your password has been changed. You can now sign in." : "This link has expired or was already used. Request a new recovery email.";
                if (success && request.getSession(false) != null) request.getSession(false).invalidate();
                token = null;
            } catch (Exception e) {
                getServletContext().log("Password reset failed", e);
                message = "Password recovery is temporarily unavailable. Please try again later.";
            }
        }
        response.sendRedirect(request.getContextPath()+"/ResetPasswordServlet?message="+java.net.URLEncoder.encode(message,"UTF-8")
                +(token != null && token.matches("[A-Za-z0-9_-]{43}") ? "&token="+token : ""));
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}
