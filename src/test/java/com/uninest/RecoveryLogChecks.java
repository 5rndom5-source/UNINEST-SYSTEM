package com.uninest;

import com.uninest.utils.RecoveryService;

public class RecoveryLogChecks {
    static int checks;
    static void check(boolean value,String label) { if(!value)throw new AssertionError(label);checks++; }
    public static void main(String[] args) {
        String token="AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-AbCdE";
        check(RecoveryService.providerReason(null).isEmpty(),"missing body logs nothing");
        check(RecoveryService.providerReason("not json").isEmpty(),"unknown body logs nothing");
        check(RecoveryService.providerReason("{\"name\":\"validation_error\",\"message\":\"The domain is not verified.\"}")
                .equals(": The domain is not verified."),"provider reason is kept");
        String echoed=RecoveryService.providerReason("{\"message\":\"Bad body https://x.onrender.com/uninest/ResetPasswordServlet?token="+token+" end\"}");
        check(!echoed.contains(token),"reset link token is removed");
        check(!echoed.contains("https://"),"links are removed");
        check(!RecoveryService.providerReason("{\"message\":\"token="+token+"\"}").contains(token),"bare token is removed");
        check(!RecoveryService.providerReason("{\"message\":\"key "+token+"\"}").contains(token),"long secrets are removed");
        String longReason="x".repeat(500);
        check(RecoveryService.providerReason("{\"message\":\""+longReason+"\"}").length()<=202,"reason length is capped");
        check(!RecoveryService.providerReason("{\"text\":\"Reset link "+token+"\",\"x\":1}").contains(token),"other fields are never logged");
        System.out.println("PASS: "+checks+" password-recovery log checks");
    }
}
