package com.asp.platform.workshop.domain;

import java.io.ByteArrayOutputStream;

/// Removes the metadata that can reveal where and with what a photo was taken (EXIF with GPS, XMP) by rewriting the
/// file at byte level, without decoding or re-encoding the picture: JPEG APP1 segments, the PNG `eXIf` chunk, the
/// WebP `EXIF` and `XMP ` chunks. It never fails open: when the structure breaks off, what was read so far is kept
/// without its metadata segments and the unread rest is kept only if it holds no EXIF or XMP signature; otherwise the
/// file is refused (415 `photo-type-not-allowed`). The browser already bakes the orientation into the pixels when it
/// resizes, so dropping the orientation tag changes nothing.
///
/// @throws PhotoTypeNotAllowedException for a broken file that still carries metadata after the break
public final class ImageSanitizer {

    private ImageSanitizer() {
    }

    public static byte[] strip(ImageType type, byte[] bytes) {
        try {
            return switch (type) {
                case JPEG -> jpeg(bytes);
                case PNG -> png(bytes);
                case WEBP -> webp(bytes);
            };
        } catch (MalformedImageException e) {
            return withUnreadRest(type, bytes, e);
        }
    }

    /// The part read so far (metadata already dropped) plus the rest as it is, if the rest holds no metadata.
    private static byte[] withUnreadRest(ImageType type, byte[] in, MalformedImageException broken) {
        byte[] rest = java.util.Arrays.copyOfRange(in, broken.at, in.length);
        String text = new String(rest, java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean metadata = switch (type) {
            case JPEG -> text.contains("Exif\0\0") || text.contains("http://ns.adobe.com/xap");
            case PNG -> text.contains("eXIf");
            case WEBP -> text.contains("EXIF") || text.contains("XMP ");
        };
        if (metadata) {
            throw new PhotoTypeNotAllowedException();
        }
        ByteArrayOutputStream joined = new ByteArrayOutputStream(broken.written.length + rest.length);
        joined.writeBytes(broken.written);
        joined.writeBytes(rest);
        byte[] result = joined.toByteArray();
        if (type == ImageType.WEBP) {
            setRiffSize(result);
        }
        return result;
    }

    private static byte[] jpeg(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 2); // SOI
        int i = 2;
        while (true) {
            if (i + 4 > in.length || (in[i] & 0xFF) != 0xFF) {
                throw new MalformedImageException(out.toByteArray(), i);
            }
            int marker = in[i + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                out.write(0xFF);
                i++;
                continue;
            }
            if (marker == 0xDA) { // start of scan: the rest is entropy-coded data
                out.write(in, i, in.length - i);
                return out.toByteArray();
            }
            if (marker == 0xD9) { // EOI before any scan
                out.write(in, i, in.length - i);
                return out.toByteArray();
            }
            int length = ((in[i + 2] & 0xFF) << 8) | (in[i + 3] & 0xFF);
            if (length < 2 || i + 2 + length > in.length) {
                throw new MalformedImageException(out.toByteArray(), i);
            }
            if (marker != 0xE1) { // APP1: EXIF and XMP
                out.write(in, i, 2 + length);
            }
            i += 2 + length;
        }
    }

    private static byte[] png(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 8);
        int i = 8;
        while (i < in.length) {
            if (i + 12 > in.length) {
                throw new MalformedImageException(out.toByteArray(), i);
            }
            long length = ((in[i] & 0xFFL) << 24) | ((in[i + 1] & 0xFFL) << 16) | ((in[i + 2] & 0xFFL) << 8)
                    | (in[i + 3] & 0xFFL);
            if (length > in.length - i - 12) {
                throw new MalformedImageException(out.toByteArray(), i);
            }
            int total = (int) length + 12;
            boolean exif = in[i + 4] == 'e' && in[i + 5] == 'X' && in[i + 6] == 'I' && in[i + 7] == 'f';
            if (!exif) {
                out.write(in, i, total);
            }
            i += total;
        }
        return out.toByteArray();
    }

    private static byte[] webp(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 12); // RIFF, size, WEBP
        int i = 12;
        while (i < in.length) {
            if (i + 8 > in.length) {
                throw new MalformedImageException(out.toByteArray(), i);
            }
            long size = (in[i + 4] & 0xFFL) | ((in[i + 5] & 0xFFL) << 8) | ((in[i + 6] & 0xFFL) << 16)
                    | ((in[i + 7] & 0xFFL) << 24);
            long padded = size + (size & 1);
            if (padded > in.length - i - 8) {
                // A last chunk may omit its padding byte.
                if (size > in.length - i - 8) {
                    throw new MalformedImageException(out.toByteArray(), i);
                }
                padded = size;
            }
            int total = 8 + (int) padded;
            String fourcc = new String(in, i, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!fourcc.equals("EXIF") && !fourcc.equals("XMP ")) {
                int start = out.size();
                out.write(in, i, total);
                if (fourcc.equals("VP8X") && size >= 1) {
                    // Clear the "has EXIF" and "has XMP" flags.
                    byte[] so = out.toByteArray();
                    so[start + 8] = (byte) (so[start + 8] & ~0x0C);
                    out.reset();
                    out.write(so, 0, so.length);
                }
            }
            i += total;
        }
        byte[] result = out.toByteArray();
        setRiffSize(result);
        return result;
    }

    private static void setRiffSize(byte[] riff) {
        long riffSize = riff.length - 8L;
        riff[4] = (byte) riffSize;
        riff[5] = (byte) (riffSize >> 8);
        riff[6] = (byte) (riffSize >> 16);
        riff[7] = (byte) (riffSize >> 24);
    }

    private static final class MalformedImageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final byte[] written;
        final int at;

        MalformedImageException(byte[] written, int at) {
            super(null, null, false, false);
            this.written = written;
            this.at = at;
        }
    }
}
