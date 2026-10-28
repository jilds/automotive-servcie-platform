import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Helpers shared by the public workshop pages (shop, quote): the customer's name rule and date formatting. */

export const NAME_MAX = 120;

/** The name goes into an e-mail: one line, no links, no tags, no control characters (the server's `OneLineName`). */
// eslint-disable-next-line no-control-regex
const NOT_PLAIN = /:\/\/|www\.|[<>]|[\u0000-\u001f\u007f-\u009f\u2028\u2029]/i;
export const plainName: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  NOT_PLAIN.test(String(control.value ?? '')) ? { plainName: true } : null;

/** Formats an instant in the UI language, in a time zone (IANA id) or else the browser's. */
export function formatInstant(instant: string, lang: string, options: Intl.DateTimeFormatOptions, timeZone?: string | null): string {
  let zone = timeZone || undefined;
  try {
    if (zone) new Intl.DateTimeFormat(lang, { timeZone: zone });
  } catch {
    zone = undefined; // An unknown zone id: the browser's zone.
  }
  return new Intl.DateTimeFormat(lang, { ...options, timeZone: zone }).format(new Date(instant));
}

export const DATE_TIME: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' };
export const LONG_DATE_TIME: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' };
