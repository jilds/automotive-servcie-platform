import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { FloorApi } from './floor-api.service';

interface Held {
  url: string;
  etag: string | null;
}

/**
 * Photo bytes of one work order screen as object URLs. The content endpoint needs the bearer token, which an `<img>`
 * cannot send, so the bytes come through `HttpClient` as a blob. Each photo is fetched once per screen; `revalidate`
 * asks again with the ETag held (304 keeps the same URL). Every URL is revoked when the screen goes or the photo is
 * forgotten, so the blobs do not pile up. Provided by the photo panel, not at the root.
 */
@Injectable()
export class PhotoCache {
  private readonly api = inject(FloorApi);
  private readonly held = new Map<string, Held>();
  private readonly inflight = new Map<string, Promise<string>>();
  private disposed = false;

  /** The object URL of a photo's bytes. */
  load(workOrderId: string, photoId: string, revalidate = false): Promise<string> {
    const held = this.held.get(photoId);
    if (held && !revalidate) return Promise.resolve(held.url);
    const running = this.inflight.get(photoId);
    if (running) return running;
    const request = this.fetch(workOrderId, photoId, held ?? null).finally(() => this.inflight.delete(photoId));
    this.inflight.set(photoId, request);
    return request;
  }

  /** Drops a photo (deleted): its URL is revoked. */
  forget(photoId: string): void {
    const held = this.held.get(photoId);
    if (held) URL.revokeObjectURL(held.url);
    this.held.delete(photoId);
  }

  /** Revokes everything; later loads are refused. */
  dispose(): void {
    this.disposed = true;
    for (const h of this.held.values()) URL.revokeObjectURL(h.url);
    this.held.clear();
  }

  private async fetch(workOrderId: string, photoId: string, held: Held | null): Promise<string> {
    try {
      const response = await firstValueFrom(this.api.photoContent(workOrderId, photoId, held?.etag ?? null));
      const blob = response.body;
      if (!blob) throw new Error('empty photo');
      const url = URL.createObjectURL(blob);
      if (this.disposed) {
        URL.revokeObjectURL(url);
        throw new Error('disposed');
      }
      if (held) URL.revokeObjectURL(held.url);
      this.held.set(photoId, { url, etag: response.headers.get('ETag') });
      return url;
    } catch (e) {
      // Not modified: the copy held is still right.
      if (held && e instanceof HttpErrorResponse && e.status === 304) return held.url;
      throw e;
    }
  }
}
