import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  Booking,
  BookingCreated,
  BookingSettings,
  BookingStatus,
  PublicBooking,
  PublicBookingRequest,
  PublicService,
  SlotDay,
} from './booking.models';

const API = '/api/v1';

/** The public booking API of one workshop: no token, found by its slug. */
function publicBase(slug: string): string {
  return `${API}/public/${encodeURIComponent(slug)}/booking`;
}

/** Online booking: the public page, the front desk's bookings and the owner's settings. */
@Injectable({ providedIn: 'root' })
export class BookingApi {
  private readonly http = inject(HttpClient);

  // Public (no token)
  publicServices(slug: string) {
    return this.http.get<PublicService[]>(`${publicBase(slug)}/services`);
  }
  /** Free slots of `days` days (1–14) from `from` (`YYYY-MM-DD`). */
  slots(slug: string, from: string, days = 7) {
    const params = new HttpParams().set('from', from).set('days', String(days));
    return this.http.get<SlotDay[]>(`${publicBase(slug)}/slots`, { params });
  }
  requestBooking(slug: string, body: PublicBookingRequest) {
    return this.http.post<BookingCreated>(`${publicBase(slug)}/bookings`, body);
  }
  publicBooking(slug: string, token: string) {
    return this.http.get<PublicBooking>(`${publicBase(slug)}/bookings/${encodeURIComponent(token)}`);
  }
  cancelPublic(slug: string, token: string) {
    return this.http.post<PublicBooking>(`${publicBase(slug)}/bookings/${encodeURIComponent(token)}/cancel`, {});
  }

  // Workshop (owner, front desk)
  /** Bookings ordered by drop-off time; `from` / `to` are `YYYY-MM-DD` (server default: today to +7 days). */
  bookings(filter: { from?: string; to?: string; status?: BookingStatus } = {}) {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) if (value) params = params.set(key, value);
    return this.http.get<Booking[]>(`${API}/bookings`, { params });
  }
  booking(id: string) {
    return this.http.get<Booking>(`${API}/bookings/${id}`);
  }
  confirm(id: string, version: number) {
    return this.http.post<Booking>(`${API}/bookings/${id}/confirm`, { version });
  }
  decline(id: string, reason: string, version: number) {
    return this.http.post<Booking>(`${API}/bookings/${id}/decline`, { reason, version });
  }
  move(id: string, slotStart: string, version: number) {
    return this.http.post<Booking>(`${API}/bookings/${id}/move`, { slotStart, version });
  }
  noShow(id: string, version: number) {
    return this.http.post<Booking>(`${API}/bookings/${id}/no-show`, { version });
  }
  /** Free slots for the front desk (moving a booking): every free slot, without the public lead time and horizon. */
  staffSlots(from: string, days = 7) {
    const params = new HttpParams().set('from', from).set('days', String(days));
    return this.http.get<SlotDay[]>(`${API}/bookings/slots`, { params });
  }
  pendingCount() {
    return this.http.get<{ count: number }>(`${API}/bookings/pending-count`);
  }

  settings() {
    return this.http.get<BookingSettings>(`${API}/booking/settings`);
  }
  saveSettings(body: BookingSettings) {
    return this.http.put<BookingSettings>(`${API}/booking/settings`, body);
  }
}
