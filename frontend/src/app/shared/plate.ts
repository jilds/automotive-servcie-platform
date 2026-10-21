import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { PlateEra } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { formatPlate, isPortugal, plateVariant } from './plate-format';

/** Twelve star positions on a ring, as on the EU band (viewBox 0 0 20 20). */
const STARS = Array.from({ length: 12 }, (_, i) => {
  const a = (i * Math.PI) / 6;
  return { x: +(10 + 6.4 * Math.sin(a)).toFixed(2), y: +(10 - 6.4 * Math.cos(a)).toFixed(2) };
});

/**
 * A number plate drawn in CSS, styled after its Portuguese era (see `plateVariant`). It scales with the font size and
 * stays white (or black, for classics) in both colour modes, like a real plate. Styles: `src/styles/_plate.scss`.
 *
 * `<app-plate [plate]="v.plateDisplay" [era]="v.plateEra" [country]="v.registrationCountry" [registrationDate]="v.registrationDate" size="sm" />`
 */
@Component({
  selector: 'app-plate',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'plate',
    role: 'img',
    '[attr.aria-label]': 'label()',
    '[attr.data-kind]': 'variant().kind',
    '[attr.data-size]': 'size()',
  },
  template: `
    @if (variant().kind === 'eu') {
      <span class="plate-band" aria-hidden="true">
        <svg class="plate-stars" viewBox="0 0 20 20" focusable="false">
          @for (s of stars; track $index) {
            <circle [attr.cx]="s.x" [attr.cy]="s.y" r="1.35" />
          }
        </svg>
        <span class="plate-band-code">P</span>
      </span>
    } @else if (variant().kind === 'neutral' && countryCode()) {
      <span class="plate-code" aria-hidden="true">{{ countryCode() }}</span>
    }
    <span class="plate-number" aria-hidden="true">{{ text() }}</span>
    @if (variant().dateStrip; as d) {
      <span class="plate-date" aria-hidden="true"><span>{{ d.year }}</span><span>{{ d.month }}</span></span>
    }
  `,
})
export class Plate {
  /** The plate, preferably `plateDisplay`; a stored plate is formatted when it fits a Portuguese layout. */
  readonly plate = input<string | null | undefined>(null);
  readonly era = input<PlateEra | null | undefined>(null);
  /** ISO 3166-1 alpha-2 registration country. */
  readonly country = input<string | null | undefined>(null);
  /** `YYYY-MM`; shown as the yellow date strip on 1998–2020 plates. */
  readonly registrationDate = input<string | null | undefined>(null);
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  private readonly i18n = inject(I18nService);
  protected readonly stars = STARS;
  protected readonly variant = computed(() => plateVariant(this.era(), this.registrationDate()));
  protected readonly text = computed(() => (this.plate() ? formatPlate(this.plate()!, this.country()) : '—'));
  protected readonly countryCode = computed(() => (this.country() ?? '').toUpperCase());
  protected readonly label = computed(() => {
    const plate = this.text();
    const country = this.countryCode();
    return country && !isPortugal(country) ? this.i18n.t('plate.labelCountry', { plate, country }) : this.i18n.t('plate.label', { plate });
  });
}
