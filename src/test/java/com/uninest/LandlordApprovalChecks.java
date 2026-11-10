package com.uninest;

import com.uninest.utils.*;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import javax.servlet.http.*;

/** Real MySQL checks; refuses any database without the disposable prefix. */
public final class LandlordApprovalChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void sql(Connection c,String sql) throws SQLException {try(Statement s=c.createStatement()){s.execute(sql);}}
    static String value(Connection c,String sql) throws SQLException {try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    public static void main(String[] args) throws Exception {
        Connection c=DatabaseConnection.getInstance().getConnection();
        if(c==null || !c.getCatalog().startsWith("uninest_landlord_check_"))throw new IllegalStateException("Disposable database required");
        String hash=PasswordUtil.hashPassword("Testing!2026");
        try(PreparedStatement q=c.prepareStatement("INSERT INTO users(user_id,full_name,email,password,role,status) VALUES(5,'Admin','admin@example.invalid',?,'admin','active'),(10,'Landlord','landlord@example.invalid',?,'landlord','pending_verification'),(11,'Student','student@example.invalid',?,'student','active')")) {
            for(int i=1;i<=3;i++)q.setString(i,hash);q.executeUpdate();
        }
        sql(c,"INSERT INTO admins(admin_id,user_id,admin_position,access_level) VALUES(1,5,'Administrator','super_admin')");
        sql(c,"INSERT INTO landlords(landlord_id,user_id,id_number,approval_status) VALUES(1,10,'TEST-ONLY','pending')");
        for(String status:List.of("active","pending_verification","inactive","suspended")) {
            for(String approval:List.of("pending","approved","rejected")) {
                sql(c,"UPDATE users SET status='"+status+"' WHERE user_id=10");
                sql(c,"UPDATE landlords SET approval_status='"+approval+"' WHERE user_id=10");
                boolean expected="active".equals(status)&&"approved".equals(approval);
                check(LandlordApproval.eligible(c,10)==expected,"eligibility "+status+"/"+approval);
                check(login(c,false)==expected,"shared login "+status+"/"+approval);
                check(login(c,true)==expected,"legacy login "+status+"/"+approval);
            }
        }
        sql(c,"UPDATE users SET status='pending_verification' WHERE user_id=10");
        sql(c,"UPDATE landlords SET approval_status='pending' WHERE user_id=10");
        check(!LandlordApproval.decide(c,11,10,true,"Test",true),"student cannot approve");
        check(!LandlordApproval.decide(c,10,10,true,"Test",true),"no self approval");
        check(!LandlordApproval.decide(c,5,11,true,"Test",true),"student target rejected");
        check(!LandlordApproval.decide(c,5,10,true," ",true),"reason required");
        sql(c,"UPDATE users SET status='inactive' WHERE user_id=5");
        check(!LandlordApproval.decide(c,5,10,true,"Test",true),"inactive admin denied");
        sql(c,"UPDATE users SET status='active' WHERE user_id=5");
        check(!LandlordApproval.decide(c,5,10,true,"Not verified",false),"independent verification required even for active admins");
        check(LandlordApproval.decide(c,5,10,true,"Identity checked",true),"approve pending");
        check(LandlordApproval.eligible(c,10),"approved active account");
        check(!LandlordApproval.decide(c,5,10,false,"Stale form",true),"stale rejection denied");
        check("2".equals(value(c,"SELECT COUNT(*) FROM notifications WHERE title='Landlord account decision'")),"actor and recipient audit records");
        check("active".equals(value(c,"SELECT status FROM users WHERE user_id=5")),"admin unchanged");
        sql(c,"UPDATE users SET status='pending_verification' WHERE user_id=10");
        sql(c,"UPDATE landlords SET approval_status='pending' WHERE user_id=10");
        check(LandlordApproval.decide(c,5,10,false,"Insufficient information",true),"reject pending");
        check("inactive".equals(value(c,"SELECT status FROM users WHERE user_id=10")),"rejection account status");
        check("rejected".equals(value(c,"SELECT approval_status FROM landlords WHERE user_id=10")),"rejection landlord status");
        check(AccountStatus.setByAdmin(c,5,10,true)==AccountStatus.Result.CHANGED,"generic reactivation");
        check(!login(c,false)&&!login(c,true),"reactivation does not bypass rejection");
        sql(c,"UPDATE users SET status='pending_verification' WHERE user_id=10");
        sql(c,"UPDATE landlords SET approval_status='pending' WHERE user_id=10");
        sql(c,"CREATE TRIGGER fail_decision BEFORE INSERT ON notifications FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        boolean failed=false;
        try{LandlordApproval.decide(c,5,10,true,"Rollback test",true);}catch(SQLException e){failed=true;}
        check(failed,"notification failure propagated");
        check("pending_verification".equals(value(c,"SELECT status FROM users WHERE user_id=10")),"user update rolled back");
        check("pending".equals(value(c,"SELECT approval_status FROM landlords WHERE user_id=10")),"approval update rolled back");
        sql(c,"DROP TRIGGER fail_decision");
        check(reviewRequest(null,null,null)==401,"anonymous review denied");
        check(reviewRequest(11,"student","valid")==403,"student review denied");
        check(reviewRequest(5,"admin",null)==403,"missing CSRF denied");
        check(reviewRequest(5,"admin","invalid")==403,"invalid CSRF denied");
        check(!LandlordApproval.eligible(c,10),"rejected requests left state unchanged");
        check(reviewRequest(5,"admin","valid")==302,"valid admin request accepted");
        check(LandlordApproval.eligible(c,10),"valid request updated both statuses");
        check(listingRequest()==302,"approved landlord can submit listing");
        check("1".equals(value(c,"SELECT COUNT(*) FROM rooms WHERE landlord_id=1 AND approval_status='pending'")),"listing created for review");
        sql(c,"UPDATE landlords SET approval_status='rejected' WHERE user_id=10");
        check(listingRequest()==403,"stale session cannot submit after rejection");
        check("1".equals(value(c,"SELECT COUNT(*) FROM rooms WHERE landlord_id=1")),"rejected submission created nothing");
        sql(c,"UPDATE users SET role='student' WHERE user_id=10");
        sql(c,"UPDATE landlords SET approval_status='approved' WHERE user_id=10");
        check(!login(c,false)&&!login(c,true),"changed role cannot use landlord login");
        System.out.println("PASS: "+checks+" isolated MySQL landlord approval/authentication checks");
        DatabaseConnection.getInstance().closeConnection();
    }
    static int listingRequest() throws Exception {
        Map<String,Object> attrs=new HashMap<>();attrs.put("userId",10);attrs.put("landlordId",1);attrs.put("role","landlord");
        HttpSession session=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class[]{HttpSession.class},(p,m,a)->{
            if(m.getName().equals("getAttribute"))return attrs.get(a[0]);
            if(m.getName().equals("setAttribute"))attrs.put((String)a[0],a[1]);
            return null;
        });
        HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->{
            switch(m.getName()) {
                case "getSession":return session;
                case "getContextPath":return "";
                case "getParts":return Collections.emptyList();
                case "getParameter":return Map.of("propertyTitle","Fixture listing","description","Fixture only","location","Test city","price","1000").get(a[0]);
                default:return null;
            }
        });
        int[] status={200};
        HttpServletResponse res=(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{
            if(m.getName().equals("sendError"))status[0]=(Integer)a[0];
            if(m.getName().equals("sendRedirect"))status[0]=302;
            return null;
        });
        Object servlet=new com.uninest.servlets.landlord.AddPropertyServlet();
        Method method=servlet.getClass().getDeclaredMethod("doPost",HttpServletRequest.class,HttpServletResponse.class);
        method.setAccessible(true);method.invoke(servlet,req,res);return status[0];
    }
    static int reviewRequest(Integer actor,String role,String token) throws Exception {
        Map<String,Object> attrs=new HashMap<>();attrs.put("userId",actor);attrs.put("role",role);
        String csrf="x".repeat(43);attrs.put("uninest.csrf",csrf);
        HttpSession session=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class[]{HttpSession.class},(p,m,a)->m.getName().equals("getAttribute")?attrs.get(a[0]):null);
        HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->{
            switch(m.getName()) {
                case "getSession":return actor==null?null:session;
                case "getHeader":return "valid".equals(token)?csrf:token;
                case "getMethod":return "POST";
                case "getContextPath":return "";
                case "getParameter":return Map.of("userId","10","decision","approve","reason","Servlet test","independentlyVerified","yes").get(a[0]);
                default:return null;
            }
        });
        int[] status={200};
        HttpServletResponse res=(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{
            if(m.getName().equals("sendError"))status[0]=(Integer)a[0];
            if(m.getName().equals("sendRedirect"))status[0]=302;
            return null;
        });
        Object servlet=new com.uninest.servlets.auth.LandlordApprovalServlet();
        Method method=servlet.getClass().getDeclaredMethod("doPost",HttpServletRequest.class,HttpServletResponse.class);
        method.setAccessible(true);method.invoke(servlet,req,res);return status[0];
    }
    static boolean login(Connection c,boolean legacy) throws Exception {
        Map<String,Object> session=new HashMap<>();
        HttpSession s=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class[]{HttpSession.class},(p,m,a)->{
            if(m.getName().equals("setAttribute")){session.put((String)a[0],a[1]);return null;}
            if(m.getName().equals("getAttribute"))return session.get(a[0]);
            return null;
        });
        HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->{
            if(m.getName().equals("getParameter"))return Map.of("email","landlord@example.invalid","password","Testing!2026","role","landlord").get(a[0]);
            if(m.getName().equals("getSession"))return a!=null && a.length==1 && Boolean.FALSE.equals(a[0])?null:s;
            if(m.getName().equals("getContextPath"))return "";
            return null;
        });
        if(legacy){
            Object servlet=new com.uninest.servlets.landlord.LandlordLoginServlet();
            Method method=servlet.getClass().getDeclaredMethod("authenticateFromDatabase",Connection.class,HttpServletRequest.class,String.class,String.class);
            method.setAccessible(true);return (Boolean)method.invoke(servlet,c,req,"landlord@example.invalid","Testing!2026");
        }
        HttpServletResponse res=(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->null);
        Object servlet=new com.uninest.servlets.account.AccountLoginServlet();
        Method method=servlet.getClass().getDeclaredMethod("doPost",HttpServletRequest.class,HttpServletResponse.class);
        method.setAccessible(true);method.invoke(servlet,req,res);
        return session.containsKey("landlordId");
    }
}
