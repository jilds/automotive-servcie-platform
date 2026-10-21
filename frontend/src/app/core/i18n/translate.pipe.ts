import { inject, Pipe, PipeTransform } from '@angular/core';

import { I18nService } from './i18n.service';
import { MessageKey, PluralKey } from './messages.en';

/** Translates a message key in templates: {{ 'nav.board' | t }}. Impure so it follows language changes. */
@Pipe({ name: 't', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: MessageKey | PluralKey, params?: Record<string, string | number | null | undefined>): string {
    return this.i18n.t(key, params);
  }
}
