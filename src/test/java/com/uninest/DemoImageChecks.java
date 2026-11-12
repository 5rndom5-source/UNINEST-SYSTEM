package com.uninest;

import com.uninest.utils.*;
import java.nio.file.*;
import java.sql.*;
import java.util.zip.ZipFile;
import java.lang.reflect.Proxy;
import javax.servlet.ServletContext;
import javax.imageio.ImageIO;
import java.io.*;

/** Test data is restricted to a fresh loopback database created by the runner. */
public class DemoImageChecks {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static void sql(Connection c,String sql)throws SQLException{try(Statement q=c.createStatement()){q.executeUpdate(sql);}}
    public static void main(String[] args)throws Exception {
        String url=System.getenv("UNINEST_DB_URL");
        if(url==null||!url.startsWith("jdbc:mysql://127.0.0.1:3306/uninest_demo_image_check_"))throw new IllegalStateException("Disposable loopback database required");
        try(Connection c=DatabaseConnection.getInstance().getConnection()) {
            if(c==null||!c.getCatalog().startsWith("uninest_demo_image_check_"))throw new IllegalStateException("Disposable database required");
            if("seed".equals(args[0])) {
                String password=PasswordUtil.hashPassword("Testing!2026");
                try(PreparedStatement q=c.prepareStatement("INSERT INTO users(user_id,full_name,email,password,role,status) VALUES(1,'Test Student','student@example.invalid',?,'student','active'),(2,'Test Landlord','landlord@example.invalid',?,'landlord','active'),(3,'Other Landlord','other@example.invalid',?,'landlord','active'),(4,'Test Admin','admin@example.invalid',?,'admin','active')")){
                    for(int i=1;i<=4;i++)q.setString(i,password);q.executeUpdate();
                }
                sql(c,"INSERT INTO students(student_id,user_id) VALUES(1,1)");
                sql(c,"INSERT INTO landlords(landlord_id,user_id,id_number,approval_status) VALUES(1,2,'TEST-ONE','approved'),(2,3,'TEST-TWO','approved')");
                sql(c,"INSERT INTO admins(admin_id,user_id,admin_position,access_level) VALUES(1,4,'Test only','moderator')");
                sql(c,"INSERT INTO rooms(room_id,landlord_id,room_number,room_type,location,price,capacity,availability_status,approval_status) VALUES(1,1,'Demo fixture','single_room','12 Test Street',1000,1,'available','approved'),(2,1,'Private fixture','single_room','Private Street',1000,1,'available','pending')");
                sql(c,"INSERT INTO room_images(image_id,room_id,image_url,is_primary,display_order,category) VALUES(1,1,'/PropertyImageServlet/existing.png',0,1,'kitchen'),(2,1,'/PropertyImageServlet/missing.png',1,0,'kitchen'),(3,2,'/PropertyImageServlet/private.png',1,0,'bedroom'),(4,1,'/PropertyImageServlet/missing-two.png',0,2,'kitchen'),(5,1,'/PropertyImageServlet/missing-three.png',0,3,'kitchen'),(6,1,'/PropertyImageServlet/missing-four.png',0,4,'kitchen')");
                sql(c,"INSERT INTO bookings(booking_id,student_id,room_id,check_in,check_out) VALUES(1,1,1,'2030-01-01','2030-02-01')");
                sql(c,"INSERT INTO reviews(review_id,from_user_id,to_user_id,room_id,rating,review_text,review_type) VALUES(1,1,2,1,5,'Keep this review','room_review')");
                Path dir=ImageStorage.directory("property-images");Files.createDirectories(dir);
                Files.copy(Path.of("HomePage/listing-cbd-room.png"),dir.resolve("existing.png"));
                Files.copy(Path.of("HomePage/listing-cbd-room.png"),dir.resolve("orphan.png"));
                System.out.println("PASS: isolated demo-image fixtures seeded");return;
            }
            try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM room_images WHERE image_id BETWEEN 1 AND 6")){r.next();check(r.getInt(1)==6,"original photo records retained");}
            try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM bookings WHERE booking_id=1")){r.next();check(r.getInt(1)==1,"booking retained");}
            try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT review_text FROM reviews WHERE review_id=1")){r.next();check("Keep this review".equals(r.getString(1)),"review retained");}
        }
        check(!DemoPropertyImages.isMissingUpload("https://example.invalid/image.png"),"external URLs excluded");
        check(!DemoPropertyImages.isMissingUpload("/ProfileImageServlet/missing.png"),"unrelated URLs excluded");
        for(String name:new String[]{"../missing.png","x/missing.png","..","","a\\b.png"})check(!DemoPropertyImages.validName(name),"invalid path excluded");
        check(DemoPropertyImages.validName("123_room..png"),"legacy valid filenames remain accepted");
        check(DemoPropertyImages.asset("missing.png").equals(DemoPropertyImages.asset("missing.png")),"deterministic mapping");
        try(ZipFile war=new ZipFile("target/uninest.war")) {
            for(String asset:new String[]{"listing-cbd-room.png","listing-bellville-residence.png","listing-rondebosch.png"})check(war.getEntry("HomePage/"+asset)!=null,"WAR includes demo asset");
            ServletContext context=(ServletContext)Proxy.newProxyInstance(DemoImageChecks.class.getClassLoader(),new Class[]{ServletContext.class},(p,m,a)->m.getName().equals("getResourceAsStream")?war.getInputStream(war.getEntry(((String)a[0]).substring(1))):null);
            byte[] bytes=DemoPropertyImages.labelledImage(context,"missing.png");
            java.awt.image.BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
            check(image!=null&&image.getWidth()<=1200,"demo image decodes");
            int white=0,black=0,y=image.getHeight()/2;
            for(int row=y-25;row<y+25;row++)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,row)&0xffffff;if(rgb==0)black++;if(rgb==0xffffff)white++;}
            check(black>1000&&white>100,"visible black/white watermark banner");
        }
        System.out.println("PASS: "+checks+" demo-image packaging, path, watermark and preservation checks");
    }
}
