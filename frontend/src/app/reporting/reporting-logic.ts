import { HttpErrorResponse, HttpParams } from '@angular/common/http';

import type { CostVsQuoteQuery, Kpi, KpiUnavailable, PeriodQuery, ProductivityQuery } from './reporting.models';

/** Shown for a value the report could not compute (a ratio over zero, a mechanic without a job). Never 0. */
export const DASH = '—';

const numberFormats = new Map<string, Intl.NumberFormat>();
const moneyFormats = new Map<string, Intl.NumberFormat>();

function numberFormat(lang: string, digits: number): Intl.NumberFormat {
  const key = `${lang}:${digits}`;
  let f = numberFormats.get(key);
  if (!f) {
    f = new Intl.NumberFormat(lang, { minimumFractionDigits: digits, maximumFractionDigits: digits });
    numberFormats.set(key, f);
  }
  return f;
}

/** A number with fixed decimals in the UI language, or an em dash for null. */
export function formatNumber(value: number | null | undefined, lang: string, digits = 2): string {
  return value == null ? DASH : numberFormat(lang, digits).format(value);
}

/** A whole count in the UI language (no decimals), or an em dash. */
export function formatCount(value: number | null | undefined, lang: string): string {
  return formatNumber(value, lang, 0);
}

/** `12,50 %`: a percentage as the API sends it (12.5 means 12.5 %), or an em dash. */
export function formatPercent(value: number | null | undefined, lang: string): string {
  return value == null ? DASH : `${numberFormat(lang, 2).format(value)} %`;
}

/** `2,75 h`, or an em dash. */
export function formatHours(value: number | null | undefined, lang: string): string {
  return value == null ? DASH : `${numberFormat(lang, 2).format(value)} h`;
}

/** Efficiency as a ratio (1.10 = 10 % faster than the standard time), or an em dash. */
export function formatRatio(value: number | null | undefined, lang: string): string {
  return formatNumber(value, lang, 2);
}

/** Euros from integer cents in the UI language, or an em dash for null. */
export function formatMoney(cents: number | null | undefined, lang: string): string {
  if (cents == null) return DASH;
  let f = moneyFormats.get(lang);
  if (!f) {
    f = new Intl.NumberFormat(lang, { style: 'currency', currency: 'EUR' });
    moneyFormats.set(lang, f);
  }
  return f.format(cents / 100);
}

/** Like `formatMoney` with an explicit `+` for a positive amount (a difference to the quote). */
export function formatSignedMoney(cents: number | null | undefined, lang: string): string {
  if (cents == null) return DASH;
  return `${cents > 0 ? '+' : ''}${formatMoney(cents, lang)}`;
}

export function formatSignedPercent(value: number | null | undefined, lang: string): string {
  if (value == null) return DASH;
  return `${value > 0 ? '+' : ''}${formatPercent(value, lang)}`;
}

/** An instant as date and time in the workshop's time zone (falls back to the browser's zone). */
export function formatInstant(iso: string | null | undefined, timeZone: string | null | undefined, lang: string): string {
  if (!iso) return DASH;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return DASH;
  const options: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' };
  try {
    return new Intl.DateTimeFormat(lang, { ...options, timeZone: timeZone ?? undefined }).format(date);
  } catch {
    return new Intl.DateTimeFormat(lang, options).format(date);
  }
}

/** The period filter as the form holds it: `yyyy-MM-dd` or '' (the server default). */
export interface PeriodDraft {
  from: string;
  to: string;
}

export const DEFAULT_PERIOD: PeriodDraft = { from: '', to: '' };

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** What is wrong with the period before it is sent: a malformed date, or `from` after `to`. */
export function periodProblem(p: PeriodDraft): 'invalid' | 'from-after-to' | null {
  if ((p.from && !ISO_DATE.test(p.from)) || (p.to && !ISO_DATE.test(p.to))) return 'invalid';
  // ISO dates compare correctly as text.
  if (p.from && p.to && p.from > p.to) return 'from-after-to';
  return null;
}

/**
 * The input values to show once the server has answered: what the user typed, and for an empty field the date the
 * server used (its default: the first of the month of `to`, and today). Never changes what is sent.
 */
export function shownPeriod(draft: PeriodDraft, echoed: PeriodDraft): PeriodDraft {
  return { from: draft.from || echoed.from, to: draft.to || echoed.to };
}

/** Query parameters of a period; empty fields are left out so the server's default applies. */
export function periodParams(q: PeriodQuery, params = new HttpParams()): HttpParams {
  if (q.from) params = params.set('from', q.from);
  if (q.to) params = params.set('to', q.to);
  return params;
}

export function costVsQuoteParams(q: CostVsQuoteQuery, withPaging = true): HttpParams {
  let params = periodParams(q);
  if (q.mechanicId) params = params.set('mechanicId', q.mechanicId);
  if (q.hasApprovedQuote !== undefined) params = params.set('hasApprovedQuote', q.hasApprovedQuote);
  if (withPaging) params = params.set('page', q.page ?? 0).set('size', q.size ?? 50);
  return params;
}

export function productivityParams(q: ProductivityQuery): HttpParams {
  const params = periodParams(q);
  return q.mechanicId ? params.set('mechanicId', q.mechanicId) : params;
}

/** The `hasApprovedQuote` select ('' = all) as the query value. */
export function approvedQuoteFilter(value: string): boolean | undefined {
  return value === 'true' ? true : value === 'false' ? false : undefined;
}

/**
 * The file name of a download from its `Content-Disposition` header (`attachment; filename="kpis-2026-11-01-2026-11-30.csv"`,
 * or the RFC 5987 `filename*=UTF-8''...` form). Path parts and odd characters are dropped; the fallback is used when
 * the header is missing or holds nothing usable.
 */
export function filenameFromDisposition(header: string | null | undefined, fallback: string): string {
  if (!header) return fallback;
  let name: string | null = null;
  const extended = /filename\*\s*=\s*([^']*)'[^']*'([^;]+)/i.exec(header);
  if (extended) {
    try {
      name = decodeURIComponent(extended[2].trim());
    } catch {
      name = null;
    }
  }
  if (!name) {
    const quoted = /filename\s*=\s*"((?:[^"\\]|\\.)*)"/i.exec(header);
    const bare = /filename\s*=\s*([^;"\s]+)/i.exec(header);
    name = quoted ? quoted[1].replace(/\\(.)/g, '$1') : bare ? bare[1] : null;
  }
  const clean = (name ?? '').split(/[\\/]/).pop()!.replace(/[^\w.\- ]+/g, '-').replace(/^[-. ]+|[-. ]+$/g, '');
  return clean || fallback;
}

/** The KPI has its figures. The screen keys on `available`, never on the feature id. */
export function isAvailable<T>(kpi: Kpi<T>): kpi is T & { available: true } {
  return kpi.available === true;
}

export function unavailable<T>(kpi: Kpi<T>): KpiUnavailable | null {
  return kpi.available === false ? kpi : null;
}

/** Feature ids the API names in `availableWith`; each has a text `reports.availableWith.<id>`. Other ids are shown as they are. */
export const AVAILABLE_WITH_FEATURES = ['plan-12-bays', 'satisfaction', 'complaints'] as const;

export function isKnownFeature(id: string): id is (typeof AVAILABLE_WITH_FEATURES)[number] {
  return (AVAILABLE_WITH_FEATURES as readonly string[]).includes(id);
}

export const REVOKE_DELAY_MS = 1000;

/** Saves a blob under a file name through a temporary link; the object URL is revoked right after the click. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
  } finally {
    // Revoked after the browser has started reading it (revoking at once cancels the download in some browsers).
    setTimeout(() => URL.revokeObjectURL(url), REVOKE_DELAY_MS);
  }
}

/** 429 `try-again`: the server runs a few reports at once and this one found no free slot (Retry-After: 5). */
export function isBusy(err: unknown): boolean {
  return err instanceof HttpErrorResponse && err.status === 429;
}
