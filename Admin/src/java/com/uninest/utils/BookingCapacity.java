package com.uninest.utils;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

/** Peak occupancy for half-open stays: [check-in, check-out). */
public final class BookingCapacity {
    private BookingCapacity() { }
    public static boolean canFit(int capacity, LocalDate start, LocalDate end, List<LocalDate[]> stays) {
        if (capacity < 1 || start == null || (end != null && !end.isAfter(start))) return false;
        TreeMap<LocalDate,Integer> events = new TreeMap<>();
        for (LocalDate[] stay : stays) {
            LocalDate from=stay[0], until=stay[1];
            if (from==null || (until!=null && !until.isAfter(from))) return false;
            if ((end!=null && !from.isBefore(end)) || (until!=null && !until.isAfter(start))) continue;
            if (from.isBefore(start)) from=start;
            if (end!=null && (until==null || until.isAfter(end))) until=end;
            events.merge(from,1,Integer::sum);
            if(until!=null) events.merge(until,-1,Integer::sum);
        }
        int occupied=0;
        for(int change:events.values()) { occupied+=change; if(occupied>=capacity)return false; }
        return true;
    }
}
