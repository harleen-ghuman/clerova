package com.clerova.trucking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

class DocumentImagesTest {

    @Test
    void aPixelBombIsRefusedBeforeDecoding() throws Exception {
        // A tiny PNG that claims to be 100,000 x 100,000 pixels
        byte[] bomb = png(100_000, 100_000);
        assertThat(bomb.length).isLessThan(2_000);
        assertThat(DocumentImages.tooManyPixels(bomb)).isTrue();
        assertThat(DocumentImages.dataUrls(bomb, "bomb.png", "image/png")).isEmpty();
    }

    @Test
    void aNormalPhotoIsRead() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1200, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", out);
        assertThat(DocumentImages.tooManyPixels(out.toByteArray())).isFalse();
        assertThat(DocumentImages.dataUrls(out.toByteArray(), "pod.jpg", "image/jpeg")).hasSize(1);
    }

    /** A PNG header with the given size and one tiny compressed row. */
    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(new byte[] {(byte) (width >>> 24), (byte) (width >>> 16), (byte) (width >>> 8), (byte) width});
        header.write(new byte[] {(byte) (height >>> 24), (byte) (height >>> 16), (byte) (height >>> 8), (byte) height});
        header.write(new byte[] {8, 0, 0, 0, 0});
        chunk(out, "IHDR", header.toByteArray());
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[] {0, 0});
        deflater.finish();
        byte[] buffer = new byte[64];
        int length = deflater.deflate(buffer);
        chunk(out, "IDAT", java.util.Arrays.copyOf(buffer, length));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        out.write(new byte[] {(byte) (data.length >>> 24), (byte) (data.length >>> 16), (byte) (data.length >>> 8), (byte) data.length});
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        long value = crc.getValue();
        out.write(new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }
}
