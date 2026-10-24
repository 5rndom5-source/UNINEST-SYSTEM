package com.uninest;
import com.uninest.utils.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.servlet.http.Part;

/** Dependency-free regression checks; run with -ea using tools/check-upgrades.ps1. */
public class UpgradeChecks {
    public static void main(String[] args) throws Exception {
        String password="ValidTest!2026";
        String hash=PasswordUtil.hashPassword(password);
        check(hash.startsWith("pbkdf2$600000$"),"new password format");
        check(PasswordUtil.verifyPassword(password,hash),"new password verification");
        check(!PasswordUtil.verifyPassword("wrong",hash),"wrong password rejected");
        check(!PasswordUtil.verifyPassword(password,"bad-hash"),"malformed hash rejected");
        byte[] salt=new byte[16]; MessageDigest digest=MessageDigest.getInstance("SHA-256");
        digest.update(salt); byte[] old=new byte[48]; System.arraycopy(digest.digest(password.getBytes()),0,old,16,32);
        check(PasswordUtil.verifyPassword(password,Base64.getEncoder().encodeToString(old)),"legacy password verification");
        check(RecoveryService.hash("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"reset-token hash");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12,8,BufferedImage.TYPE_INT_RGB),"png",bytes);
        check(ImageStorage.decode(part(bytes.toByteArray())).getWidth()==12,"PNG decode");
        boolean invalid=false;
        try{ImageStorage.decode(part("not an image".getBytes()));}catch(IOException e){invalid=true;}
        check(invalid,"forged image rejected");
        Path folder=Files.createTempDirectory("uninest-check-");
        Path image=folder.resolve("avatar.png");
        ImageStorage.write(new BufferedImage(12,8,BufferedImage.TYPE_INT_RGB),image);
        ImageStorage.write(new BufferedImage(8,12,BufferedImage.TYPE_INT_RGB),image);
        check(ImageIO.read(image.toFile()).getHeight()==12,"atomic image replacement");
        Files.delete(image);Files.delete(folder);
        System.out.println("PASS: 9 upgrade regression checks");
    }
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);}
    static Part part(byte[] bytes){return (Part)Proxy.newProxyInstance(Part.class.getClassLoader(),new Class[]{Part.class},(object,method,args)->{
        if(method.getName().equals("getSize"))return (long)bytes.length;
        if(method.getName().equals("getInputStream"))return new ByteArrayInputStream(bytes);
        return null;
    });}
}
