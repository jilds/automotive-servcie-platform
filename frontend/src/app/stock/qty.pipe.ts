import { Pipe, PipeTransform } from '@angular/core';

const FORMATS = new Map<string, Intl.NumberFormat>();
const SIGNED = new Map<string, Intl.NumberFormat>();

/**
 * A stock quantity in the given language with up to 3 decimals, for example 1.5 as "1,5" in pt-PT; "—" when there is
 * none. `signed` adds "+" to positive values, for ledger rows.
 */
export function formatQuantity(value: number | null | undefined, lang: string, signed = false): string {
  if (value == null) return '—';
  const cache = signed ? SIGNED : FORMATS;
  let format = cache.get(lang);
  if (!format) {
    format = new Intl.NumberFormat(lang, { maximumFractionDigits: 3, ...(signed ? { signDisplay: 'exceptZero' as const } : {}) });
    cache.set(lang, format);
  }
  return format.format(value);
}

const MONEY = new Map<string, Intl.NumberFormat>();

/** Cents as euros in the given language, like the `money` pipe, for texts built in code (select options). */
export function formatMoney(cents: number | null | undefined, lang: string): string {
  if (cents == null) return '';
  let format = MONEY.get(lang);
  if (!format) {
    format = new Intl.NumberFormat(lang, { style: 'currency', currency: 'EUR' });
    MONEY.set(lang, format);
  }
  return format.format(cents / 100);
}

/** Formats a stock quantity in the UI language: {{ p.available | qty: lang() }}. Pure: the language is an argument. */
@Pipe({ name: 'qty' })
export class QtyPipe implements PipeTransform {
  transform(value: number | null | undefined, lang: string, signed = false): string {
    return formatQuantity(value, lang, signed);
  }
}
