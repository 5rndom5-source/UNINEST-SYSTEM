package com.uninest.utils;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Map;

/** Demo-only accounting; callers must enforce the demo feature flag and CSRF. */
public final class DemoFunding {
    private DemoFunding() { }
    public static BigDecimal money(String value) {
        if(value==null || !value.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("Enter a non-negative amount with at most two decimal places.");
        return new BigDecimal(value).setScale(2);
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new IllegalArgumentException(message);
    }
    private static String text(String value,String label) {
        require(value!=null && !value.trim().isEmpty() && value.trim().length()<=100,label+" is required (maximum 100 characters).");
        return value.trim();
    }
    public static void perform(Connection c,int userId,String role,int booking,String action,Map<String,String> input) throws SQLException {
        boolean auto=c.getAutoCommit();c.setAutoCommit(false);
        try {
            int studentUser,landlordUser;BigDecimal monthlyRent;
            try(PreparedStatement q=c.prepareStatement("SELECT s.user_id,l.user_id,r.price,b.booking_status FROM bookings b JOIN students s ON s.student_id=b.student_id JOIN rooms r ON r.room_id=b.room_id JOIN landlords l ON l.landlord_id=r.landlord_id WHERE b.booking_id=? FOR UPDATE")) {
                q.setInt(1,booking);
                try(ResultSet r=q.executeQuery()) {
                    require(r.next(),"Booking not found.");studentUser=r.getInt(1);landlordUser=r.getInt(2);monthlyRent=r.getBigDecimal(3);
                    require(("student".equals(role)&&userId==studentUser)||("landlord".equals(role)&&userId==landlordUser),"This booking does not belong to your account.");
                    require("confirmed".equals(r.getString(4)),"Only an accepted booking can use demo payments.");
                }
            }
            boolean landlord="landlord".equals(role);
            if("configure".equals(action)) {
                require(landlord,"Only the landlord can propose payment terms.");
                BigDecimal deposit=money(input.get("deposit"));
                require(monthlyRent.signum()>=0 && deposit.add(monthlyRent).compareTo(new BigDecimal("99999999.99"))<=0,"The combined charges exceed the supported amount.");
                try(PreparedStatement q=c.prepareStatement("SELECT 1 FROM payments WHERE receipt_number=? AND payment_status='completed'")) {
                    q.setString(1,"DEMO-BOOKING-"+booking);
                    try(ResultSet r=q.executeQuery()){require(!r.next(),"This booking already has a legacy demo payment. Use a new demo booking.");}
                }
                try(PreparedStatement q=c.prepareStatement("INSERT INTO demo_payment_plans(booking_id,deposit,rent) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM demo_payment_plans WHERE booking_id=?)")) {
                    q.setInt(1,booking);q.setBigDecimal(2,deposit);q.setBigDecimal(3,monthlyRent);q.setInt(4,booking);
                    if(q.executeUpdate()>0)notify(c,studentUser,landlordUser,"Demo deposit and first-month rent terms are ready.");
                }
                c.commit();return;
            }
            BigDecimal deposit,rent,coveredDeposit,coveredRent;String source,status,studentStatus,sponsorStatus;boolean accepted;int version;
            try(PreparedStatement q=c.prepareStatement("SELECT * FROM demo_payment_plans WHERE booking_id=?")) {
                q.setInt(1,booking);
                try(ResultSet r=q.executeQuery()) {
                    require(r.next(),"The landlord must set payment terms first.");
                    deposit=r.getBigDecimal("deposit");rent=r.getBigDecimal("rent");coveredDeposit=r.getBigDecimal("covered_deposit");coveredRent=r.getBigDecimal("covered_rent");
                    source=r.getString("funding_source");status=r.getString("funding_status");accepted=r.getBoolean("terms_accepted");studentStatus=r.getString("student_status");sponsorStatus=r.getString("sponsor_status");
                    version=r.getInt("terms_version");
                }
            }
            boolean paid="completed".equals(studentStatus)||"completed".equals(sponsorStatus);
            if("route".equals(action)) {
                require(!landlord && !paid,"Funding cannot be changed after a payment is recorded.");
                String route=input.get("source");
                require(java.util.Arrays.asList("self","nsfas","bursary").contains(route),"Choose a funding source.");
                String provider="bursary".equals(route)?text(input.get("provider"),"Bursary provider"):"nsfas".equals(route)?"NSFAS":"Self-funded";
                try(PreparedStatement q=c.prepareStatement("UPDATE demo_payment_plans SET funding_source=?,provider_name=?,funding_status=?,covered_deposit=0,covered_rent=0,verification_reference=NULL,terms_accepted=FALSE,terms_version=terms_version+1,student_status='pending',sponsor_status='pending' WHERE booking_id=?")) {
                    q.setString(1,route);q.setString(2,provider);q.setString(3,"self".equals(route)?"not_required":"pending");q.setInt(4,booking);q.executeUpdate();
                }
                notify(c,studentUser,landlordUser,"Demo funding route selected: "+provider+". This is not proof of payment.");
            } else if("verify".equals(action)) {
                require(landlord && source!=null && !"self".equals(source) && !paid,"Only the owning landlord can verify unpaid funded bookings.");
                String decision=input.get("decision");require("verified".equals(decision)||"rejected".equals(decision),"Choose a funding decision.");
                BigDecimal cd="verified".equals(decision)?money(input.get("coveredDeposit")):BigDecimal.ZERO;
                BigDecimal cr="verified".equals(decision)?money(input.get("coveredRent")):BigDecimal.ZERO;
                require(cd.compareTo(deposit)<=0 && cr.compareTo(rent)<=0,"Funding coverage cannot exceed the disclosed charges.");
                String reference=text(input.get("reference"),"Demo verification reference");
                try(PreparedStatement q=c.prepareStatement("UPDATE demo_payment_plans SET funding_status=?,covered_deposit=?,covered_rent=?,verification_reference=?,terms_accepted=FALSE,terms_version=terms_version+1,student_status='pending',sponsor_status='pending' WHERE booking_id=?")) {
                    q.setString(1,decision);q.setBigDecimal(2,cd);q.setBigDecimal(3,cr);q.setString(4,reference);q.setInt(5,booking);q.executeUpdate();
                }
                notify(c,studentUser,landlordUser,"Demo funding "+decision+". Payment has not been received. Review the updated student share.");
            } else if("accept".equals(action)) {
                require(!landlord && source!=null && ("self".equals(source)||"verified".equals(status)),"Funding must be verified before accepting the payable amounts.");
                require("yes".equals(input.get("consent")),"Confirm acceptance of the deposit, rent and funding split.");
                require(Integer.toString(version).equals(input.get("version")),"The funding terms changed. Reload and review the amounts before accepting.");
                try(PreparedStatement q=c.prepareStatement("UPDATE demo_payment_plans SET terms_accepted=TRUE WHERE booking_id=?")){q.setInt(1,booking);q.executeUpdate();}
            } else if("pay".equals(action)||"external".equals(action)) {
                boolean external="external".equals(action);
                require(external==landlord,"Only the student can pay their share; only the landlord can record provider receipts.");
                require(accepted && source!=null && ("self".equals(source)||"verified".equals(status)),"The student must accept verified payment terms first.");
                BigDecimal coverage=coveredDeposit.add(coveredRent);
                BigDecimal amount=external?coverage:deposit.add(rent).subtract(coverage);
                require(amount.signum()>0,"No amount is due from this payer.");
                if(external)require(!"self".equals(source)&&"verified".equals(status),"External funding must be verified.");
                String outcome=external?"completed":input.get("outcome");
                require(java.util.Arrays.asList("completed","failed","cancelled").contains(outcome),"Choose a valid demo outcome.");
                if(!"completed".equals(external?sponsorStatus:studentStatus)) {
                    String reference=external?text(input.get("reference"),"External demo receipt reference"):null;
                    if("completed".equals(outcome))settle(c,booking,external,amount,outcome,reference);
                    try(PreparedStatement q=c.prepareStatement(external?"UPDATE demo_payment_plans SET sponsor_status=?,external_reference=? WHERE booking_id=?":"UPDATE demo_payment_plans SET student_status=? WHERE booking_id=?")) {
                        q.setString(1,outcome);if(external){q.setString(2,reference);q.setInt(3,booking);}else q.setInt(2,booking);q.executeUpdate();
                    }
                    notify(c,studentUser,landlordUser,"DEMO "+(external?"provider receipt":"student payment")+": "+outcome+". No real money moved.");
                }
            } else throw new IllegalArgumentException("Unknown payment action.");
            c.commit();
        } catch(SQLException | RuntimeException error) {c.rollback();throw error;}
        finally {c.setAutoCommit(auto);}
    }
    private static void settle(Connection c,int booking,boolean external,BigDecimal amount,String outcome,String reference) throws SQLException {
        try(PreparedStatement q=c.prepareStatement("INSERT INTO tenancy_records(booking_id,tenancy_start,tenancy_end,tenancy_status) SELECT booking_id,check_in,check_out,'active' FROM bookings WHERE booking_id=? AND NOT EXISTS(SELECT 1 FROM tenancy_records WHERE booking_id=?)")){q.setInt(1,booking);q.setInt(2,booking);q.executeUpdate();}
        String receipt="DEMO-V2-"+booking+(external?"-PROVIDER":"-STUDENT");
        try(PreparedStatement q=c.prepareStatement("INSERT INTO payments(tenancy_id,landlord_id,student_id,amount,due_date,payment_method,payment_status,receipt_number) SELECT t.tenancy_id,r.landlord_id,b.student_id,?,b.check_in,'other','pending',? FROM bookings b JOIN rooms r ON r.room_id=b.room_id JOIN tenancy_records t ON t.booking_id=b.booking_id WHERE b.booking_id=? AND NOT EXISTS(SELECT 1 FROM payments WHERE receipt_number=?) ORDER BY t.tenancy_id LIMIT 1")) {
            q.setBigDecimal(1,amount);q.setString(2,receipt);q.setInt(3,booking);q.setString(4,receipt);q.executeUpdate();
        }
        try(PreparedStatement q=c.prepareStatement("UPDATE payments SET amount=?,payment_status=?,payment_date=CASE WHEN ?='completed' THEN NOW() ELSE NULL END WHERE receipt_number=? AND payment_status<>'completed'")) {
            q.setBigDecimal(1,amount);q.setString(2,outcome);q.setString(3,outcome);q.setString(4,receipt);q.executeUpdate();
        }
        try(PreparedStatement q=c.prepareStatement("INSERT INTO transaction_history(payment_id,transaction_type,amount,reference_number,notes) SELECT payment_id,'credit',amount,receipt_number,? FROM payments p WHERE receipt_number=? AND payment_status='completed' AND NOT EXISTS(SELECT 1 FROM transaction_history h WHERE h.payment_id=p.payment_id AND h.transaction_type='credit')")) {
            q.setString(1,"SIMULATION ONLY: no money transferred"+(external?"; external reference: "+reference:""));q.setString(2,receipt);q.executeUpdate();
        }
    }
    private static void notify(Connection c,int student,int landlord,String message) throws SQLException {
        try(PreparedStatement q=c.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,action_url) VALUES(?,'Demo payment update',?,'payment_due','PaymentSimulationServlet'),(?,'Demo payment update',?,'payment_due','PaymentSimulationServlet')")) {
            q.setInt(1,student);q.setString(2,message);q.setInt(3,landlord);q.setString(4,message);q.executeUpdate();
        }
    }
}
