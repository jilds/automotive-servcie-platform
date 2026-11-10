import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { BillingApi } from './billing-api.service';
import { issuedAfterDiscard, KIND_TEXT, PAYMENT_STATUS_BADGE, paymentStatusText, STATUS_BADGE, statusText } from './billing-logic';
import {
  BillingSettings,
  INVOICE_STATUSES,
  InvoiceKind,
  InvoicePage,
  InvoiceQuery,
  InvoiceStatus,
  InvoiceSummary,
  PAYMENT_STATUSES,
  PaymentStatusOfInvoice,
} from './billing.models';

const PAGE_SIZE = 20;

/** The filters of the list as the form holds them ('' = all). */
export interface InvoiceFilters {
  status: InvoiceStatus | '';
  kind: InvoiceKind | '';
  paymentStatus: PaymentStatusOfInvoice | '';
  from: string;
  to: string;
  q: string;
}

export const NO_FILTERS: InvoiceFilters = { status: '', kind: '', paymentStatus: '', from: '', to: '', q: '' };

/** The query for the API: empty filters left out, a search under 2 characters ignored (the server needs 2). */
export function toQuery(f: InvoiceFilters, page: number): InvoiceQuery {
  return {
    status: f.status ? [f.status] : undefined,
    kind: f.kind,
    paymentStatus: f.paymentStatus,
    from: f.from || undefined,
    to: f.to || undefined,
    q: f.q.trim().length >= 2 ? f.q.trim() : undefined,
    page,
    size: PAGE_SIZE,
  };
}

/**
 * `/invoices` for the owner and the front desk: invoices and credit notes with filters (status, kind, payment
 * status, dates, search by number, customer, NIF or plate), paging, state badges, and a link to each document.
 */
@Component({
  selector: 'app-invoices-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-3">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'billing.list.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'billing.list.subtitle' | t }}</p>
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

    @if (settings()?.invoicing?.sandbox) {
      <app-alert tone="warning"><span data-test="sandbox">{{ 'billing.sandbox.banner' | t }}</span></app-alert>
    }
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <form class="card shadow-sm mb-3" novalidate (submit)="$event.preventDefault(); apply()" data-test="filters" [attr.aria-label]="'billing.filter.title' | t">
      <div class="card-body">
        <div class="row g-3">
          <div class="col-12 col-md-6 col-lg-4">
            <label class="form-label" for="f-q">{{ 'billing.filter.search' | t }}</label>
            <input id="f-q" class="form-control" type="search" autocomplete="off" [value]="draft().q" [placeholder]="'billing.filter.searchPlaceholder' | t" (input)="set('q', $event)" />
            @if (draft().q.trim().length === 1) {
              <div class="form-text">{{ 'billing.filter.searchShort' | t }}</div>
            }
          </div>
          <div class="col-6 col-md-3 col-lg-2">
            <label class="form-label" for="f-status">{{ 'billing.filter.status' | t }}</label>
            <select id="f-status" class="form-select" [value]="draft().status" (change)="set('status', $event)">
              <option value="">{{ 'billing.filter.all' | t }}</option>
              @for (s of statuses; track s) {
                <option [value]="s">{{ statusKey(s) | t }}</option>
              }
            </select>
          </div>
          <div class="col-6 col-md-3 col-lg-2">
            <label class="form-label" for="f-kind">{{ 'billing.filter.kind' | t }}</label>
            <select id="f-kind" class="form-select" [value]="draft().kind" (change)="set('kind', $event)">
              <option value="">{{ 'billing.filter.all' | t }}</option>
              <option value="INVOICE">{{ 'billing.kind.INVOICE' | t }}</option>
              <option value="CREDIT_NOTE">{{ 'billing.kind.CREDIT_NOTE' | t }}</option>
            </select>
          </div>
          <div class="col-12 col-md-4 col-lg-4">
            <label class="form-label" for="f-pay">{{ 'billing.filter.paymentStatus' | t }}</label>
            <select id="f-pay" class="form-select" [value]="draft().paymentStatus" (change)="set('paymentStatus', $event)">
              <option value="">{{ 'billing.filter.all' | t }}</option>
              @for (p of paymentStatuses; track p) {
                <option [value]="p">{{ paymentKey(p) | t }}</option>
              }
            </select>
          </div>
          <div class="col-6 col-md-4 col-lg-3">
            <label class="form-label" for="f-from">{{ 'billing.filter.from' | t }}</label>
            <input id="f-from" class="form-control" type="date" [value]="draft().from" (change)="set('from', $event)" />
          </div>
          <div class="col-6 col-md-4 col-lg-3">
            <label class="form-label" for="f-to">{{ 'billing.filter.to' | t }}</label>
            <input id="f-to" class="form-control" type="date" [value]="draft().to" (change)="set('to', $event)" />
          </div>
          <div class="col-12 col-lg-6 d-flex flex-wrap gap-2 align-items-end justify-content-lg-end">
            <button type="submit" class="btn btn-primary" data-test="apply"><app-icon name="search" [size]="16" />{{ 'billing.filter.apply' | t }}</button>
            <button type="button" class="btn btn-outline-secondary" data-test="reset" (click)="reset()">{{ 'billing.filter.reset' | t }}</button>
          </div>
        </div>
      </div>
    </form>

    <div class="card table-stack mb-3" [attr.aria-busy]="loading()">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>{{ 'billing.col.number' | t }}</th>
              <th>{{ 'billing.col.customer' | t }}</th>
              <th>{{ 'billing.col.plate' | t }}</th>
              <th>{{ 'billing.col.date' | t }}</th>
              <th class="text-end">{{ 'billing.col.total' | t }}</th>
              <th class="text-end">{{ 'billing.col.balance' | t }}</th>
              <th>{{ 'billing.col.status' | t }}</th>
            </tr>
          </thead>
          <tbody>
            @for (i of items(); track i.id) {
              <tr>
                <td class="cell-title">
                  <a class="fw-semibold text-break" [routerLink]="['/invoices', i.id]" [attr.aria-label]="'billing.list.open' | t: { number: i.number ?? ('billing.number.none' | t) }">{{ i.number ?? ('billing.number.none' | t) }}</a>
                  <span class="d-block small text-body-secondary">{{ kindKey(i) | t }}</span>
                </td>
                <td [attr.data-label]="'billing.col.customer' | t"><span class="text-break">{{ i.customerName }}</span>@if (i.customerNif) {<span class="d-block small text-body-secondary">{{ i.customerNif }}</span>}</td>
                <td [attr.data-label]="'billing.col.plate' | t">{{ i.plate }}</td>
                <td [attr.data-label]="'billing.col.date' | t" class="text-nowrap">{{ (i.issuedAt ?? i.createdAt) | date: 'dd/MM/yyyy' }}</td>
                <td class="text-end text-nowrap" [attr.data-label]="'billing.col.total' | t">{{ i.totalCents | money }}</td>
                <td class="text-end text-nowrap" [attr.data-label]="'billing.col.balance' | t">{{ i.paymentStatus && i.kind === 'INVOICE' ? (i.balanceCents | money) : '' }}</td>
                <td [attr.data-label]="'billing.col.status' | t">
                  <span class="d-inline-flex flex-wrap gap-1">
                    <span class="badge" [class]="statusBadge(i)" data-test="status">{{ statusKey(i.status) | t }}</span>
                    @if (i.paymentStatus; as p) {
                      <span class="badge" [class]="paymentBadge(p)" data-test="payment-status">{{ paymentKey(p) | t }}</span>
                    }
                    @if (afterDiscard(i)) {
                      <span class="badge bg-danger-subtle text-danger-emphasis border border-danger-subtle" data-test="issued-after-discard" [title]="'billing.issuedAfterDiscard.warning' | t">{{ 'billing.issuedAfterDiscard.badge' | t }}</span>
                    }
                    @if (i.refundDueCents > 0) {
                      <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle" data-test="refund">{{ 'billing.refund.badge' | t: { amount: (i.refundDueCents | money) } }}</span>
                    }
                    @if (i.sandbox) {
                      <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle">{{ 'billing.sandbox.badge' | t }}</span>
                    }
                  </span>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="cell-full">
                  <div class="py-4 text-center text-body-secondary" data-test="empty">{{ (loading() ? 'common.loading' : 'billing.list.empty') | t }}</div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    @if (total() > 0) {
      <nav class="d-flex flex-wrap align-items-center justify-content-between gap-2" [attr.aria-label]="'billing.page.nav' | t">
        <span class="small text-body-secondary" data-test="range">{{ 'billing.page.range' | t: { from: from(), to: to(), total: total() } }}</span>
        <span class="d-flex gap-2">
          <button type="button" class="btn btn-outline-secondary" data-test="prev" [disabled]="page() === 0 || loading()" (click)="go(page() - 1)">{{ 'billing.page.prev' | t }}</button>
          <button type="button" class="btn btn-outline-secondary" data-test="next" [disabled]="to() >= total() || loading()" (click)="go(page() + 1)">{{ 'billing.page.next' | t }}</button>
        </span>
      </nav>
    }
  `,
})
export class InvoicesPage implements OnInit {
  private readonly api = inject(BillingApi);
  private readonly i18n = inject(I18nService);

  protected readonly statuses = INVOICE_STATUSES;
  protected readonly paymentStatuses = PAYMENT_STATUSES;
  /** What the form shows; `applied` is what the list was loaded with. */
  protected readonly draft = signal<InvoiceFilters>({ ...NO_FILTERS });
  private readonly applied = signal<InvoiceFilters>({ ...NO_FILTERS });
  protected readonly page = signal(0);
  private readonly result = signal<InvoicePage | null>(null);
  protected readonly settings = signal<BillingSettings | null>(null);
  protected readonly loading = signal(false);
  private loadSeq = 0;
  protected readonly error = signal<string | null>(null);

  protected readonly items = computed<InvoiceSummary[]>(() => this.result()?.items ?? []);
  protected readonly total = computed(() => this.result()?.total ?? 0);
  protected readonly from = computed(() => (this.total() ? this.page() * PAGE_SIZE + 1 : 0));
  protected readonly to = computed(() => Math.min(this.total(), (this.page() + 1) * PAGE_SIZE));

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => this.settings.set(s), error: () => this.settings.set(null) });
    this.load();
  }

  load(): void {
    const seq = ++this.loadSeq;
    this.loading.set(true);
    this.error.set(null);
    this.api.list(toQuery(this.applied(), this.page())).subscribe({
      next: (r) => {
        if (seq !== this.loadSeq) return; // an older answer arriving late must not replace the newer list
        this.result.set(r);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        if (seq !== this.loadSeq) return;
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
      },
    });
  }

  protected set(field: keyof InvoiceFilters, event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.draft.update((d) => ({ ...d, [field]: value }));
  }

  protected apply(): void {
    this.applied.set({ ...this.draft() });
    this.page.set(0);
    this.load();
  }

  protected reset(): void {
    this.draft.set({ ...NO_FILTERS });
    this.apply();
  }

  protected go(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  protected statusKey = (s: InvoiceStatus): MessageKey => statusText(s);
  protected paymentKey = (p: PaymentStatusOfInvoice): MessageKey => paymentStatusText(p);
  protected kindKey = (i: InvoiceSummary): MessageKey => KIND_TEXT[i.kind];
  protected afterDiscard = issuedAfterDiscard;
  protected statusBadge = (i: InvoiceSummary) => STATUS_BADGE[i.status];
  protected paymentBadge = (p: PaymentStatusOfInvoice) => PAYMENT_STATUS_BADGE[p];
}
