package com.uninest;

import com.uninest.utils.CsrfToken;
import com.uninest.servlets.system.RequestLifecycleFilter;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import javax.servlet.http.*;

public class CsrfChecks {
    static int checks;
    static void check(boolean value,String label) { if(!value)throw new AssertionError(label);checks++; }
    @SuppressWarnings("unchecked")
    static <T> T mock(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    public static void main(String[] args) throws Exception {
        Map<String,Object> attributes=new HashMap<>();
        Map<String,String> headers=new HashMap<>();
        String[] field={null},query={null};
        HttpSession session=mock(HttpSession.class,(p,m,a)->{
            if(m.getName().equals("getAttribute"))return attributes.get(a[0]);
            if(m.getName().equals("setAttribute"))attributes.put((String)a[0],a[1]);
            return null;
        });
        HttpServletRequest request=mock(HttpServletRequest.class,(p,m,a)->{
            switch(m.getName()) {
                case "getSession":return session;
                case "getMethod":return "POST";
                case "getServletPath":return "/example";
                case "getHeader":return headers.get(a[0]);
                case "getParameter":return field[0];
                case "getQueryString":return query[0];
                default:return null;
            }
        });
        String token=CsrfToken.issue(request);
        check(token.matches("[A-Za-z0-9_-]{43}"),"secure token format");
        check(token.equals(CsrfToken.issue(request)),"stable within session");
        check(!CsrfToken.valid(request),"missing token rejected");
        headers.put("X-CSRF-Token",token);
        check(CsrfToken.valid(request),"header accepted");
        headers.clear();field[0]=token;
        check(CsrfToken.valid(request),"native form accepted");
        query[0]="%5fcsrf="+token;
        check(!CsrfToken.valid(request),"encoded URL token rejected");
        query[0]=null;attributes.clear();CsrfToken.issue(request);
        check(!CsrfToken.valid(request),"other session token rejected");
        field[0]=null;
        int[] status={200};boolean[] called={false};StringWriter body=new StringWriter();
        HttpServletResponse response=mock(HttpServletResponse.class,(p,m,a)->{
            if(m.getName().equals("setStatus"))status[0]=(Integer)a[0];
            if(m.getName().equals("getWriter"))return new PrintWriter(body);
            return null;
        });
        new RequestLifecycleFilter().doFilter(request,response,(q,s)->called[0]=true);
        check(status[0]==403 && !called[0],"filter rejects before application code");
        check(body.toString().contains("CSRF_INVALID"),"clear rejection response");
        check(!CsrfToken.unsafe("GET") && !CsrfToken.unsafe("HEAD"),"reads allowed");
        check(CsrfToken.unsafe("POST") && CsrfToken.unsafe("DELETE") && CsrfToken.unsafe("PATCH"),"write methods protected");
        System.out.println("PASS: "+checks+" CSRF checks");
    }
}
