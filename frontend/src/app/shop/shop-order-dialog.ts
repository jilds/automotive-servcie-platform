import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { fieldMessage, isStale, problemCode, problemOf, SERVER_ERROR } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { DATE_TIME, formatInstant, STATUS_BADGE, statusText } from './shop-forms';
import { ShopApi } from './shop-api.service';
import { CANCEL_REASON_MAX, PAYMENT_METHODS, PaymentMethod, ShopOrder } from './shop.models';

/** What the dialog shows below the details: the actions, the payment method to collect, or the cancel reason. */
export type DialogMode = 'view' | 'collect' | 'cancel';

/** A change the page reports and reloads its lists for. */
export interface ShopOrderChange {
  order: ShopOrder;
  message: string;
}

/** Actions the front desk may take now, from the status (moves in `docs/api/release-4.md`). */
export function orderActions(o: Pick<ShopOrder, 'status'>) {
  const open = o.status === 'PLACED' || o.status === 'READY';
  return { ready: o.status === 'PLACED', collect: open, cancel: open };
}

/**
 * Detail of an online order for the owner and the front desk (`/shop-orders`): the customer's contact, the lines
 * with prices, and the actions mark ready / collect (with the payment method) / cancel (with a reason). A 409
 * `stale-data` or `shop-order-not-open` reloads the order and says why.
 */
@Component({
  selector: 'app-shop-order-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, MoneyPipe, Alert, Icon],
  styles: `
    .price {
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
  `,
  template: `
    <dialog #dialog class="modal" aria-labelledby="sod-title" (close)="mode.set('view')">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <h2 class="modal-title h5 d-flex flex-wrap align-items-center gap-2" id="sod-title">
              @if (order(); as o) {
                {{ 'shop.detail.title' | t: { number: o.number } }}
                <span class="badge" [class]="badge(o)">{{ status(o) | t }}</span>
              } @else {
                {{ 'common.loading' | t }}
              }
            </h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            <div aria-live="polite">
              @if (error()) {
                <app-alert>{{ error() }}</app-alert>
              }
            </div>
            @if (order(); as o) {
              <div class="row g-3 mb-3">
                <div class="col-md-6">
                  <h3 class="h6 mb-2">{{ 'shop.detail.customer' | t }}</h3>
                  <p class="fw-semibold text-break mb-1">{{ o.name }}</p>
                  @if (o.phone) {
                    <p class="mb-1"><a [href]="telHref(o.phone)">{{ o.phone }}</a></p>
                  }
                  @if (o.email) {
                    <p class="mb-1 text-break"><a [href]="'mailto:' + o.email">{{ o.email }}</a></p>
                  }
                  @if (o.nif) {
                    <p class="mb-1 small">{{ 'common.nif' | t }}: {{ o.nif }}</p>
                  }
                  @if (o.marketingConsent) {
                    <p class="mb-0 small text-body-secondary">{{ 'shop.detail.marketing' | t }}</p>
                  }
                </div>
                <div class="col-md-6 small">
                  <p class="mb-1">{{ 'shop.detail.placedAt' | t: { date: when(o.placedAt) } }}</p>
                  @if (o.readyAt) {
                    <p class="mb-1">{{ 'shop.detail.readyAt' | t: { date: when(o.readyAt) } }}</p>
                  }
                  @if (actions().collect) {
                    <p class="mb-1 fw-semibold">{{ 'shop.detail.holdUntil' | t: { date: when(o.holdUntil) } }}</p>
                  }
                  @if (o.collectedAt && o.paymentMethod) {
                    <p class="mb-1">{{ 'shop.detail.collected' | t: { name: o.collectedByName ?? '', date: when(o.collectedAt), method: (paymentKey(o.paymentMethod) | t) } }}</p>
                  }
                  @if (o.cancelledBy; as by) {
                    <p class="mb-1">{{ cancelledByKey(by) | t }}</p>
                  }
                  @if (o.cancelReason) {
                    <p class="mb-1 text-break">{{ 'shop.detail.cancelReason' | t: { reason: o.cancelReason } }}</p>
                  }
                </div>
              </div>

              <h3 class="h6 mb-2">{{ 'shop.detail.lines' | t }}</h3>
              <ul class="list-group mb-3">
                @for (l of o.lines; track l.productId) {
                  <li class="list-group-item d-flex justify-content-between gap-3">
                    <span class="d-flex flex-column overflow-hidden">
                      <span class="text-break">{{ l.name }} <span class="small text-body-secondary">· {{ l.sku }}</span></span>
                      <span class="small text-body-secondary">{{ 'shop.detail.unitPrice' | t: { quantity: l.quantity, unit: l.unit, price: (l.unitPriceWithVatCents | money), vat: l.vatRate } }}</span>
                    </span>
                    <span class="price">{{ l.lineTotalCents | money }}</span>
                  </li>
                }
                <li class="list-group-item d-flex justify-content-between gap-3 bg-body-tertiary fw-semibold">
                  <span>{{ 'shop.order.total' | t }}</span>
                  <span class="price">{{ o.totalCents | money }}</span>
                </li>
              </ul>

              @switch (mode()) {
                @case ('collect') {
                  <fieldset class="border rounded-3 p-3" aria-describedby="sod-method-help">
                    <legend class="h6 float-none w-auto px-1 mb-1">{{ 'shop.collect.title' | t }}</legend>
                    <p class="small text-body-secondary">{{ 'shop.collect.hint' | t: { total: (o.totalCents | money) } }}</p>
                    <div class="d-flex flex-wrap gap-2 mb-2" role="radiogroup" [attr.aria-label]="'shop.collect.method' | t">
                      @for (m of methods; track m) {
                        <input type="radio" class="btn-check" name="sod-method" [id]="'sod-method-' + m" [value]="m" [formControl]="method" />
                        <label class="btn btn-outline-primary" [for]="'sod-method-' + m">{{ paymentKey(m) | t }}</label>
                      }
                    </div>
                    @if (methodMessage(); as msg) {
                      <div class="invalid-feedback d-block" id="sod-method-help"><app-icon name="alert-circle" [size]="14" /> {{ msg | t }}</div>
                    }
                    <div class="d-flex flex-wrap gap-2 mt-3">
                      <button type="button" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="collect(o)">
                        @if (busy()) {
                          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                        }
                        {{ 'shop.collect.confirm' | t }}
                      </button>
                      <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="mode.set('view')">{{ 'common.back' | t }}</button>
                    </div>
                  </fieldset>
                }
                @case ('cancel') {
                  <div class="border rounded-3 p-3">
                    <label class="form-label fw-semibold" for="sod-reason">{{ 'shop.cancel.reason' | t }}</label>
                    <textarea id="sod-reason" class="form-control" rows="3" [attr.maxlength]="reasonMax" [formControl]="reason" [class.is-invalid]="!!reasonMessage()" [attr.aria-invalid]="!!reasonMessage()" aria-describedby="sod-reason-help"></textarea>
                    @if (reasonMessage(); as msg) {
                      <div class="invalid-feedback" id="sod-reason-help"><app-icon name="alert-circle" [size]="14" /> {{ msg | t }}</div>
                    } @else {
                      <div class="form-text" id="sod-reason-help">{{ 'shop.cancel.reasonHint' | t }}</div>
                    }
                    <div class="d-flex flex-wrap gap-2 mt-3">
                      <button type="button" class="btn btn-danger" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="cancel(o)">
                        @if (busy()) {
                          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                        }
                        {{ 'shop.cancel.confirm' | t }}
                      </button>
                      <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="mode.set('view')">{{ 'shop.cancel.keep' | t }}</button>
                    </div>
                  </div>
                }
                @default {
                  @if (actions().collect) {
                    <div class="d-flex flex-wrap gap-2">
                      @if (actions().ready) {
                        <button type="button" class="btn btn-outline-primary" [disabled]="busy()" (click)="markReady(o)"><app-icon name="check-circle" [size]="16" />{{ 'shop.action.ready' | t }}</button>
                      }
                      <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="startCollect()">{{ 'shop.action.collect' | t }}</button>
                      <button type="button" class="btn btn-outline-danger ms-sm-auto" [disabled]="busy()" (click)="startCancel()"><app-icon name="x" [size]="16" />{{ 'shop.action.cancel' | t }}</button>
                    </div>
                  }
                }
              }
            } @else if (!error()) {
              <div class="placeholder-glow vstack gap-2" aria-hidden="true">
                <span class="placeholder placeholder-lg col-6"></span>
                <span class="placeholder placeholder-block w-100"></span>
              </div>
            }
          </div>
        </div>
      </div>
    </dialog>
  `,
})
export class ShopOrderDialog {
  private readonly api = inject(ShopApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  /** After a status change; the page reloads its lists and shows the message. */
  readonly changed = output<ShopOrderChange>();
  /** The order changed elsewhere; the page reloads its lists. */
  readonly stale = output<void>();

  protected readonly methods = PAYMENT_METHODS;
  protected readonly reasonMax = CANCEL_REASON_MAX;
  protected readonly order = signal<ShopOrder | null>(null);
  readonly mode = signal<DialogMode>('view');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly method = new FormControl<PaymentMethod | null>(null, Validators.required);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(CANCEL_REASON_MAX)] });
  private readonly tick = signal(0);
  protected readonly actions = computed(() => orderActions(this.order() ?? { status: 'COLLECTED' }));

  constructor() {
    this.method.events.pipe(takeUntilDestroyed()).subscribe(() => this.tick.update((n) => n + 1));
    this.reason.events.pipe(takeUntilDestroyed()).subscribe(() => this.tick.update((n) => n + 1));
  }

  open(id: string): void {
    this.order.set(null);
    this.mode.set('view');
    this.error.set(null);
    this.busy.set(false);
    this.dialog().nativeElement.showModal();
    this.load(id);
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected badge(o: ShopOrder): string {
    return STATUS_BADGE[o.status];
  }

  protected status(o: ShopOrder): MessageKey {
    return statusText(o.status);
  }

  protected paymentKey(m: PaymentMethod): MessageKey {
    return `shop.payment.${m}`;
  }

  protected cancelledByKey(by: NonNullable<ShopOrder['cancelledBy']>): MessageKey {
    return `shop.detail.cancelledBy.${by}`;
  }

  /** Staff screens show the browser's time (the workshop's own computer). */
  protected when(instant: string): string {
    return formatInstant(instant, this.i18n.lang(), DATE_TIME);
  }

  /** A dialable number only: digits and a leading +, nothing a link could misuse. */
  protected telHref(phone: string): string {
    return 'tel:' + phone.replace(/[^\d+]/g, '');
  }

  protected methodMessage(): MessageKey | null {
    this.tick();
    return fieldMessage(this.method, { required: 'shop.collect.methodRequired' });
  }

  protected reasonMessage(): MessageKey | null {
    this.tick();
    return fieldMessage(this.reason, { required: 'error.cancel-reason-required', maxlength: 'shop.cancel.tooLong' });
  }

  startCollect(): void {
    this.method.reset(null);
    this.error.set(null);
    this.mode.set('collect');
  }

  startCancel(): void {
    this.reason.reset('');
    this.error.set(null);
    this.mode.set('cancel');
    setTimeout(() => document.getElementById('sod-reason')?.focus());
  }

  markReady(o: ShopOrder): void {
    this.run(this.api.markReady(o.id, o.version), 'shop.done.ready');
  }

  collect(o: ShopOrder): void {
    const method = this.method.value;
    if (!method) {
      this.method.markAsTouched();
      this.tick.update((n) => n + 1);
      return;
    }
    this.run(this.api.collect(o.id, method, o.version), 'shop.done.collected');
  }

  cancel(o: ShopOrder): void {
    const reason = this.reason.value.trim();
    if (!reason) {
      this.reason.setValue('');
      this.reason.markAsTouched();
      this.tick.update((n) => n + 1);
      return;
    }
    this.run(this.api.cancel(o.id, reason, o.version), 'shop.done.cancelled');
  }

  private run(request: Observable<ShopOrder>, done: MessageKey): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (o) => {
        this.busy.set(false);
        this.order.set(o);
        this.mode.set('view');
        this.changed.emit({ order: o, message: this.i18n.t(done, { number: o.number }) });
        this.close();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const code = problemCode(e);
        const fields = problemOf(e)?.errors ?? {};
        if (code === 'cancel-reason-required' || (code === 'validation' && fields['reason'])) {
          this.reason.setErrors({ [SERVER_ERROR]: 'cancel-reason-required' });
          this.reason.markAsTouched();
          this.tick.update((n) => n + 1);
          return;
        }
        if (code === 'invalid-payment-method' || (code === 'validation' && fields['paymentMethod'])) {
          this.method.setErrors({ [SERVER_ERROR]: 'invalid-payment-method' });
          this.method.markAsTouched();
          this.tick.update((n) => n + 1);
          return;
        }
        this.error.set(this.i18n.error(e));
        if (isStale(e) || code === 'shop-order-not-open') {
          // Changed by someone else (or by the expiry job): show the stored order.
          this.mode.set('view');
          const id = this.order()?.id;
          if (id) this.load(id, false);
          this.stale.emit();
        }
      },
    });
  }

  private load(id: string, clearError = true): void {
    if (clearError) this.error.set(null);
    this.api.order(id).subscribe({
      next: (o) => this.order.set(o),
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }
}
