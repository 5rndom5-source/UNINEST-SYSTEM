package com.uninest;

import com.uninest.utils.*;
import com.uninest.servlets.landlord.ListingEditor;
import java.sql.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

/** Only runs against a fresh loopback test database created by check-listing-area.ps1. */
public final class ListingAreaChecks {
    static int checks;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static void sql(Connection c,String text)throws SQLException{try(Statement q=c.createStatement()){q.execute(text);}}
    static String value(Connection c,String text)throws SQLException{try(Statement q=c.createStatement();ResultSet r=q.executeQuery(text)){r.next();return r.getString(1);}}
    static HttpServletRequest request(Map<String,String> values){return (HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->m.getName().equals("getParameter")?values.get(a[0]):null);}
    static void area(Connection c,int owner,Map<String,String> fields)throws Exception {
        c.setAutoCommit(false);
        try{ListingEditor.saveArea(request(fields),c,1,owner);c.commit();}
        catch(Exception e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
    public static void main(String[] args)throws Exception {
        Connection c=DatabaseConnection.getInstance().getConnection();
        if(c==null||!c.getCatalog().startsWith("uninest_area_check_"))throw new IllegalStateException("Disposable area database required");
        if("seed".equals(args[0])) {
            String hash=PasswordUtil.hashPassword("Testing!2026");
            try(PreparedStatement q=c.prepareStatement("INSERT INTO users(user_id,full_name,email,password,role,status) VALUES(10,'Fixture landlord','landlord@example.invalid',?,'landlord','active'),(11,'Fixture student','student@example.invalid',?,'student','active')")){q.setString(1,hash);q.setString(2,hash);q.executeUpdate();}
            sql(c,"INSERT INTO landlords(landlord_id,user_id,id_number,approval_status) VALUES(1,10,'TEST-AREA','approved')");
            sql(c,"INSERT INTO students(student_id,user_id) VALUES(1,11)");
            String[] titles={"Modern House","Legacy house","Legacy CBD","Other City","Pending house","Occupied house"};
            String[] addresses={"12 Main Road","5 Quiet Lane","Cape Town CBD","8 Plain Street","9 Hide Road","10 Occupied Street"};
            for(int i=0;i<titles.length;i++) {
                try(PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_id,landlord_id,room_number,room_type,location,price,capacity,description,approval_status,availability_status) VALUES(?,1,?,'single_room',?,1000,1,'Keep description',?,?)")) {
                    q.setInt(1,i+1);q.setString(2,titles[i]);q.setString(3,addresses[i]);q.setString(4,i==4?"pending":"approved");q.setString(5,i==5?"occupied":"available");q.executeUpdate();
                }
            }
            sql(c,"INSERT INTO listing_details(room_id,city,suburb) VALUES(1,'Cape Town','Observatory'),(4,'Stellenbosch',''),(5,'Cape Town',''),(6,'Cape Town','')");
            Path images=ImageStorage.directory("property-images");Files.createDirectories(images);
            Files.copy(Path.of("HomePage/listing-rondebosch.png"),images.resolve("area-fixture.png"),StandardCopyOption.REPLACE_EXISTING);
            for(int i=0;i<7;i++)sql(c,"INSERT INTO room_images(image_id,room_id,image_url,is_primary,display_order,category) VALUES("+(i+1)+","+(i<6?1:2)+",'/PropertyImageServlet/area-fixture.png',"+(i==0||i==6?1:0)+","+(i<6?i:0)+",'building')");
            sql(c,"INSERT INTO bookings(booking_id,student_id,room_id,check_in,check_out,booking_status) VALUES(1,1,2,'2030-01-01','2030-02-01','confirmed')");
            sql(c,"INSERT INTO reviews(review_id,from_user_id,to_user_id,room_id,rating,review_text,review_type) VALUES(1,11,10,2,5,'Keep review','room_review')");
            System.out.println("PASS: pre-migration fixtures created in disposable database");
        } else if("verify".equals(args[0])) {
            check("6".equals(value(c,"SELECT COUNT(*) FROM rooms WHERE area IS NULL")),"migration leaves every area unknown");
            check(ListingEditor.read(c,2,1,false,"").contains("\"details\":{\"area\":\"\"}"),"legacy edit does not guess details");
            area(c,1,Map.of("area","Cape Town CBD"));
            check("Cape Town CBD".equals(value(c,"SELECT area FROM rooms WHERE room_id=1")),"separate area persisted");
            check(ListingEditor.read(c,1,1,false,"").contains("\"area\":\"Cape Town CBD\""),"area returned to editor");
            area(c,1,Map.of());
            check("Cape Town CBD".equals(value(c,"SELECT area FROM rooms WHERE room_id=1")),"old clients preserve existing area");
            for(Map<String,String> input:List.of(Map.of("area","Not a supported area"),Map.of("area","Cape Town CBD' OR 1=1"))) {
                boolean rejected=false;try{area(c,1,input);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid area rejected");
            }
            boolean denied=false;try{area(c,999,Map.of("area","Bellville"));}catch(IllegalArgumentException expected){denied=true;}check(denied,"other landlord denied");
            area(c,1,Map.of("area",""));check(value(c,"SELECT area FROM rooms WHERE room_id=1")==null,"explicit unknown stored as null");
            area(c,1,Map.of("area","Cape Town CBD"));
            preserved(c);
            System.out.println("PASS: "+checks+" additive migration and area persistence checks");
        } else if("after".equals(args[0])) {
            preserved(c);
            check("Bellville".equals(value(c,"SELECT area FROM rooms WHERE room_id=2")),"legacy area-only edit persisted");
            check("Claremont".equals(value(c,"SELECT area FROM rooms WHERE room_id=1")),"full edit persisted area");
            check("1".equals(value(c,"SELECT COUNT(*) FROM rooms WHERE room_number='New area listing' AND area='Stellenbosch' AND location='22 Test Street'")),"new listing persisted area and address separately");
            System.out.println("PASS: "+checks+" post-browser preservation checks");
        } else throw new IllegalArgumentException("Unknown phase");
        DatabaseConnection.getInstance().closeConnection();
    }
    static void preserved(Connection c)throws SQLException {
        check("12 Main Road".equals(value(c,"SELECT location FROM rooms WHERE room_id=1")),"modern street address preserved");
        check("5 Quiet Lane".equals(value(c,"SELECT location FROM rooms WHERE room_id=2")),"legacy street address preserved");
        check("0".equals(value(c,"SELECT COUNT(*) FROM listing_details WHERE room_id=2")),"no invented legacy details");
        check("7".equals(value(c,"SELECT COUNT(*) FROM room_images WHERE image_id BETWEEN 1 AND 7 AND image_url='/PropertyImageServlet/area-fixture.png'")),"original photo IDs and URLs preserved");
        check("1".equals(value(c,"SELECT COUNT(*) FROM bookings WHERE booking_id=1 AND room_id=2 AND booking_status='confirmed' AND check_in='2030-01-01' AND check_out='2030-02-01'")),"booking preserved");
        check("1".equals(value(c,"SELECT COUNT(*) FROM reviews WHERE review_id=1 AND room_id=2 AND rating=5 AND review_text='Keep review'")),"review preserved");
    }
}
