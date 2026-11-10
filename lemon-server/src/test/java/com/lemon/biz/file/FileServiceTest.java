package com.lemon.biz.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileServiceTest {

    private static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return out;
    }

    @Test
    void detectsRealImageTypes() {
        assertThat(FileService.detectImageType(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A))).isEqualTo("png");
        assertThat(FileService.detectImageType(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("jpg");
        assertThat(FileService.detectImageType("GIF89a...".getBytes(StandardCharsets.US_ASCII))).isEqualTo("gif");
        assertThat(FileService.detectImageType("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isEqualTo("webp");
    }

    @Test
    void rejectsNonImages() {
        assertThat(FileService.detectImageType("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(FileService.detectImageType("<html>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(FileService.detectImageType("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(FileService.detectImageType(new byte[0])).isNull();
    }
}
