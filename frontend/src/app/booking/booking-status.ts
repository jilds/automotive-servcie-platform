import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { PublicPage } from '../shared/public-page';
import { BookingApi } from './booking-api.service';
import { durationKey, formatInstant, LONG_DATE, STATUS_BADGE, statusText, TIME } from './booking-calendar';
import { PublicBooking } from './booking.models';
import { pageStateOf, PageState } from './public-booking';

/**
 * Public status page of a booking, `/w/{slug}/booking/{token}`: the status, the drop-off time, the services and the
 * total, the reason when declined, and a cancel button (with a confirmation step) while the customer may still cancel.
 */
@Component({
  selector: 'app-booking-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, MoneyPipe, Alert, Icon, PublicPage],
  styles: `
    .ticket {
      border-top: 4px solid var(--bs-primary);
    }
    .when {
      font-size: 1.375rem;
      font-weight: 650;
      font-stretch: 87.5%;
    }
    .price {
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
  `,
  template: `
    <app-public-page [slug]="slug()">
      <div class="card shadow-sm ticket">
        <div class="card-body">
          @if (booking(); as b) {
            <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
              <h1 class="h4 mb-0 me-auto">{{ 'booking.status.title' | t: { number: b.number } }}</h1>
              <span class="badge fs-6 fw-semibold" [class]="badge(b)" aria-live="polite">{{ statusKey(b) | t }}</span>
            </div>
            <p class="text-body-secondary">{{ hintKey(b) | t }}</p>
            @if (b.declineReason) {
              <app-alert tone="info">{{ 'booking.status.declineReason' | t: { reason: b.declineReason } }}</app-alert>
            }
            <p class="small text-body-secondary mb-1">{{ 'booking.summary.when' | t }}</p>
            <p class="when mb-3">{{ when(b.slotStart, b.timeZone) }}</p>

            <h2 class="h6 mb-2">{{ 'booking.status.services' | t }}</h2>
            <ul class="list-group mb-3">
              @for (s of b.services; track $index) {
                <li class="list-group-item d-flex justify-content-between gap-3">
                  <span class="d-flex flex-column">
                    <span class="text-break">{{ s.name }}</span>
                    <span class="small text-body-secondary">{{ duration(s.durationMinutes) }}</span>
                  </span>
                  <span class="price">{{ s.priceWithVatCents | money }}</span>
                </li>
              }
              <li class="list-group-item d-flex justify-content-between gap-3 bg-body-tertiary fw-semibold">
                <span>{{ 'booking.services.total' | t }}</span>
                <span class="price">{{ total() | money }}</span>
              </li>
            </ul>

            @if (error()) {
              <app-alert>{{ error() }}</app-alert>
            }
            @if (notice()) {
              <app-alert tone="success">{{ notice() }}</app-alert>
            }

            @if (b.canCancel) {
              @if (confirming()) {
                <div class="border rounded-3 p-3" role="group" aria-labelledby="bs-cancel-q">
                  <p id="bs-cancel-q" class="fw-semibold mb-3">{{ 'booking.status.cancelConfirm' | t }}</p>
                  <div class="d-flex flex-wrap gap-2">
                    <button type="button" class="btn btn-danger" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="cancel()">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      }
                      {{ 'booking.status.cancelYes' | t }}
                    </button>
                    <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="confirming.set(false)">{{ 'booking.status.cancelNo' | t }}</button>
                  </div>
                </div>
              } @else {
                <button type="button" class="btn btn-outline-secondary" (click)="confirming.set(true)"><app-icon name="x" [size]="16" />{{ 'booking.status.cancel' | t }}</button>
              }
            }
            <p class="mt-4 mb-0"><a [routerLink]="['/w', slug(), 'book']">{{ 'booking.status.newBooking' | t }}</a></p>
          } @else if (state() !== 'ok') {
            <div class="d-flex flex-column align-items-center gap-2 py-4 text-center" role="status">
              <app-icon name="info" [size]="36" class="text-body-tertiary" />
              <h1 class="h4 mb-0">{{ stateTitle() | t }}</h1>
              <p class="text-body-secondary mb-0">{{ stateBody() | t }}</p>
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
export class BookingStatusPage {
  private readonly api = inject(BookingApi);
  private readonly i18n = inject(I18nService);
  protected readonly lang = this.i18n.lang;

  readonly slug = input.required<string>();
  readonly token = input.required<string>();

  protected readonly booking = signal<PublicBooking | null>(null);
  /** `not-found` here also covers an unknown token (`booking-not-found`). */
  protected readonly state = signal<PageState>('ok');
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly confirming = signal(false);
  protected readonly busy = signal(false);

  protected readonly total = computed(() => (this.booking()?.services ?? []).reduce((sum, s) => sum + s.priceWithVatCents, 0));
  protected readonly stateTitle = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'booking.limited.title' : this.state() === 'unavailable' ? 'booking.unavailable.title' : 'booking.status.notFoundTitle',
  );
  protected readonly stateBody = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'booking.limited.body' : this.state() === 'unavailable' ? 'booking.unavailable.body' : 'booking.status.notFound',
  );

  constructor() {
    effect(() => {
      const slug = this.slug();
      const token = this.token();
      untracked(() => this.load(slug, token));
    });
  }

  protected statusKey(b: PublicBooking): MessageKey {
    return statusText(b.status);
  }

  protected hintKey(b: PublicBooking): MessageKey {
    return `booking.statusHint.${b.status}`;
  }

  protected badge(b: PublicBooking): string {
    return STATUS_BADGE[b.status];
  }

  /** The drop-off time in the workshop's time zone. */
  protected when(instant: string, timeZone: string | null): string {
    const lang = this.lang();
    return `${formatInstant(instant, lang, LONG_DATE, timeZone)} · ${formatInstant(instant, lang, TIME, timeZone)}`;
  }

  protected duration(minutes: number): string {
    const d = durationKey(minutes);
    return this.i18n.t(d.key, d.params);
  }

  cancel(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.cancelPublic(this.slug(), this.token()).subscribe({
      next: (b) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.booking.set(b);
        this.notice.set(this.i18n.t('booking.status.cancelled'));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.error.set(this.i18n.error(e));
        // The deadline passed or the status changed meanwhile: show what is stored now.
        this.api.publicBooking(this.slug(), this.token()).subscribe({ next: (b) => this.booking.set(b), error: () => {} });
      },
    });
  }

  private load(slug: string, token: string): void {
    this.booking.set(null);
    this.state.set('ok');
    this.error.set(null);
    this.api.publicBooking(slug, token).subscribe({
      next: (b) => this.booking.set(b),
      error: (e: unknown) => {
        const state = pageStateOf(e) ?? (problemStatus(e) === 404 ? 'not-found' : null);
        if (state) this.state.set(state);
        else this.error.set(this.i18n.error(e));
      },
    });
  }
}

function problemStatus(e: unknown): number | null {
  return (e as { status?: number } | null)?.status ?? null;
}
