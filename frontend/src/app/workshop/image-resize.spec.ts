import { vi } from 'vitest';

import { exifOrientation, ImageEnv, preparePhoto, safeToSendAsIs } from './image-resize';

const upright = () => Promise.resolve(true);

/** A JPEG head with one EXIF orientation tag (big endian), or without EXIF when `orientation` is null. */
function jpeg(orientation: number | null, little = false): Uint8Array {
  const tag = little ? [0x12, 0x01, 0x03, 0x00, 1, 0, 0, 0, orientation ?? 1, 0, 0, 0] : [0x01, 0x12, 0x00, 0x03, 0, 0, 0, 1, 0, orientation ?? 1, 0, 0];
  const tiff = little ? [0x49, 0x49, 0x2a, 0x00, 8, 0, 0, 0, 1, 0, ...tag, 0, 0, 0, 0] : [0x4d, 0x4d, 0x00, 0x2a, 0, 0, 0, 8, 0, 1, ...tag, 0, 0, 0, 0];
  const exif = [0x45, 0x78, 0x69, 0x66, 0, 0, ...tiff];
  const len = exif.length + 2;
  const app1 = orientation === null ? [] : [0xff, 0xe1, len >> 8, len & 0xff, ...exif];
  return new Uint8Array([0xff, 0xd8, ...app1, 0xff, 0xda, 0, 2]);
}

function env(width: number, height: number, drawn: Blob | null = new Blob(['x'], { type: 'image/jpeg' })) {
  const draw = vi.fn().mockResolvedValue(drawn);
  const close = vi.fn();
  const impl: ImageEnv = { decode: () => Promise.resolve({ width, height, draw, close }) };
  return { impl, draw, close };
}

const file = (name: string, type: string, size = 10) => new File([new Uint8Array(size)], name, { type });

describe('exif orientation', () => {
  it('reads the orientation tag in both byte orders', () => {
    expect(exifOrientation(jpeg(6))).toBe(6);
    expect(exifOrientation(jpeg(3, true))).toBe(3);
    expect(exifOrientation(jpeg(1))).toBe(1);
  });

  it('says null when there is no EXIF before the pixels, and unknown when it cannot tell', () => {
    expect(exifOrientation(jpeg(null))).toBeNull();
    expect(exifOrientation(new Uint8Array([0x89, 0x50, 0x4e, 0x47]))).toBe('unknown');
    expect(exifOrientation(jpeg(6).slice(0, 12))).toBe('unknown');
    expect(exifOrientation(new Uint8Array([0xff, 0xd8]))).toBe('unknown');
  });

  it('lets only an upright JPEG go up untouched', async () => {
    const blob = (bytes: Uint8Array, type = 'image/jpeg') => new Blob([bytes as BlobPart], { type });
    expect(await safeToSendAsIs(blob(jpeg(null)))).toBe(true);
    expect(await safeToSendAsIs(blob(jpeg(1)))).toBe(true);
    expect(await safeToSendAsIs(blob(jpeg(8)))).toBe(false);
    expect(await safeToSendAsIs(blob(jpeg(1), 'image/png'))).toBe(false);
    expect(await safeToSendAsIs(blob(new Uint8Array([1, 2, 3, 4])))).toBe(false);
  });
});

describe('preparePhoto', () => {
  it('sends a small supported picture as it is', async () => {
    const { impl, draw, close } = env(800, 600);
    const f = file('a.jpg', 'image/jpeg');
    const r = await preparePhoto(f, impl, 1600, upright);
    expect(r).toEqual({ ok: true, blob: f, name: 'a.jpg' });
    expect(draw).not.toHaveBeenCalled();
    expect(close).toHaveBeenCalledOnce();
  });

  it('draws a small JPEG through the canvas when its rotation tag is not 1, or cannot be told', async () => {
    const a = env(800, 600);
    const f = file('a.jpg', 'image/jpeg');
    expect((await preparePhoto(f, a.impl, 1600, () => Promise.resolve(false))).ok).toBe(true);
    expect(a.draw).toHaveBeenCalledWith(800, 600);
    const b = env(800, 600);
    await preparePhoto(f, b.impl);
    expect(b.draw).toHaveBeenCalledOnce();
  });

  it('shrinks a large picture to 1600 px on the long side and re-encodes it as .jpg', async () => {
    const { impl, draw } = env(4000, 3000);
    const r = await preparePhoto(file('IMG 1.png', 'image/png'), impl);
    expect(draw).toHaveBeenCalledWith(1600, 1200);
    expect(r.ok && r.name).toBe('IMG_1.jpg');
  });

  it('re-encodes a small picture of an unsupported type (for example HEIC) at its own size', async () => {
    const { impl, draw } = env(1000, 500);
    const r = await preparePhoto(file('x.heic', 'image/heic'), impl);
    expect(draw).toHaveBeenCalledWith(1000, 500);
    expect(r.ok).toBe(true);
  });

  it('re-encodes a small picture that is over 5 MB', async () => {
    const { impl, draw } = env(1000, 500);
    const r = await preparePhoto(file('big.png', 'image/png', 5 * 1024 * 1024 + 1), impl);
    expect(draw).toHaveBeenCalledOnce();
    expect(r.ok).toBe(true);
  });

  it('reports a file the browser cannot decode as unreadable', async () => {
    const impl: ImageEnv = { decode: () => Promise.reject(new Error('bad')) };
    expect(await preparePhoto(file('a.bin', 'application/octet-stream'), impl)).toEqual({ ok: false, reason: 'unreadable' });
  });

  it('reports an empty canvas result as unreadable and a result still over 5 MB as too large', async () => {
    expect(await preparePhoto(file('a.png', 'image/png'), env(3000, 3000, null).impl)).toEqual({ ok: false, reason: 'unreadable' });
    const huge = { size: 6 * 1024 * 1024 } as Blob;
    const { impl, close } = env(3000, 3000, huge);
    expect(await preparePhoto(file('a.png', 'image/png'), impl)).toEqual({ ok: false, reason: 'too-large' });
    expect(close).toHaveBeenCalledOnce();
  });
});
