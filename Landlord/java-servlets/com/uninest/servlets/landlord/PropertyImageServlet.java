package com.uninest.servlets.landlord;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet("/PropertyImageServlet/*")
public class PropertyImageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        String fileName = pathInfo == null ? "" : Path.of(pathInfo).getFileName().toString();
        if (fileName.isEmpty() || !fileName.matches("[A-Za-z0-9._-]+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Path root = Path.of(System.getProperty("user.home"), ".uninest", "property-images");
        Path image = root.resolve(fileName).normalize();
        if (!image.getParent().equals(root) || !Files.isRegularFile(image)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String type = Files.probeContentType(image);
        if (type == null || !type.startsWith("image/")) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        response.setContentType(type);
        response.setHeader("Cache-Control", "public, max-age=86400");
        response.setContentLengthLong(Files.size(image));
        Files.copy(image, response.getOutputStream());
    }
}
