package com.uninest.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded single-instance limiter. Counts attempts before expensive password verification. */
public final class LoginThrottle {
    private static final class Bucket {
        final long expiresAt;
        int attempts;
        Bucket(long expiresAt) { this.expiresAt=expiresAt; }
    }
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final int ipLimit,accountLimit,maxKeys;
    private final long windowMillis;
    private final LongSupplier clock;
    private long nextCleanup;

    public LoginThrottle() {
        this(60,10,15*60*1000L,10000,()->System.nanoTime()/1000000L);
    }

    public LoginThrottle(int ipLimit,int accountLimit,long windowMillis,int maxKeys,LongSupplier clock) {
        if(ipLimit<1 || accountLimit<1 || windowMillis<1 || maxKeys<2) throw new IllegalArgumentException("Invalid throttle limits");
        this.ipLimit=ipLimit;this.accountLimit=accountLimit;this.windowMillis=windowMillis;
        this.maxKeys=maxKeys;this.clock=Objects.requireNonNull(clock);
    }

    public static boolean isLoginPath(String path) {
        return "/LoginServlet".equals(path) || "/AccountLoginServlet".equals(path) || "/LandlordLoginServlet".equals(path);
    }

    /** Zero means allowed; positive values are seconds until a retry can be attempted. */
    public synchronized long retryAfterSeconds(String address,String email) {
        long now=clock.getAsLong();
        if(now>=nextCleanup || buckets.size()>=maxKeys) {
            buckets.values().removeIf(bucket->bucket.expiresAt<=now);
            nextCleanup=now+60000;
        }
        String ip="ip:"+digest(address==null?"unknown":address);
        String normalized=email==null?"":email.trim().toLowerCase(Locale.ROOT);
        String account=normalized.isEmpty()?null:"account:"+digest(normalized);
        Bucket ipBucket=buckets.get(ip),accountBucket=account==null?null:buckets.get(account);
        long wait=Math.max(waitFor(ipBucket,ipLimit,now),waitFor(accountBucket,accountLimit,now));
        if(wait>0) return wait;
        int newKeys=(ipBucket==null?1:0)+(account!=null && accountBucket==null?1:0);
        // Do not evict active limits: rotating arbitrary account names must not reset them.
        if(buckets.size()+newKeys>maxKeys) return 60;
        increment(ip,now);
        if(account!=null) increment(account,now);
        return 0;
    }
    private long waitFor(Bucket bucket,int limit,long now) {
        return bucket!=null && bucket.expiresAt>now && bucket.attempts>=limit
                ? Math.max(1,(bucket.expiresAt-now+999)/1000):0;
    }
    private void increment(String key,long now) {
        Bucket bucket=buckets.get(key);
        if(bucket==null || bucket.expiresAt<=now) { bucket=new Bucket(now+windowMillis);buckets.put(key,bucket); }
        bucket.attempts++;
    }
    private static String digest(String text) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
