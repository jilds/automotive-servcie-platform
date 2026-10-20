import { inject, Pipe, PipeTransform } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';

/** Formats integer cents as euros in the UI language, for example 10148 as 101,48 € in Portuguese. */
@Pipe({ name: 'money', pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);
  private formats = new Map<string, Intl.NumberFormat>();

  transform(cents: number | null | undefined): string {
    if (cents == null) return '';
    const lang = this.i18n.lang();
    let format = this.formats.get(lang);
    if (!format) {
      format = new Intl.NumberFormat(lang, { style: 'currency', currency: 'EUR' });
      this.formats.set(lang, format);
    }
    return format.format(cents / 100);
  }
}
