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
        String[] emailReasons={
            "You can only send testing emails to your own email address (owner@example.com).",
            "Invalid `to` field: Student.Name+uninest@mail.example.co.za",
            "From UniNest <noreply@mail.uninest.co.za> is not verified",
            "Recipient o'brien@example.com rejected",
            "Encoded owner\\u0040example.com",
            "Encoded owner%40example.com",
            "Encoded owner&#64;example.com",
            "Upper case OWNER@EXAMPLE.COM"
        };
        for(String reason:emailReasons) {
            String logged=RecoveryService.providerReason("{\"message\":\""+reason+"\"}");
            check(!logged.contains("@")&&!logged.toLowerCase().contains("example")
                    &&!logged.contains("uninest.co.za")&&!logged.contains("u0040")&&!logged.contains("%40")
                    &&!logged.contains("&#64;"),"email removed: "+reason);
            check(logged.contains("[email removed]"),"redaction is marked: "+reason);
        }
        String nearLimit="{\"message\":\""+"x".repeat(185)+" owner@example.com\"}";
        check(!RecoveryService.providerReason(nearLimit).contains("owner"),"email cut by length limit is removed");
        check(RecoveryService.providerReason("{\"message\":\"Domain not verified. Contact owner@example.com\"}")
                .startsWith(": Domain not verified."),"reason text around an email is kept");
        System.out.println("PASS: "+checks+" password-recovery log checks");
    }
}
