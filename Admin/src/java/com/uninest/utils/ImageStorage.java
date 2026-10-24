package com.uninest.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.http.Part;

public final class ImageStorage {
    private ImageStorage() {}

    public static Path directory(String name) {
        String configured = System.getenv("UNINEST_UPLOAD_DIR");
        Path root = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".uninest") : Path.of(configured);
        return root.toAbsolutePath().normalize().resolve(name);
    }

    public static BufferedImage decode(Part part) throws IOException {
        if (part == null || part.getSize() == 0 || part.getSize() > 5 * 1024 * 1024)
            throw new IOException("Choose an image between 1 byte and 5 MB.");
        try (InputStream input = part.getInputStream();
             ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Choose a valid JPEG or PNG image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
                    throw new IOException("Choose a JPEG or PNG image.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 20000000)
                    throw new IOException("Image dimensions exceed 20 megapixels.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    public static void write(BufferedImage image, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "upload-", ".tmp");
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) throw new IOException("Image encoding failed");
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
