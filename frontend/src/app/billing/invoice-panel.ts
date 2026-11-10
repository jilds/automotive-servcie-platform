import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { WorkOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { BillingApi } from './billing-api.service';
import { actionsOf, refundAlertKey, canIssue, issuedAfterDiscard, KIND_TEXT, pdfName, saveBlob, STATUS_BADGE, statusText } from './billing-logic';
import { Invoice, IssueResult } from './billing.models';
import { InvoiceIssueDialog } from './invoice-issue-dialog';

/**
 * Invoices of a delivered work order, for the owner and the front desk (the work order screen never renders it for
 * a mechanic): "Issue invoice" while no invoice is active, and every document of the order with its state. A
 * PENDING or FAILED document shows the provider's message with "Try again"; payments, void and credit note live on
 * the document's own screen (`/invoices/:id`). The answer of an issue is 201 (issued), 200 (replay of an issued
 * one) or 202 (stored: still PENDING or FAILED, never lost).
 */
@Component({
  selector: 'app-invoice-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslatePipe, MoneyPipe, Alert, Icon, InvoiceIssueDialog],
  template: `
    @if (visible()) {
      <section class="card shadow-sm mb-4" aria-labelledby="invoice-panel-title">
        <div class="card-body">
          <div class="d-flex flex-wrap align-items-center gap-3 mb-2">
            <h2 class="card-title me-auto mb-0" id="invoice-panel-title">{{ 'billing.panel.title' | t }}</h2>
            @if (issuable()) {
              <button type="button" class="btn btn-sm btn-primary" data-test="issue" (click)="dialog().open()">
                <app-icon name="send" [size]="16" />{{ 'billing.panel.issue' | t }}
              </button>
            }
          </div>
          <div aria-live="polite">
            @if (notice(); as n) {
              <app-alert [tone]="n.tone"><span data-test="notice">{{ n.key | t: n.params }}</span></app-alert>
            }
            @if (error()) {
              <app-alert>{{ error() }}</app-alert>
            }
          </div>
          @if (sandbox()) {
            <app-alert tone="warning"><span data-test="sandbox">{{ 'billing.sandbox.banner' | t }}</span></app-alert>
          }
          @if (loaded() && !invoices().length) {
            <p class="small text-body-secondary mb-0" data-test="empty">{{ 'billing.panel.empty' | t }}</p>
          }
          <ul class="list-group" data-test="documents">
            @for (i of invoices(); track i.id) {
              <li class="list-group-item" [class.text-body-secondary]="i.status === 'DISCARDED'">
                <div class="d-flex flex-wrap align-items-center gap-2">
                  <a class="fw-semibold text-break" [routerLink]="['/invoices', i.id]">{{ i.number ?? ('billing.number.none' | t) }}</a>
                  <span class="small text-body-secondary">{{ kindText(i) | t }}</span>
                  <span class="badge" [class]="badge(i)" data-test="status">{{ status(i) | t }}</span>
                  @if (i.paymentStatus; as p) {
                    <span class="badge bg-body-secondary text-body-secondary border">{{ paymentText(p) | t }}</span>
                  }
                  <span class="ms-auto fw-semibold text-nowrap">{{ i.totalCents | money }}</span>
                </div>
                @if (afterDiscard(i)) {
                  <p class="small fw-semibold text-danger-emphasis mt-2 mb-0" data-test="issued-after-discard">{{ 'billing.issuedAfterDiscard.warning' | t }}</p>
                }
                @if (i.refundDueCents > 0) {
                  <p class="small fw-semibold text-warning-emphasis mt-2 mb-0" data-test="refund">{{ refundKey(i.status) | t: { amount: (i.refundDueCents | money) } }}</p>
                }
                @if (i.failure && (i.status === 'PENDING' || i.status === 'FAILED')) {
                  <p class="small text-danger-emphasis mt-2 mb-0" data-test="failure">
                    {{ (i.status === 'PENDING' ? 'billing.failure.pending' : 'billing.failure.failed') | t }}
                    <span class="text-body-secondary text-break">{{ i.failure.message }}</span>
                  </p>
                } @else if (i.status === 'PENDING') {
                  <p class="small text-body-secondary mt-2 mb-0">{{ 'billing.failure.pending' | t }}</p>
                }
                <div class="d-flex flex-wrap gap-2 mt-2">
                  @if (actions(i).retry) {
                    <button type="button" class="btn btn-sm btn-outline-primary" data-test="retry" [disabled]="busy()" (click)="retry(i)">
                      <app-icon name="refresh" [size]="16" />{{ 'billing.action.retry' | t }}
                    </button>
                  }
                  @if (actions(i).pdf) {
                    <button type="button" class="btn btn-sm btn-outline-secondary" data-test="pdf" [disabled]="busy()" (click)="pdf(i)">{{ 'billing.action.pdf' | t }}</button>
                  }
                  <a class="btn btn-sm btn-link" [routerLink]="['/invoices', i.id]">{{ 'billing.panel.open' | t }}</a>
                  @if (i.issuedAt) {
                    <span class="small text-body-secondary align-self-center ms-auto">{{ 'billing.detail.issuedAt' | t: { date: (i.issuedAt | date: 'dd/MM/yyyy HH:mm') } }}</span>
                  }
                </div>
              </li>
            }
          </ul>
        </div>
      </section>
      <app-invoice-issue-dialog
        [workOrderId]="workOrder().id"
        [workOrderNumber]="workOrder().number"
        [vehicleId]="workOrder().vehicleId"
        (issued)="issued($event)"
        (outdated)="load()"
      />
    }
  `,
})
export class InvoicePanel {
  private readonly api = inject(BillingApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required(InvoiceIssueDialog);

  readonly workOrder = input.required<WorkOrder>();

  protected readonly invoices = signal<Invoice[]>([]);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<{ tone: 'success' | 'warning' | 'info'; key: MessageKey; params: Record<string, string | number> } | null>(null);
  /** Invoices exist only for a delivered order. */
  protected readonly visible = computed(() => this.workOrder().status === 'DELIVERED');
  protected readonly issuable = computed(() => this.loaded() && canIssue(this.workOrder().status, this.invoices()));
  protected readonly sandbox = computed(() => this.invoices().some((i) => i.sandbox));

  constructor() {
    effect(() => {
      if (this.visible()) untracked(() => this.load());
    });
  }

  load(): void {
    this.api.ofWorkOrder(this.workOrder().id).subscribe({
      next: (list) => {
        this.invoices.set(list);
        this.loaded.set(true);
      },
      error: (e: unknown) => {
        this.error.set(this.i18n.error(e));
        this.loaded.set(true);
      },
    });
  }

  protected actions = actionsOf;
  protected afterDiscard = issuedAfterDiscard;
  protected refundKey = refundAlertKey;
  protected kindText = (i: Invoice) => KIND_TEXT[i.kind];
  protected badge = (i: Invoice) => STATUS_BADGE[i.status];
  protected status = (i: Invoice) => statusText(i.status);
  protected paymentText = (p: NonNullable<Invoice['paymentStatus']>): MessageKey => `billing.paymentStatus.${p}`;

  /** 201 issued, 200 replay of an issued invoice, 202 stored as PENDING or FAILED. */
  protected issued(result: IssueResult): void {
    this.error.set(null);
    const { invoice, outcome } = result;
    const params = { number: invoice.number ?? '' };
    if (outcome === 'stored') {
      this.notice.set({ tone: 'warning', key: invoice.status === 'FAILED' ? 'billing.done.failed' : 'billing.done.pending', params });
    } else {
      this.notice.set({ tone: 'success', key: outcome === 'replay' ? 'billing.done.replay' : 'billing.done.issued', params });
    }
    this.load();
  }

  protected retry(i: Invoice): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.retry(i.id).subscribe({
      next: (done) => {
        this.busy.set(false);
        const params = { number: done.number ?? '' };
        this.notice.set(
          done.status === 'ISSUED'
            ? { tone: 'success', key: 'billing.done.issued', params }
            : { tone: 'warning', key: done.status === 'FAILED' ? 'billing.done.failed' : 'billing.done.pending', params },
        );
        this.load();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
        this.load();
      },
    });
  }

  protected pdf(i: Invoice): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.pdf(i.id).subscribe({
      next: (blob) => {
        this.busy.set(false);
        saveBlob(blob, pdfName(i.number, i.id));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }
}
