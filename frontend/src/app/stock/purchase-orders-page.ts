import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';

import { Toasts } from '../backoffice/toasts';
import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { PurchaseOrderDialog } from './purchase-order-dialog';
import { QtyPipe } from './qty.pipe';
import { StockApi } from './stock-api.service';
import { Product, PurchaseOrder, PurchaseOrderStatus } from './stock.models';

const STATUSES: PurchaseOrderStatus[] = ['DRAFT', 'ORDERED', 'RECEIVED'];

/** Stock → Purchase orders: prepare, send and receive orders of parts (owner and front desk). */
@Component({
  selector: 'app-purchase-orders-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, MoneyPipe, QtyPipe, Alert, Icon, PurchaseOrderDialog],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'stock.purchaseOrders' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'stock.po.subtitle' | t }}</p>
      </div>
      <button type="button" class="btn btn-primary" [disabled]="!activeProducts()" (click)="dialog.open(null, products())"><app-icon name="plus" [size]="16" />{{ 'stock.po.new' | t }}</button>
    </header>

    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
      <label class="form-label mb-0 me-1" for="po-filter">{{ 'stock.po.filter' | t }}</label>
      <select id="po-filter" class="form-select w-auto" (change)="filter($any($event.target).value)">
        <option value="">{{ 'stock.po.all' | t }}</option>
        @for (s of statuses; track s) {
          <option [value]="s" [selected]="status() === s">{{ statusKey(s) | t }}</option>
        }
      </select>
    </div>

    @if (!activeProducts() && loaded()) {
      <app-alert tone="info">{{ 'stock.po.noProducts' | t }}</app-alert>
    }
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <div class="vstack gap-3 mb-4">
      @for (po of orders(); track po.id) {
        <section class="card shadow-sm" [attr.aria-labelledby]="'po-' + po.id">
          <div class="card-body">
            <div class="d-flex flex-wrap align-items-start gap-2 column-gap-3 mb-2">
              <div class="me-auto overflow-hidden">
                <h2 class="h6 mb-1 text-break" [id]="'po-' + po.id">{{ 'stock.po.numberValue' | t: { number: po.number } }} · {{ po.supplier }}</h2>
                <p class="small text-body-secondary mb-0">
                  @if (po.receivedAt) {
                    {{ 'stock.po.receivedOn' | t: { date: (po.receivedAt | date: 'dd/MM/yyyy HH:mm') ?? '' } }}
                  } @else if (po.orderedAt) {
                    {{ 'stock.po.orderedOn' | t: { date: (po.orderedAt | date: 'dd/MM/yyyy HH:mm') ?? '' } }}
                  } @else {
                    {{ 'stock.po.createdDraft' | t }}
                  }
                </p>
              </div>
              <span class="badge" [class]="badge(po.status)">{{ statusKey(po.status) | t }}</span>
            </div>
            @if (po.notes) {
              <p class="small text-break mb-2">{{ po.notes }}</p>
            }
            <ul class="list-unstyled small mb-2">
              @for (l of po.lines; track $index) {
                <li class="d-flex flex-wrap justify-content-between gap-2 py-1 border-bottom">
                  <span class="text-break">{{ l.quantity | qty: lang() }} {{ l.unit }} × {{ l.name }} <span class="text-body-secondary">({{ l.sku }})</span></span>
                  <span class="text-nowrap">{{ l.totalCents | money }}</span>
                </li>
              }
            </ul>
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="fw-semibold me-auto">{{ 'stock.po.total' | t }}: {{ po.totalCents | money }}</span>
              @switch (po.status) {
                @case ('DRAFT') {
                  <button type="button" class="btn btn-sm btn-outline-secondary" (click)="dialog.open(po, products())">{{ 'common.edit' | t }}</button>
                  <button type="button" class="btn btn-sm btn-primary" [disabled]="busy() === po.id" (click)="markOrdered(po)">{{ 'stock.po.markOrdered' | t }}</button>
                }
                @case ('ORDERED') {
                  @if (confirming() === po.id) {
                    <span class="small text-body-secondary w-100 text-sm-end">{{ 'stock.po.receiveHelp' | t }}</span>
                    <button type="button" class="btn btn-sm btn-outline-secondary" (click)="confirming.set(null)">{{ 'common.cancel' | t }}</button>
                    <button type="button" class="btn btn-sm btn-primary" [disabled]="busy() === po.id" (click)="receive(po)">{{ 'stock.po.confirmReceive' | t }}</button>
                  } @else {
                    <button type="button" class="btn btn-sm btn-primary" (click)="confirming.set(po.id)">{{ 'stock.po.receive' | t }}</button>
                  }
                }
              }
            </div>
          </div>
        </section>
      } @empty {
        @if (loaded()) {
          <div class="card">
            <div class="card-body d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
              <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
              <p class="fw-semibold text-body mb-0">{{ 'stock.po.empty' | t }}</p>
              <p class="mb-0">{{ 'stock.po.emptyHint' | t }}</p>
            </div>
          </div>
        }
      }
    </div>

    <app-purchase-order-dialog #dialog (saved)="saved($event)" />
  `,
})
export class PurchaseOrdersPage implements OnInit {
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  protected readonly lang = this.i18n.lang;
  protected readonly statuses = STATUSES;
  protected readonly orders = signal<PurchaseOrder[]>([]);
  /** Every product, inactive ones too: a draft may still hold them. */
  protected readonly products = signal<Product[]>([]);
  protected readonly activeProducts = signal(true);
  protected readonly status = signal<PurchaseOrderStatus | null>(null);
  protected readonly loaded = signal(false);
  protected readonly busy = signal<string | null>(null);
  /** The order whose receipt waits for a second click. */
  protected readonly confirming = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
    this.api.products(undefined, { activeOnly: false }).subscribe({
      next: (list) => {
        this.products.set(list);
        this.activeProducts.set(list.some((p) => p.active));
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }

  protected statusKey(s: PurchaseOrderStatus): MessageKey {
    return `stock.po.status.${s}` as MessageKey;
  }

  protected badge(s: PurchaseOrderStatus): string {
    switch (s) {
      case 'DRAFT':
        return 'badge bg-secondary-subtle text-secondary-emphasis';
      case 'ORDERED':
        return 'badge bg-info-subtle text-info-emphasis';
      default:
        return 'badge bg-success-subtle text-success-emphasis';
    }
  }

  protected filter(value: string): void {
    this.status.set(value ? (value as PurchaseOrderStatus) : null);
    this.load();
  }

  protected saved(po: PurchaseOrder): void {
    this.toasts.show(this.i18n.t('stock.po.saved', { number: po.number }));
    this.load();
  }

  protected markOrdered(po: PurchaseOrder): void {
    this.run(po, this.api.markOrdered(po.id), 'stock.po.ordered');
  }

  protected receive(po: PurchaseOrder): void {
    this.run(po, this.api.receive(po.id), 'stock.po.received');
  }

  private run(po: PurchaseOrder, call: ReturnType<StockApi['receive']>, done: MessageKey): void {
    this.busy.set(po.id);
    this.error.set(null);
    call.subscribe({
      next: (saved) => {
        this.busy.set(null);
        this.confirming.set(null);
        this.toasts.show(this.i18n.t(done, { number: saved.number }));
        this.load();
      },
      error: (e) => {
        this.busy.set(null);
        this.confirming.set(null);
        this.error.set(this.i18n.error(e));
        this.load();
      },
    });
  }

  private load(): void {
    this.api.purchaseOrders(this.status() ?? undefined).subscribe({
      next: (list) => {
        this.orders.set(list);
        this.loaded.set(true);
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
