package com.uninest.utils;

import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public final class MessageAttachmentSupport {
    public static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private MessageAttachmentSupport() {
    }

    public static String save(Part part, int messageId) throws IOException {
        if (part == null || part.getSize() == 0) return null;
        if (part.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Attachments must be 5 MB or smaller");
        }

        String extension = extensionFor(part.getContentType());
        if (extension == null) {
            throw new IllegalArgumentException("Only JPG, PNG, WebP, and PDF attachments are allowed");
        }

        byte[] bytes;
        try (InputStream input = part.getInputStream()) {
            bytes = input.readNBytes((int) MAX_FILE_SIZE + 1);
        }
        if (bytes.length > MAX_FILE_SIZE) throw new IOException("Attachments must be 5 MB or smaller");
        if (!matchesSignature(bytes, extension)) throw new IOException("The attachment content does not match its file type");
        String original = safeOriginalName(part.getSubmittedFileName(), extension);
        String storedName = messageId + "_" + UUID.randomUUID().toString()
                + "_" + original;
        Path directory = directory();
        Files.createDirectories(directory);
        Path destination = directory.resolve(storedName).normalize();
        if (!directory.equals(destination.getParent())) {
            throw new IOException("Invalid attachment path");
        }

        try {
            Files.write(destination, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException collision) {
            throw collision;
        } catch (IOException e) {
            try { Files.deleteIfExists(destination); }
            catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        return storedName;
    }

    public static Path resolve(String storedName) {
        if (storedName == null || storedName.trim().isEmpty()) return null;
        if (storedName.contains("/") || storedName.contains("\\") || storedName.indexOf('\0') >= 0) return null;
        Path directory = directory();
        try {
            Path candidate = directory.resolve(storedName).normalize();
            return directory.equals(candidate.getParent()) ? candidate : null;
        } catch (java.nio.file.InvalidPathException invalid) { return null; }
    }

    public static void delete(String storedName) {
        Path path = resolve(storedName);
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The database record is the source of truth; an orphan file can be cleaned later.
        }
    }

    public static String displayName(String storedName) {
        if (storedName == null) return "";
        int first = storedName.indexOf('_');
        int second = first < 0 ? -1 : storedName.indexOf('_', first + 1);
        return second < 0 ? storedName : storedName.substring(second + 1);
    }

    private static Path directory() {
        return ImageStorage.directory("message-attachments");
    }

    private static String safeOriginalName(String submittedName, String extension) {
        String fileName = submittedName == null ? "attachment." + extension : submittedName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (fileName.length() > 80) {
            int dot = fileName.lastIndexOf('.');
            String suffix = dot >= 0 ? fileName.substring(dot) : "." + extension;
            fileName = fileName.substring(0, Math.min(70, fileName.length())) + suffix;
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            fileName += "." + extension;
        }
        return fileName;
    }

    private static String extensionFor(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) return "jpg";
        if ("image/png".equalsIgnoreCase(contentType)) return "png";
        if ("image/webp".equalsIgnoreCase(contentType)) return "webp";
        if ("application/pdf".equalsIgnoreCase(contentType)) return "pdf";
        return null;
    }

    // Signature checks reject obvious type spoofing; they are not malware scanning.
    private static boolean matchesSignature(byte[] bytes, String extension) {
        if ("png".equals(extension)) return starts(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10}, 0);
        if ("jpg".equals(extension)) return starts(bytes, new byte[]{(byte)255,(byte)216,(byte)255}, 0);
        if ("pdf".equals(extension)) return starts(bytes, new byte[]{37,80,68,70,45}, 0);
        return "webp".equals(extension) && starts(bytes, new byte[]{82,73,70,70}, 0)
                && starts(bytes, new byte[]{87,69,66,80}, 8);
    }
    private static boolean starts(byte[] bytes, byte[] signature, int offset) {
        if (bytes.length < offset + signature.length) return false;
        for (int i=0;i<signature.length;i++) if (bytes[offset+i]!=signature[i]) return false;
        return true;
    }
}
