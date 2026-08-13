package com.uninest.servlets.account;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@WebServlet("/ProfileImageServlet")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class ProfileImageServlet extends HttpServlet {
    private static final String[] EXTENSIONS = {"jpg", "png", "webp"};

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = authenticatedUserId(request, response);
        if (userId == null) return;

        Path image = findImage(userId);
        if (image == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String type = Files.probeContentType(image);
        response.setContentType(type == null ? "image/jpeg" : type);
        response.setHeader("Cache-Control", "no-store");
        response.setContentLengthLong(Files.size(image));
        Files.copy(image, response.getOutputStream());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        Integer userId = authenticatedUserId(request, response);
        if (userId == null) return;

        Part photo = request.getPart("photo");
        String extension = extensionFor(photo == null ? null : photo.getContentType());
        if (photo == null || photo.getSize() == 0 || extension == null) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Please choose a JPG, PNG, or WebP image");
            return;
        }

        Path directory = imageDirectory();
        Files.createDirectories(directory);
        for (String oldExtension : EXTENSIONS) {
            Files.deleteIfExists(directory.resolve(userId + "." + oldExtension));
        }
        Path destination = directory.resolve(userId + "." + extension);
        Files.copy(photo.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        sendJson(response, HttpServletResponse.SC_OK, "Profile picture updated");
    }

    private Integer authenticatedUserId(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Integer)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        return (Integer) session.getAttribute("userId");
    }

    private Path findImage(int userId) {
        Path directory = imageDirectory();
        for (String extension : EXTENSIONS) {
            Path candidate = directory.resolve(userId + "." + extension);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private Path imageDirectory() {
        return Path.of(System.getProperty("user.home"), ".uninest", "profile-images");
    }

    private String extensionFor(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) return "jpg";
        if ("image/png".equalsIgnoreCase(contentType)) return "png";
        if ("image/webp".equalsIgnoreCase(contentType)) return "webp";
        return null;
    }

    private void sendJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String safe = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().print("{\"message\":\"" + safe + "\"}");
    }
}
