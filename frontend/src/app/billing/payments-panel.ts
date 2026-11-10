import { DatePipe } from '@angular/common';
import { finalize } from 'rxjs';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';

import { problemCode, problemOf } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { BillingApi } from './billing-api.service';
import {
  actionsOf,
  eurosText,
  formatReference,
  isImmediate,
  leftCents,
  methodText,
  PAYMENT_BADGE,
  paymentStateText,
  parseEuros,
  pollable,
  POLL_MS,
  RELOAD_CODES,
} from './billing-logic';
import { BillingSettings, CreatePaymentRequest, IMMEDIATE_METHODS, Invoice, NOTE_MAX, Payment, PaymentMethod, SandboxOutcome } from './billing.models';

const SANDBOX_OUTCOMES: readonly SandboxOutcome[] = ['PAID', 'FAILED', 'EXPIRED'];

/**
 * Payments of an issued invoice (owner, front desk): the balance, the list with state badges, and "record a
 * payment". Cash, card and transfer are recorded as paid at once. Multibanco shows the entity, reference, amount and
 * validity to give the customer. MB WAY asks for the customer's mobile number and then waits: the panel asks the
 * server for the state every few seconds while a request is PENDING, and offers refresh and cancel. The sandbox
 * "simulate outcome" controls appear only while `GET /billing/settings` says the payments provider is a sandbox.
 */
@Component({
  selector: 'app-payments-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="pay-title">
      <div class="card-body">
        <h2 class="card-title" id="pay-title">{{ 'billing.payments.title' | t }}</h2>
        <dl class="row g-2 mb-3">
          <div class="col-6 col-md-3"><dt class="small text-body-secondary fw-semibold">{{ 'billing.pay.paid' | t }}</dt><dd class="mb-0" data-test="paid">{{ invoice().paidCents | money }}</dd></div>
          <div class="col-6 col-md-3"><dt class="small text-body-secondary fw-semibold">{{ 'billing.pay.pending' | t }}</dt><dd class="mb-0">{{ invoice().pendingCents | money }}</dd></div>
          <div class="col-6 col-md-3"><dt class="small text-body-secondary fw-semibold">{{ 'billing.pay.balance' | t }}</dt><dd class="mb-0 fw-semibold" data-test="balance">{{ invoice().balanceCents | money }}</dd></div>
          @if (invoice().overpaidCents > 0) {
            <div class="col-6 col-md-3"><dt class="small text-body-secondary fw-semibold">{{ 'billing.pay.overpaid' | t }}</dt><dd class="mb-0">{{ invoice().overpaidCents | money }}</dd></div>
          }
          @if (invoice().refundDueCents > 0) {
            <div class="col-6 col-md-3"><dt class="small text-body-secondary fw-semibold">{{ 'billing.pay.refundDue' | t }}</dt><dd class="mb-0" data-test="refund">{{ invoice().refundDueCents | money }}</dd></div>
          }
        </dl>

        <div aria-live="polite">
          @if (notice()) {
            <app-alert tone="success"><span data-test="notice">{{ notice() }}</span></app-alert>
          }
          @if (error()) {
            <app-alert>{{ error() }}</app-alert>
          }
        </div>

        <ul class="list-group mb-3" data-test="payments">
          @for (p of invoice().payments; track p.id) {
            <li class="list-group-item">
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="fw-semibold">{{ method(p) | t }}</span>
                <span class="badge" [class]="badge(p)" data-test="state">{{ state(p) | t }}</span>
                <span class="small text-body-secondary">{{ p.paidAt ? ('billing.payments.paidAt' | t: { date: (p.paidAt | date: 'dd/MM/yyyy HH:mm') }) : (p.createdAt | date: 'dd/MM/yyyy HH:mm') }}</span>
                <span class="ms-auto fw-semibold text-nowrap">{{ p.amountCents | money }}</span>
              </div>
              @if (p.note) {
                <p class="small text-body-secondary text-break mb-0 mt-1">{{ p.note }}</p>
              }
              @if (p.status === 'PENDING') {
                <div class="border rounded-3 p-3 mt-2 bg-body-tertiary" data-test="pending">
                  @if (p.multibanco; as mb) {
                    <dl class="row g-2 mb-2">
                      <div class="col-4"><dt class="small text-body-secondary fw-semibold">{{ 'billing.mb.entity' | t }}</dt><dd class="mb-0 font-monospace fs-5" data-test="entity">{{ mb.entity }}</dd></div>
                      <div class="col-8"><dt class="small text-body-secondary fw-semibold">{{ 'billing.mb.reference' | t }}</dt><dd class="mb-0 font-monospace fs-5" data-test="reference">{{ reference(mb.reference) }}</dd></div>
                      <div class="col-12"><dt class="small text-body-secondary fw-semibold">{{ 'billing.mb.amount' | t }}</dt><dd class="mb-0">{{ p.amountCents | money }}</dd></div>
                    </dl>
                  }
                  @if (p.mbway; as mw) {
                    <p class="mb-2" data-test="mbway-waiting">
                      <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>{{ 'billing.mbway.waiting' | t: { phone: mw.phoneMasked } }}
                    </p>
                  }
                  @if (p.expiresAt) {
                    <p class="small text-body-secondary mb-2">{{ 'billing.payments.expires' | t: { date: (p.expiresAt | date: 'dd/MM/yyyy HH:mm') } }}</p>
                  }
                  <div class="d-flex flex-wrap gap-2">
                    <button type="button" class="btn btn-sm btn-outline-primary" data-test="refresh" [disabled]="busy()" (click)="refresh(p)">
                      <app-icon name="refresh" [size]="16" />{{ 'billing.payments.refresh' | t }}
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-danger" data-test="cancel" [disabled]="busy()" (click)="cancel(p)">
                      <app-icon name="x" [size]="16" />{{ 'billing.payments.cancel' | t }}
                    </button>
                  </div>
                  @if (sandbox() && p.provider) {
                    <div class="mt-3" data-test="simulate">
                      <p class="small fw-semibold mb-1">{{ 'billing.payments.simulate' | t }}</p>
                      <div class="d-flex flex-wrap gap-2">
                        @for (o of outcomes; track o) {
                          <button type="button" class="btn btn-sm btn-outline-secondary" [attr.data-outcome]="o" [disabled]="busy()" (click)="simulate(p, o)">{{ outcomeText(o) | t }}</button>
                        }
                      </div>
                    </div>
                  }
                </div>
              }
            </li>
          } @empty {
            <li class="list-group-item small text-body-secondary" data-test="none">{{ 'billing.payments.empty' | t }}</li>
          }
        </ul>

        @if (canPay()) {
          <h3 class="h6">{{ 'billing.payments.record' | t }}</h3>
          <p class="small text-body-secondary">{{ 'billing.payments.left' | t: { amount: (left() | money) } }}</p>
          <div class="d-flex flex-wrap gap-2 mb-3" role="group" [attr.aria-label]="'billing.payments.pick' | t">
            @for (m of methods(); track m) {
              <button type="button" class="btn btn-outline-primary" [attr.data-method]="m" [class.active]="picked() === m" [attr.aria-pressed]="picked() === m" (click)="pick(m)">{{ methodName(m) | t }}</button>
            }
          </div>
          @if (picked(); as m) {
            <form class="border rounded-3 p-3" novalidate (submit)="$event.preventDefault(); record(m)" data-test="pay-form">
              <div class="row g-3">
                <div class="col-sm-5">
                  <label class="form-label" for="pay-amount">{{ 'billing.payments.amount' | t }}</label>
                  <input id="pay-amount" class="form-control" inputmode="decimal" autocomplete="off" [value]="amount()" [class.is-invalid]="!!amountMessage()" (input)="amount.set(value($event)); serverAmount.set(false)" />
                  @if (amountMessage(); as msg) {
                    <div class="invalid-feedback">{{ msg | t }}</div>
                  }
                </div>
                @if (m === 'MBWAY') {
                  <div class="col-sm-7">
                    <label class="form-label" for="pay-phone">{{ 'billing.payments.phone' | t }}</label>
                    <input id="pay-phone" class="form-control" type="tel" inputmode="tel" autocomplete="off" placeholder="9XX XXX XXX" [value]="phone()" [class.is-invalid]="!!phoneMessage()" (input)="phone.set(value($event))" />
                    @if (phoneMessage(); as msg) {
                      <div class="invalid-feedback">{{ msg | t }}</div>
                    } @else {
                      <div class="form-text">{{ 'billing.payments.phoneHint' | t }}</div>
                    }
                  </div>
                } @else if (immediate(m)) {
                  <div class="col-sm-7">
                    <label class="form-label" for="pay-note">{{ 'billing.payments.note' | t }}</label>
                    <input id="pay-note" class="form-control" autocomplete="off" [attr.maxlength]="noteMax" [value]="note()" (input)="note.set(value($event))" />
                  </div>
                } @else {
                  <div class="col-sm-7"><p class="form-text mt-4 mb-0">{{ 'billing.payments.multibancoHint' | t }}</p></div>
                }
              </div>
              <div class="d-flex flex-wrap gap-2 mt-3">
                <button type="submit" class="btn btn-primary" data-test="record" [disabled]="busy()" [attr.aria-busy]="busy()">
                  @if (busy()) {
                    <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                  }
                  {{ (immediate(m) ? 'billing.payments.confirm' : 'billing.payments.request') | t }}
                </button>
                <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="picked.set(null)">{{ 'common.cancel' | t }}</button>
              </div>
            </form>
          }
        } @else if (invoice().creditNote) {
          <p class="small text-body-secondary mb-0" data-test="credited">{{ 'billing.payments.credited' | t }}</p>
        }
      </div>
    </section>
  `,
})
export class PaymentsPanel {
  private readonly api = inject(BillingApi);
  private readonly i18n = inject(I18nService);

  readonly invoice = input.required<Invoice>();
  /** Which methods exist and whether the payments provider is a sandbox; null while loading (then only the three offline methods). */
  readonly settings = input<BillingSettings | null>(null);
  /** A payment changed: the screen reloads the invoice. */
  readonly changed = output<void>();

  protected readonly outcomes = SANDBOX_OUTCOMES;
  protected readonly noteMax = NOTE_MAX;
  protected readonly picked = signal<PaymentMethod | null>(null);
  protected readonly amount = signal('');
  protected readonly phone = signal('');
  protected readonly note = signal('');
  protected readonly busy = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  private readonly tried = signal(false);
  private inFlight = false;
  /** Payments already refreshed once after their expiry: no more polling for them. */
  private readonly expiredAsked = new Set<string>();
  private readonly serverPhone = signal(false);
  /** The server said the amount is more than what is left: shown once, on the amount field. */
  protected readonly serverAmount = signal(false);

  protected readonly left = computed(() => leftCents(this.invoice()));
  protected readonly canPay = computed(() => actionsOf(this.invoice()).pay);
  protected readonly methods = computed<readonly PaymentMethod[]>(() => this.settings()?.methods ?? IMMEDIATE_METHODS);
  protected readonly sandbox = computed(() => !!this.settings()?.payments.sandbox);
  protected readonly amountCents = computed(() => parseEuros(this.amount()));
  protected readonly amountMessage = computed<MessageKey | null>(() => {
    if (this.serverAmount()) return 'billing.payments.amountTooHigh';
    if (!this.tried()) return null;
    const cents = this.amountCents();
    if (cents === null || cents < 1) return 'billing.payments.amountInvalid';
    return cents > this.left() ? 'billing.payments.amountTooHigh' : null;
  });
  protected readonly phoneMessage = computed<MessageKey | null>(() => {
    if (this.serverPhone()) return 'error.invalid-phone';
    return this.tried() && this.picked() === 'MBWAY' && !this.phone().trim() ? 'billing.payments.phoneRequired' : null;
  });

  constructor() {
    // MB WAY: ask for the state every few seconds while a request waits; a change reloads the invoice.
    // Polling stops at the request's expiry with one last refresh; a tick is skipped while a call is in flight.
    effect((onCleanup) => {
      const waiting = pollable(this.invoice().payments).filter((p) => !this.expiredAsked.has(p.id));
      if (!waiting.length) return;
      const timer = setInterval(() => {
        if (this.inFlight) return;
        for (const p of waiting) {
          const expired = !!p.expiresAt && Date.now() >= Date.parse(p.expiresAt);
          if (expired) this.expiredAsked.add(p.id);
          this.inFlight = true;
          (expired ? this.api.refreshPayment(p.id) : this.api.payment(p.id)).pipe(finalize(() => (this.inFlight = false))).subscribe({
            next: (fresh) => {
              if (expired || fresh.status !== 'PENDING') this.changed.emit();
            },
            error: () => {
              if (expired) this.changed.emit();
            },
          });
          return;
        }
      }, POLL_MS);
      onCleanup(() => clearInterval(timer));
    });
  }

  protected method = (p: Payment) => methodText(p.method);
  protected methodName = methodText;
  protected state = (p: Payment) => paymentStateText(p.status);
  protected badge = (p: Payment) => PAYMENT_BADGE[p.status];
  protected reference = formatReference;
  protected immediate = isImmediate;
  protected outcomeText = (o: SandboxOutcome): MessageKey => `billing.simulate.${o}`;

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected pick(m: PaymentMethod): void {
    this.picked.set(m);
    this.amount.set(eurosText(this.left()));
    this.phone.set('');
    this.note.set('');
    this.tried.set(false);
    this.serverPhone.set(false);
    this.serverAmount.set(false);
    this.error.set(null);
    this.notice.set(null);
  }

  protected record(method: PaymentMethod): void {
    if (this.busy()) return;
    this.tried.set(true);
    this.serverPhone.set(false);
    this.serverAmount.set(false);
    this.error.set(null);
    const cents = this.amountCents();
    if (this.amountMessage() || (method === 'MBWAY' && !this.phone().trim())) return;
    const body: CreatePaymentRequest = { method, amountCents: cents ?? undefined };
    if (method === 'MBWAY') body.phone = this.phone().trim();
    else if (isImmediate(method) && this.note().trim()) body.note = this.note().trim();
    this.busy.set(true);
    this.api.createPayment(this.invoice().id, body).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.picked.set(null);
        this.notice.set(this.i18n.t(this.createdKey(p), { amount: new Intl.NumberFormat(this.i18n.lang(), { style: 'currency', currency: 'EUR' }).format(p.amountCents / 100), phone: p.mbway?.phoneMasked ?? '' }));
        this.changed.emit();
      },
      error: (e: unknown) => this.failed(e),
    });
  }

  private createdKey(p: Payment): MessageKey {
    return p.method === 'MULTIBANCO' ? 'billing.payments.created.multibanco' : p.method === 'MBWAY' ? 'billing.payments.created.mbway' : 'billing.payments.created.immediate';
  }

  protected refresh(p: Payment): void {
    this.run(this.api.refreshPayment(p.id));
  }

  protected cancel(p: Payment): void {
    this.run(this.api.cancelPayment(p.id, p.version));
  }

  protected simulate(p: Payment, outcome: SandboxOutcome): void {
    this.run(this.api.simulatePayment(p.id, outcome));
  }

  private run(call: ReturnType<BillingApi['payment']>): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.changed.emit();
      },
      error: (e: unknown) => this.failed(e),
    });
  }

  private failed(e: unknown): void {
    this.busy.set(false);
    const code = problemCode(e);
    if (code === 'invalid-phone' || (code === 'validation' && problemOf(e)?.errors?.['phone'])) {
      this.serverPhone.set(true);
      return;
    }
    if (code === 'payment-exceeds-balance') {
      // Said once, next to the amount; the invoice reloads so the amount left is right.
      this.serverAmount.set(true);
      this.changed.emit();
      return;
    }
    this.error.set(this.i18n.error(e));
    // The invoice or the payment changed under this screen (another user, a webhook): show the fresh state.
    if (code && RELOAD_CODES.has(code)) this.changed.emit();
  }
}
