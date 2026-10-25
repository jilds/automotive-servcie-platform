import type { MessageKey } from '../core/i18n/messages.en';
import { BookingStatus, Slot, SlotDay } from './booking.models';

/**
 * Date-only helpers for the slot picker and the agenda. Days are `YYYY-MM-DD` strings computed in UTC, so a day
 * never shifts with the browser's time zone or a daylight-saving change.
 */

const DAY_MS = 86_400_000;

function utc(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

/** The browser's calendar day of an instant, as `YYYY-MM-DD`. */
export function localDay(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** The calendar day of an instant in a time zone (IANA id), as YYYY-MM-DD; the browser's day without one. */
export function dayIn(date: Date, timeZone?: string | null): string {
  if (!timeZone) return localDay(date);
  try {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date);
    const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
    return `${part('year')}-${part('month')}-${part('day')}`;
  } catch {
    return localDay(date);
  }
}

export function addDays(iso: string, days: number): string {
  return new Date(utc(iso).getTime() + days * DAY_MS).toISOString().slice(0, 10);
}

/** ISO weekday: 1 = Monday … 7 = Sunday. */
export function isoWeekday(iso: string): number {
  return utc(iso).getUTCDay() || 7;
}

/** Formats a calendar day in the UI language without moving it to another day. */
export function formatDay(iso: string, lang: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(lang, { ...options, timeZone: 'UTC' }).format(utc(iso));
}

/** Name of an ISO weekday (1 = Monday) in the UI language. 2024-01-01 was a Monday. */
export function weekdayName(weekday: number, lang: string, style: 'long' | 'short' = 'long'): string {
  return formatDay(addDays('2024-01-01', weekday - 1), lang, { weekday: style });
}

/** One day of the picker's strip. */
export interface DayTile {
  date: string;
  slots: Slot[];
  /** Free places over all slots of the day. */
  free: number;
  /** False for days without a free slot (closed, full, or not offered): the tile is disabled. */
  open: boolean;
}

/**
 * The days of the strip from `from`, `count` days long, filled from the `GET /slots` answer. Days missing from the
 * answer count as closed, and slots without a free place are dropped.
 */
export function dayTiles(from: string, answer: readonly SlotDay[], count = 7): DayTile[] {
  const byDate = new Map(answer.map((d) => [d.date, d.slots]));
  return Array.from({ length: count }, (_, i) => {
    const date = addDays(from, i);
    const slots = (byDate.get(date) ?? []).filter((s) => s.free > 0);
    return { date, slots, free: slots.reduce((sum, s) => sum + s.free, 0), open: slots.length > 0 };
  });
}

/**
 * The day to show: the one asked for when it is open, else the first open day, else the first day of the strip
 * (which then says there is nothing free).
 */
export function pickDay(tiles: readonly DayTile[], wanted: string | null): string | null {
  if (!tiles.length) return null;
  const open = tiles.find((t) => t.date === wanted && t.open) ?? tiles.find((t) => t.open);
  return (open ?? tiles[0]).date;
}

/** Hours and minutes of a duration, for `booking.duration.*`. */
export function durationKey(minutes: number): { key: MessageKey; params: { hours: number; minutes: number } } {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const key: MessageKey = hours === 0 ? 'booking.duration.minutes' : rest === 0 ? 'booking.duration.hours' : 'booking.duration.hoursMinutes';
  return { key, params: { hours, minutes: hours === 0 ? minutes : rest } };
}

/** Badge colours of a booking status (Bootstrap tint pairs that follow the colour mode). */
export const STATUS_BADGE: Record<BookingStatus, string> = {
  REQUESTED: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  CONFIRMED: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  CHECKED_IN: 'bg-info-subtle text-info-emphasis border border-info-subtle',
  DECLINED: 'bg-danger-subtle text-danger-emphasis border border-danger-subtle',
  CANCELLED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
  NO_SHOW: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

export function statusText(status: BookingStatus): MessageKey {
  return `booking.status.${status}`;
}

/** Formats an instant (`slotStart`, `createdAt`) in the UI language, in a time zone (IANA id) or else the browser's. */
export function formatInstant(instant: string, lang: string, options: Intl.DateTimeFormatOptions, timeZone?: string | null): string {
  let zone = timeZone || undefined;
  try {
    if (zone) new Intl.DateTimeFormat(lang, { timeZone: zone });
  } catch {
    zone = undefined; // An unknown zone id: the browser's zone.
  }
  return new Intl.DateTimeFormat(lang, { ...options, timeZone: zone }).format(new Date(instant));
}

export const LONG_DATE: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long' };
export const TIME: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' };
