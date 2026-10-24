package com.uninest;

import com.uninest.utils.LoginThrottle;
import com.uninest.servlets.system.RequestLifecycleFilter;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.servlet.http.*;

public class LoginThrottleChecks {
    private static int checks;
    private static void check(boolean value,String label) { if(!value)throw new AssertionError(label);checks++; }
    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    public static void main(String[] args) throws Exception {
        AtomicLong now=new AtomicLong();
        LoginThrottle limiter=new LoginThrottle(3,2,1000,100,now::get);
        check(limiter.retryAfterSeconds("ip1"," User@example.com ")==0,"first attempt allowed");
        check(limiter.retryAfterSeconds("ip2","user@EXAMPLE.com")==0,"normalized account shared across IPs");
        check(limiter.retryAfterSeconds("ip3","user@example.com")==1,"account limit enforced");
        check(limiter.retryAfterSeconds("ip1","other@example.com")==0,"other account unaffected");
        check(limiter.retryAfterSeconds("ip1","third@example.com")==0,"IP last allowed slot");
        check(limiter.retryAfterSeconds("ip1","fourth@example.com")==1,"rotating accounts cannot bypass IP limit");
        now.set(999);
        check(limiter.retryAfterSeconds("ip3","user@example.com")==1,"retry rounded up");
        now.set(1000);
        check(limiter.retryAfterSeconds("ip1","user@example.com")==0,"window expiry allows recovery");
        LoginThrottle bounded=new LoginThrottle(2,2,1000,2,now::get);
        check(bounded.retryAfterSeconds("ip","one")==0,"bounded map initial pair");
        check(bounded.retryAfterSeconds("new-ip","new-account")>0,"saturated map fails closed");
        check(bounded.retryAfterSeconds("ip","one")==0,"saturation does not evict active limits");
        check(bounded.retryAfterSeconds("ip","one")>0,"existing count retained");
        now.set(2000);
        check(bounded.retryAfterSeconds("new-ip","new-account")==0,"expired entries reclaimed");

        LoginThrottle concurrent=new LoginThrottle(5,5,1000,100,now::get);
        ExecutorService pool=Executors.newFixedThreadPool(8);
        AtomicInteger accepted=new AtomicInteger();
        try {
            List<Future<?>> tasks=new ArrayList<>();
            for(int i=0;i<30;i++) tasks.add(pool.submit(()->{if(concurrent.retryAfterSeconds("same","same")==0)accepted.incrementAndGet();}));
            for(Future<?> task:tasks)task.get();
        } finally {pool.shutdownNow();}
        check(accepted.get()==5,"concurrent attempts cannot overrun limit");
        check(!LoginThrottle.isLoginPath("/LogoutServlet"),"logout never throttled");
        check(!LoginThrottle.isLoginPath("/ResetPasswordServlet"),"recovery remains separately available");

        RequestLifecycleFilter filter=new RequestLifecycleFilter(new LoginThrottle(20,2,1000,100,now::get));
        String[] endpoints={"/LoginServlet","/AccountLoginServlet","/LandlordLoginServlet"};
        AtomicInteger called=new AtomicInteger();
        for(int i=0;i<endpoints.length;i++) {
            final String endpoint=endpoints[i];
            Map<String,String> headers=new HashMap<>();
            AtomicInteger status=new AtomicInteger(200);
            StringWriter body=new StringWriter();
            HttpServletRequest request=mock(HttpServletRequest.class,(p,m,a)->{
                switch(m.getName()) {
                    case "getMethod": return "POST";
                    case "getServletPath": return endpoint;
                    case "getRemoteAddr": return "127.0.0.1";
                    case "getContextPath": return "/uninest";
                    case "getSession": return mock(HttpSession.class,(sp,sm,sa)->sm.getName().equals("getAttribute") && "uninest.csrf".equals(sa[0]) ? "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" : null);
                    case "getHeader": return "X-CSRF-Token".equals(a[0]) ? "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" : null;
                    case "getParameter": return (endpoint.equals("/LandlordLoginServlet")?"landlordEmail":"email").equals(a[0])?"same@example.invalid":null;
                    default: return null;
                }
            });
            HttpServletResponse response=mock(HttpServletResponse.class,(p,m,a)->{
                if(m.getName().equals("setHeader"))headers.put((String)a[0],(String)a[1]);
                if(m.getName().equals("setStatus"))status.set((Integer)a[0]);
                if(m.getName().equals("getWriter"))return new PrintWriter(body);
                return null;
            });
            filter.doFilter(request,response,(q,s)->called.incrementAndGet());
            check(status.get()==(i<2?200:429),"shared endpoint limit "+endpoint);
            if(i==2) {
                check("1".equals(headers.get("Retry-After")),"HTTP retry header");
                check(body.toString().contains("/uninest/createAccount/login.html"),"role chooser remains available");
                check(!body.toString().contains("same@example.invalid"),"blocked response does not echo identifier");
            }
        }
        check(called.get()==2,"blocked request never reaches authentication");
        System.out.println("PASS: "+checks+" login throttle checks");
    }
}
