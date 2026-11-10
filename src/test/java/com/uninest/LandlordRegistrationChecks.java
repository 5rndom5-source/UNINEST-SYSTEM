package com.uninest;

import com.uninest.utils.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/** Real isolated MySQL; servlet request/response objects are test doubles. */
public final class LandlordRegistrationChecks {
    static int checks;
    static ServletContext context;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void sql(Connection c,String text) throws SQLException {LandlordApprovalChecks.sql(c,text);}
    static String value(Connection c,String text) throws SQLException {return LandlordApprovalChecks.value(c,text);}
    public static void main(String[] args) throws Exception {
        Connection c=DatabaseConnection.getInstance().getConnection();
        if(c==null || !c.getCatalog().startsWith("uninest_landlord_check_"))throw new IllegalStateException("Disposable database required");
        Path web=ImageStorage.directory("fixture-web");Files.createDirectories(web);
        context=(ServletContext)Proxy.newProxyInstance(ServletContext.class.getClassLoader(),new Class[]{ServletContext.class},(p,m,a)->m.getName().equals("getRealPath")?web.toString():null);
        for(boolean legacy:List.of(false,true)) {
            String prefix=legacy?"legacy":"shared";
            sql(c,"CREATE TRIGGER fail_registration BEFORE INSERT ON landlords FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rollback'");
            register(legacy,prefix+"-failed",null);
            check("0".equals(value(c,"SELECT COUNT(*) FROM users WHERE email='"+prefix+"-failed@example.invalid'")),prefix+" account rollback");
            check(c.getAutoCommit(),prefix+" transaction restored");
            sql(c,"DROP TRIGGER fail_registration");
            String redirect=register(legacy,prefix+"-success",null);
            check(java.net.URLDecoder.decode(redirect,"UTF-8").contains("Awaiting Admin approval"),prefix+" pending message");
            check("1".equals(value(c,"SELECT COUNT(*) FROM users u JOIN landlords l ON l.user_id=u.user_id WHERE u.email='"+prefix+"-success@example.invalid' AND u.status='pending_verification' AND l.approval_status='pending'")),prefix+" both records created pending");
            register(legacy,prefix+"-success",null);
            check("1".equals(value(c,"SELECT COUNT(*) FROM users WHERE email='"+prefix+"-success@example.invalid'")),prefix+" duplicate rejected");
        }
        Part pdf=part("%PDF-1.4\nSynthetic test document only".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        sql(c,"CREATE TRIGGER fail_document BEFORE INSERT ON documents FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture document rollback'");
        register(true,"document-failed",pdf);
        check("0".equals(value(c,"SELECT COUNT(*) FROM users WHERE email='document-failed@example.invalid'")),"document insert failure rolls back account");
        try(java.util.stream.Stream<Path> files=Files.list(ImageStorage.directory("landlord-documents"))) {check(files.count()==0,"failed registration file removed");}
        sql(c,"DROP TRIGGER fail_document");
        register(true,"document-success",pdf);
        String ref=value(c,"SELECT file_url FROM documents d JOIN users u ON u.user_id=d.user_id WHERE u.email='document-success@example.invalid'");
        check(ref.startsWith("private-landlord:"),"document reference is not a public URL");
        check(Files.exists(LandlordDocuments.resolve(ref,context)),"private document readable");
        check(!LandlordDocuments.resolve(ref,context).startsWith(web),"private document outside webroot");
        for(String unsafe:List.of("https://example.invalid/id.pdf","/uploads/../secret","/uploads/..\\secret","private-landlord:../../secret","/uploads/C:secret"))
            check(LandlordDocuments.resolve(unsafe,context)==null,"reject unsafe reference");
        boolean invalid=false;
        try{LandlordDocuments.save(part("<html>unsafe</html>".getBytes()),context);}catch(IOException expected){invalid=true;}
        check(invalid,"reject HTML upload");
        Path legacyDir=web.resolve("uploads");Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("legacy.pdf"),"%PDF-1.4 fixture");
        check(LandlordDocuments.resolve("/uploads/legacy.pdf",context)!=null,"existing document accessible through private resolver");
        check(LandlordDocuments.resolve("/uploads/missing.pdf",context)==null,"missing file not treated as evidence");
        // Fixtures for the real HTTP/browser checks. Only this disposable database is changed.
        sql(c,"UPDATE users SET role='landlord',status='pending_verification' WHERE user_id=10");
        sql(c,"UPDATE landlords SET approval_status='pending' WHERE user_id=10");
        sql(c,"INSERT INTO students(user_id) VALUES(11)");
        check("active".equals(value(c,"SELECT status FROM users WHERE user_id=5")),"fixture Admin status preserved");
        System.out.println("PASS: "+checks+" isolated MySQL registration/document checks");
        DatabaseConnection.getInstance().closeConnection();
    }

    static Part part(byte[] bytes) {
        return (Part)Proxy.newProxyInstance(Part.class.getClassLoader(),new Class[]{Part.class},(p,m,a)->{
            switch(m.getName()) {
                case "getSize":return (long)bytes.length;
                case "getInputStream":return new ByteArrayInputStream(bytes);
                case "getSubmittedFileName":return "../../identity.pdf";
                default:return null;
            }
        });
    }
    static String register(boolean legacy,String name,Part file) throws Exception {
        Map<String,String> values=new HashMap<>();
        values.put("fullName",name);values.put("email",name+"@example.invalid");values.put("password","Testing!2026");values.put("confirmPassword","Testing!2026");
        values.put("role","landlord");values.put("phone","0712345678");values.put("businessName","Fixture business");values.put("businessAddress","Fixture address");values.put("idNumber",name);
        HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->{
            switch(m.getName()) {
                case "getParameter":return values.get(a[0]);
                case "getPart":return file;
                case "getContextPath":return "/uninest";
                default:return null;
            }
        });
        String[] redirect={""};
        HttpServletResponse res=(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{
            if(m.getName().equals("sendRedirect"))redirect[0]=(String)a[0];return null;
        });
        HttpServlet servlet=legacy?new com.uninest.servlets.auth.RegisterServlet():new com.uninest.servlets.account.AccountRegisterServlet();
        ServletConfig config=(ServletConfig)Proxy.newProxyInstance(ServletConfig.class.getClassLoader(),new Class[]{ServletConfig.class},(p,m,a)->m.getName().equals("getServletContext")?context:null);
        servlet.init(config);
        Method method=servlet.getClass().getDeclaredMethod("doPost",HttpServletRequest.class,HttpServletResponse.class);
        method.setAccessible(true);method.invoke(servlet,req,res);return redirect[0];
    }
}
