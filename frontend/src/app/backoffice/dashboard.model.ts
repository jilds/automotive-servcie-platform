import { Dashboard, OPEN_STATUSES, OpenStatus } from './backoffice.models';

export interface Bar<T> {
  item: T;
  value: number;
  /** Bar length in % of the largest value, 0..100 with one decimal; 0 when every value is 0. */
  percent: number;
}

/** `value` as a percentage of `max`, clamped to 0..100, rounded to one decimal. Zero-safe. */
export function percentOf(value: number, max: number): number {
  if (!(max > 0) || !(value > 0)) return 0;
  return Math.round(Math.min(value / max, 1) * 1000) / 10;
}

/** Scales values against the largest one so the longest bar is full width. */
export function bars<T>(items: readonly T[], valueOf: (item: T) => number): Bar<T>[] {
  const max = items.reduce((m, item) => Math.max(m, valueOf(item)), 0);
  return items.map((item) => ({ item, value: valueOf(item), percent: percentOf(valueOf(item), max) }));
}

/** Open orders per board status, in board order, missing statuses counted as 0. */
export function statusBars(openByStatus: Partial<Record<OpenStatus, number>>): Bar<OpenStatus>[] {
  return bars(OPEN_STATUSES, (s) => openByStatus[s] ?? 0);
}

/** Mechanics with their open orders; the busiest one gets the full bar. */
export function workloadBars(dashboard: Pick<Dashboard, 'mechanicWorkload'>) {
  return bars(dashboard.mechanicWorkload, (m) => m.openOrders);
}

/** First day of a `YYYY-MM` month as a local date (for month-name formatting), or null when malformed. */
export function monthDate(month: string): Date | null {
  const m = /^(\d{4})-(\d{2})$/.exec(month);
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, 1) : null;
}
