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

@WebServlet("/ProfileImageServlet")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class ProfileImageServlet extends HttpServlet {
    private static final String[] EXTENSIONS = {"png", "jpg", "webp"};

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = authenticatedUserId(request, response);
        if (userId == null) return;

        Path image = findImage(userId);
        if (image == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String name = image.getFileName().toString();
        response.setContentType(name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(image));
        Files.copy(image, response.getOutputStream());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        Integer userId = authenticatedUserId(request, response);
        if (userId == null) return;

        try {
        Part photo = request.getPart("photo");
        String extension = extensionFor(photo == null ? null : photo.getContentType());
        if (photo == null || photo.getSize() == 0 || extension == null) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Please choose a JPG or PNG image");
            return;
        }

        Path destination = imageDirectory().resolve(userId + ".png");
        com.uninest.utils.ImageStorage.write(com.uninest.utils.ImageStorage.decode(photo), destination);
        sendJson(response, HttpServletResponse.SC_OK, "Profile picture updated");
        } catch (IllegalStateException e) {
            sendJson(response, 413, "Profile pictures must be 5 MB or smaller");
        } catch (IOException | ServletException e) {
            getServletContext().log("Profile image upload failed", e);
            sendJson(response, 400, "Upload failed. Choose a valid JPEG or PNG up to 5 MB; contact support if it persists.");
        }
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
        return com.uninest.utils.ImageStorage.directory("profile-images");
    }

    private String extensionFor(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) return "jpg";
        if ("image/jpg".equalsIgnoreCase(contentType)) return "jpg";
        if ("image/png".equalsIgnoreCase(contentType)) return "png";
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
