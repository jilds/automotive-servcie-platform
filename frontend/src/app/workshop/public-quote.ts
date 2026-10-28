import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { applyProblem, fieldMessage, problemCode, problemOf, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { Plate } from '../shared/plate';
import { PublicPage } from '../shared/public-page';
import { formatInstant, LONG_DATE_TIME, NAME_MAX, plainName } from '../shared/public-forms';
import { QuoteApi } from './quote-api.service';
import { NOTE_MAX, QUOTE_BADGE } from './quote-logic';
import { PublicQuote, QuoteStatus } from './quote.models';

/** Why the page cannot show a quote: unknown token or workshop, or too many requests. */
export type QuotePageState = 'ok' | 'not-found' | 'limited';

/** The page state a failed GET leads to; null when the page shows the message instead. */
export function quotePageState(err: unknown): QuotePageState | null {
  const problem = problemOf(err);
  if (problem?.status === 429) return 'limited';
  return problem?.status === 404 ? 'not-found' : null;
}

/** Codes after which the stored quote is shown again (decided, superseded or expired meanwhile). */
const RELOAD_CODES = new Set(['quote-not-open', 'quote-expired']);

export function approveForm() {
  return new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(NAME_MAX), plainName] }),
    accepted: new FormControl(false, { nonNullable: true, validators: Validators.requiredTrue }),
  });
}

export function rejectForm() {
  return new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(NOTE_MAX)] }),
  });
}

/**
 * Public page of a quote, `/w/{slug}/quote/{token}`: the vehicle, the lines, VAT by rate and the total, how long it
 * is valid (in the workshop's time zone), and approve (typed name + acceptance) or reject (optional reason) while it
 * is open; afterwards the decision. No sign-in and no token sent (the link is the key).
 */
@Component({
  selector: 'app-public-quote',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DecimalPipe, TranslatePipe, MoneyPipe, Alert, Icon, Plate, PublicPage],
  styles: `
    .ticket {
      border-top: 4px solid var(--bs-primary);
    }
    .price {
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .cap::first-letter {
      text-transform: uppercase;
    }
  `,
  template: `
    <app-public-page [slug]="slug()">
      <div class="card shadow-sm ticket">
        <div class="card-body">
          @if (quote(); as q) {
            <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
              <h1 class="h4 mb-0 me-auto">{{ 'quote.public.title' | t: { number: q.number } }}</h1>
              <span class="badge fs-6 fw-semibold" [class]="badge(q.status)" aria-live="polite" data-test="status">{{ statusKey(q.status) | t }}</span>
            </div>
            <p class="text-body-secondary mb-3">{{ 'quote.public.subtitle' | t: { number: q.workOrderNumber, workshop: q.workshopName } }}</p>

            @if (decidedKey(q); as key) {
              <app-alert [tone]="q.status === 'APPROVED' ? 'success' : 'info'"><span data-test="decided">{{ key | t: { date: when(q.decidedAt, q.timeZone) } }}</span></app-alert>
            } @else if (!q.canDecide) {
              <app-alert tone="warning"><span data-test="closed">{{ closedKey(q.status) | t: { date: when(q.validUntil, q.timeZone) } }}</span></app-alert>
            } @else {
              <p class="mb-3">{{ 'quote.public.intro' | t }}</p>
            }

            <dl class="row mb-3">
              @if (q.vehicle; as v) {
                <dt class="col-sm-4 small text-body-secondary fw-normal">{{ 'quote.public.vehicle' | t }}</dt>
                <dd class="col-sm-8 d-flex flex-wrap align-items-center gap-2">
                  <app-plate [plate]="v.plateDisplay" [era]="v.plateEra" [country]="v.registrationCountry" size="sm" />
                  @if (v.label) {
                    <span class="text-break">{{ v.label }}</span>
                  }
                </dd>
              }
              <dt class="col-sm-4 small text-body-secondary fw-normal">{{ 'quote.public.sentAt' | t }}</dt>
              <dd class="col-sm-8 cap">{{ when(q.sentAt, q.timeZone) }}</dd>
              <dt class="col-sm-4 small text-body-secondary fw-normal">{{ 'quote.public.validUntil' | t }}</dt>
              <dd class="col-sm-8 cap fw-semibold" data-test="valid-until">{{ when(q.validUntil, q.timeZone) }}</dd>
            </dl>

            <h2 class="h6 mb-2">{{ 'quote.public.lines' | t }}</h2>
            <div class="card table-stack mb-3">
              <div class="table-responsive">
                <table class="table align-middle mb-0">
                  <thead>
                    <tr>
                      <th>{{ 'quote.public.description' | t }}</th>
                      <th class="text-end">{{ 'quote.public.qty' | t }}</th>
                      <th class="text-end">{{ 'quote.public.unitPrice' | t }}</th>
                      <th class="text-end">{{ 'quote.public.discount' | t }}</th>
                      <th class="text-end">{{ 'quote.public.vatRate' | t }}</th>
                      <th class="text-end">{{ 'quote.public.net' | t }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (l of q.lines; track $index) {
                      <tr>
                        <td class="cell-title text-break">{{ l.description }}</td>
                        <td class="text-end" [attr.data-label]="'quote.public.qty' | t">{{ l.quantity | number: '1.0-2' }}</td>
                        <td class="text-end price" [attr.data-label]="'quote.public.unitPrice' | t">{{ l.unitPriceCents | money }}</td>
                        <td class="text-end" [attr.data-label]="'quote.public.discount' | t">{{ l.discountPercent | number: '1.0-2' }}%</td>
                        <td class="text-end" [attr.data-label]="'quote.public.vatRate' | t">{{ l.vatRate | number: '1.0-2' }}%</td>
                        <td class="text-end price" [attr.data-label]="'quote.public.net' | t"><strong>{{ l.netCents | money }}</strong></td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>

            <ul class="list-group mb-4" data-test="totals">
              <li class="list-group-item d-flex justify-content-between gap-3">
                <span>{{ 'quote.public.netTotal' | t }}</span><span class="price">{{ q.netCents | money }}</span>
              </li>
              @for (r of q.vatByRate; track r.vatRate) {
                <li class="list-group-item d-flex justify-content-between gap-3 small text-body-secondary">
                  <span>{{ 'quote.public.vatAt' | t: { rate: (r.vatRate | number: '1.0-2'), net: (r.netCents | money) } }}</span><span class="price">{{ r.vatCents | money }}</span>
                </li>
              }
              <li class="list-group-item d-flex justify-content-between gap-3">
                <span>{{ 'quote.public.vatTotal' | t }}</span><span class="price">{{ q.vatCents | money }}</span>
              </li>
              <li class="list-group-item d-flex justify-content-between gap-3 bg-body-tertiary fw-semibold fs-5">
                <span>{{ 'quote.public.total' | t }}</span><span class="price" data-test="total">{{ q.totalCents | money }}</span>
              </li>
            </ul>

            @if (error()) {
              <app-alert>{{ error() }}</app-alert>
            }

            @if (q.canDecide) {
              @if (!rejecting()) {
                <form [formGroup]="approve" (ngSubmit)="submitApprove()" class="border rounded-3 p-3 mb-3" novalidate data-test="approve-form">
                  <h2 class="h5 mb-3">{{ 'quote.public.approveTitle' | t }}</h2>
                  <label class="form-label" for="pq-name">{{ 'quote.public.name' | t }}</label>
                  <input id="pq-name" class="form-control" formControlName="name" autocomplete="name" [attr.maxlength]="nameMax" [class.is-invalid]="invalid(approve.controls.name)" />
                  @if (invalid(approve.controls.name)) {
                    <div class="invalid-feedback">{{ nameMessageFor() | t }}</div>
                  }
                  <div class="form-check mt-3">
                    <input id="pq-accept" class="form-check-input" type="checkbox" formControlName="accepted" [class.is-invalid]="invalid(approve.controls.accepted)" />
                    <label class="form-check-label" for="pq-accept">{{ 'quote.public.accept' | t: { total: (q.totalCents | money) } }}</label>
                    @if (invalid(approve.controls.accepted)) {
                      <div class="invalid-feedback">{{ 'quote.public.acceptRequired' | t }}</div>
                    }
                  </div>
                  <div class="d-grid d-sm-flex gap-2 mt-3">
                    <button type="submit" class="btn btn-primary" data-test="approve" [disabled]="busy()" [attr.aria-busy]="busy()">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      }
                      <app-icon name="check-circle" [size]="16" />{{ 'quote.public.approve' | t }}
                    </button>
                    <button type="button" class="btn btn-outline-secondary ms-sm-auto" data-test="reject-toggle" [disabled]="busy()" (click)="rejecting.set(true)">{{ 'quote.public.rejectToggle' | t }}</button>
                  </div>
                </form>
              } @else {
                <form [formGroup]="reject" (ngSubmit)="submitReject()" class="border rounded-3 p-3 mb-3" novalidate data-test="reject-form">
                  <h2 class="h5 mb-3">{{ 'quote.public.rejectTitle' | t }}</h2>
                  <label class="form-label" for="pq-reason">{{ 'quote.public.reason' | t }}</label>
                  <textarea id="pq-reason" class="form-control" rows="3" formControlName="reason" [attr.maxlength]="noteMax" [class.is-invalid]="invalid(reject.controls.reason)"></textarea>
                  @if (invalid(reject.controls.reason)) {
                    <div class="invalid-feedback">{{ 'quote.public.reasonTooLong' | t }}</div>
                  }
                  <div class="d-grid d-sm-flex gap-2 mt-3">
                    <button type="submit" class="btn btn-danger" data-test="reject" [disabled]="busy()" [attr.aria-busy]="busy()">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      }
                      {{ 'quote.public.reject' | t }}
                    </button>
                    <button type="button" class="btn btn-outline-secondary ms-sm-auto" [disabled]="busy()" (click)="rejecting.set(false)">{{ 'quote.public.keep' | t }}</button>
                  </div>
                </form>
              }
            }
          } @else if (state() !== 'ok') {
            <div class="d-flex flex-column align-items-center gap-2 py-4 text-center" role="status" data-test="state">
              <app-icon name="info" [size]="36" class="text-body-tertiary" />
              <h1 class="h4 mb-0">{{ (state() === 'limited' ? 'quote.public.limitedTitle' : 'quote.public.notFoundTitle') | t }}</h1>
              <p class="text-body-secondary mb-0">{{ (state() === 'limited' ? 'quote.public.limited' : 'quote.public.notFound') | t }}</p>
            </div>
          } @else if (error()) {
            <app-alert>{{ error() }}</app-alert>
          } @else {
            <div class="placeholder-glow vstack gap-2" aria-hidden="true">
              <span class="placeholder placeholder-lg col-6"></span>
              <span class="placeholder col-10"></span>
              <span class="placeholder placeholder-block w-100"></span>
            </div>
          }
        </div>
      </div>
    </app-public-page>
  `,
})
export class PublicQuotePage {
  private readonly api = inject(QuoteApi);
  private readonly i18n = inject(I18nService);

  readonly slug = input.required<string>();
  readonly token = input.required<string>();

  protected readonly nameMax = NAME_MAX;
  protected readonly noteMax = NOTE_MAX;
  protected readonly quote = signal<PublicQuote | null>(null);
  protected readonly state = signal<QuotePageState>('ok');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly rejecting = signal(false);
  protected readonly approve = approveForm();
  protected readonly reject = rejectForm();


  constructor() {
    effect(() => {
      const slug = this.slug();
      const token = this.token();
      untracked(() => this.load(slug, token));
    });
  }

  protected statusKey(status: QuoteStatus): MessageKey {
    return `quote.status.${status}`;
  }

  protected badge(status: QuoteStatus): string {
    return QUOTE_BADGE[status];
  }

  /** The decision message, or null while there is none. */
  protected decidedKey(q: PublicQuote): MessageKey | null {
    if (!q.decidedAt || (q.status !== 'APPROVED' && q.status !== 'REJECTED')) return null;
    const by = q.decidedChannel === 'STAFF' ? 'Staff' : 'Customer';
    return `quote.public.decided${by}.${q.status}`;
  }

  /** Why an undecided quote can no longer be decided (superseded, expired). */
  protected closedKey(status: QuoteStatus): MessageKey {
    return status === 'SUPERSEDED' ? 'quote.public.closed.SUPERSEDED' : 'quote.public.closed.EXPIRED';
  }

  /** A time in the workshop's time zone. */
  protected when(instant: string | null, timeZone: string | null): string {
    return instant ? formatInstant(instant, this.i18n.lang(), LONG_DATE_TIME, timeZone) : '—';
  }

  protected invalid(control: FormControl<unknown>): boolean {
    return control.invalid && (control.touched || control.dirty);
  }

  /** Bound in the template as a method so the message follows the control's current errors. */
  protected nameMessageFor(): MessageKey {
    return (
      fieldMessage(this.approve.controls.name, {
        required: 'quote.public.nameRequired',
        maxlength: 'quote.public.nameTooLong',
        plainName: 'quote.public.nameInvalid',
      }) ?? 'quote.public.nameRequired'
    );
  }

  submitApprove(): void {
    this.approve.markAllAsTouched();
    if (this.approve.invalid || this.busy()) return;
    const v = this.approve.getRawValue();
    this.decide(this.api.approve(this.slug(), this.token(), { name: v.name.trim(), accepted: v.accepted }), this.approve);
  }

  submitReject(): void {
    this.reject.markAllAsTouched();
    if (this.reject.invalid || this.busy()) return;
    const reason = this.reject.getRawValue().reason.trim();
    this.decide(this.api.reject(this.slug(), this.token(), { reason: reason || null }), this.reject);
  }

  private decide(call: ReturnType<QuoteApi['approve']>, form: FormGroup): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (q) => {
        this.busy.set(false);
        this.rejecting.set(false);
        this.quote.set(q);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const state = quotePageState(e);
        if (state === 'not-found') {
          this.quote.set(null);
          this.state.set(state);
          return;
        }
        const marked = applyProblem(form, e);
        if (!shownOnFields(e, marked)) this.error.set(this.i18n.error(e));
        // Decided, superseded or expired meanwhile: show what is stored now.
        if (RELOAD_CODES.has(problemCode(e) ?? '')) {
          this.rejecting.set(false);
          this.api.publicQuote(this.slug(), this.token()).subscribe({ next: (q) => this.quote.set(q), error: () => {} });
        }
      },
    });
  }

  private load(slug: string, token: string): void {
    this.quote.set(null);
    this.state.set('ok');
    this.error.set(null);
    this.api.publicQuote(slug, token).subscribe({
      next: (q) => this.quote.set(q),
      error: (e: unknown) => {
        const state = quotePageState(e);
        if (state) this.state.set(state);
        else this.error.set(this.i18n.error(e));
      },
    });
  }
}
