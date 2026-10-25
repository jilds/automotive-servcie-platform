/** Online booking (add-on `booking`), see `docs/api/release-3.md`, "Online booking". Money in cents. */

export type BookingStatus = 'REQUESTED' | 'CONFIRMED' | 'CHECKED_IN' | 'DECLINED' | 'CANCELLED' | 'NO_SHOW';

/** A service the public page offers (`GET /public/{slug}/booking/services`). */
export interface PublicService {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  durationMinutes: number;
  priceCents: number;
  vatRate: number;
  priceWithVatCents: number;
}

/** A free drop-off slot: `start` is the instant, `time` the clock time in the workshop's time zone. */
export interface Slot {
  start: string;
  time: string;
  free: number;
}

/** One day of `GET /slots`; a day without free slots has an empty array. `date` is `YYYY-MM-DD`. */
export interface SlotDay {
  date: string;
  slots: Slot[];
}

/** Body of `POST /public/{slug}/booking/bookings`. */
export interface PublicBookingRequest {
  serviceIds: string[];
  slotStart: string;
  name: string;
  phone: string | null;
  email: string | null;
  plate: string;
  note: string | null;
  privacyAccepted: boolean;
  marketingConsent: boolean;
  /** Hidden field: people leave it empty, so it is sent as "". */
  website: string;
}

/** `201` answer of the public POST. The token is the only key of the status link. */
export interface BookingCreated {
  number: number;
  statusToken: string;
  slotStart: string;
}

/** `GET /public/{slug}/booking/bookings/{token}`: what the customer sees on the status link. */
export interface PublicBooking {
  number: number;
  status: BookingStatus;
  slotStart: string;
  workshopName: string;
  services: { name: string; durationMinutes: number; priceWithVatCents: number }[];
  canCancel: boolean;
  declineReason: string | null;
  /** The workshop time zone (IANA id), for showing slotStart. */
  timeZone: string;
}

export interface BookingService {
  serviceId: string;
  name: string;
  durationMinutes: number;
  priceCents: number;
  vatRate: number;
}

/** A booking as the front desk sees it (`GET /bookings/{id}`). */
export interface Booking {
  id: string;
  number: number;
  status: BookingStatus;
  slotStart: string;
  name: string;
  phone: string | null;
  email: string | null;
  /** Normalised, e.g. `AA00AA`. */
  plate: string;
  note: string | null;
  marketingConsent: boolean;
  createdAt: string;
  services: BookingService[];
  /** The workshop's vehicle with this plate, when there is one. */
  vehicleId: string | null;
  workOrderId: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  declineReason: string | null;
  version: number;
}

/** One opening range; ISO weekday 1 = Monday … 7 = Sunday, times `HH:mm`. */
export interface OpeningRange {
  weekday: number;
  opens: string;
  closes: string;
}

export interface Closure {
  /** `YYYY-MM-DD`. */
  day: string;
  reason: string | null;
}

/** `GET/PUT /booking/settings` (owner writes). */
export interface BookingSettings {
  slotMinutes: number;
  carsPerSlot: number;
  leadTimeHours: number;
  horizonDays: number;
  cancelUntilHours: number;
  openingHours: OpeningRange[];
  closures: Closure[];
  version: number;
  /** The workshop's time zone (IANA id); read-only, set in the workshop settings. */
  timeZone?: string;
}

/** Slot lengths the server accepts. */
export const SLOT_LENGTHS = [15, 30, 60] as const;
