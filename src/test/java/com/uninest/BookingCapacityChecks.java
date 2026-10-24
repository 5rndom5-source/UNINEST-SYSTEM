package com.uninest;
import com.uninest.utils.BookingCapacity;
import java.time.LocalDate;
import java.util.*;
public class BookingCapacityChecks {
    static LocalDate d(int day) { return LocalDate.of(2030,1,day); }
    static LocalDate[] stay(int start,int end) { return new LocalDate[]{d(start),end==0?null:d(end)}; }
    static int checks;
    static void check(boolean result,String name){if(!result)throw new AssertionError(name);checks++;}
    public static void main(String[] args) {
        List<LocalDate[]> adjacent=Arrays.asList(stay(1,10),stay(10,20));
        check(BookingCapacity.canFit(2,d(1),d(20),adjacent),"adjacent occupants not simultaneous");
        check(!BookingCapacity.canFit(1,d(1),d(20),adjacent),"single bed full");
        check(!BookingCapacity.canFit(2,d(1),d(20),Arrays.asList(stay(1,11),stay(10,20))),"true peak exceeds capacity");
        check(BookingCapacity.canFit(1,d(10),d(20),Collections.singletonList(stay(1,10))),"same-day turnover");
        check(BookingCapacity.canFit(1,d(10),d(20),Collections.singletonList(stay(20,25))),"checkout excluded");
        check(!BookingCapacity.canFit(1,d(10),null,Collections.singletonList(stay(20,0))),"open-ended future conflict");
        check(BookingCapacity.canFit(2,d(10),null,Collections.singletonList(stay(1,0))),"remaining bed open-ended");
        check(!BookingCapacity.canFit(2,d(10),d(10),Collections.emptyList()),"zero-night stay invalid");
        check(!BookingCapacity.canFit(0,d(1),d(2),Collections.emptyList()),"zero capacity invalid");
        check(!BookingCapacity.canFit(2,d(10),d(5),Collections.emptyList()),"reversed dates invalid");
        check(!BookingCapacity.canFit(2,d(1),d(20),Collections.singletonList(stay(10,5))),"malformed stay fails closed");
        System.out.println("PASS: "+checks+" booking capacity checks");
    }
}
