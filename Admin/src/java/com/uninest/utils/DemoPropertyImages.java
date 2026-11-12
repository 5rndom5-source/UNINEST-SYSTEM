package com.uninest.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.servlet.ServletContext;

/** Bundled, visibly labelled substitutes; callers must authorize the photo first. */
public final class DemoPropertyImages {
    public static final String LABEL = "DEMO PHOTO \u2014 NOT ACTUAL PROPERTY";
    private static final String PREFIX = "/PropertyImageServlet/";
    private static final String[] ASSETS = {"listing-cbd-room.png", "listing-bellville-residence.png", "listing-rondebosch.png"};
    private static final Map<String, byte[]> CACHE = new HashMap<>();
    private DemoPropertyImages() {}

    public static boolean enabled() {
        return "true".equalsIgnoreCase(System.getenv("UNINEST_DEMO_IMAGE_FALLBACK"));
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9._-]+") && !name.equals(".") && !name.equals("..");
    }

    public static boolean isMissingUpload(String url) {
        if (!enabled() || url == null || !url.startsWith(PREFIX)) return false;
        String name = url.substring(PREFIX.length());
        // notExists deliberately excludes unreadable files and unknown filesystem states.
        return validName(name) && Files.notExists(ImageStorage.directory("property-images").resolve(name));
    }

    public static String asset(String name) {
        if (!validName(name)) throw new IllegalArgumentException("Invalid photo name");
        return "/HomePage/" + ASSETS[Math.floorMod(name.hashCode(), ASSETS.length)];
    }

    public static synchronized byte[] labelledImage(ServletContext context, String name) throws IOException {
        String resource = asset(name);
        byte[] cached = CACHE.get(resource);
        if (cached != null) return cached;
        try (InputStream input = context.getResourceAsStream(resource)) {
            if (input == null) throw new FileNotFoundException("Bundled demo photo unavailable");
            BufferedImage source = ImageIO.read(input);
            if (source == null) throw new IOException("Bundled demo photo is invalid");
            int width = Math.min(1200, source.getWidth());
            int height = Math.max(1, (int) ((long) source.getHeight() * width / source.getWidth()));
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = output.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(source, 0, 0, width, height, null);
                int fontSize = 32;
                do { g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize--)); }
                while (g.getFontMetrics().stringWidth(LABEL) > width - 24 && fontSize > 8);
                FontMetrics fm = g.getFontMetrics();
                int band = fm.getHeight() + 24;
                int y = (height - band) / 2;
                g.setColor(Color.BLACK);g.fillRect(0, y, width, band);
                g.setColor(Color.WHITE);
                g.drawString(LABEL, (width - fm.stringWidth(LABEL)) / 2, y + 12 + fm.getAscent());
            } finally { g.dispose(); }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "png", bytes)) throw new IOException("Demo image encoding failed");
            cached = bytes.toByteArray();
            CACHE.put(resource, cached); // At most three public, watermarked assets; never uploaded images.
            return cached;
        }
    }
}
