package com.uninest;

import com.uninest.utils.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import javax.servlet.http.Part;

public final class AttachmentChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory=ImageStorage.directory("message-attachments");
        if(!directory.toString().contains("attachment-checks"))throw new IllegalStateException("Isolated upload directory required");
        for(String type:new String[]{"image/png","image/jpeg","image/webp","application/pdf"}) {
            reject(part("<script>not an attachment</script>".getBytes(),type,12,"fake"),"forged "+type);
        }
        reject(part(new byte[0],"text/html",10,"bad.html"),"unsupported MIME");
        reject(part(new byte[(int)MessageAttachmentSupport.MAX_FILE_SIZE+1],"application/pdf",10,"big.pdf"),"actual stream size enforced");
        check(MessageAttachmentSupport.resolve("../secret")==null,"parent traversal rejected");
        check(MessageAttachmentSupport.resolve("..\\secret")==null,"Windows traversal rejected");
        check(MessageAttachmentSupport.resolve("bad\0name")==null,"invalid path rejected");
        byte[] pdf="%PDF-1.4\nTest fixture only\n%%EOF".getBytes();
        String name=MessageAttachmentSupport.save(part(pdf,"application/pdf",pdf.length,"C:\\fakepath\\demo.pdf"),123);
        try {
            check(MessageAttachmentSupport.resolve(name).getParent().equals(directory),"configured upload directory used");
            check(Files.size(MessageAttachmentSupport.resolve(name))==pdf.length,"accepted signature saved");
            check("demo.pdf".equals(MessageAttachmentSupport.displayName(name)),"browser path removed");
        } finally { MessageAttachmentSupport.delete(name); }
        check(!Files.exists(MessageAttachmentSupport.resolve(name)),"attachment cleanup");
        System.out.println("PASS: "+checks+" attachment checks");
    }
    private static void reject(Part part,String label)throws Exception {
        boolean rejected=false;
        try{MessageAttachmentSupport.save(part,123);}catch(IOException|IllegalArgumentException expected){rejected=true;}
        check(rejected,label);
    }
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    private static Part part(byte[] bytes,String type,long reported,String name) {
        return (Part)Proxy.newProxyInstance(Part.class.getClassLoader(),new Class[]{Part.class},(p,m,a)->{
            switch(m.getName()) {
                case "getSize":return reported;
                case "getContentType":return type;
                case "getSubmittedFileName":return name;
                case "getInputStream":return new ByteArrayInputStream(bytes);
                default:return null;
            }
        });
    }
}
