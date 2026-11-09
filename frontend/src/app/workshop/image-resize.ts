import { InjectionToken } from '@angular/core';

import { fitWithin, jpegName, MAX_PHOTO_BYTES, PHOTO_MAX_EDGE } from './floor-logic';

/** What the browser can do for the resize; a seam for tests. */
export interface ImageEnv {
  decode(file: Blob): Promise<{ width: number; height: number; draw(width: number, height: number): Promise<Blob | null>; close(): void }>;
}

export type PreparedPhoto = { ok: true; blob: Blob; name: string } | { ok: false; reason: 'unreadable' | 'too-large' };

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];
const JPEG_QUALITY = 0.85;

/** The browser's own decoder and canvas. */
export const browserImageEnv: ImageEnv = {
  async decode(file) {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    return {
      width: bitmap.width,
      height: bitmap.height,
      close: () => bitmap.close(),
      draw: (width, height) =>
        new Promise<Blob | null>((resolve) => {
          const canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          const ctx = canvas.getContext('2d');
          if (!ctx) return resolve(null);
          // JPEG has no transparency: a PNG with a clear background would turn black.
          ctx.fillStyle = '#fff';
          ctx.fillRect(0, 0, width, height);
          ctx.drawImage(bitmap, 0, 0, width, height);
          canvas.toBlob((b) => resolve(b), 'image/jpeg', JPEG_QUALITY);
        }),
    };
  },
};

/**
 * The EXIF orientation of a JPEG from its first bytes: 1 to 8, null when the file has no EXIF orientation (so it is
 * upright), or 'unknown' when it cannot be told (not a JPEG, damaged, or the EXIF block is past what was read). The
 * server removes EXIF, so a picture whose orientation is not null or 1 must be re-drawn on a canvas, which bakes the
 * rotation into the pixels.
 */
export function exifOrientation(bytes: Uint8Array): number | null | 'unknown' {
  const n = bytes.length;
  if (n < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return 'unknown';
  let p = 2;
  while (p + 4 <= n) {
    if (bytes[p] !== 0xff) return 'unknown';
    const marker = bytes[p + 1];
    if (marker === 0xff) {
      p++;
      continue;
    }
    // Start of scan or end of image: no EXIF came before the pixels.
    if (marker === 0xda || marker === 0xd9) return null;
    const length = (bytes[p + 2] << 8) | bytes[p + 3];
    if (length < 2) return 'unknown';
    if (marker === 0xe1) {
      if (p + 2 + length > n) return 'unknown';
      const isExif = bytes[p + 4] === 0x45 && bytes[p + 5] === 0x78 && bytes[p + 6] === 0x69 && bytes[p + 7] === 0x66 && bytes[p + 8] === 0 && bytes[p + 9] === 0;
      if (isExif) return tiffOrientation(bytes, p + 10, p + 2 + length);
    }
    p += 2 + length;
  }
  return 'unknown';
}

function tiffOrientation(b: Uint8Array, tiff: number, end: number): number | null | 'unknown' {
  if (tiff + 8 > end) return 'unknown';
  const little = b[tiff] === 0x49 && b[tiff + 1] === 0x49;
  if (!little && !(b[tiff] === 0x4d && b[tiff + 1] === 0x4d)) return 'unknown';
  const u16 = (o: number) => (little ? b[o] | (b[o + 1] << 8) : (b[o] << 8) | b[o + 1]);
  const u32 = (o: number) => (little ? (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) >>> 0 : ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0);
  const ifd = tiff + u32(tiff + 4);
  if (ifd + 2 > end) return 'unknown';
  const count = u16(ifd);
  for (let i = 0; i < count; i++) {
    const entry = ifd + 2 + i * 12;
    if (entry + 12 > end) return 'unknown';
    if (u16(entry) === 0x0112) {
      const value = u16(entry + 8);
      return value >= 1 && value <= 8 ? value : 'unknown';
    }
  }
  return null;
}

/** How many bytes of a file are read to look for the EXIF orientation. */
const HEAD_BYTES = 128 * 1024;

/**
 * True when a file can go up untouched without losing its rotation: a JPEG whose orientation is absent or 1. Anything
 * else (PNG, WebP, another orientation, or not knowing) is drawn through the canvas.
 */
export async function safeToSendAsIs(file: Blob): Promise<boolean> {
  if (file.type !== 'image/jpeg') return false;
  try {
    const head = new Uint8Array(await file.slice(0, HEAD_BYTES).arrayBuffer());
    const o = exifOrientation(head);
    return o === null || o === 1;
  } catch {
    return false;
  }
}

/** How the photo panel prepares a picked file; the real resize in the app, replaceable in tests. */
export const PHOTO_PREPARER = new InjectionToken<(file: File) => Promise<PreparedPhoto>>('PHOTO_PREPARER', {
  factory: () => (file) => preparePhoto(file),
});

/**
 * Gets a picked picture ready to upload: the long side is cut to 1600 px and the result is re-encoded as JPEG (which
 * also applies the camera's rotation and drops metadata such as the GPS position). A picture that is already small
 * enough, a JPEG without a rotation tag (see `safeToSendAsIs`) and under the 5 MB limit is sent as it is. A file the browser cannot decode (for example
 * HEIC) is `unreadable`; one still over 5 MB after the resize is `too-large`.
 */
export async function preparePhoto(file: File, env: ImageEnv = browserImageEnv, maxEdge = PHOTO_MAX_EDGE, asIs: (f: Blob) => Promise<boolean> = safeToSendAsIs): Promise<PreparedPhoto> {
  let image: Awaited<ReturnType<ImageEnv['decode']>>;
  try {
    image = await env.decode(file);
  } catch {
    return { ok: false, reason: 'unreadable' };
  }
  try {
    const size = fitWithin(image.width, image.height, maxEdge);
    if (!size.scaled && ACCEPTED.includes(file.type) && file.size <= MAX_PHOTO_BYTES && (await asIs(file))) {
      return { ok: true, blob: file, name: file.name || 'photo' };
    }
    const blob = await image.draw(size.width, size.height);
    if (!blob) return { ok: false, reason: 'unreadable' };
    if (blob.size > MAX_PHOTO_BYTES) return { ok: false, reason: 'too-large' };
    return { ok: true, blob, name: jpegName(file.name) };
  } finally {
    image.close();
  }
}
