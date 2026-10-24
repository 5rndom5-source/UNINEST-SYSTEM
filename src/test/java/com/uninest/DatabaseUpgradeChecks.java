package com.uninest;
import com.uninest.utils.*;
import java.sql.*;
import java.util.Base64;

/** Runs only against a disposable database created by the test runner. */
public class DatabaseUpgradeChecks {
    public static void main(String[] args) throws Exception {
        Connection c=DatabaseConnection.getInstance().getConnection();
        if(c==null || !c.getCatalog().startsWith("uninest_upgrade_check_")) throw new IllegalStateException("Disposable test database required");
        try {
            String password=PasswordUtil.hashPassword("Testing!2026");
            try(PreparedStatement q=c.prepareStatement("INSERT INTO users(user_id,full_name,email,password,role,status) VALUES(1,'Test Student','student@example.invalid',?,'student','active'),(2,'Test Landlord','landlord@example.invalid',?,'landlord','active')")) {
                q.setString(1,password);q.setString(2,password);q.executeUpdate();
            }
            execute(c,"INSERT INTO students(student_id,user_id) VALUES(1,1)");
            execute(c,"INSERT INTO landlords(landlord_id,user_id,id_number) VALUES(1,2,'TEST-ONLY')");
            execute(c,"INSERT INTO rooms(room_id,landlord_id,room_number,room_type,location,price,capacity,availability_status,approval_status) VALUES(1,1,'Test Room','single_room','Test',1000,1,'available','approved')");
            execute(c,"INSERT INTO bookings(booking_id,student_id,room_id,check_in,check_out) VALUES(1,1,1,'2030-01-01','2030-02-01'),(2,1,1,'2030-01-10','2030-01-20'),(3,1,1,'2030-02-01','2030-03-01')");
            check(BookingTransitions.update(c,1,1,"confirmed",null)==1,"initial booking confirmation");
            check(BookingTransitions.update(c,2,1,"confirmed",null)==0,"overlapping capacity rejected");
            check(BookingTransitions.update(c,3,1,"confirmed",null)==1,"adjacent booking accepted");
            check(BookingTransitions.update(c,1,1,"confirmed",null)==0,"repeated confirmation rejected");
            check(BookingTransitions.update(c,1,999,"cancelled","Test")==0,"wrong landlord rejected");
            execute(c,"UPDATE rooms SET capacity=2 WHERE room_id=1");
            execute(c,"INSERT INTO bookings(booking_id,student_id,room_id,check_in,check_out) VALUES(4,1,1,'2030-01-01','2030-03-01'),(5,1,1,'2030-01-15','2030-02-15'),(6,1,1,'2030-04-01','2030-04-01')");
            check(BookingTransitions.update(c,4,1,"confirmed",null)==1,"adjacent occupants leave one bed for a longer stay");
            check(BookingTransitions.update(c,5,1,"confirmed",null)==0,"actual peak capacity rejected");
            check(BookingTransitions.update(c,6,1,"confirmed",null)==0,"invalid stay dates rejected");
            String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
            try(PreparedStatement q=c.prepareStatement("INSERT INTO password_reset_tokens(token_hash,user_id,expires_at) VALUES(?,1,DATE_ADD(NOW(),INTERVAL 20 MINUTE))")) {q.setString(1,RecoveryService.hash(token));q.executeUpdate();}
            check(RecoveryService.reset(c,token,"Changed!2026"),"valid reset accepted");
            check(!RecoveryService.reset(c,token,"Again!2026"),"reused token rejected");
            try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT password,password_changed_at FROM users WHERE user_id=1")){r.next();check(PasswordUtil.verifyPassword("Changed!2026",r.getString(1)) && r.getTimestamp(2)!=null,"new password and session revocation timestamp");}
            execute(c,"UPDATE password_reset_tokens SET used_at=NULL,expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE)");
            check(!RecoveryService.reset(c,token,"Again!2026"),"expired token rejected");
            int adminId=AdminRegistration.createPending(c,"Test Admin","admin@example.invalid","Testing!2026","Testing registration");
            try(PreparedStatement q=c.prepareStatement("SELECT u.status,u.password,a.access_level FROM users u JOIN admins a ON a.user_id=u.user_id WHERE u.user_id=?")) {
                q.setInt(1,adminId);
                try(ResultSet r=q.executeQuery()) {
                    check(r.next() && "pending_verification".equals(r.getString(1)) && PasswordUtil.verifyPassword("Testing!2026",r.getString(2)) && "moderator".equals(r.getString(3)),"pending admin created without privileged login");
                }
            }
            boolean duplicate=false;
            try {AdminRegistration.createPending(c,"Duplicate","admin@example.invalid","Testing!2026","Duplicate");}catch(SQLException expected){duplicate=true;}
            check(duplicate && c.getAutoCommit(),"duplicate rejected and transaction state restored");
            execute(c,"UPDATE rooms SET location='12 Test Street, Rondebosch, Cape Town, 7700, South Africa' WHERE room_id=1");
            try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT location FROM rooms WHERE room_id=1")){r.next();check(r.getString(1).startsWith("12 Test Street"),"full property address retained");}
            System.out.println("PASS: 15 MySQL integration checks");
            int owner=AdminRegistration.createPending(c,"Test Owner","owner@example.invalid","Testing!2026","Test seed");
            execute(c,"UPDATE users SET status='active' WHERE user_id="+owner);
            check(!AdminRegistration.decide(c,1,adminId,true),"student cannot approve admin");
            check(!AdminRegistration.decide(c,adminId,owner,true),"pending admin cannot approve");
            check(!AdminRegistration.decide(c,owner,owner,true),"cannot approve own account");
            check(AdminRegistration.decide(c,owner,adminId,true),"active admin can approve member");
            check(!AdminRegistration.decide(c,owner,adminId,true),"repeat approval rejected");
            int declined=AdminRegistration.createPending(c,"Declined","declined@example.invalid","Testing!2026","Test");
            check(AdminRegistration.decide(c,owner,declined,false),"decline pending account");
            check(!AdminRegistration.decide(c,owner,declined,true),"cannot reactivate declined via pending queue");
            check(c.getAutoCommit(),"approval restores transaction state");
            System.out.println("PASS: 8 admin approval integration checks");
            DemoFundingChecks.run(c);
            ListingPhotoChecks.run(c);
        }finally{DatabaseConnection.getInstance().closeConnection();}
    }
    static void execute(Connection c,String sql)throws SQLException{try(Statement q=c.createStatement()){q.executeUpdate(sql);}}
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
