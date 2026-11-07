package com.clerova.trucking;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextTest {

    @Test
    void readsTheTextOfAPdf() throws Exception {
        byte[] pdf = Base64.getDecoder().decode(com.clerova.TruckingIntakeTestPdf.pdf("Line haul $2,150.00"));
        assertThat(PdfText.read(pdf)).isEqualTo("Line haul $2,150.00");
    }

    @Test
    void damagedOrEmptyFilesGiveNothing() {
        assertThat(PdfText.read("not a pdf".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(PdfText.read(new byte[0])).isNull();
        assertThat(PdfText.read(null)).isNull();
    }

    @Test
    void knowsAPdfByTypeOrName() {
        assertThat(PdfText.isPdf("RC 0457.PDF", null)).isTrue();
        assertThat(PdfText.isPdf("scan", "application/pdf")).isTrue();
        assertThat(PdfText.isPdf("photo.jpg", "image/jpeg")).isFalse();
    }

    @Test
    void aPasswordLockedPdfIsToldApartFromAnUnreadableOne() throws Exception {
        byte[] open;
        byte[] locked;

        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.io.ByteArrayOutputStream plain = new java.io.ByteArrayOutputStream();
            document.save(plain);
            open = plain.toByteArray();

            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy =
                    new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                            "owner", "1234", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            policy.setEncryptionKeyLength(256);
            document.protect(policy);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            locked = out.toByteArray();
        }

        assertThat(PdfText.isLocked(locked)).isTrue();
        assertThat(PdfText.isLocked(open)).isFalse();
        assertThat(PdfText.isLocked("not a pdf".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(DocumentImages.dataUrls(locked, "signed_RateCon.pdf", "application/pdf")).isEmpty();
        assertThat(DocumentImages.dataUrls(open, "blank.pdf", "application/pdf")).hasSize(1);
    }
}
