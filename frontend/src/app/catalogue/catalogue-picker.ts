import { ChangeDetectionStrategy, Component, inject, input, OnInit, output, signal } from '@angular/core';

import { WorkOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { CatalogueApi } from './catalogue-api.service';
import { CatalogueService, CataloguePackage } from './catalogue.models';

/** `s:<id>` for a service, `p:<id>` for a package. */
export function parseChoice(value: string): { serviceId: string } | { packageId: string } | null {
  const [kind, id] = [value.slice(0, 2), value.slice(2)];
  if (!id) return null;
  if (kind === 's:') return { serviceId: id };
  if (kind === 'p:') return { packageId: id };
  return null;
}

/**
 * Adds a catalogue service or package to a work order as labour lines at the catalogue price (owner and front desk,
 * while the order is open). An hourly service shows and is added at the current labour rate. Emits the work order as saved.
 */
@Component({
  selector: 'app-catalogue-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="catalogue-picker-title">
      <div class="card-body">
        <h3 class="h6 mb-3" id="catalogue-picker-title">{{ 'catalogue.picker.title' | t }}</h3>
        @if (error()) {
          <app-alert class="mb-3">{{ error() }}</app-alert>
        }
        @if (loaded() && !services().length && !packages().length) {
          <p class="text-body-secondary mb-0">{{ 'catalogue.picker.empty' | t }}</p>
        } @else {
          <form class="d-flex flex-wrap gap-2" (submit)="$event.preventDefault(); add(choice.value)">
            <select #choice class="form-select flex-grow-1" style="min-width: 0; max-width: 32rem" [attr.aria-label]="'catalogue.picker.choose' | t" (change)="selected.set(choice.value)">
              <option value="">{{ 'catalogue.picker.choose' | t }}</option>
              @if (services().length) {
                <optgroup [label]="'catalogue.picker.services' | t">
                  @for (s of services(); track s.id) {
                    <option [value]="'s:' + s.id">{{ s.name }} · {{ (s.effectivePriceCents ?? s.priceCents) | money }}</option>
                  }
                </optgroup>
              }
              @if (packages().length) {
                <optgroup [label]="'catalogue.picker.packages' | t">
                  @for (p of packages(); track p.id) {
                    <option [value]="'p:' + p.id">{{ p.name }} · {{ p.priceCents | money }}</option>
                  }
                </optgroup>
              }
            </select>
            <button type="submit" class="btn btn-primary" [disabled]="!selected() || busy()" [attr.aria-busy]="busy()">
              <app-icon name="plus" [size]="16" />{{ 'catalogue.picker.add' | t }}
            </button>
          </form>
        }
      </div>
    </section>
  `,
})
export class CataloguePicker implements OnInit {
  readonly workOrderId = input.required<string>();
  /** Emits the work order with its new lines. */
  readonly added = output<WorkOrder>();
  private readonly api = inject(CatalogueApi);
  private readonly i18n = inject(I18nService);
  protected readonly services = signal<CatalogueService[]>([]);
  protected readonly packages = signal<CataloguePackage[]>([]);
  protected readonly loaded = signal(false);
  protected readonly selected = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.services().subscribe({
      next: (list) => {
        this.services.set(list);
        this.loaded.set(true);
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
    this.api.packages().subscribe({ next: (list) => this.packages.set(list), error: (e) => this.error.set(this.i18n.error(e)) });
  }

  add(value: string): void {
    const choice = parseChoice(value);
    if (!choice) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.addToWorkOrder(this.workOrderId(), choice).subscribe({
      next: (w) => {
        this.busy.set(false);
        this.added.emit(w);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }
}
