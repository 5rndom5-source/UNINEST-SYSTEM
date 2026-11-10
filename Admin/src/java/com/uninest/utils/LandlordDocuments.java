package com.uninest.utils;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.servlet.ServletContext;
import javax.servlet.http.Part;

/** Identity files are never stored under the deployed website or served by a public URL. */
public final class LandlordDocuments {
    private static final String PREFIX = "private-landlord:";
    private LandlordDocuments() { }

    private static Path privateRoot(ServletContext context) throws IOException {
        Path root = ImageStorage.directory("landlord-documents");
        Files.createDirectories(root);
        root = root.toRealPath();
        String web = context.getRealPath("/");
        if (web != null && root.startsWith(Path.of(web).toRealPath()))
            throw new IOException("Private document storage must be outside the website");
        return root;
    }

    public static String save(Part part, ServletContext context) throws IOException {
        if (part == null || part.getSize() == 0) return null;
        if (part.getSize() > 5 * 1024 * 1024) throw new IOException("Choose a JPEG, PNG or PDF up to 5 MB.");
        byte[] bytes;
        try (InputStream in = part.getInputStream()) { bytes = in.readNBytes(5 * 1024 * 1024 + 1); }
        if (bytes.length > 5 * 1024 * 1024) throw new IOException("Choose a file up to 5 MB.");
        boolean pdf = bytes.length >= 5 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F' && bytes[4]=='-';
        String name = UUID.randomUUID() + (pdf ? ".pdf" : ".png");
        Path target = privateRoot(context).resolve(name);
        if (pdf) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        else ImageStorage.write(ImageStorage.decode(part), target);
        return PREFIX + name;
    }

    public static Path resolve(String reference, ServletContext context) throws IOException {
        if (reference == null) return null;
        Path root;
        String name;
        if (reference.startsWith(PREFIX)) {
            name = reference.substring(PREFIX.length());
            if (!name.matches("[a-f0-9-]{36}\\.(png|pdf)")) return null;
            root = privateRoot(context);
        } else if (reference.startsWith("/uploads/")) {
            // Read existing files through the authenticated endpoint only; do not relocate production data.
            name = reference.substring("/uploads/".length());
            String web = context.getRealPath("/");
            if (web == null) return null;
            root = Path.of(web).resolve("uploads");
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) return null;
            root = root.toRealPath();
        } else return null;
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("..")) return null;
        Path file = root.resolve(name).normalize();
        if (!file.getParent().equals(root) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)) return null;
        return file.toRealPath().startsWith(root) ? file : null;
    }

    public static void discardNew(String reference, ServletContext context) throws IOException {
        if (reference != null && reference.startsWith(PREFIX)) {
            Path file = resolve(reference, context);
            if (file != null) Files.deleteIfExists(file);
        }
    }
}
