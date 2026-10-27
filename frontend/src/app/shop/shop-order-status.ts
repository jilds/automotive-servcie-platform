import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { problemOf } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { PublicPage } from '../shared/public-page';
import { formatInstant, isOpen, LONG_DATE_TIME, PageState, pageStateOf, STATUS_BADGE, statusText } from './shop-forms';
import { ShopApi } from './shop-api.service';
import { PublicShopOrder } from './shop.models';

/**
 * Public status page of an order, `/w/{slug}/shop/{token}`: the status, the products and the total, the hold time
 * in the workshop's time zone, the pickup note, and a cancel button (with a confirmation step) while it may be
 * cancelled.
 */
@Component({
  selector: 'app-shop-order-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, MoneyPipe, Alert, Icon, PublicPage],
  styles: `
    .ticket {
      border-top: 4px solid var(--bs-primary);
    }
    .when {
      font-size: 1.125rem;
      font-weight: 650;
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
          @if (order(); as o) {
            <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
              <h1 class="h4 mb-0 me-auto">{{ 'shop.order.title' | t: { number: o.number } }}</h1>
              <span class="badge fs-6 fw-semibold" [class]="badge(o)" aria-live="polite">{{ statusKey(o) | t }}</span>
            </div>
            <p class="text-body-secondary">{{ hintKey(o) | t }}</p>

            <dl class="row mb-2">
              <dt class="col-sm-4 small text-body-secondary fw-normal">{{ 'shop.order.placedAt' | t }}</dt>
              <dd class="col-sm-8 cap">{{ when(o.placedAt, o.timeZone) }}</dd>
              @if (open(o)) {
                <dt class="col-sm-4 small text-body-secondary fw-normal">{{ 'shop.order.holdUntil' | t }}</dt>
                <dd class="col-sm-8 when cap">{{ when(o.holdUntil, o.timeZone) }}</dd>
              }
            </dl>
            @if (o.pickupNote && open(o)) {
              <app-alert tone="info"><strong>{{ 'shop.order.pickup' | t }}:</strong> {{ o.pickupNote }}</app-alert>
            }

            <h2 class="h6 mb-2">{{ 'shop.order.lines' | t }}</h2>
            <ul class="list-group mb-3">
              @for (l of o.lines; track $index) {
                <li class="list-group-item d-flex justify-content-between gap-3">
                  <span class="d-flex flex-column overflow-hidden">
                    <span class="text-break">{{ l.name }}</span>
                    <span class="small text-body-secondary">{{ 'shop.order.lineDetail' | t: { quantity: l.quantity, unit: l.unit, price: (l.unitPriceWithVatCents | money) } }}</span>
                  </span>
                  <span class="price">{{ l.lineTotalCents | money }}</span>
                </li>
              }
              <li class="list-group-item d-flex justify-content-between gap-3 bg-body-tertiary fw-semibold">
                <span>{{ 'shop.order.total' | t }}</span>
                <span class="price">{{ o.totalCents | money }}</span>
              </li>
            </ul>

            @if (error()) {
              <app-alert>{{ error() }}</app-alert>
            }
            @if (notice()) {
              <app-alert tone="success">{{ notice() }}</app-alert>
            }

            @if (o.canCancel) {
              @if (confirming()) {
                <div class="border rounded-3 p-3" role="group" aria-labelledby="so-cancel-q">
                  <p id="so-cancel-q" class="fw-semibold mb-3">{{ 'shop.order.cancelConfirm' | t }}</p>
                  <div class="d-flex flex-wrap gap-2">
                    <button type="button" class="btn btn-danger" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="cancel()">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      }
                      {{ 'shop.order.cancelYes' | t }}
                    </button>
                    <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="confirming.set(false)">{{ 'shop.order.cancelNo' | t }}</button>
                  </div>
                </div>
              } @else {
                <button type="button" class="btn btn-outline-secondary" (click)="confirming.set(true)"><app-icon name="x" [size]="16" />{{ 'shop.order.cancel' | t }}</button>
              }
            }
            <p class="mt-4 mb-0"><a [routerLink]="['/w', slug(), 'shop']">{{ 'shop.order.backToShop' | t }}</a></p>
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
export class ShopOrderStatusPage {
  private readonly api = inject(ShopApi);
  private readonly i18n = inject(I18nService);

  readonly slug = input.required<string>();
  readonly token = input.required<string>();

  protected readonly order = signal<PublicShopOrder | null>(null);
  /** `not-found` here also covers an unknown token (`shop-order-not-found`). */
  protected readonly state = signal<PageState>('ok');
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly confirming = signal(false);
  protected readonly busy = signal(false);

  protected readonly stateTitle = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'shop.limited.title' : this.state() === 'unavailable' ? 'shop.unavailable.title' : 'shop.order.notFoundTitle',
  );
  protected readonly stateBody = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'shop.limited.body' : this.state() === 'unavailable' ? 'shop.unavailable.body' : 'shop.order.notFound',
  );

  constructor() {
    effect(() => {
      const slug = this.slug();
      const token = this.token();
      untracked(() => this.load(slug, token));
    });
  }

  protected statusKey(o: PublicShopOrder): MessageKey {
    return statusText(o.status);
  }

  protected hintKey(o: PublicShopOrder): MessageKey {
    return `shop.statusHint.${o.status}`;
  }

  protected badge(o: PublicShopOrder): string {
    return STATUS_BADGE[o.status];
  }

  protected open(o: PublicShopOrder): boolean {
    return isOpen(o.status);
  }

  /** A time in the workshop's time zone. */
  protected when(instant: string, timeZone: string | null): string {
    return formatInstant(instant, this.i18n.lang(), LONG_DATE_TIME, timeZone);
  }


  cancel(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.cancelPublic(this.slug(), this.token()).subscribe({
      next: (o) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.order.set(o);
        this.notice.set(this.i18n.t('shop.order.cancelled'));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.error.set(this.i18n.error(e));
        // Readied or closed meanwhile: show what is stored now.
        this.api.publicOrder(this.slug(), this.token()).subscribe({ next: (o) => this.order.set(o), error: () => {} });
      },
    });
  }

  private load(slug: string, token: string): void {
    this.order.set(null);
    this.state.set('ok');
    this.error.set(null);
    this.api.publicOrder(slug, token).subscribe({
      next: (o) => this.order.set(o),
      error: (e: unknown) => {
        const state = pageStateOf(e) ?? (problemOf(e)?.status === 404 ? 'not-found' : null);
        if (state) this.state.set(state);
        else this.error.set(this.i18n.error(e));
      },
    });
  }
}
