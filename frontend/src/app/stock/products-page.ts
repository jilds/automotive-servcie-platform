import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Toasts } from '../backoffice/toasts';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { AdjustDialog } from './adjust-dialog';
import { MovementsDialog } from './movements-dialog';
import { ProductDialog } from './product-dialog';
import { QtyPipe } from './qty.pipe';
import { StockApi } from './stock-api.service';
import { Product } from './stock.models';

/** Stock → Products: what is on hand, reserved and available; adjust stock, see movements, edit (owner). */
@Component({
  selector: 'app-products-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, MoneyPipe, QtyPipe, Alert, Icon, ProductDialog, AdjustDialog, MovementsDialog],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'stock.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'stock.subtitle' | t }}</p>
      </div>
      @if (isOwner) {
        <button type="button" class="btn btn-primary" (click)="productDialog.open(null)"><app-icon name="plus" [size]="16" />{{ 'stock.newProduct' | t }}</button>
      }
    </header>

    <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
      <div class="input-group flex-grow-1" style="max-width: 24rem">
        <span class="input-group-text"><app-icon name="search" [size]="16" /></span>
        <input #q type="search" class="form-control" [attr.aria-label]="'stock.search' | t" [placeholder]="'stock.search' | t" (input)="search(q.value)" />
      </div>
      <div class="form-check">
        <input id="stock-low" class="form-check-input" type="checkbox" [checked]="lowOnly()" (change)="toggleLow()" />
        <label class="form-check-label" for="stock-low">{{ 'stock.lowOnly' | t }}</label>
      </div>
      <div class="form-check">
        <input id="stock-inactive" class="form-check-input" type="checkbox" [checked]="showInactive()" (change)="toggleInactive()" />
        <label class="form-check-label" for="stock-inactive">{{ 'stock.showInactive' | t }}</label>
      </div>
    </div>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <div class="card table-stack mb-4">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>{{ 'stock.name' | t }}</th>
              <th>{{ 'stock.bin' | t }}</th>
              <th class="text-end">{{ 'stock.onHand' | t }}</th>
              <th class="text-end">{{ 'stock.reserved' | t }}</th>
              <th class="text-end">{{ 'stock.available' | t }}</th>
              <th class="text-end">{{ 'stock.priceShort' | t }}</th>
              <th>{{ 'stock.status' | t }}</th>
              <th><span class="visually-hidden">{{ 'stock.actions' | t }}</span></th>
            </tr>
          </thead>
          <tbody>
            @for (p of products(); track p.id) {
              <tr [class.row-muted]="!p.active">
                <td class="cell-title">
                  <div>{{ p.name }}</div>
                  <div class="small text-body-secondary fw-normal">{{ p.sku }}@if (p.brand) { · {{ p.brand }}}</div>
                </td>
                <td [attr.data-label]="'stock.bin' | t">{{ p.bin || '—' }}</td>
                <td class="text-end" [attr.data-label]="'stock.onHand' | t">{{ p.onHand | qty: lang() }} {{ p.unit }}</td>
                <td class="text-end" [attr.data-label]="'stock.reserved' | t">{{ p.reserved | qty: lang() }}</td>
                <td class="text-end fw-semibold" [attr.data-label]="'stock.available' | t" [class.text-danger-emphasis]="p.low">{{ p.available | qty: lang() }}</td>
                <td class="text-end" [attr.data-label]="'stock.priceShort' | t">{{ p.salePriceCents | money }}</td>
                <td [attr.data-label]="'stock.status' | t">
                  <span class="d-inline-flex flex-wrap gap-1">
                    @if (p.low && p.active) {
                      <span class="badge bg-danger-subtle text-danger-emphasis">{{ 'stock.low' | t }}</span>
                    }
                    @if (!p.active) {
                      <span class="badge bg-secondary-subtle text-secondary-emphasis">{{ 'stock.inactive' | t }}</span>
                    }
                    @if (p.listedOnline) {
                      <span class="badge bg-info-subtle text-info-emphasis">{{ 'stock.online' | t }}</span>
                    }
                  </span>
                </td>
                <td class="cell-actions text-nowrap">
                  <button type="button" class="btn btn-sm btn-link" (click)="adjustDialog.open(p)">{{ 'stock.adjust' | t }}</button>
                  <button type="button" class="btn btn-sm btn-link" (click)="movementsDialog.open(p)">{{ 'stock.movements' | t }}</button>
                  @if (isOwner) {
                    <button type="button" class="btn btn-sm btn-link" (click)="productDialog.open(p)">{{ 'common.edit' | t }}</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" class="cell-full">
                  <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                    <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
                    @if (filtered()) {
                      <p class="fw-semibold text-body mb-0">{{ 'stock.emptyFiltered' | t }}</p>
                    } @else {
                      <p class="fw-semibold text-body mb-0">{{ 'stock.empty' | t }}</p>
                      <p class="mb-0">{{ 'stock.emptyHint' | t }}</p>
                    }
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    <app-product-dialog #productDialog (saved)="saved($event)" />
    <app-adjust-dialog #adjustDialog (saved)="adjusted($event)" />
    <app-movements-dialog #movementsDialog />
  `,
})
export class ProductsPage implements OnInit, OnDestroy {
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  protected readonly isOwner = inject(AuthService).hasRole('OWNER');
  protected readonly lang = this.i18n.lang;
  protected readonly products = signal<Product[]>([]);
  /** `?low=1` (the dashboard's low-stock tile) opens the page filtered. */
  protected readonly lowOnly = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('low') === '1');
  protected readonly showInactive = signal(false);
  protected readonly error = signal<string | null>(null);
  private query = '';
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  /** True when a search or a filter hides products, so the empty state does not say "no products yet". */
  protected filtered(): boolean {
    return !!this.query.trim() || this.lowOnly();
  }

  protected search(q: string): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.query = q;
      this.load();
    }, 250);
  }

  protected toggleLow(): void {
    this.lowOnly.update((v) => !v);
    this.load();
  }

  protected toggleInactive(): void {
    this.showInactive.update((v) => !v);
    this.load();
  }

  protected saved(p: Product): void {
    this.toasts.show(this.i18n.t('stock.saved', { name: p.name }));
    this.load();
  }

  protected adjusted(p: Product): void {
    this.toasts.show(this.i18n.t('stock.adjusted', { name: p.name }));
    this.load();
  }

  private load(): void {
    this.error.set(null);
    this.api.products(this.query, { activeOnly: !this.showInactive(), lowOnly: this.lowOnly() }).subscribe({
      next: (list) => this.products.set(list),
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
