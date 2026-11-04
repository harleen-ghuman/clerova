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
}
