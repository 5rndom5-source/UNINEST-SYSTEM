package com.uninest.servlets.landlord;

import com.uninest.utils.ImageStorage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

/** Persists the editor inside the caller's listing transaction. */
public final class ListingEditor {
    private ListingEditor() {}
    static final Set<String> TYPES = Set.of("Bedroom","Bathroom","Kitchen","Lounge","Study area","Laundry","Shared space","Exterior","Other");
    private static final class Group {
        String id,type,label; int order; List<String> photos=new ArrayList<>();
        Group(String id,String type,String label,int order){this.id=id;this.type=type;this.label=label;this.order=order;}
        String encode(){return "{\"id\":"+json(id)+",\"type\":"+json(type)+",\"label\":"+json(label)+",\"order\":"+order+",\"photos\":["+String.join(",",photos)+"]}";}
    }
    public static String read(Connection c,int id,int owner,boolean admin,String context) throws SQLException {
        StringBuilder b=new StringBuilder(); boolean furnished;
        try(PreparedStatement q=c.prepareStatement("SELECT room_number,description,location,price,furnishing FROM rooms WHERE room_id=? AND (approval_status='approved' OR landlord_id=? OR ?=1)")) {
            q.setInt(1,id);q.setInt(2,owner);q.setInt(3,admin?1:0);
            try(ResultSet r=q.executeQuery()) {
                if(!r.next())return "null";
                b.append("{\"id\":").append(id).append(",\"title\":").append(json(r.getString(1))).append(",\"description\":").append(json(r.getString(2))).append(",\"location\":").append(json(r.getString(3))).append(",\"price\":").append(r.getBigDecimal(4));
                furnished="fully_furnished".equals(r.getString(5));
            }
        }
        b.append(",\"details\":{");
        try(PreparedStatement q=c.prepareStatement("SELECT * FROM listing_details WHERE room_id=?")) {
            q.setInt(1,id);try(ResultSet r=q.executeQuery()){if(r.next()){
                String[] cols={"suburb","city","deposit","bedrooms","bathrooms","campus_distance","contact_name","contact_email","contact_phone"};
                String[] keys={"suburb","city","deposit","bedrooms","bathrooms","campusDistance","contactName","contactEmail","contactPhone"};
                for(int i=0;i<cols.length;i++){if(i>0)b.append(',');b.append(json(keys[i])).append(':').append(json(r.getString(cols[i])));}
            }}
        }
        b.append("},\"amenities\":[");List<String> amenities=new ArrayList<>();if(furnished)amenities.add(json("Furnished"));
        try(PreparedStatement q=c.prepareStatement("SELECT a.amenity_name FROM amenities a JOIN room_amenities ra ON a.amenity_id=ra.amenity_id WHERE ra.room_id=?")) {
            q.setInt(1,id);try(ResultSet r=q.executeQuery()){while(r.next())amenities.add(json(r.getString(1)));}
        }
        b.append(String.join(",",amenities)).append("],\"rooms\":[");
        Map<String,Group> groups=new LinkedHashMap<>();
        try(PreparedStatement q=c.prepareStatement("SELECT * FROM listing_photo_rooms WHERE room_id=? ORDER BY display_order,group_id")) {
            q.setInt(1,id);try(ResultSet r=q.executeQuery()){while(r.next()){String gid=r.getString("group_id");groups.put(gid,new Group(gid,r.getString("room_type"),r.getString("label"),r.getInt("display_order")));}}
        }
        try(PreparedStatement q=c.prepareStatement("SELECT i.*,d.group_id,d.caption FROM room_images i LEFT JOIN listing_photo_details d ON d.image_id=i.image_id WHERE i.room_id=? ORDER BY i.display_order,i.image_id")) {
            q.setInt(1,id);try(ResultSet r=q.executeQuery()){while(r.next()){
                String url=r.getString("image_url");if(url==null||!url.startsWith("/PropertyImageServlet/"))continue;
                String gid=r.getString("group_id"),category=Objects.toString(r.getString("category"),"other");
                if(!groups.containsKey(gid)) {
                    gid=UUID.nameUUIDFromBytes((id+":"+category).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    String type=category.contains("room")?"Bedroom":category.equals("building")?"Exterior":category.equals("kitchen")?"Kitchen":Set.of("bathroom","shower","tub","toilet").contains(category)?"Bathroom":"Other";
                    String label=category.replace('_',' ');if(label.isEmpty())label="Other";label=Character.toUpperCase(label.charAt(0))+label.substring(1);
                    if(!groups.containsKey(gid))groups.put(gid,new Group(gid,type,label,groups.size()));
                }
                groups.get(gid).photos.add("{\"id\":"+r.getInt("image_id")+",\"url\":"+json(context+url)+",\"caption\":"+json(r.getString("caption"))+",\"order\":"+r.getInt("display_order")+",\"isCover\":"+r.getBoolean("is_primary")+",\"isDemo\":"+com.uninest.utils.DemoPropertyImages.isMissingUpload(url)+"}");
            }}
        }
        List<String> encoded=new ArrayList<>();for(Group g:groups.values())encoded.add(g.encode());
        return b.append(String.join(",",encoded)).append("]}").toString();
    }
    public static String json(String s) {
        if(s==null) return "\"\"";
        StringBuilder b=new StringBuilder("\"");
        for(char ch:s.toCharArray()) {
            if(ch=='"'||ch=='\\') b.append('\\').append(ch);
            else if(ch<32) b.append(String.format("\\u%04x",(int)ch)); else b.append(ch);
        }
        return b.append('"').toString();
    }
    static String text(HttpServletRequest r,String key,int max) {
        String v=Objects.toString(r.getParameter(key),"").trim();
        if(v.length()>max) throw new IllegalArgumentException(key+" is too long."); return v;
    }
    static int number(HttpServletRequest r,String key,int min,int max) {
        try { int n=Integer.parseInt(r.getParameter(key));if(n>=min&&n<=max)return n; }catch(Exception ignored){}
        throw new IllegalArgumentException("Invalid "+key+".");
    }
    static BigDecimal money(HttpServletRequest r,String key) {
        String v=text(r,key,12); if(!v.matches("\\d{1,8}(\\.\\d{1,2})?"))throw new IllegalArgumentException("Invalid "+key+".");return new BigDecimal(v);
    }
    public static void save(HttpServletRequest r,Connection c,int listing,int owner,List<Path> written) throws SQLException,IOException {
        try(PreparedStatement q=c.prepareStatement("SELECT room_id FROM rooms WHERE room_id=? AND landlord_id=? FOR UPDATE")) {
            q.setInt(1,listing);q.setInt(2,owner);try(ResultSet rs=q.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Listing does not belong to this landlord.");}
        }
        int groupCount=number(r,"groupCount",1,30), photoCount=number(r,"photoCount",5,60);
        String[] ids=new String[groupCount], types=new String[groupCount], labels=new String[groupCount];
        Set<String> unique=new HashSet<>();
        for(int i=0;i<groupCount;i++) {
            ids[i]=text(r,"groupId"+i,36);types[i]=text(r,"groupType"+i,30);labels[i]=text(r,"groupLabel"+i,80);
            if(!ids[i].matches("[a-f0-9-]{36}")||!unique.add(ids[i])||!TYPES.contains(types[i])||labels[i].isEmpty()) throw new IllegalArgumentException("Invalid room group.");
        }
        int cover=number(r,"coverIndex",0,photoCount-1);
        List<Integer> saved=new ArrayList<>(); Set<String> refs=new HashSet<>();
        // Existing image IDs are accepted only for this locked, owner-checked listing.
        for(int i=0;i<photoCount;i++) {
            String ref=text(r,"photoRef"+i,40);number(r,"photoGroup"+i,0,groupCount-1);text(r,"photoCaption"+i,80);
            if(!refs.add(ref))throw new IllegalArgumentException("Duplicate photo.");
            if(ref.matches("[0-9]+")) {
                try(PreparedStatement q=c.prepareStatement("SELECT image_id FROM room_images WHERE image_id=? AND room_id=?")) {
                    q.setString(1,ref);q.setInt(2,listing);try(ResultSet rs=q.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Photo does not belong to this listing.");saved.add(rs.getInt(1));}
                }
            } else {
                byte[] bytes;
                try(PreparedStatement q=c.prepareStatement("SELECT image_data FROM listing_photo_uploads WHERE token=? AND landlord_id=? AND created_at >= CURRENT_TIMESTAMP - INTERVAL 1 DAY FOR UPDATE")) {
                    q.setString(1,ref);q.setInt(2,owner);try(ResultSet rs=q.executeQuery()){if(!rs.next())throw new IllegalArgumentException("An upload expired. Remove it and add the photo again.");bytes=rs.getBytes(1);}
                }
                String name=UUID.randomUUID()+".jpg";Path path=ImageStorage.directory("property-images").resolve(name);
                Files.createDirectories(path.getParent());written.add(path);Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
                try(PreparedStatement q=c.prepareStatement("INSERT INTO room_images(room_id,image_url,is_primary,display_order,category) VALUES(?,?,0,?,'other')",Statement.RETURN_GENERATED_KEYS)) {
                    q.setInt(1,listing);q.setString(2,"/PropertyImageServlet/"+name);q.setInt(3,i);q.executeUpdate();try(ResultSet rs=q.getGeneratedKeys()){rs.next();saved.add(rs.getInt(1));}
                }
                try(PreparedStatement q=c.prepareStatement("DELETE FROM listing_photo_uploads WHERE token=? AND landlord_id=?")){q.setString(1,ref);q.setInt(2,owner);q.executeUpdate();}
            }
        }
        try(PreparedStatement q=c.prepareStatement("DELETE FROM listing_photo_rooms WHERE room_id=?")){q.setInt(1,listing);q.executeUpdate();}
        for(int i=0;i<groupCount;i++) {
            try(PreparedStatement q=c.prepareStatement("INSERT INTO listing_photo_rooms VALUES(?,?,?,?,?)")){q.setString(1,ids[i]);q.setInt(2,listing);q.setString(3,types[i]);q.setString(4,labels[i]);q.setInt(5,i);q.executeUpdate();}
        }
        for(int i=0;i<photoCount;i++) {
            try(PreparedStatement q=c.prepareStatement("INSERT INTO listing_photo_details VALUES(?,?,?)")){q.setInt(1,saved.get(i));q.setString(2,ids[number(r,"photoGroup"+i,0,groupCount-1)]);q.setString(3,text(r,"photoCaption"+i,80));q.executeUpdate();}
            try(PreparedStatement q=c.prepareStatement("UPDATE room_images SET is_primary=?,display_order=? WHERE image_id=?")){q.setBoolean(1,i==cover);q.setInt(2,i);q.setInt(3,saved.get(i));q.executeUpdate();}
        }
        String placeholders=String.join(",",Collections.nCopies(saved.size(),"?"));
        try(PreparedStatement q=c.prepareStatement("DELETE FROM room_images WHERE room_id=? AND image_id NOT IN ("+placeholders+")")){q.setInt(1,listing);for(int i=0;i<saved.size();i++)q.setInt(i+2,saved.get(i));q.executeUpdate();}
        String email=text(r,"contactEmail",254);
        if(!email.isEmpty()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Invalid contact email.");
        String distance=text(r,"campusDistance",8);
        if(!distance.isEmpty()&&!distance.matches("\\d{1,5}(\\.\\d{1,2})?"))throw new IllegalArgumentException("Invalid campus distance.");
        try(PreparedStatement q=c.prepareStatement("INSERT INTO listing_details VALUES(?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE suburb=VALUES(suburb),city=VALUES(city),deposit=VALUES(deposit),bedrooms=VALUES(bedrooms),bathrooms=VALUES(bathrooms),campus_distance=VALUES(campus_distance),contact_name=VALUES(contact_name),contact_email=VALUES(contact_email),contact_phone=VALUES(contact_phone)")) {
            q.setInt(1,listing);q.setString(2,text(r,"suburb",100));q.setString(3,text(r,"city",100));q.setBigDecimal(4,money(r,"deposit"));q.setInt(5,number(r,"bedrooms",0,999));q.setInt(6,number(r,"bathrooms",0,999));
            if(distance.isEmpty())q.setNull(7,Types.DECIMAL);else q.setBigDecimal(7,new BigDecimal(distance));
            q.setString(8,text(r,"contactName",100));q.setString(9,email);q.setString(10,text(r,"contactPhone",40));q.executeUpdate();
        }
    }
}
