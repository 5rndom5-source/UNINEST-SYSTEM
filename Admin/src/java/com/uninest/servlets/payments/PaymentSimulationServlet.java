package com.uninest.servlets.payments;

import com.uninest.utils.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

@WebServlet("/PaymentSimulationServlet")
public class PaymentSimulationServlet extends HttpServlet {
    private Integer user(HttpServletRequest request,HttpServletResponse response) throws IOException {
        if(!"true".equalsIgnoreCase(System.getenv("UNINEST_DEMO_PAYMENTS"))){response.sendError(404);return null;}
        HttpSession session=request.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Integer)
                || !("student".equals(session.getAttribute("role"))||"landlord".equals(session.getAttribute("role")))) {
            response.sendRedirect(request.getContextPath()+"/createAccount/login.html");return null;
        }
        return (Integer)session.getAttribute("userId");
    }
    private static String e(String value){return value==null?"":ValidationUtil.sanitizeXSS(value);}
    private static String amount(BigDecimal value){return "R"+value.setScale(2).toPlainString();}
    private static String form(HttpServletRequest request,int booking,String action) {
        return "<form method='post'>"+CsrfToken.field(request)+"<input type='hidden' name='bookingId' value='"+booking+"'><input type='hidden' name='action' value='"+action+"'>";
    }
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws IOException {
        Integer user=user(request,response);if(user==null)return;
        boolean landlord="landlord".equals(request.getSession(false).getAttribute("role"));
        String back=request.getContextPath()+(landlord?"/Landlord/booking-requests.html":"/UserDashboard/requests.html");
        StringBuilder page=new StringBuilder("<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Demo payments | UniNest</title><style>body{margin:0;font:16px system-ui;background:#f6f8f7;color:#20342b}main{max-width:900px;margin:auto;padding:24px}h1{font-size:28px}h2{font-size:21px}section{border-top:1px solid #bbc9c3;padding:24px 0}label{display:block;margin:12px 0}input,select,button{font:inherit;padding:10px;max-width:100%;box-sizing:border-box}input:not([type=hidden]):not([type=checkbox]),select{display:block;width:100%;max-width:420px}button{background:#16634b;color:white;border:0;border-radius:4px;cursor:pointer}dt{font-weight:600}dd{margin:4px 0 12px}p,a,dd{overflow-wrap:anywhere}.notice{border-left:4px solid #d79b25;padding:12px;background:#fff5db}.error{color:#a22020}form{margin:18px 0}a{color:#145d86}@media print{form,nav{display:none}}</style></head><body><main><nav><a href='").append(back).append("'>Back to bookings</a></nav><h1>Demo payments</h1><p class='notice'>Simulation only. No money is transferred. Funding decisions are examples, not NSFAS or bursary approval. Do not submit real financial documents or card details.</p>");
        if(request.getParameter("error")!=null)page.append("<p class='error' role='alert'>").append(e(request.getParameter("error"))).append("</p>");
        if(request.getParameter("done")!=null)page.append("<p role='status'>Demo record updated.</p>");
        Connection c=DatabaseConnection.getInstance().getConnection();if(c==null){response.sendError(503);return;}
        String sql="SELECT b.booking_id,b.check_in,b.booking_status,r.room_number,r.price,su.full_name AS student_name,p.* FROM bookings b JOIN rooms r ON r.room_id=b.room_id JOIN students s ON s.student_id=b.student_id JOIN users su ON su.user_id=s.user_id JOIN landlords l ON l.landlord_id=r.landlord_id LEFT JOIN demo_payment_plans p ON p.booking_id=b.booking_id WHERE "+(landlord?"l.user_id":"s.user_id")+"=? AND (b.booking_status='confirmed' OR p.booking_id IS NOT NULL) ORDER BY b.booking_id DESC";
        try(PreparedStatement q=c.prepareStatement(sql)) {
            q.setInt(1,user);
            try(ResultSet r=q.executeQuery()) {
                boolean any=false;
                while(r.next()) {
                    any=true;int booking=r.getInt(1);
                    page.append("<section><h2>").append(e(r.getString("room_number"))).append(" · Booking #").append(booking).append("</h2><p>").append(e(r.getString("student_name"))).append(" | Due: ").append(r.getDate("check_in")).append("</p>");
                    if(r.getBigDecimal("deposit")==null) {
                        if(landlord)page.append(form(request,booking,"configure")).append("<p>First month's rent: ").append(amount(r.getBigDecimal("price"))).append("</p><label>Deposit (ZAR)<input name='deposit' type='number' min='0' max='99999999.99' step='0.01' required></label><button>Propose demo terms</button></form>");
                        else page.append("<p>Awaiting the landlord's deposit and rent proposal. No payment is due through this demo until you accept the terms.</p>");
                        page.append("</section>");continue;
                    }
                    BigDecimal deposit=r.getBigDecimal("deposit"),rent=r.getBigDecimal("rent"),cd=r.getBigDecimal("covered_deposit"),cr=r.getBigDecimal("covered_rent");
                    BigDecimal sponsor=cd.add(cr),student=deposit.add(rent).subtract(sponsor);
                    String source=r.getString("funding_source"),status=r.getString("funding_status");
                    boolean accepted=r.getBoolean("terms_accepted"),paid="completed".equals(r.getString("student_status"))||"completed".equals(r.getString("sponsor_status"));
                    page.append("<dl><dt>Deposit</dt><dd>").append(amount(deposit)).append("</dd><dt>First month's rent</dt><dd>").append(amount(rent)).append("</dd><dt>Total initial charges</dt><dd>").append(amount(deposit.add(rent))).append("</dd><dt>Funding</dt><dd>").append(source==null?"Not selected":e(r.getString("provider_name"))+" / "+e(status)).append("</dd><dt>Provider coverage: deposit / rent</dt><dd>").append(amount(cd)).append(" / ").append(amount(cr)).append("</dd><dt>Student share</dt><dd>").append(amount(student)).append("</dd><dt>Student payment</dt><dd>").append(student.signum()==0?"Nothing due":e(r.getString("student_status"))).append("</dd><dt>Provider payment</dt><dd>").append(sponsor.signum()==0?"Nothing due":e(r.getString("sponsor_status"))).append("</dd><dt>Terms accepted</dt><dd>").append(accepted?"Yes":"No").append("</dd></dl>");
                    if(!"confirmed".equals(r.getString("booking_status"))) {
                        page.append("<p>Booking ").append(e(r.getString("booking_status"))).append(". Payment history is read-only; any refund must be handled separately.</p>");
                        receipts(c,page,booking);page.append("</section>");continue;
                    }
                    if(!landlord && !paid)page.append(form(request,booking,"route")).append("<label>Funding source<select name='source'>").append(option("self","Self-funded",source)).append(option("nsfas","NSFAS",source)).append(option("bursary","Bursary",source)).append("</select></label><label>Bursary provider (for bursary funding)<input name='provider' maxlength='100' value='").append(e(r.getString("provider_name"))).append("'></label><button>Set funding route</button></form>");
                    if(landlord && source!=null && !"self".equals(source) && !paid)page.append(form(request,booking,"verify")).append("<label>Demo funding decision<select name='decision'><option value='verified'>Verify coverage</option><option value='rejected'>Reject funding</option></select></label><label>Deposit covered (ZAR)<input name='coveredDeposit' type='number' min='0' max='").append(deposit).append("' step='0.01' value='").append(cd).append("' required></label><label>Rent covered (ZAR)<input name='coveredRent' type='number' min='0' max='").append(rent).append("' step='0.01' value='").append(cr).append("' required></label><label>Demo verification reference<input name='reference' maxlength='100' required></label><button>Record funding decision</button></form>");
                    boolean payable=source!=null && ("self".equals(source)||"verified".equals(status));
                    if(!landlord && !accepted && payable)page.append(form(request,booking,"accept")).append("<input type='hidden' name='version' value='").append(r.getInt("terms_version")).append("'><label><input type='checkbox' name='consent' value='yes' required> I accept the deposit, first month's rent and the student/provider split shown above.</label><button>Accept demo terms</button></form>");
                    if(!landlord && accepted && payable && student.signum()>0 && !"completed".equals(r.getString("student_status")))page.append(form(request,booking,"pay")).append("<label>Demo checkout outcome<select name='outcome'><option value='completed'>Success</option><option value='failed'>Declined</option><option value='cancelled'>Cancel</option></select></label><button>Simulate student payment</button></form>");
                    if(landlord && accepted && payable && sponsor.signum()>0 && !"completed".equals(r.getString("sponsor_status")))page.append(form(request,booking,"external")).append("<label>External demo receipt reference<input name='reference' maxlength='100' required></label><button>Simulate provider payment received</button></form>");
                    receipts(c,page,booking);
                    page.append("</section>");
                }
                if(!any)page.append("<p>No accepted bookings are available.</p>");
            }
            response.setContentType("text/html;charset=UTF-8");response.setHeader("Cache-Control","no-store");
            response.getWriter().print(page.append("</main></body></html>"));
        }catch(SQLException error){getServletContext().log("Demo payment workflow unavailable",error);response.sendError(503,"Demo payment setup is unavailable. Check the database migration.");}
    }
    private static String option(String value,String label,String selected) {
        return "<option value='"+value+"'"+(value.equals(selected)?" selected":"")+">"+label+"</option>";
    }
    private void receipts(Connection c,StringBuilder page,int booking) throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT receipt_number,amount,payment_date FROM payments WHERE receipt_number IN (?,?) AND payment_status='completed'")) {
            q.setString(1,"DEMO-V2-"+booking+"-STUDENT");q.setString(2,"DEMO-V2-"+booking+"-PROVIDER");
            try(ResultSet r=q.executeQuery()){while(r.next())page.append("<p><strong>DEMO RECEIPT — NOT PROOF OF REAL PAYMENT</strong><br>").append(e(r.getString(1))).append(" | ").append(amount(r.getBigDecimal(2))).append(" | ").append(r.getTimestamp(3)).append("</p>");}
        }
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException {
        Integer user=user(request,response);if(user==null)return;
        Connection c=DatabaseConnection.getInstance().getConnection();if(c==null){response.sendError(503);return;}
        try {
            int booking=Integer.parseInt(request.getParameter("bookingId"));
            Map<String,String> input=new HashMap<>();
            for(String key:new String[]{"deposit","source","provider","coveredDeposit","coveredRent","decision","reference","consent","outcome","version"})input.put(key,request.getParameter(key));
            DemoFunding.perform(c,user,(String)request.getSession(false).getAttribute("role"),booking,request.getParameter("action"),input);
            response.sendRedirect(request.getContextPath()+"/PaymentSimulationServlet?done=1");
        }catch(IllegalArgumentException error){response.sendRedirect(request.getContextPath()+"/PaymentSimulationServlet?error="+java.net.URLEncoder.encode(error.getMessage()==null?"Invalid request":error.getMessage(),"UTF-8"));}
        catch(SQLException error){getServletContext().log("Demo payment update failed",error);response.sendError(503);}
    }
}
