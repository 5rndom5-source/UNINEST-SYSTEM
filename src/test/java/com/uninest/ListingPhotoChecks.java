package com.uninest;
import com.uninest.servlets.landlord.ListingEditor;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

public class ListingPhotoChecks {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static HttpServletRequest request(Map<String,String> fields){return (HttpServletRequest)Proxy.newProxyInstance(ListingPhotoChecks.class.getClassLoader(),new Class[]{HttpServletRequest.class},(proxy,method,args)->method.getName().equals("getParameter")?fields.get(args[0]):null);}
    static void reject(Connection c,Map<String,String> fields,int owner,String name)throws Exception {
        c.setAutoCommit(false);boolean rejected=false;
        try{ListingEditor.save(request(fields),c,1,owner,new ArrayList<>());}catch(IllegalArgumentException expected){rejected=true;}
        finally{c.rollback();c.setAutoCommit(true);}check(rejected,name);
    }
    public static void run(Connection c)throws Exception {
        if(!c.getCatalog().startsWith("uninest_upgrade_check_"))throw new IllegalStateException("Disposable database required");
        List<Integer> ids=new ArrayList<>();
        for(int i=0;i<6;i++)try(PreparedStatement q=c.prepareStatement("INSERT INTO room_images(room_id,image_url,is_primary,display_order,category) VALUES(1,?, ?,?,'kitchen')",Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1,"/PropertyImageServlet/test-"+i+".png");q.setBoolean(2,i==0);q.setInt(3,i);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();ids.add(r.getInt(1));}
        }
        check(ListingEditor.read(c,1,1,false,"").contains("\"type\":\"Kitchen\""),"legacy photos grouped");
        Map<String,String> fields=new HashMap<>();
        fields.put("groupCount","2");fields.put("photoCount","5");fields.put("coverIndex","3");fields.put("deposit","1200.50");fields.put("bedrooms","2");fields.put("bathrooms","1");fields.put("campusDistance","1.25");fields.put("contactEmail","contact@example.invalid");
        for(int i=0;i<2;i++){fields.put("groupId"+i,UUID.randomUUID().toString());fields.put("groupType"+i,"Bedroom");fields.put("groupLabel"+i,"Bedroom "+(i+1));}
        for(int i=0;i<5;i++){fields.put("photoRef"+i,ids.get(4-i).toString());fields.put("photoGroup"+i,i<3?"0":"1");fields.put("photoCaption"+i,i==0?"Window \"view\"\nTest":"Photo "+i);}
        c.setAutoCommit(false);try{ListingEditor.save(request(fields),c,1,1,new ArrayList<>());c.commit();}finally{c.setAutoCommit(true);}
        String json=ListingEditor.read(c,1,1,false,"");
        check(json.contains("Bedroom 2")&&json.contains("1200.50"),"group labels and details persisted");
        check(json.contains("Window \\\"view\\\"\\u000aTest"),"caption safely encoded");
        try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*),SUM(is_primary) FROM room_images WHERE room_id=1")){r.next();check(r.getInt(1)==5&&r.getInt(2)==1,"removal and single cover persisted");}
        try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT image_id FROM room_images WHERE room_id=1 ORDER BY display_order LIMIT 1")){r.next();check(r.getInt(1)==ids.get(4),"reorder persisted");}
        reject(c,fields,999,"wrong owner denied");
        Map<String,String> bad=new HashMap<>(fields);bad.put("photoCount","4");reject(c,bad,1,"minimum five enforced");
        bad=new HashMap<>(fields);bad.put("coverIndex","-1");reject(c,bad,1,"cover required");
        bad=new HashMap<>(fields);bad.put("photoRef0","99999999");reject(c,bad,1,"foreign photo denied");
        bad=new HashMap<>(fields);bad.put("photoRef1",bad.get("photoRef0"));reject(c,bad,1,"duplicate photo denied");
        bad=new HashMap<>(fields);bad.put("photoCaption0","x".repeat(81));reject(c,bad,1,"caption length enforced");
        bad=new HashMap<>(fields);bad.put("photoGroup0","2");reject(c,bad,1,"invalid target room denied");
        bad=new HashMap<>(fields);bad.put("groupType0","Unknown");reject(c,bad,1,"invalid room type denied");
        bad=new HashMap<>(fields);bad.put("photoRef0",UUID.randomUUID().toString());reject(c,bad,1,"unknown upload token denied");
        try(Statement q=c.createStatement()){q.executeUpdate("UPDATE rooms SET approval_status='pending' WHERE room_id=1");}
        check("null".equals(ListingEditor.read(c,1,-1,false,"")),"unapproved gallery private");
        check(!"null".equals(ListingEditor.read(c,1,1,false,"")),"owner sees unapproved gallery");
        check(!"null".equals(ListingEditor.read(c,1,-1,true,"")),"admin sees unapproved gallery");
        System.out.println("PASS: "+checks+" listing photo database checks");
    }
}
