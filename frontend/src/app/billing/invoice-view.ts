import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { problemCode } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { BillingApi } from './billing-api.service';
import { actionsOf, refundAlertKey, issuedAfterDiscard, KIND_TEXT, newRequestId, REQUEST_ID_REUSED, PAYMENT_STATUS_BADGE, paymentStatusText, pdfName, RELOAD_CODES, saveBlob, STATUS_BADGE, statusText } from './billing-logic';
import { Invoice, IssueResult, REASON_MAX } from './billing.models';

/** The reason form below the action buttons: which action it belongs to. */
export type ReasonAction = 'discard' | 'void' | 'credit';

/**
 * One document in full (`/invoices/:id`), for the owner and the front desk: the customer as it was invoiced, the
 * lines and totals, PDF, and the actions its state allows: try again (PENDING, FAILED), discard (FAILED, with a
 * reason), void and credit note (an ISSUED invoice, with a reason). A credit note's request id is made when its form
 * opens and reused if the same attempt is sent again, so a lost answer never creates two. The payments live in
 * `app-payments-panel`.
 */
@Component({
  selector: 'app-invoice-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    @let i = invoice();
    <header class="d-flex flex-wrap align-items-end gap-3 mb-3">
      <div class="me-auto">
        <h1 class="d-flex flex-wrap align-items-center gap-2 column-gap-3 mb-1" data-test="title">
          <span class="text-break">{{ i.number ?? ('billing.number.none' | t) }}</span>
          <span class="badge fs-6" [class]="badge()" data-test="status">{{ status() | t }}</span>
          @if (i.paymentStatus; as p) {
            <span class="badge fs-6" [class]="paymentBadge(p)" data-test="payment-status">{{ paymentText(p) | t }}</span>
          }
        </h1>
        <p class="text-body-secondary mb-0">
          {{ kindText() | t }} ·
          <a [routerLink]="['/work-orders', i.workOrderId]">{{ 'billing.detail.workOrder' | t: { number: i.workOrderNumber } }}</a>
          @if (i.creditOfId) {
            · <a [routerLink]="['/invoices', i.creditOfId]" data-test="credit-of">{{ 'billing.detail.creditOf' | t: { number: i.creditOfNumber ?? '' } }}</a>
          }
          @if (i.issuedAt) {
            · {{ 'billing.detail.issuedAt' | t: { date: (i.issuedAt | date: 'dd/MM/yyyy HH:mm') } }}
          }
        </p>
      </div>
      <div class="d-flex flex-wrap gap-2">
        @if (actions().pdf) {
          <button type="button" class="btn btn-outline-secondary" data-test="pdf" [disabled]="busy()" (click)="pdf()">{{ 'billing.action.pdf' | t }}</button>
        }
        @if (actions().retry) {
          <button type="button" class="btn btn-primary" data-test="retry" [disabled]="busy()" (click)="retry()"><app-icon name="refresh" [size]="16" />{{ 'billing.action.retry' | t }}</button>
        }
      </div>
    </header>

    @if (i.sandbox) {
      <app-alert tone="warning"><span data-test="sandbox">{{ 'billing.sandbox.banner' | t }}</span></app-alert>
    }
    <div aria-live="polite">
      @if (notice(); as n) {
        <app-alert [tone]="n.tone"><span data-test="notice">{{ n.key | t: n.params }}</span></app-alert>
      }
      @if (error()) {
        <app-alert>{{ error() }}</app-alert>
      }
    </div>

    @if (afterDiscard()) {
      <app-alert tone="danger"><strong data-test="issued-after-discard">{{ 'billing.issuedAfterDiscard.warning' | t }}</strong></app-alert>
    }
    @if (i.refundDueCents > 0) {
      <app-alert tone="warning"><span data-test="refund-alert">{{ refundKey(i.status) | t: { amount: (i.refundDueCents | money) } }}</span></app-alert>
    }
    @if (i.status === 'PENDING' || i.status === 'FAILED') {
      <app-alert [tone]="i.status === 'FAILED' ? 'danger' : 'info'">
        <span data-test="failure">
          {{ (i.status === 'PENDING' ? 'billing.failure.pending' : 'billing.failure.failed') | t }}
          @if (i.failure; as f) {
            @if (f.code === 'provider-total-mismatch') {
              <span class="d-block" data-test="mismatch">{{ 'billing.failure.mismatch' | t }}</span>
            }
            <span class="d-block text-body-secondary">{{ 'billing.failure.detail' | t: { message: f.message, attempts: f.attempts } }}</span>
          }
        </span>
      </app-alert>
    }
    @if (i.reason) {
      <app-alert tone="info"><span data-test="reason">{{ 'billing.detail.reason' | t: { reason: i.reason } }}</span></app-alert>
    }
    @if (i.creditNote; as c) {
      <app-alert tone="info">
        <a [routerLink]="['/invoices', c.id]" data-test="credit-note">{{ 'billing.detail.creditNote' | t: { number: c.number ?? ('billing.number.none' | t) } }}</a>
        ({{ creditStatus(c.status) | t }})
      </app-alert>
    }

    <div class="row g-3 mb-4">
      <section class="col-md-6" aria-labelledby="inv-customer">
        <div class="card shadow-sm h-100">
          <div class="card-body">
            <h2 class="h6 card-title" id="inv-customer">{{ 'billing.detail.customer' | t }}</h2>
            <p class="fw-semibold text-break mb-1" data-test="customer-name">{{ i.customer.finalConsumer ? ('billing.issue.mode.final' | t) : i.customer.name }}</p>
            <p class="mb-1 small">{{ 'common.nif' | t }}: {{ i.customer.nif ?? ('billing.detail.noNif' | t) }}</p>
            @if (i.customer.address; as a) {
              <p class="mb-0 small text-break">{{ a.street }}{{ a.detail ? ", " + a.detail : "" }}<br />{{ a.postalCode }} {{ a.locality }} · {{ a.country }}</p>
            } @else {
              <p class="mb-0 small text-body-secondary">{{ 'billing.detail.noAddress' | t }}</p>
            }
            @if (i.plate) {
              <p class="mb-0 small mt-2">{{ 'billing.col.plate' | t }}: {{ i.plate }}</p>
            }
          </div>
        </div>
      </section>
      <section class="col-md-6" aria-labelledby="inv-issuer">
        <div class="card shadow-sm h-100">
          <div class="card-body">
            <h2 class="h6 card-title" id="inv-issuer">{{ 'billing.detail.issuer' | t }}</h2>
            <p class="fw-semibold text-break mb-1">{{ i.issuer.name }}</p>
            <p class="mb-1 small">{{ 'common.nif' | t }}: {{ i.issuer.nif ?? '—' }}</p>
            @if (i.atcud) {
              <p class="mb-0 small">{{ 'billing.detail.atcud' | t }}: <span class="font-monospace">{{ i.atcud }}</span></p>
            }
          </div>
        </div>
      </section>
    </div>

    <h2 class="h5 mb-2">{{ 'billing.lines.title' | t }}</h2>
    <div class="card table-stack mb-4">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>{{ 'billing.lines.description' | t }}</th>
              <th class="text-end">{{ 'billing.lines.qty' | t }}</th>
              <th class="text-end">{{ 'billing.lines.unit' | t }}</th>
              <th class="text-end">{{ 'billing.lines.discount' | t }}</th>
              <th class="text-end">{{ 'billing.lines.vat' | t }}</th>
              <th class="text-end">{{ 'billing.lines.total' | t }}</th>
            </tr>
          </thead>
          <tbody>
            @for (l of i.lines; track l.id) {
              <tr>
                <td class="cell-title">{{ l.description }}</td>
                <td class="text-end" [attr.data-label]="'billing.lines.qty' | t">{{ l.quantity | number: '1.0-2' }}</td>
                <td class="text-end" [attr.data-label]="'billing.lines.unit' | t">{{ l.unitPriceCents | money }}</td>
                <td class="text-end" [attr.data-label]="'billing.lines.discount' | t">{{ l.discountPercent }}%</td>
                <td class="text-end" [attr.data-label]="'billing.lines.vat' | t">{{ l.vatRate }}%</td>
                <td class="text-end" [attr.data-label]="'billing.lines.total' | t"><strong>{{ l.totalCents | money }}</strong></td>
              </tr>
            }
          </tbody>
          <tfoot>
            <tr class="bg-body-tertiary"><td colspan="5" class="text-end">{{ 'billing.totals.net' | t }}</td><td class="text-end">{{ i.netCents | money }}</td></tr>
            @for (v of i.vatSummary; track v.vatRate) {
              <tr class="bg-body-tertiary"><td colspan="5" class="text-end">{{ 'billing.totals.vatAt' | t: { rate: v.vatRate } }} ({{ v.netCents | money }})</td><td class="text-end">{{ v.vatCents | money }}</td></tr>
            }
            <tr class="bg-body-tertiary fs-6"><td colspan="5" class="text-end"><strong>{{ 'billing.totals.total' | t }}</strong></td><td class="text-end" data-test="total"><strong>{{ i.totalCents | money }}</strong></td></tr>
          </tfoot>
        </table>
      </div>
    </div>

    @if (actions().void || actions().creditNote || actions().discard) {
      <section class="card shadow-sm mb-4" aria-labelledby="inv-actions">
        <div class="card-body">
          <h2 class="h6 card-title" id="inv-actions">{{ 'billing.actions.title' | t }}</h2>
          @if (!form()) {
            <div class="d-flex flex-wrap gap-2">
              @if (actions().discard) {
                <button type="button" class="btn btn-outline-danger" data-test="discard" (click)="start('discard')">{{ 'billing.action.discard' | t }}</button>
              }
              @if (actions().creditNote) {
                <button type="button" class="btn btn-outline-secondary" data-test="credit" (click)="start('credit')">{{ 'billing.action.credit' | t }}</button>
              }
              @if (actions().void) {
                <button type="button" class="btn btn-outline-danger" data-test="void" (click)="start('void')">{{ 'billing.action.void' | t }}</button>
              }
            </div>
          }
          @if (form(); as f) {
            <form novalidate (submit)="$event.preventDefault(); confirm(f)" data-test="reason-form">
              <p class="fw-semibold mb-1">{{ titleKey(f) | t }}</p>
              <p class="small text-body-secondary">{{ helpKey(f) | t }}</p>
              <label class="form-label" for="inv-reason">{{ 'billing.reason.label' | t }}</label>
              <textarea id="inv-reason" class="form-control" rows="2" [attr.maxlength]="reasonMax" [value]="reason()" [class.is-invalid]="!!reasonMessage()" (input)="reason.set(text($event))"></textarea>
              @if (reasonMessage(); as msg) {
                <div class="invalid-feedback">{{ msg | t }}</div>
              }
              <div class="d-flex flex-wrap gap-2 mt-3">
                <button type="submit" class="btn" [class]="f === 'credit' ? 'btn-primary' : 'btn-danger'" data-test="confirm" [disabled]="busy()" [attr.aria-busy]="busy()">
                  @if (busy()) {
                    <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                  }
                  {{ confirmKey(f) | t }}
                </button>
                <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="form.set(null)">{{ 'common.cancel' | t }}</button>
              </div>
            </form>
          }
        </div>
      </section>
    }
  `,
})
export class InvoiceView {
  private readonly api = inject(BillingApi);
  private readonly i18n = inject(I18nService);

  readonly invoice = input.required<Invoice>();
  /** The document changed on the server: the screen reloads it. */
  readonly changed = output<void>();

  protected readonly reasonMax = REASON_MAX;
  protected readonly actions = computed(() => actionsOf(this.invoice()));
  protected readonly refundKey = refundAlertKey;
  protected readonly afterDiscard = computed(() => issuedAfterDiscard(this.invoice()));
  protected readonly form = signal<ReasonAction | null>(null);
  protected readonly reason = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<{ tone: 'success' | 'warning' | 'info'; key: MessageKey; params: Record<string, string | number> } | null>(null);
  private readonly tried = signal(false);
  /** Made when the credit note form opens; reused when the same attempt is sent again. */
  private creditRequestId = newRequestId();

  protected readonly reasonMessage = computed<MessageKey | null>(() => {
    if (!this.tried()) return null;
    const r = this.reason().trim();
    return !r ? 'billing.reason.required' : r.length > REASON_MAX ? 'billing.reason.tooLong' : null;
  });

  protected badge = () => STATUS_BADGE[this.invoice().status];
  protected status = () => statusText(this.invoice().status);
  protected kindText = () => KIND_TEXT[this.invoice().kind];
  protected paymentBadge = (p: NonNullable<Invoice['paymentStatus']>) => PAYMENT_STATUS_BADGE[p];
  protected paymentText = paymentStatusText;
  protected creditStatus = (s: 'PENDING' | 'ISSUED' | 'FAILED'): MessageKey => statusText(s);

  protected text(event: Event): string {
    return (event.target as HTMLTextAreaElement).value;
  }

  protected titleKey = (a: ReasonAction): MessageKey => `billing.${a}.title`;
  protected helpKey = (a: ReasonAction): MessageKey => `billing.${a}.help`;
  protected confirmKey = (a: ReasonAction): MessageKey => `billing.${a}.confirm`;

  protected start(action: ReasonAction): void {
    this.reason.set('');
    this.tried.set(false);
    this.error.set(null);
    this.notice.set(null);
    if (action === 'credit') this.creditRequestId = newRequestId();
    this.form.set(action);
    setTimeout(() => document.getElementById('inv-reason')?.focus());
  }

  protected confirm(action: ReasonAction): void {
    if (this.busy()) return;
    this.tried.set(true);
    if (this.reasonMessage()) return;
    const reason = this.reason().trim();
    const i = this.invoice();
    this.busy.set(true);
    this.error.set(null);
    if (action === 'credit') {
      this.api.creditNote(i.id, this.creditRequestId, reason).subscribe({
        next: (r) => this.creditDone(r),
        error: (e: unknown) => this.failed(e),
      });
    } else {
      const call = action === 'void' ? this.api.void(i.id, reason, i.version) : this.api.discard(i.id, reason, i.version);
      call.subscribe({
        next: () => this.done(action === 'void' ? 'billing.done.voided' : 'billing.done.discarded', {}),
        error: (e: unknown) => this.failed(e),
      });
    }
  }

  /** 201 issued, 200 replay of an issued one, 202 stored (PENDING or FAILED: retry on the credit note's own screen). */
  private creditDone(r: IssueResult): void {
    const key: MessageKey = r.outcome === 'stored' ? (r.invoice.status === 'FAILED' ? 'billing.done.creditFailed' : 'billing.done.creditPending') : 'billing.done.credited';
    this.done(key, { number: r.invoice.number ?? '' }, r.outcome === 'stored' ? 'warning' : 'success');
  }

  private done(key: MessageKey, params: Record<string, string | number>, tone: 'success' | 'warning' = 'success'): void {
    this.busy.set(false);
    this.form.set(null);
    this.notice.set({ tone, key, params });
    this.changed.emit();
  }

  protected retry(): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.retry(this.invoice().id).subscribe({
      next: (d) =>
        this.done(d.status === 'ISSUED' ? 'billing.done.issued' : d.status === 'FAILED' ? 'billing.done.failed' : 'billing.done.pending', { number: d.number ?? '' }, d.status === 'ISSUED' ? 'success' : 'warning'),
      error: (e: unknown) => this.failed(e),
    });
  }

  protected pdf(): void {
    const i = this.invoice();
    this.busy.set(true);
    this.error.set(null);
    this.api.pdf(i.id).subscribe({
      next: (blob) => {
        this.busy.set(false);
        saveBlob(blob, pdfName(i.number, i.id));
      },
      error: (e: unknown) => this.failed(e),
    });
  }

  private failed(e: unknown): void {
    this.busy.set(false);
    this.error.set(this.i18n.error(e));
    const code = problemCode(e);
    if (code === REQUEST_ID_REUSED) this.creditRequestId = newRequestId();
    // The document changed under this screen: show its fresh state next to the message.
    if (code && RELOAD_CODES.has(code)) {
      this.form.set(null);
      this.changed.emit();
    }
  }
}
