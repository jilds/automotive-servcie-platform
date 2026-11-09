package com.asp.platform.workshop.domain;

import java.util.Optional;

/// The image formats a photo may have, recognised by the first bytes of the file and never by what the client says
/// (a name, an extension or a declared content type can be anything).
public enum ImageType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final String contentType;

    ImageType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    /// The format of the bytes, or empty when they are none of the three.
    public static Optional<ImageType> sniff(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        // RIFF <4 bytes of size> WEBP
        if (bytes.length >= 12 && startsWith(bytes, 0, new byte[] {'R', 'I', 'F', 'F'})
                && startsWith(bytes, 8, new byte[] {'W', 'E', 'B', 'P'})) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /// The type stored with a photo, back from its content type.
    public static ImageType ofContentType(String contentType) {
        for (ImageType type : values()) {
            if (type.contentType.equals(contentType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown image content type " + contentType);
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        if (bytes.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
