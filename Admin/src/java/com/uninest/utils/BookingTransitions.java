package com.uninest.utils;
import java.sql.*;

public final class BookingTransitions {
    private BookingTransitions() {}
    public static int update(Connection conn,int bookingId,Integer landlordId,String status,String reason) throws SQLException {
        return update(conn,bookingId,landlordId,null,status,reason);
    }
    public static int cancelByStudent(Connection conn,int bookingId,int studentId,String reason) throws SQLException {
        return update(conn,bookingId,null,studentId,"cancelled",reason);
    }
    private static int update(Connection conn,int bookingId,Integer landlordId,Integer studentId,String status,String reason) throws SQLException {
        if(!conn.getAutoCommit())throw new SQLException("Booking transaction already active");
        if(reason!=null && reason.length()>255)throw new IllegalArgumentException("Cancellation reason must be 255 characters or fewer");
        conn.setAutoCommit(false);
        try {
            int roomId;
            try(PreparedStatement query=conn.prepareStatement("SELECT room_id FROM bookings WHERE booking_id=?")) {
                query.setInt(1,bookingId);try(ResultSet rows=query.executeQuery()){if(!rows.next()){conn.rollback();return 0;}roomId=rows.getInt(1);}
            }
            int capacity;
            try(PreparedStatement query=conn.prepareStatement("SELECT capacity,landlord_id,approval_status,availability_status FROM rooms WHERE room_id=? FOR UPDATE")) {
                query.setInt(1,roomId);
                try(ResultSet rows=query.executeQuery()) {
                    if(!rows.next() || (landlordId!=null && rows.getInt(2)!=landlordId)){conn.rollback();return 0;}
                    if("confirmed".equals(status) && (!"approved".equals(rows.getString(3)) || !"available".equals(rows.getString(4)))){conn.rollback();return 0;}
                    capacity=rows.getInt(1);
                }
            }
            String old; Date start,end;
            try(PreparedStatement query=conn.prepareStatement("SELECT booking_status,check_in,check_out,student_id FROM bookings WHERE booking_id=? FOR UPDATE")) {
                query.setInt(1,bookingId);
                try(ResultSet rows=query.executeQuery()){if(!rows.next() || (studentId!=null && rows.getInt(4)!=studentId)){conn.rollback();return 0;}old=rows.getString(1);start=rows.getDate(2);end=rows.getDate(3);}
            }
            boolean allowed=("confirmed".equals(status)&&"pending".equals(old))
                    || ("cancelled".equals(status)&&("pending".equals(old)||"confirmed".equals(old)))
                    || ("completed".equals(status)&&"confirmed".equals(old));
            if(!allowed){conn.rollback();return 0;}
            if("confirmed".equals(status)) {
                try(PreparedStatement query=conn.prepareStatement("SELECT check_in,check_out FROM bookings WHERE room_id=? AND booking_id<>? AND booking_status='confirmed' FOR UPDATE")) {
                    query.setInt(1,roomId);query.setInt(2,bookingId);
                    java.util.List<java.time.LocalDate[]> stays=new java.util.ArrayList<>();
                    try(ResultSet rows=query.executeQuery()) {
                        while(rows.next()) {
                            Date from=rows.getDate(1),until=rows.getDate(2);
                            stays.add(new java.time.LocalDate[]{from==null?null:from.toLocalDate(),until==null?null:until.toLocalDate()});
                        }
                    }
                    if(!BookingCapacity.canFit(capacity,start==null?null:start.toLocalDate(),end==null?null:end.toLocalDate(),stays)) {
                        conn.rollback();return 0;
                    }
                }
            }
            try(PreparedStatement update=conn.prepareStatement("UPDATE bookings SET booking_status=?,cancellation_reason=? WHERE booking_id=?")) {
                update.setString(1,status);update.setString(2,"cancelled".equals(status)?reason:null);update.setInt(3,bookingId);update.executeUpdate();
            }
            try(PreparedStatement insert=conn.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,action_url) SELECT s.user_id,'Booking updated',?,'booking_update','UserDashboard/requests.html' FROM bookings b JOIN students s ON s.student_id=b.student_id WHERE b.booking_id=?")) {
                insert.setString(1,"Your booking is now "+status);insert.setInt(2,bookingId);insert.executeUpdate();
            }
            if("confirmed".equals(status)) {
                try(PreparedStatement insert=conn.prepareStatement("INSERT INTO tenancy_records(booking_id,tenancy_start,tenancy_end,tenancy_status) SELECT booking_id,check_in,check_out,'active' FROM bookings WHERE booking_id=? AND NOT EXISTS(SELECT 1 FROM tenancy_records WHERE booking_id=?)")) {
                    insert.setInt(1,bookingId);insert.setInt(2,bookingId);insert.executeUpdate();
                }
            } else {
                try(PreparedStatement update=conn.prepareStatement("UPDATE tenancy_records SET tenancy_status=? WHERE booking_id=? AND tenancy_status='active'")) {
                    update.setString(1,"cancelled".equals(status)?"terminated":"completed");update.setInt(2,bookingId);update.executeUpdate();
                }
            }
            if(studentId!=null) {
                try(PreparedStatement insert=conn.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,action_url) SELECT l.user_id,'Booking cancelled','The student cancelled their booking. Review any payment history separately.','booking_update','Landlord/booking-requests.html' FROM bookings b JOIN rooms r ON r.room_id=b.room_id JOIN landlords l ON l.landlord_id=r.landlord_id WHERE b.booking_id=?")) {
                    insert.setInt(1,bookingId);insert.executeUpdate();
                }
            }
            conn.commit();return 1;
        }catch(SQLException e){conn.rollback();throw e;}
        finally{conn.setAutoCommit(true);}
    }
}
