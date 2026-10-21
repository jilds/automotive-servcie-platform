import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { merge } from 'rxjs';

import { fieldMessage } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from './icon';
import { currentMonth, formatPlate, isPortugal, MONTH_ERROR, MONTH_MIN, plateMessage, RegistrationControls } from './plate-format';

/** Registration countries offered in the select; a stored country outside the list is added to it. */
export const REGISTRATION_COUNTRIES = ['PT', 'ES', 'FR', 'DE', 'IT', 'NL', 'BE', 'LU', 'GB', 'CH', 'BR', 'AO', 'CV', 'MZ'] as const;

/** The registration country options with names in `lang`; `current` is added when it is not in the list. */
export function countryOptions(lang: string, current: string): { code: string; name: string }[] {
  const names = new Intl.DisplayNames([lang], { type: 'region' });
  const codes: string[] = [...REGISTRATION_COUNTRIES];
  const code = current.toUpperCase();
  if (/^[A-Z]{2}$/.test(code) && !codes.includes(code)) codes.push(code);
  return codes.map((c) => ({ code: c, name: names.of(c) ?? c }));
}

/**
 * Registration country, plate and registration month of a vehicle form, as three grid columns. Put it straight
 * inside the form's `.row`: the host is a full-width column holding its own `.row`, so the gutters line up. Build the controls with `registrationControls()`.
 * Portuguese plates are checked live against the four layouts and formatted to `XX-XX-XX` on blur; plates of other
 * countries are free text. Server marks (`invalid-plate`, `duplicate-plate`) show on the plate field.
 *
 * `<app-registration-fields [controls]="form.controls" prefix="ve" col="col-sm-6" />`
 */
@Component({
  selector: 'app-registration-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Icon],
  host: { class: 'col-12' },
  template: `
    <div class="row g-3">
    <div [class]="col()">
      <label class="form-label" [for]="prefix() + '-country'">{{ 'plate.country' | t }}</label>
      <select class="form-select" [id]="prefix() + '-country'" [formControl]="controls().registrationCountry">
        @for (c of countries(); track c.code) {
          <option [value]="c.code">{{ c.name }}</option>
        }
      </select>
    </div>
    <div [class]="col()">
      <label class="form-label" [for]="prefix() + '-plate'">{{ 'vehicles.plate' | t }}</label>
      <input
        class="form-control plate-input"
        [id]="prefix() + '-plate'"
        [formControl]="controls().plate"
        (blur)="tidy()"
        autocomplete="off"
        autocapitalize="characters"
        spellcheck="false"
        [placeholder]="pt() ? 'AA-00-AA' : ''"
        [class.is-invalid]="!!plateMessage()"
        [attr.aria-invalid]="!!plateMessage()"
        [attr.aria-describedby]="prefix() + '-plate-help'"
      />
      @if (plateMessage(); as m) {
        <div class="invalid-feedback" [id]="prefix() + '-plate-help'"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
      } @else if (pt()) {
        <div class="form-text" [id]="prefix() + '-plate-help'">{{ 'plate.layoutsHint' | t }}</div>
      }
    </div>
    <div [class]="col()">
      <label class="form-label" [for]="prefix() + '-month'">{{ 'plate.registrationMonth' | t }}</label>
      <input
        type="month"
        class="form-control"
        [id]="prefix() + '-month'"
        [formControl]="controls().registrationDate"
        [min]="monthMin"
        [max]="monthMax"
        [class.is-invalid]="!!monthMessage()"
        [attr.aria-invalid]="!!monthMessage()"
      />
      @if (monthMessage(); as m) {
        <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
      }
    </div>
    </div>
  `,
})
export class RegistrationFields {
  readonly controls = input.required<RegistrationControls>();
  /** Prefix of the element ids: `<prefix>-country`, `<prefix>-plate`, `<prefix>-month`. */
  readonly prefix = input.required<string>();
  /** Grid classes of each of the three columns. */
  readonly col = input('col-sm-6');

  private readonly i18n = inject(I18nService);
  protected readonly monthMin = MONTH_MIN;
  protected readonly monthMax = currentMonth();
  private readonly country = signal('PT');
  protected readonly pt = computed(() => isPortugal(this.country()));
  protected readonly countries = computed(() => countryOptions(this.i18n.lang(), this.country()));
  /**
   * Bumped on every event of the plate and month controls (value, status, touched). The parent form sets server
   * marks on these controls (`duplicate-plate`), and control errors are not signals, so the OnPush view reads this.
   */
  private readonly controlEvents = signal(0);

  constructor() {
    effect((onCleanup) => {
      const { plate, registrationDate } = this.controls();
      const sub = merge(plate.events, registrationDate.events).subscribe(() => this.controlEvents.update((n) => n + 1));
      onCleanup(() => sub.unsubscribe());
    });
    // The country decides the plate rules; follow it, also when the form sets it (e.g. the workshop default).
    effect((onCleanup) => {
      const control = this.controls().registrationCountry;
      this.country.set(control.value);
      const sub = control.valueChanges.subscribe((v) => this.country.set(v));
      onCleanup(() => sub.unsubscribe());
    });
  }

  /** On blur: a Portuguese plate becomes `XX-XX-XX` upper case; others are upper-cased and trimmed. */
  protected tidy(): void {
    const plate = this.controls().plate;
    const tidy = formatPlate(plate.value, this.country());
    if (tidy !== plate.value) plate.setValue(tidy);
  }

  protected plateMessage() {
    this.controlEvents();
    return plateMessage(this.controls().plate);
  }

  protected monthMessage() {
    this.controlEvents();
    return fieldMessage(this.controls().registrationDate, { [MONTH_ERROR]: 'plate.monthInvalid' });
  }
}
