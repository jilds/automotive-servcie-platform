package com.asp.platform.workshop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// The byte-level removal of EXIF and XMP metadata from JPEG, PNG and WebP uploads.
class ImageSanitizerTests {

    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object part : parts) {
            switch (part) {
                case byte[] b -> out.writeBytes(b);
                case String s -> out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
                case Integer i -> out.write(i);
                default -> throw new IllegalArgumentException();
            }
        }
        return out.toByteArray();
    }

    private static byte[] jpegSegment(int marker, String payload) {
        int length = payload.length() + 2;
        return bytes(0xFF, marker, length >> 8, length & 0xFF, payload);
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    @Test
    void should_drop_the_exif_and_xmp_segments_of_a_jpeg_and_keep_the_picture() {
        byte[] jpeg = bytes(0xFF, 0xD8, jpegSegment(0xE0, "JFIF\0abc"), jpegSegment(0xE1, "Exif\0\0GPS-LAT-38.7"),
                jpegSegment(0xE1, "http://ns.adobe.com/xap/1.0/\0<gps/>"), jpegSegment(0xDB, "quant"),
                bytes(0xFF, 0xDA, 0x00, 0x04, 0x01, 0x02, 0x11, 0x22, 0xFF, 0xE1, 0x33, 0xFF, 0xD9));

        byte[] clean = ImageSanitizer.strip(ImageType.JPEG, jpeg);

        assertThat(contains(clean, "Exif")).isFalse();
        assertThat(contains(clean, "GPS")).isFalse();
        assertThat(contains(clean, "adobe")).isFalse();
        assertThat(contains(clean, "JFIF")).isTrue();
        assertThat(contains(clean, "quant")).isTrue();
        // The scan data after SOS is copied as it is, even when it holds bytes that look like a marker.
        byte[] tail = bytes(0xFF, 0xDA, 0x00, 0x04, 0x01, 0x02, 0x11, 0x22, 0xFF, 0xE1, 0x33, 0xFF, 0xD9);
        assertThat(Arrays.copyOfRange(clean, clean.length - tail.length, clean.length)).isEqualTo(tail);
    }

    @Test
    void should_drop_the_exif_chunk_of_a_png() {
        byte[] crc = {0, 0, 0, 0};
        byte[] png = bytes(0x89, "PNG\r\n", 0x1A, 0x0A,
                0, 0, 0, 13, "IHDR", new byte[13], crc,
                0, 0, 0, 9, "eXIf", "GPSDATA!!", crc,
                0, 0, 0, 2, "IDAT", "ab", crc,
                0, 0, 0, 0, "IEND", crc);

        byte[] clean = ImageSanitizer.strip(ImageType.PNG, png);

        assertThat(contains(clean, "eXIf")).isFalse();
        assertThat(contains(clean, "GPSDATA")).isFalse();
        assertThat(contains(clean, "IHDR")).isTrue();
        assertThat(contains(clean, "IDAT")).isTrue();
        assertThat(clean).hasSize(png.length - 21);
    }

    @Test
    void should_drop_the_exif_and_xmp_chunks_of_a_webp_and_fix_the_size_and_flags() {
        byte[] vp8x = bytes("VP8X", 10, 0, 0, 0, 0x2C, new byte[9]); // alpha + ICC + EXIF + XMP flags
        byte[] exif = bytes("EXIF", 5, 0, 0, 0, "GPS!!", 0);        // odd size, padded
        byte[] xmp = bytes("XMP ", 4, 0, 0, 0, "<gps");
        byte[] data = bytes("VP8 ", 4, 0, 0, 0, "data");
        int size = 4 + vp8x.length + exif.length + xmp.length + data.length;
        byte[] webp = bytes("RIFF", size & 0xFF, size >> 8, 0, 0, "WEBP", vp8x, exif, xmp, data);

        byte[] clean = ImageSanitizer.strip(ImageType.WEBP, webp);

        assertThat(contains(clean, "GPS")).isFalse();
        assertThat(contains(clean, "EXIF")).isFalse();
        assertThat(contains(clean, "XMP ")).isFalse();
        assertThat(contains(clean, "data")).isTrue();
        int newSize = clean.length - 8;
        assertThat(clean[4] & 0xFF).isEqualTo(newSize & 0xFF);
        assertThat(clean[5] & 0xFF).isEqualTo(newSize >> 8);
        // VP8X flags: only the 0x20 (ICC) and 0x10 (alpha) bits may stay; EXIF (0x08) and XMP (0x04) are cleared.
        assertThat(clean[20] & 0x0C).isZero();
        assertThat(clean[20] & 0x20).isEqualTo(0x20);
    }

    @Test
    void should_not_fail_open_on_a_broken_file_and_keep_the_hash_of_what_is_stored() {
        // APP1 first, then a segment whose length runs past the end: the APP1 is dropped, the rest has no metadata.
        byte[] brokenAfterExif = bytes(0xFF, 0xD8, jpegSegment(0xE0, "JFIF"), jpegSegment(0xE1, "Exif\0\0GPS-1"),
                bytes(0xFF, 0xDB, 0x7F, 0xFF, "quant"));
        byte[] kept = ImageSanitizer.strip(ImageType.JPEG, brokenAfterExif);
        assertThat(contains(kept, "GPS")).isFalse();
        assertThat(contains(kept, "JFIF")).isTrue();
        assertThat(contains(kept, "quant")).isTrue();

        // A break before the metadata: the unread rest holds EXIF or XMP, so the file is refused.
        byte[] brokenBeforeExif = bytes(0xFF, 0xD8, bytes(0xFF, 0xDB, 0x7F, 0xFF), "Exif\0\0GPS-2");
        assertThatThrownBy(() -> ImageSanitizer.strip(ImageType.JPEG, brokenBeforeExif))
                .isInstanceOf(PhotoTypeNotAllowedException.class);
        byte[] brokenXmp = bytes(0xFF, 0xD8, bytes(0xFF, 0xDB, 0x7F, 0xFF), "http://ns.adobe.com/xap/1.0/");
        assertThatThrownBy(() -> ImageSanitizer.strip(ImageType.JPEG, brokenXmp))
                .isInstanceOf(PhotoTypeNotAllowedException.class);
        byte[] brokenPng = bytes(0x89, "PNG\r\n", 0x1A, 0x0A, 0, 0, 0xFF, 0xFF, "eXIf", "GPS");
        assertThatThrownBy(() -> ImageSanitizer.strip(ImageType.PNG, brokenPng))
                .isInstanceOf(PhotoTypeNotAllowedException.class);
        byte[] brokenWebp = bytes("RIFF", 0x30, 0, 0, 0, "WEBP", "VP8 ", 0xFF, 0xFF, 0, 0, "EXIF-GPS");
        assertThatThrownBy(() -> ImageSanitizer.strip(ImageType.WEBP, brokenWebp))
                .isInstanceOf(PhotoTypeNotAllowedException.class);
        // Junk without metadata is kept as it is.
        byte[] junk = bytes(0xFF, 0xD8, 0xFF, 0x01, 0x02, 0x03);
        assertThat(ImageSanitizer.strip(ImageType.JPEG, junk)).isEqualTo(junk);

        byte[] jpeg = bytes(0xFF, 0xD8, jpegSegment(0xE1, "Exif\0\0GPS"), bytes(0xFF, 0xDA, 0x00, 0x02, 0x01));
        WorkOrderPhoto.Accepted accepted = WorkOrderPhoto.accept(UUID.randomUUID(), UUID.randomUUID(),
                PhotoStage.WORK, null, jpeg, UUID.randomUUID(), java.time.Instant.now());
        assertThat(accepted.bytes()).hasSizeLessThan(jpeg.length);
        assertThat(accepted.photo().sizeBytes()).isEqualTo(accepted.bytes().length);
        assertThat(accepted.photo().sha256()).isEqualTo(sha256(accepted.bytes()));
    }

    private static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
