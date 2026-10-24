package com.uninest.servlets.landlord;
import com.uninest.utils.DatabaseConnection;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@WebServlet("/ListingMapServlet")
public class ListingMapServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException {
        int id;try{id=Integer.parseInt(req.getParameter("roomId"));}catch(Exception e){res.sendError(400);return;}
        HttpSession s=req.getSession(false);
        int owner=s!=null&&s.getAttribute("landlordId") instanceof Integer?(Integer)s.getAttribute("landlordId"):-1;
        boolean admin=s!=null&&"admin".equals(s.getAttribute("role"));
        try {
            Connection c=DatabaseConnection.getInstance().getConnection();if(c==null){res.sendError(503);return;}
            try(PreparedStatement q=c.prepareStatement("SELECT r.location,d.suburb,d.city FROM rooms r LEFT JOIN listing_details d ON d.room_id=r.room_id WHERE r.room_id=? AND (r.approval_status='approved' OR r.landlord_id=? OR ?=1)")) {
                q.setInt(1,id);q.setInt(2,owner);q.setInt(3,admin?1:0);
                try(ResultSet r=q.executeQuery()) {
                    if(!r.next()){res.sendError(404);return;}
                    List<String> parts=new ArrayList<>();Set<String> seen=new HashSet<>();
                    for(int i=1;i<=3;i++){String v=Objects.toString(r.getString(i),"").trim();if(!v.isEmpty()&&seen.add(v.toLowerCase(Locale.ROOT)))parts.add(v);}
                    if(parts.isEmpty()){res.sendError(404);return;}
                    res.sendRedirect("https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(String.join(", ",parts),StandardCharsets.UTF_8));
                }
            }
        }catch(SQLException e){getServletContext().log("Listing map unavailable",e);res.sendError(503);}
    }
}
