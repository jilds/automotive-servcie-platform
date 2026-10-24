import { ChangeDetectionStrategy, Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { maxDecimals } from '../backoffice/settings-form';
import { WorkOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { formatMoney, formatQuantity } from './qty.pipe';
import { StockApi } from './stock-api.service';
import { Product } from './stock.models';

/**
 * Adds a part from stock to a work order at its sale price and reserves it (owner and front desk, while the order is
 * open). Shows what is available; parts with nothing available cannot be chosen. Emits the work order as saved.
 */
@Component({
  selector: 'app-stock-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="stock-picker-title">
      <div class="card-body">
        <h3 class="h6 mb-3" id="stock-picker-title">{{ 'stock.picker.title' | t }}</h3>
        @if (error()) {
          <app-alert class="mb-3">{{ error() }}</app-alert>
        }
        @if (loaded() && !products().length) {
          <p class="text-body-secondary mb-0">{{ 'stock.picker.empty' | t }}</p>
        } @else {
          <form class="d-flex flex-wrap gap-2" (submit)="$event.preventDefault(); add()">
            <select class="form-select flex-grow-1" style="min-width: 0; max-width: 32rem" [attr.aria-label]="'stock.picker.choose' | t" [formControl]="product">
              <option value="">{{ 'stock.picker.choose' | t }}</option>
              @for (p of products(); track p.id) {
                <option [value]="p.id" [disabled]="p.available <= 0">{{ label(p) }}</option>
              }
            </select>
            <input class="form-control" style="width: 6.5rem" type="number" min="0.01" step="1" [attr.aria-label]="'stock.picker.quantity' | t" [placeholder]="'stock.picker.quantity' | t" [formControl]="quantity" [class.is-invalid]="quantity.invalid && quantity.touched" />
            <button type="submit" class="btn btn-primary" [disabled]="!product.value || busy()" [attr.aria-busy]="busy()">
              <app-icon name="plus" [size]="16" />{{ 'stock.picker.add' | t }}
            </button>
            @if (quantity.invalid && quantity.touched) {
              <div class="w-100 small text-danger-emphasis">{{ 'stock.picker.quantityInvalid' | t }}</div>
            }
          </form>
          <p class="form-text mb-0 mt-2">{{ 'stock.picker.hint' | t }}</p>
        }
      </div>
    </section>
  `,
})
export class StockPicker implements OnInit {
  readonly workOrderId = input.required<string>();
  /** Emits the work order with its new line. */
  readonly added = output<WorkOrder>();
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  protected readonly products = signal<Product[]>([]);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly product = new FormControl('', { nonNullable: true });
  protected readonly quantity = new FormControl<number | null>(1, [Validators.required, Validators.min(0.01), maxDecimals(2)]);

  ngOnInit(): void {
    this.load();
  }

  /** "Filtro de óleo (OF-712) · 3 un disponível · 9,00 €", or "… · sem stock disponível". */
  protected label(p: Product): string {
    const lang = this.i18n.lang();
    if (p.available <= 0) return this.i18n.t('stock.picker.none', { name: p.name, sku: p.sku });
    return this.i18n.t('stock.picker.option', {
      name: p.name,
      sku: p.sku,
      available: `${formatQuantity(p.available, lang)} ${p.unit}`,
      price: formatMoney(p.salePriceCents, lang),
    });
  }

  add(): void {
    const productId = this.product.value;
    if (!productId) return;
    if (this.quantity.invalid) {
      this.quantity.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.addToWorkOrder(this.workOrderId(), productId, Number(this.quantity.value)).subscribe({
      next: (w) => {
        this.busy.set(false);
        this.product.setValue('');
        this.quantity.reset(1);
        this.added.emit(w);
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
        this.load();
      },
    });
  }

  private load(): void {
    this.api.products().subscribe({
      next: (list) => {
        this.products.set(list);
        this.loaded.set(true);
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
