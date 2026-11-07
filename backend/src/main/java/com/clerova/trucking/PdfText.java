package com.clerova.trucking;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/** Reads the text of a PDF, such as a rate confirmation. */
public final class PdfText {

    private static final Logger log = LoggerFactory.getLogger(PdfText.class);

    /** Rate cons run 1 to 5 pages; anything longer is cut. */
    static final int MAX_PAGES = 20;
    static final int MAX_CHARS = 60_000;

    private PdfText() {
    }

    public static boolean isPdf(String fileName, String contentType) {
        return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf"))
                || (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    /**
     * The PDF's text, or null when it can't be read (damaged, locked with a
     * password). A scanned page has no text and gives an empty string.
     */
    /** A PDF that needs a password to open (some e-sign and portal downloads are locked). */
    public static boolean isLocked(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }

        try (PDDocument ignored = Loader.loadPDF(content)) {
            return false;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String read(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PAGES));

            String text = stripper.getText(document).replace("\u0000", "").strip();

            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (Exception e) {
            log.warn("Could not read PDF text: {}", e.toString());
            return null;
        }
    }
}
