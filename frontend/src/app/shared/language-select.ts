import { Component, inject } from '@angular/core';

import { I18nService, Language, LANGUAGES } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/** Drop-down to switch the UI language. */
@Component({
  selector: 'app-language-select',
  imports: [TranslatePipe],
  template: `
    <select class="form-select form-select-sm" [attr.aria-label]="'language.label' | t" [value]="i18n.lang()" (change)="i18n.use($any($event.target).value)">
      @for (l of languages; track l.code) {
        <option [value]="l.code" [selected]="l.code === i18n.lang()">{{ l.label }}</option>
      }
    </select>
  `,
})
export class LanguageSelect {
  protected readonly i18n = inject(I18nService);
  protected readonly languages: readonly { code: Language; label: string }[] = LANGUAGES;
}
