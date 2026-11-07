package com.clerova.trucking;

import com.clerova.domain.InboundAttachment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MainDocumentTest {

    @Test
    void aSmallPhotoSentOnItsOwnIsRead() {
        InboundAttachment photo = file("IMG_4471.jpg", "image/jpeg", 30 * 1024);

        assertThat(TruckingIntakeService.mainDocument(List.of(photo))).contains(photo);
    }

    @Test
    void aSmallPictureNextToOthersIsALogo() {
        InboundAttachment logo = file("image001.png", "image/png", 30 * 1024);
        InboundAttachment banner = file("image002.png", "image/png", 20 * 1024);

        assertThat(TruckingIntakeService.mainDocument(List.of(logo, banner))).isEmpty();
        assertThat(TruckingIntakeService.mainDocument(List.of(file("tiny.png", "image/png", 4 * 1024)))).isEmpty();
    }

    @Test
    void aPdfComesBeforeAnyPhoto() {
        InboundAttachment pdf = file("RC_55102.pdf", "application/pdf", 40 * 1024);
        InboundAttachment photo = file("IMG_1.jpg", "image/jpeg", 900 * 1024);

        assertThat(TruckingIntakeService.mainDocument(List.of(photo, pdf))).contains(pdf);
    }

    @Test
    void aSmallPhotoReadAsAPodIsListedWithTheLoadsDocuments() {
        InboundAttachment photo = file("IMG_4471.jpg", "image/jpeg", 30 * 1024);
        assertThat(LoadService.isDocument(photo)).isFalse();

        photo.setDocumentKind("POD");
        assertThat(LoadService.isDocument(photo)).isTrue();
        assertThat(LoadService.isDocument(file("scan.jpg", "image/jpeg", 600 * 1024))).isTrue();
    }

    private static InboundAttachment file(String name, String type, long size) {
        InboundAttachment attachment = new InboundAttachment();
        attachment.setFileName(name);
        attachment.setContentType(type);
        attachment.setSizeBytes(size);
        return attachment;
    }
}
