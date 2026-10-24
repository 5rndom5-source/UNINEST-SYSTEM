package com.uninest;

import com.uninest.utils.DemoFunding;
import java.sql.*;
import java.util.*;

public final class DemoFundingChecks {
    static int checks;
    interface Work { void run() throws Exception; }
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static void rejected(Work work,String label)throws Exception {
        boolean rejected=false;try{work.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,label);
    }
    static Map<String,String> values(String... fields){Map<String,String> map=new HashMap<>();for(int i=0;i<fields.length;i+=2)map.put(fields[i],fields[i+1]);return map;}
    static String scalar(Connection c,String sql)throws SQLException{try(Statement q=c.createStatement();ResultSet r=q.executeQuery(sql)){r.next();return r.getString(1);}}
    static void command(Connection c,int user,String role,int booking,String action,String... fields)throws SQLException {DemoFunding.perform(c,user,role,booking,action,values(fields));}
    static void accept(Connection c,int booking)throws SQLException{command(c,1,"student",booking,"accept","consent","yes","version",scalar(c,"SELECT terms_version FROM demo_payment_plans WHERE booking_id="+booking));}
    public static void run(Connection c)throws Exception {
        if(!c.getCatalog().startsWith("uninest_upgrade_check_"))throw new IllegalStateException("Disposable database only");
        rejected(()->DemoFunding.money("-1"),"negative amount rejected");
        rejected(()->DemoFunding.money("1.001"),"fractional cents rejected");
        rejected(()->DemoFunding.money("NaN"),"invalid amount rejected");
        rejected(()->command(c,1,"student",1,"configure","deposit","500"),"student cannot set charges");
        rejected(()->command(c,999,"landlord",1,"configure","deposit","500"),"other landlord rejected");
        rejected(()->command(c,2,"landlord",2,"configure","deposit","500"),"pending booking rejected");
        command(c,2,"landlord",1,"configure","deposit","500");
        command(c,2,"landlord",1,"configure","deposit","999");
        check("500.00".equals(scalar(c,"SELECT deposit FROM demo_payment_plans WHERE booking_id=1")),"repeated proposal does not change charges");
        command(c,1,"student",1,"route","source","self");
        rejected(()->command(c,1,"student",1,"pay","outcome","completed"),"payment requires explicit acceptance");
        rejected(()->command(c,1,"student",1,"accept","consent","yes","version","0"),"stale terms rejected");
        accept(c,1);
        command(c,1,"student",1,"pay","outcome","failed");
        check("0".equals(scalar(c,"SELECT COUNT(*) FROM payments WHERE receipt_number LIKE 'DEMO-V2-1-%'")),"decline creates no paid receipt");
        command(c,1,"student",1,"pay","outcome","cancelled");
        check("cancelled".equals(scalar(c,"SELECT student_status FROM demo_payment_plans WHERE booking_id=1")),"cancellation retained");
        command(c,1,"student",1,"pay","outcome","completed");
        command(c,1,"student",1,"pay","outcome","completed");
        command(c,1,"student",1,"pay","outcome","failed");
        check("1500.00".equals(scalar(c,"SELECT amount FROM payments WHERE receipt_number='DEMO-V2-1-STUDENT'")),"deposit plus rent charged once");
        check("1".equals(scalar(c,"SELECT COUNT(*) FROM transaction_history WHERE reference_number='DEMO-V2-1-STUDENT'")),"duplicate completion creates one ledger entry");
        check("completed".equals(scalar(c,"SELECT student_status FROM demo_payment_plans WHERE booking_id=1")),"completed payment cannot revert");
        rejected(()->command(c,1,"student",1,"route","source","nsfas"),"cannot change funding after payment");
        command(c,2,"landlord",3,"configure","deposit","500");
        command(c,1,"student",3,"route","source","nsfas");
        rejected(()->accept(c,3),"pending funding blocks payment acceptance");
        rejected(()->command(c,1,"student",3,"verify","decision","verified","coveredDeposit","500","coveredRent","1000","reference","DEMO"),"student cannot verify funding");
        rejected(()->command(c,2,"landlord",3,"verify","decision","verified","coveredDeposit","501","coveredRent","1000","reference","DEMO"),"excess coverage rejected");
        command(c,2,"landlord",3,"verify","decision","rejected","reference","DEMO-REJECTED");
        rejected(()->accept(c,3),"rejected funding cannot be treated as paid");
        command(c,2,"landlord",3,"verify","decision","verified","coveredDeposit","0","coveredRent","1000","reference","DEMO-AWARD");
        check("0".equals(scalar(c,"SELECT COUNT(*) FROM payments WHERE receipt_number LIKE 'DEMO-V2-3-%'")),"verified funding is not money received");
        accept(c,3);
        rejected(()->command(c,1,"student",3,"external","reference","DEMO-TRANSFER"),"student cannot record provider money");
        command(c,1,"student",3,"pay","outcome","completed");
        command(c,2,"landlord",3,"external","reference","DEMO-TRANSFER");
        command(c,2,"landlord",3,"external","reference","DEMO-TRANSFER");
        check("500.00".equals(scalar(c,"SELECT amount FROM payments WHERE receipt_number='DEMO-V2-3-STUDENT'")),"student pays uncovered deposit only");
        check("1000.00".equals(scalar(c,"SELECT amount FROM payments WHERE receipt_number='DEMO-V2-3-PROVIDER'")),"provider payment recorded separately");
        check("1".equals(scalar(c,"SELECT COUNT(*) FROM transaction_history WHERE reference_number='DEMO-V2-3-PROVIDER'")),"external payment cannot duplicate");
        command(c,2,"landlord",4,"configure","deposit","500");
        rejected(()->command(c,1,"student",4,"route","source","bursary"),"bursary provider required");
        command(c,1,"student",4,"route","source","bursary","provider","Demo Scholarship");
        command(c,2,"landlord",4,"verify","decision","verified","coveredDeposit","500","coveredRent","1000","reference","DEMO-FULL-AWARD");
        accept(c,4);
        rejected(()->command(c,1,"student",4,"pay","outcome","completed"),"fully funded student has no checkout charge");
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures=new java.util.ArrayList<>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->{
                try(Connection concurrent=DriverManager.getConnection(System.getenv("UNINEST_DB_URL"),System.getenv("UNINEST_DB_USER"),System.getenv("UNINEST_DB_PASSWORD"))) {
                    if(!concurrent.getCatalog().startsWith("uninest_upgrade_check_"))throw new IllegalStateException("Test database required");
                    command(concurrent,2,"landlord",4,"external","reference","DEMO-FULL-RECEIPT");
                    return null;
                }
            }));
            for(java.util.concurrent.Future<?> future:futures)future.get();
        } finally {pool.shutdownNow();}
        check("1".equals(scalar(c,"SELECT COUNT(*) FROM transaction_history WHERE reference_number='DEMO-V2-4-PROVIDER'")),"simultaneous provider receipts create one credit");
        check("1500.00".equals(scalar(c,"SELECT amount FROM payments WHERE receipt_number='DEMO-V2-4-PROVIDER'")),"bursary covers both charges");
        check(c.getAutoCommit(),"transaction state restored");
        System.out.println("PASS: "+checks+" demo funding checks");
    }
}
