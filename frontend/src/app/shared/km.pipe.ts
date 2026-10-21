import { Pipe, PipeTransform } from '@angular/core';

const FORMATS = new Map<string, Intl.NumberFormat>();

/** A distance in the given language, for example 100100 as "100 100 km" in pt-PT; "—" when there is no reading. */
export function formatKm(km: number | null | undefined, lang: string): string {
  if (km == null) return '—';
  let format = FORMATS.get(lang);
  if (!format) {
    format = new Intl.NumberFormat(lang, { style: 'unit', unit: 'kilometer', maximumFractionDigits: 0 });
    FORMATS.set(lang, format);
  }
  return format.format(km);
}

/**
 * Formats a mileage in the UI language: {{ v.mileage | km: lang() }}. The language is an argument so the pipe can
 * stay pure and still follow a language switch.
 */
@Pipe({ name: 'km' })
export class KmPipe implements PipeTransform {
  transform(km: number | null | undefined, lang: string): string {
    return formatKm(km, lang);
  }
}
