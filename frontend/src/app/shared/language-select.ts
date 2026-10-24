import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { I18nService, LANGUAGES } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * Switches the UI language. The default is a drop-down (sidebar footer, login page); `compact` shows three small
 * buttons, PT / EN / BR, for the phone top bar, where the sidebar is behind the menu.
 */
@Component({
  selector: 'app-language-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    @if (compact()) {
      <div class="btn-group btn-group-sm language-compact" role="group" [attr.aria-label]="'language.label' | t">
        @for (l of languages; track l.code) {
          <button
            type="button"
            class="btn"
            [class.btn-primary]="l.code === i18n.lang()"
            [class.btn-outline-secondary]="l.code !== i18n.lang()"
            [attr.aria-pressed]="l.code === i18n.lang()"
            [attr.lang]="l.code"
            [title]="l.label"
            (click)="i18n.use(l.code)"
          >
            <span aria-hidden="true">{{ l.short }}</span><span class="visually-hidden">{{ l.label }}</span>
          </button>
        }
      </div>
    } @else {
      <select class="form-select form-select-sm" [attr.aria-label]="'language.label' | t" [value]="i18n.lang()" (change)="i18n.use($any($event.target).value)">
        @for (l of languages; track l.code) {
          <option [value]="l.code" [selected]="l.code === i18n.lang()">{{ l.label }}</option>
        }
      </select>
    }
  `,
})
export class LanguageSelect {
  /** Three buttons instead of a drop-down, for the phone top bar. */
  readonly compact = input(false);
  protected readonly i18n = inject(I18nService);
  protected readonly languages = LANGUAGES;
}
