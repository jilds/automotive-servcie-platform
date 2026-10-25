import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of, tap } from 'rxjs';

import { BookingSettings } from './booking.models';

/**
 * The workshop's time zone for the staff booking screens (from `GET /booking/settings`), so "today", the agenda's
 * day and every time shown follow the workshop, not the browser. Read once; null (the browser's zone) until then or
 * when it cannot be read.
 */
@Injectable({ providedIn: 'root' })
export class WorkshopZone {
  private readonly http = inject(HttpClient);
  private loaded = false;
  readonly zone = signal<string | null>(null);

  /** The zone, read from the server the first time only. */
  load(): Observable<string | null> {
    if (this.loaded) return of(this.zone());
    return this.http.get<BookingSettings>('/api/v1/booking/settings').pipe(
      map((s) => s.timeZone ?? null),
      catchError(() => of(null)),
      tap((zone) => {
        this.zone.set(zone);
        this.loaded = true;
      }),
    );
  }
}
