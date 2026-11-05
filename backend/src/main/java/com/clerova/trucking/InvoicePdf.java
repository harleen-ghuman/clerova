package com.clerova.trucking;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws the invoice PDF and joins it with the load's paperwork into one packet. */
final class InvoicePdf {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdf.class);

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color INK = new Color(0x16, 0x18, 0x1D);
    private static final Color GREY = new Color(0x5D, 0x64, 0x70);
    private static final Color RULE = new Color(0xD5, 0xD9, 0xE0);
    private static final Color SHADE = new Color(0xF3, 0xF4, 0xF6);
    private static final float MARGIN = 54;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    /** What the invoice shows; everything is already plain text. */
    record Content(
            String number, LocalDate date, LocalDate dueDate, String paymentTerms,
            String carrierName, String carrierAddress, String carrierPhone, String carrierEmail,
            String mcNumber, String dotNumber,
            String billTo, String remitTo, String factorName,
            String loadNumber, String references, List<String[]> stops,
            List<InvoiceLine> lines, long totalCents, Long quickPayFeeCents, String quickPayPercent
    ) {
    }

    /** A file for the packet. */
    record PacketFile(String name, String contentType, byte[] content) {
    }

    /** The packet, and the files that couldn't be added (like a locked PDF). */
    record Packet(byte[] pdf, List<String> skipped) {
    }

    private InvoicePdf() {
    }

    static byte[] invoice(Content content) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            float width = page.getMediaBox().getWidth();
            float right = width - MARGIN;

            float top = page.getMediaBox().getHeight() - MARGIN;
            PDPageContentStream out = new PDPageContentStream(document, page);

            try {
                float y = top;

                // Carrier on the left, invoice facts on the right
                float leftY = text(out, BOLD, 16, INK, MARGIN, y, content.carrierName(), 300);
                leftY = lines(out, REGULAR, 10, GREY, MARGIN, leftY - 4, content.carrierAddress(), 280);
                List<String> ids = new ArrayList<>();

                if (content.mcNumber() != null) {
                    ids.add("MC " + content.mcNumber());
                }

                if (content.dotNumber() != null) {
                    ids.add("DOT " + content.dotNumber());
                }

                leftY = lines(out, REGULAR, 10, GREY, MARGIN, leftY, String.join("  |  ", ids), 280);
                leftY = lines(out, REGULAR, 10, GREY, MARGIN, leftY, join("  |  ", content.carrierPhone(),
                        content.carrierEmail()), 280);

                right(out, BOLD, 22, INK, right, y, "INVOICE");
                float rightY = y - 30;
                rightY = fact(out, right, rightY, "Invoice", content.number());
                rightY = fact(out, right, rightY, "Date", DATE.format(content.date()));

                if (content.paymentTerms() != null) {
                    rightY = fact(out, right, rightY, "Terms", shorten(content.paymentTerms(), 40));
                }

                if (content.dueDate() != null) {
                    rightY = fact(out, right, rightY, "Due", DATE.format(content.dueDate()));
                }

                if (content.loadNumber() != null) {
                    rightY = fact(out, right, rightY, "Load", content.loadNumber());
                }

                y = Math.min(leftY, rightY) - 18;
                rule(out, MARGIN, right, y);
                y -= 22;

                // Bill to and remit to side by side
                float column = (right - MARGIN) / 2;
                float billY = label(out, MARGIN, y, "BILL TO");
                billY = lines(out, REGULAR, 10.5f, INK, MARGIN, billY, content.billTo(), column - 16);
                float remitY = label(out, MARGIN + column, y, "REMIT PAYMENT TO");
                remitY = lines(out, REGULAR, 10.5f, INK, MARGIN + column, remitY, content.remitTo(), column - 8);
                y = Math.min(billY, remitY) - 14;

                // The stops
                if (!content.stops().isEmpty() || content.references() != null) {
                    rule(out, MARGIN, right, y);
                    y -= 22;
                    y = label(out, MARGIN, y, "LOAD");

                    for (String[] stop : content.stops()) {
                        float stopY = text(out, BOLD, 10, INK, MARGIN, y, stop[0], 80);
                        float placeY = lines(out, REGULAR, 10, INK, MARGIN + 80, y, stop[1], right - MARGIN - 80);
                        y = Math.min(stopY, placeY) - 4;
                    }

                    if (content.references() != null) {
                        y = lines(out, REGULAR, 9.5f, GREY, MARGIN, y, "References: " + content.references(),
                                right - MARGIN) - 4;
                    }

                    y -= 10;
                }

                // The lines
                out.setNonStrokingColor(SHADE);
                out.addRect(MARGIN, y - 8, right - MARGIN, 22);
                out.fill();
                text(out, BOLD, 9.5f, GREY, MARGIN + 8, y, "DESCRIPTION", 300);
                right(out, BOLD, 9.5f, GREY, right - 8, y, "AMOUNT");
                y -= 28;

                for (InvoiceLine line : content.lines()) {
                    // A long invoice goes on to a new page
                    if (y < MARGIN + 60) {
                        out.close();
                        page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);
                        out = new PDPageContentStream(document, page);
                        y = top;
                        text(out, BOLD, 10, GREY, MARGIN, y, "Invoice " + content.number() + " (continued)", 300);
                        y -= 28;
                    }

                    float lineY = lines(out, REGULAR, 10.5f, INK, MARGIN + 8, y, line.getDescription(),
                            right - MARGIN - 140);
                    right(out, REGULAR, 10.5f, INK, right - 8, y, Money.dollars(line.getAmountCents()));
                    // The last line's baseline, then a rule a little under it
                    float ruleY = lineY + 10.5f * 1.35f - 8;
                    rule(out, MARGIN, right, ruleY);
                    y = ruleY - 17;
                }

                // The total, quick pay and the notice of assignment stay together
                float footer = 40 + (content.quickPayFeeCents() != null ? 16 : 0) + (content.factorName() != null ? 90 : 0);

                if (y - footer < MARGIN + 20) {
                    out.close();
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    out = new PDPageContentStream(document, page);
                    y = top;
                }

                y -= 4;
                right(out, BOLD, 12, INK, right - 8, y, "Total  " + Money.dollars(content.totalCents()));
                y -= 18;

                if (content.quickPayFeeCents() != null) {
                    right(out, REGULAR, 10, GREY, right - 8, y, "Quick pay requested (" + content.quickPayPercent()
                            + "% fee, " + Money.dollars(content.quickPayFeeCents()) + "): pay "
                            + Money.dollars(content.totalCents() - content.quickPayFeeCents()));
                    y -= 16;
                }

                if (content.factorName() != null) {
                    y -= 14;
                    out.setNonStrokingColor(SHADE);
                    float boxTop = y + 14;
                    List<String> notice = wrap(REGULAR, 10, "This invoice has been sold and assigned to "
                            + content.factorName() + ". Pay only " + content.factorName()
                            + " at the remit-to address above. Payment to anyone else does not settle this invoice.",
                            right - MARGIN - 20);
                    float boxHeight = 22 + notice.size() * 13;
                    out.addRect(MARGIN, boxTop - boxHeight, right - MARGIN, boxHeight);
                    out.fill();
                    text(out, BOLD, 10, INK, MARGIN + 10, y, "Notice of assignment", 300);
                    y -= 14;

                    for (String line : notice) {
                        text(out, REGULAR, 10, INK, MARGIN + 10, y, line, right - MARGIN - 20);
                        y -= 13;
                    }
                }

                text(out, REGULAR, 9, GREY, MARGIN, MARGIN, "Thank you for your business.", 300);
            } finally {
                out.close();
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't draw the invoice", e);
        }
    }

    /** The invoice first, then each file in order; photos become pages. */
    static Packet packet(byte[] invoice, List<PacketFile> files) {
        List<String> skipped = new ArrayList<>();

        // The added documents stay open until the packet is saved: their pages are shared, not copied
        List<PDDocument> sources = new ArrayList<>();

        try (PDDocument packet = Loader.loadPDF(invoice)) {
            PDFMergerUtility merger = new PDFMergerUtility();

            for (PacketFile file : files) {
                try {
                    if (PdfText.isPdf(file.name(), file.contentType())) {
                        PDDocument source = Loader.loadPDF(file.content());
                        sources.add(source);

                        if (source.isEncrypted()) {
                            skipped.add(file.name());
                            continue;
                        }

                        merger.appendDocument(packet, source);
                    } else {
                        addImage(packet, file.content());
                    }
                } catch (IOException | RuntimeException e) {
                    log.info("Left {} out of an invoice packet: {}", file.name(), e.getMessage());
                    skipped.add(file.name());
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            packet.save(bytes);
            return new Packet(bytes.toByteArray(), skipped);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't make the packet", e);
        } finally {
            for (PDDocument source : sources) {
                try {
                    source.close();
                } catch (IOException e) {
                    // Nothing more to do with it
                }
            }
        }
    }

    private static void addImage(PDDocument packet, byte[] content) throws IOException {
        if (DocumentImages.tooManyPixels(content)) {
            throw new IOException("picture too large");
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));

        if (image == null) {
            throw new IOException("not an image");
        }

        PDImageXObject picture = image.getColorModel().hasAlpha()
                ? LosslessFactory.createFromImage(packet, image)
                : org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(packet, image, 0.85f);
        PDPage page = new PDPage(PDRectangle.LETTER);
        packet.addPage(page);
        float maxWidth = page.getMediaBox().getWidth() - 2 * 36;
        float maxHeight = page.getMediaBox().getHeight() - 2 * 36;
        float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        float drawWidth = image.getWidth() * scale;
        float drawHeight = image.getHeight() * scale;

        try (PDPageContentStream out = new PDPageContentStream(packet, page)) {
            out.drawImage(picture, (page.getMediaBox().getWidth() - drawWidth) / 2,
                    (page.getMediaBox().getHeight() - drawHeight) / 2, drawWidth, drawHeight);
        }
    }

    private static float fact(PDPageContentStream out, float right, float y, String label, String value)
            throws IOException {
        String text = safe(value);
        float valueWidth = width(BOLD, 10, text);
        right(out, BOLD, 10, INK, right, y, text);
        right(out, REGULAR, 10, GREY, right - valueWidth - 8, y, label);
        return y - 15;
    }

    private static float label(PDPageContentStream out, float x, float y, String label) throws IOException {
        text(out, BOLD, 8.5f, GREY, x, y, label, 300);
        return y - 15;
    }

    private static void rule(PDPageContentStream out, float from, float to, float y) throws IOException {
        out.setStrokingColor(RULE);
        out.setLineWidth(0.75f);
        out.moveTo(from, y);
        out.lineTo(to, y);
        out.stroke();
    }

    /** One line of text, cut to fit; returns the y for the next line. */
    private static float text(PDPageContentStream out, PDType1Font font, float size, Color color, float x, float y,
                              String value, float maxWidth) throws IOException {
        if (value == null || value.isBlank()) {
            return y;
        }

        String text = safe(value);

        while (text.length() > 1 && width(font, size, text) > maxWidth) {
            text = text.substring(0, text.length() - 2) + "…";
            text = safe(text);
        }

        out.beginText();
        out.setFont(font, size);
        out.setNonStrokingColor(color);
        out.newLineAtOffset(x, y);
        out.showText(text);
        out.endText();
        return y - size * 1.35f;
    }

    /** Text over several lines, wrapped to the width; returns the y for what follows. */
    private static float lines(PDPageContentStream out, PDType1Font font, float size, Color color, float x, float y,
                               String value, float maxWidth) throws IOException {
        if (value == null || value.isBlank()) {
            return y;
        }

        for (String line : wrap(font, size, value, maxWidth)) {
            y = text(out, font, size, color, x, y, line, maxWidth);
        }

        return y;
    }

    private static void right(PDPageContentStream out, PDType1Font font, float size, Color color, float right,
                              float y, String value) throws IOException {
        String text = safe(value);
        text(out, font, size, color, right - width(font, size, text), y, text, 1000);
    }

    static List<String> wrap(PDType1Font font, float size, String value, float maxWidth) {
        List<String> result = new ArrayList<>();

        for (String paragraph : safe(value).split("\n")) {
            StringBuilder line = new StringBuilder();

            for (String word : paragraph.strip().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;

                if (!line.isEmpty() && width(font, size, candidate) > maxWidth) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }

            if (!line.isEmpty()) {
                result.add(line.toString());
            }
        }

        return result;
    }

    private static float width(PDType1Font font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException e) {
            return text.length() * size * 0.5f;
        }
    }

    /** Keeps what the built-in fonts can draw: other characters become close matches or go. */
    static String safe(String value) {
        if (value == null) {
            return "";
        }

        String text = value
                .replace("\u2192", "to").replace('\u2013', '-').replace('\u2014', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace(' ', ' ').replace('\t', ' ').replace("\r", "");
        StringBuilder out = new StringBuilder();
        boolean dropped = false;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String character = new String(Character.toChars(codePoint));
            i += Character.charCount(codePoint);

            if (character.equals("\n")) {
                out.append('\n');
                dropped = false;
                continue;
            }

            // Where a character was left out, its space isn't doubled ("Freight ✨ LLC")
            if (dropped && character.equals(" ") && (out.isEmpty() || out.charAt(out.length() - 1) == ' ')) {
                continue;
            }

            try {
                REGULAR.encode(character);
                BOLD.encode(character);
                out.append(character);
                dropped = false;
            } catch (IOException | IllegalArgumentException e) {
                // Not in the font: leave it out
                dropped = true;
            }
        }

        return out.toString();
    }

    private static String join(String separator, String... values) {
        List<String> kept = new ArrayList<>();

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                kept.add(value);
            }
        }

        return String.join(separator, kept);
    }

    private static String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
