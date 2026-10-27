import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { ShopOrderChange, ShopOrderDialog } from './shop-order-dialog';
import { DATE_TIME, formatInstant, STATUS_BADGE, statusText } from './shop-forms';
import { ShopApi } from './shop-api.service';
import { ShopOrderStatus, ShopOrderSummary } from './shop.models';

export type OrderTab = 'prepare' | 'ready' | 'done';

const TABS: { tab: OrderTab; key: MessageKey; empty: MessageKey }[] = [
  { tab: 'prepare', key: 'shop.staff.tab.prepare', empty: 'shop.staff.empty.prepare' },
  { tab: 'ready', key: 'shop.staff.tab.ready', empty: 'shop.staff.empty.ready' },
  { tab: 'done', key: 'shop.staff.tab.done', empty: 'shop.staff.empty.done' },
];

/** The tab an order belongs to: PLACED to prepare, READY waiting for the customer, the rest done. */
export function tabOf(status: ShopOrderStatus): OrderTab {
  return status === 'PLACED' ? 'prepare' : status === 'READY' ? 'ready' : 'done';
}

/**
 * Online shop orders for the owner and the front desk (`/shop-orders`): to prepare, ready for collection, and done
 * in the last 30 days (the server's default range, one request split here). An order opens in a dialog.
 */
@Component({
  selector: 'app-shop-orders-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, MoneyPipe, Alert, Icon, ShopOrderDialog],
  styles: `
    .price {
      font-weight: 650;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
  `,
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'shop.staff.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'shop.staff.subtitle' | t }}</p>
      </div>
      <button type="button" class="btn btn-outline-secondary" [disabled]="loading()" [attr.aria-busy]="loading()" (click)="load()">
        @if (loading()) {
          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
        } @else {
          <app-icon name="refresh" [size]="16" />
        }
        {{ 'common.refresh' | t }}
      </button>
    </header>

    <div aria-live="polite">
      @if (notice()) {
        <app-alert tone="success">{{ notice() }}</app-alert>
      }
    </div>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <nav class="nav nav-underline flex-nowrap overflow-x-auto border-bottom mb-3" role="tablist" [attr.aria-label]="'shop.staff.tabs' | t">
      @for (t of tabs; track t.tab) {
        <button
          type="button"
          class="nav-link d-inline-flex align-items-center gap-2 text-nowrap"
          role="tab"
          [id]="'so-tab-' + t.tab"
          [class.active]="tab() === t.tab"
          [attr.aria-selected]="tab() === t.tab"
          aria-controls="so-panel"
          (click)="tab.set(t.tab)"
        >
          {{ t.key | t }}
          @if (t.tab !== 'done' && count(t.tab)) {
            <span class="badge" [class]="t.tab === 'prepare' ? 'bg-warning-subtle text-warning-emphasis' : 'bg-success-subtle text-success-emphasis'">{{ count(t.tab) }}</span>
          }
        </button>
      }
    </nav>

    <section id="so-panel" role="tabpanel" [attr.aria-labelledby]="'so-tab-' + tab()">
      @if (tab() === 'done') {
        <p class="small text-body-secondary">{{ 'shop.staff.doneHint' | t }}</p>
      }
      <div class="list-group" [attr.aria-busy]="loading()">
        @for (o of shown(); track o.id) {
          <button type="button" class="list-group-item list-group-item-action d-flex gap-3 align-items-start" [attr.aria-label]="'shop.staff.open' | t: { number: o.number }" (click)="open(o)">
            <span class="d-flex flex-column flex-grow-1 overflow-hidden gap-1">
              <span class="d-flex flex-wrap justify-content-between gap-2">
                <span class="fw-semibold text-break">#{{ o.number }} · {{ o.name }}</span>
                <span class="price">{{ o.totalCents | money }}</span>
              </span>
              <span class="d-flex flex-wrap align-items-center gap-2 small text-body-secondary">
                <span>{{ 'shop.staff.lines' | t: { count: o.lineCount } }}</span>
                <span>· {{ 'shop.staff.placed' | t: { date: when(o.placedAt) } }}</span>
                @if (o.status === 'PLACED' || o.status === 'READY') {
                  <span>· {{ 'shop.staff.holdUntil' | t: { date: when(o.holdUntil) } }}</span>
                }
              </span>
            </span>
            @if (tab() === 'done') {
              <span class="badge flex-shrink-0" [class]="badge(o)">{{ status(o) | t }}</span>
            }
          </button>
        } @empty {
          <div class="list-group-item text-body-secondary py-4 text-center">
            @if (loading()) {
              {{ 'common.loading' | t }}
            } @else {
              {{ emptyKey() | t }}
            }
          </div>
        }
      </div>
    </section>

    <app-shop-order-dialog (changed)="changed($event)" (stale)="load(false)" />
  `,
})
export class ShopOrdersPage implements OnInit {
  private readonly api = inject(ShopApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required(ShopOrderDialog);

  protected readonly tabs = TABS;
  readonly tab = signal<OrderTab>('prepare');
  private readonly orders = signal<ShopOrderSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  /** Oldest first while open (first come, first served), newest first when done. */
  protected readonly shown = computed(() => {
    const list = this.orders().filter((o) => tabOf(o.status) === this.tab());
    return this.tab() === 'done' ? list : [...list].reverse();
  });
  protected readonly emptyKey = computed(() => TABS.find((t) => t.tab === this.tab())!.empty);

  ngOnInit(): void {
    this.load();
  }

  load(clearNotice = true): void {
    this.loading.set(true);
    this.error.set(null);
    if (clearNotice) this.notice.set(null);
    this.api.orders().subscribe({
      next: (list) => {
        this.orders.set(list);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
      },
    });
  }

  protected count(tab: OrderTab): number {
    return this.orders().filter((o) => tabOf(o.status) === tab).length;
  }

  open(o: ShopOrderSummary): void {
    this.notice.set(null);
    this.dialog().open(o.id);
  }

  protected changed(change: ShopOrderChange): void {
    this.load();
    this.notice.set(change.message);
  }

  protected badge(o: ShopOrderSummary): string {
    return STATUS_BADGE[o.status];
  }

  protected status(o: ShopOrderSummary): MessageKey {
    return statusText(o.status);
  }

  protected when(instant: string): string {
    return formatInstant(instant, this.i18n.lang(), DATE_TIME);
  }
}
