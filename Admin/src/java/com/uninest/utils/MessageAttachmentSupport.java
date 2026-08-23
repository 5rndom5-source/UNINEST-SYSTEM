package com.uninest.utils;

import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

        String original = safeOriginalName(part.getSubmittedFileName(), extension);
        String storedName = messageId + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "_" + original;
        Path directory = directory();
        Files.createDirectories(directory);
        Path destination = directory.resolve(storedName).normalize();
        if (!directory.equals(destination.getParent())) {
            throw new IOException("Invalid attachment path");
        }

        try (InputStream input = part.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return storedName;
    }

    public static Path resolve(String storedName) {
        if (storedName == null || storedName.trim().isEmpty()) return null;
        Path directory = directory();
        Path candidate = directory.resolve(storedName).normalize();
        return directory.equals(candidate.getParent()) ? candidate : null;
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
        return Path.of(System.getProperty("user.home"), ".uninest", "message-attachments")
                .toAbsolutePath().normalize();
    }

    private static String safeOriginalName(String submittedName, String extension) {
        String fileName = submittedName == null ? "attachment." + extension
                : Path.of(submittedName).getFileName().toString();
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
}
