package com.clerova.trucking;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Turns a document into page pictures the AI can read. Scanned rate cons
 * and BOLs often have no text, or a garbled text layer, so the AI reads
 * the pages the way a person would.
 */
public final class DocumentImages {

    private static final Logger log = LoggerFactory.getLogger(DocumentImages.class);

    /** Rate cons put the load, stops and terms in the first pages. */
    static final int MAX_PAGES = 4;
    /** Long side in pixels: enough for small print, without huge requests. */
    static final int MAX_SIDE = 1700;
    static final float DPI = 150;
    /** Photos bigger than this are shrunk before sending. */
    static final int MAX_PHOTO_BYTES = 4 * 1024 * 1024;

    private DocumentImages() {
    }

    public static boolean isImage(String fileName, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return type.startsWith("image/jpeg") || type.startsWith("image/png") || type.startsWith("image/webp")
                || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp");
    }

    /**
     * Data URLs of the document's pages (a PDF) or of the photo itself.
     * Empty when the file can't be read.
     */
    public static List<String> dataUrls(byte[] content, String fileName, String contentType) {
        if (content == null || content.length == 0) {
            return List.of();
        }

        try {
            if (PdfText.isPdf(fileName, contentType)) {
                return pdfPages(content);
            }

            if (isImage(fileName, contentType)) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));

                if (image == null) {
                    // A format Java can't decode (like WebP): send it as it is when small enough
                    return content.length <= MAX_PHOTO_BYTES && contentType != null && contentType.startsWith("image/")
                            ? List.of(dataUrl(contentType, content))
                            : List.of();
                }

                return List.of(dataUrl("image/jpeg", jpeg(shrink(image))));
            }
        } catch (Exception | OutOfMemoryError e) {
            log.warn("Could not turn {} into pictures: {}", fileName, e.toString());
        }

        return List.of();
    }

    private static List<String> pdfPages(byte[] content) throws IOException {
        List<String> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int count = Math.min(document.getNumberOfPages(), MAX_PAGES);

            for (int page = 0; page < count; page++) {
                float width = document.getPage(page).getMediaBox().getWidth();
                float height = document.getPage(page).getMediaBox().getHeight();
                // Points are 1/72 inch; keep the long side under MAX_SIDE
                float dpi = Math.min(DPI, MAX_SIDE * 72f / Math.max(1, Math.max(width, height)));
                BufferedImage image = renderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                pages.add(dataUrl("image/jpeg", jpeg(image)));
            }
        }

        return pages;
    }

    private static BufferedImage shrink(BufferedImage image) {
        int longSide = Math.max(image.getWidth(), image.getHeight());

        if (longSide <= MAX_SIDE && image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        double scale = Math.min(1.0, (double) MAX_SIDE / longSide);
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.82f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return out.toByteArray();
    }

    private static String dataUrl(String contentType, byte[] content) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content);
    }
}
