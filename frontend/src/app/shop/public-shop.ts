import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject, Subscription } from 'rxjs';

import { applyProblem, fieldMessage, problemCode } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { PublicPage } from '../shared/public-page';
import { addToCart, CartLimit, CartLine, cartTotalCents, cartUnits, pruneCart, readCart, removeFromCart, setQuantity, writeCart } from './cart';
import { checkoutForm, checkoutOutcome, LineProblem, LONG_DATE_TIME, formatInstant, PageState, pageStateOf, statusLink, toOrderRequest } from './shop-forms';
import { ShopApi } from './shop-api.service';
import { Availability, MAX_QUANTITY, OrderPlaced, PublicProduct, SEARCH_MAX } from './shop.models';

export type ShopStep = 'shop' | 'checkout' | 'done';

/** The server lists at most this many products; a full page may leave offered products out, so it never prunes. */
const LIST_LIMIT = 200;
const SEARCH_DEBOUNCE_MS = 300;

const AVAILABILITY_BADGE: Record<Availability, string> = {
  IN_STOCK: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  LAST_UNITS: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  OUT: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

const LIMIT_TEXT: Record<CartLimit, MessageKey> = {
  'max-lines': 'shop.cart.maxLines',
  'max-quantity': 'shop.cart.maxQuantity',
  'out-of-stock': 'shop.cart.outOfStock',
};

const LINE_TEXT: Record<LineProblem, MessageKey> = {
  'product-not-listed': 'shop.cart.lineNotListed',
  'insufficient-stock': 'shop.cart.lineInsufficient',
};

/**
 * Public shop of a workshop, `/w/{slug}/shop` (add-on `shop`): stock products listed online with their availability,
 * a cart kept in this browser, the checkout (contact, consents) and a receipt with the order's status link. No
 * sign-in and no online payment: the customer pays at the counter on collection.
 */
@Component({
  selector: 'app-public-shop',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, MoneyPipe, Alert, Icon, PublicPage],
  styles: `
    .product-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
      gap: 10px;
    }
    .product {
      display: flex;
      flex-direction: column;
      gap: 6px;
      height: 100%;
      min-width: 0;
      padding: 14px;
      border: 1px solid var(--bs-border-color);
      border-radius: var(--bs-border-radius-lg);
      background: var(--asp-surface);
    }
    .product.is-out {
      opacity: 0.75;
    }
    .price {
      font-weight: 650;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .qty {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .qty input {
      width: 3.5rem;
      text-align: center;
    }
    .cart-line {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 6px 12px;
      padding: 12px 0;
      border-top: 1px solid var(--bs-border-color);
    }
    .cart-line.is-problem {
      border-left: 3px solid var(--bs-danger);
      padding-left: 10px;
    }
    @media (min-width: 992px) {
      .cart {
        position: sticky;
        top: 16px;
      }
    }
    .hp {
      position: absolute;
      left: -10000px;
      width: 1px;
      height: 1px;
      overflow: hidden;
    }
    .ticket {
      border-top: 4px solid var(--bs-primary);
    }
    .ticket-number {
      font-size: 2.25rem;
      font-weight: 650;
      font-stretch: 75%;
      line-height: 1;
    }
  `,
  template: `
    <app-public-page [slug]="slug()">
      @switch (state()) {
        @case ('ok') {
          @if (step() === 'done' && placed(); as p) {
            <div class="card shadow-sm ticket">
              <div class="card-body">
                <h1 #heading class="mb-3" tabindex="-1">{{ 'shop.done.title' | t }}</h1>
                <p class="mb-1 text-body-secondary small">{{ 'shop.done.numberLabel' | t }}</p>
                <p class="ticket-number mb-3">{{ p.number }}</p>
                <p class="d-flex flex-wrap justify-content-between gap-2 fw-semibold mb-2">
                  <span>{{ 'shop.cart.total' | t }}</span><span class="price">{{ p.totalCents | money }}</span>
                </p>
                <p class="mb-1">{{ 'shop.done.body' | t }}</p>
                <p class="text-body-secondary">{{ 'shop.done.holdUntil' | t: { date: when(p.holdUntil, p.timeZone) } }}</p>
                <label class="form-label fw-semibold" for="sh-link">{{ 'shop.link.label' | t }}</label>
                <div class="input-group mb-2">
                  <input id="sh-link" class="form-control small" type="text" readonly [value]="link()" (focus)="$any($event.target).select()" />
                  <button type="button" class="btn btn-primary" (click)="copy()">{{ (copied() ? 'shop.link.copied' : 'shop.link.copy') | t }}</button>
                </div>
                <p class="form-text mb-3">{{ 'shop.link.hint' | t }}</p>
                <div class="d-flex flex-wrap gap-2">
                  <a class="btn btn-outline-secondary" [routerLink]="['/w', slug(), 'shop', p.statusToken]">{{ 'shop.link.open' | t }}</a>
                  <button type="button" class="btn btn-link" (click)="restart()">{{ 'shop.done.backToShop' | t }}</button>
                </div>
              </div>
            </div>
          } @else {
            <div class="card shadow-sm mb-3">
              <div class="card-body">
                <h1 class="mb-1">{{ 'shop.public.title' | t }}</h1>
                <p class="text-body-secondary mb-0">{{ 'shop.public.intro' | t }}</p>
              </div>
            </div>
            <div aria-live="polite">
              @if (notice()) {
                <app-alert tone="warning">{{ notice() }}</app-alert>
              }
              @if (error()) {
                <app-alert>{{ error() }}</app-alert>
              }
            </div>

            <div class="row g-3">
              <div class="col-lg-7">
                @if (step() === 'shop') {
                  <section class="card shadow-sm" aria-labelledby="sh-products-title">
                    <div class="card-body">
                      <h2 #heading id="sh-products-title" class="h5 mb-3" tabindex="-1">{{ 'shop.products.title' | t }}</h2>
                      <div class="input-group mb-3">
                        <span class="input-group-text"><app-icon name="search" [size]="16" /></span>
                        <input
                          id="sh-search"
                          class="form-control"
                          type="search"
                          [attr.maxlength]="searchMax"
                          [value]="query()"
                          [attr.aria-label]="'shop.search.label' | t"
                          [placeholder]="'shop.search.placeholder' | t"
                          (input)="search($any($event.target).value)"
                        />
                      </div>
                      @if (products(); as list) {
                        @if (list.length) {
                          <ul class="product-grid list-unstyled mb-0" [attr.aria-busy]="searching()">
                            @for (p of list; track p.id) {
                              <li class="product" [class.is-out]="p.availability === 'OUT'">
                                <span class="d-flex justify-content-between align-items-start gap-2">
                                  <span class="fw-semibold text-break">{{ p.name }}</span>
                                  <span class="badge flex-shrink-0" [class]="availabilityBadge(p.availability)">{{ availabilityKey(p.availability) | t }}</span>
                                </span>
                                <span class="small text-body-secondary text-break">
                                  @if (p.brand) {
                                    {{ p.brand }} ·
                                  }
                                  {{ 'shop.product.ref' | t: { sku: p.sku } }}
                                </span>
                                <span class="d-flex flex-wrap align-items-end justify-content-between gap-2 mt-auto pt-2">
                                  <span>
                                    <span class="price d-block">{{ p.unitPriceWithVatCents | money }}</span>
                                    <span class="small text-body-secondary">{{ 'shop.product.perUnit' | t: { unit: p.unit } }}</span>
                                  </span>
                                  <button
                                    type="button"
                                    class="btn btn-sm btn-primary"
                                    [disabled]="p.availability === 'OUT'"
                                    [attr.aria-label]="'shop.product.addLabel' | t: { name: p.name }"
                                    (click)="add(p)"
                                  >
                                    <app-icon name="plus" [size]="14" />{{ 'shop.product.add' | t }}
                                  </button>
                                </span>
                                @if (inCart(p.id); as n) {
                                  <span class="small text-success-emphasis">{{ 'shop.product.inCart' | t: { count: n } }}</span>
                                }
                              </li>
                            }
                          </ul>
                        } @else {
                          <div class="d-flex flex-column align-items-center gap-2 py-4 text-center text-body-secondary">
                            <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
                            <p class="fw-semibold text-body mb-0">
                              @if (query().trim()) {
                                {{ 'shop.products.noMatch' | t: { q: query().trim() } }}
                              } @else {
                                {{ 'shop.products.empty' | t }}
                              }
                            </p>
                          </div>
                        }
                      } @else {
                        <div class="product-grid placeholder-glow" aria-hidden="true">
                          <span class="placeholder placeholder-block w-100"></span>
                          <span class="placeholder placeholder-block w-100"></span>
                        </div>
                      }
                    </div>
                  </section>
                } @else {
                  <section class="card shadow-sm" aria-labelledby="sh-checkout-title">
                    <div class="card-body">
                      <h2 #heading id="sh-checkout-title" class="h5 mb-3" tabindex="-1">{{ 'shop.checkout.title' | t }}</h2>
                      <form id="sh-checkout" [formGroup]="form" (ngSubmit)="submit()" novalidate>
                        <div class="row g-3">
                          <div class="col-12">
                            <label class="form-label" for="sh-name">{{ 'common.name' | t }}</label>
                            <input id="sh-name" class="form-control" formControlName="name" autocomplete="name" [attr.maxlength]="120" [class.is-invalid]="!!message('name')" [attr.aria-invalid]="!!message('name')" aria-describedby="sh-name-help" />
                            @if (message('name'); as m) {
                              <div class="invalid-feedback" id="sh-name-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                            }
                          </div>
                          <div class="col-sm-6">
                            <label class="form-label" for="sh-phone">{{ 'common.phone' | t }}</label>
                            <input id="sh-phone" class="form-control" formControlName="phone" type="tel" autocomplete="tel" [class.is-invalid]="contactMissing() || !!message('phone')" [attr.aria-invalid]="contactMissing() || !!message('phone')" aria-describedby="sh-contact-help" />
                            @if (message('phone'); as m) {
                              <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                            }
                          </div>
                          <div class="col-sm-6">
                            <label class="form-label" for="sh-email">{{ 'common.email' | t }}</label>
                            <input id="sh-email" class="form-control" formControlName="email" type="email" autocomplete="email" [class.is-invalid]="contactMissing() || !!message('email')" [attr.aria-invalid]="contactMissing() || !!message('email')" aria-describedby="sh-contact-help" />
                            @if (message('email'); as m) {
                              <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                            }
                          </div>
                          <div class="col-12 mt-1">
                            <div [class]="contactMissing() ? 'invalid-feedback d-block mt-0' : 'form-text mt-0'" id="sh-contact-help">
                              @if (contactMissing()) {
                                <app-icon name="alert-circle" [size]="14" />
                              }
                              {{ 'shop.checkout.phoneOrEmail' | t }}
                            </div>
                          </div>
                          <div class="col-sm-6">
                            <label class="form-label" for="sh-nif">{{ 'shop.checkout.nif' | t }}</label>
                            <input id="sh-nif" class="form-control" formControlName="nif" inputmode="numeric" autocomplete="off" [class.is-invalid]="!!message('nif')" [attr.aria-invalid]="!!message('nif')" aria-describedby="sh-nif-help" />
                            @if (message('nif'); as m) {
                              <div class="invalid-feedback" id="sh-nif-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                            } @else {
                              <div class="form-text" id="sh-nif-help">{{ 'shop.checkout.nifHint' | t }}</div>
                            }
                          </div>
                          <div class="hp" aria-hidden="true">
                            <!-- Hidden trap for bots (sent as "website"): a name no browser autofills, never focused by keyboard. -->
                            <label for="sh-hp-x9">{{ 'shop.checkout.website' | t }}</label>
                            <input id="sh-hp-x9" name="sh-hp-x9" type="text" formControlName="website" tabindex="-1" autocomplete="new-password" />
                          </div>
                          <div class="col-12">
                            <div class="form-check">
                              <input id="sh-privacy" class="form-check-input" type="checkbox" formControlName="privacyAccepted" [class.is-invalid]="!!message('privacyAccepted')" [attr.aria-invalid]="!!message('privacyAccepted')" aria-describedby="sh-privacy-help" />
                              <label class="form-check-label" for="sh-privacy">{{ 'shop.checkout.privacy' | t }}</label>
                              @if (message('privacyAccepted'); as m) {
                                <div class="invalid-feedback" id="sh-privacy-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                              }
                            </div>
                            <button type="button" class="btn btn-link btn-sm px-0" aria-controls="sh-privacy-notice" [attr.aria-expanded]="privacyOpen()" (click)="privacyOpen.set(!privacyOpen())">
                              {{ 'shop.checkout.privacyShow' | t }}
                            </button>
                            @if (platformPrivacyUrl(); as url) {
                              <a class="btn btn-link btn-sm px-0 ms-3" [href]="url" target="_blank" rel="noopener noreferrer">{{ 'shop.checkout.platformPrivacy' | t }}</a>
                            }
                            @if (privacyOpen()) {
                              <p id="sh-privacy-notice" class="small text-body-secondary bg-body-tertiary rounded p-3 mb-0">{{ 'shop.checkout.privacyNotice' | t: { workshop: workshopName() } }}</p>
                            }
                          </div>
                          <div class="col-12">
                            <div class="form-check">
                              <input id="sh-marketing" class="form-check-input" type="checkbox" formControlName="marketingConsent" />
                              <label class="form-check-label" for="sh-marketing">{{ 'shop.checkout.marketing' | t }}</label>
                            </div>
                          </div>
                          <div class="col-12">
                            <app-alert tone="info">{{ 'shop.checkout.payAtCounter' | t }}</app-alert>
                          </div>
                        </div>
                      </form>
                    </div>
                  </section>
                }
              </div>

              <div class="col-lg-5">
                <section id="sh-cart" class="card shadow-sm cart" aria-labelledby="sh-cart-title">
                  <div class="card-body">
                    <h2 id="sh-cart-title" class="h5 d-flex align-items-center gap-2 mb-1">
                      {{ (step() === 'checkout' ? 'shop.checkout.summary' : 'shop.cart.title') | t }}
                      @if (units()) {
                        <span class="badge bg-primary-subtle text-primary-emphasis">{{ 'shop.cart.items' | t: { count: units() } }}</span>
                      }
                    </h2>
                    @for (line of cartView(); track line.productId) {
                      <div class="cart-line" [class.is-problem]="!!line.problem" [attr.data-product]="line.productId">
                        <span class="d-flex flex-column overflow-hidden">
                          <span class="fw-semibold text-break">{{ line.name ?? ('shop.cart.loadingProduct' | t) }}</span>
                          @if (line.product; as p) {
                            <span class="small text-body-secondary">{{ p.unitPriceWithVatCents | money }} · {{ 'shop.product.perUnit' | t: { unit: p.unit } }}</span>
                          }
                        </span>
                        <span class="price text-end">{{ line.totalCents | money }}</span>
                        @if (step() === 'shop') {
                          <span class="qty">
                            <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="line.quantity <= 1" [attr.aria-label]="'shop.cart.decrease' | t: { name: line.name ?? '' }" (click)="changeQuantity(line.productId, line.quantity - 1)">−</button>
                            <input class="form-control form-control-sm" type="number" inputmode="numeric" min="1" [attr.max]="maxQuantity" step="1" [value]="line.quantity" [attr.aria-label]="'shop.cart.quantity' | t: { name: line.name ?? '' }" (change)="changeQuantity(line.productId, $any($event.target).valueAsNumber, $any($event.target))" />
                            <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="line.quantity >= maxQuantity" [attr.aria-label]="'shop.cart.increase' | t: { name: line.name ?? '' }" (click)="changeQuantity(line.productId, line.quantity + 1)">+</button>
                          </span>
                          <span class="text-end">
                            <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'shop.cart.remove' | t: { name: line.name ?? '' }" (click)="remove(line.productId)"><app-icon name="trash" [size]="18" /></button>
                          </span>
                        } @else {
                          <span class="small text-body-secondary">× {{ line.quantity }}</span>
                        }
                        @if (line.problem; as m) {
                          <span class="small text-danger-emphasis" style="grid-column: 1 / -1" role="alert"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</span>
                        }
                      </div>
                    } @empty {
                      <p class="text-body-secondary mb-0 pt-2">{{ 'shop.cart.empty' | t }}</p>
                    }
                    @if (cart().length) {
                      <div class="d-flex flex-wrap justify-content-between align-items-baseline gap-2 border-top pt-3">
                        <span class="small text-body-secondary">{{ 'shop.cart.total' | t }}</span>
                        <span class="price fs-5">{{ totalCents() | money }}</span>
                      </div>
                    }
                    <div class="d-flex flex-wrap justify-content-between gap-2 mt-3">
                      @if (step() === 'checkout') {
                        <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="back()"><app-icon name="chevron-left" [size]="16" />{{ 'shop.checkout.back' | t }}</button>
                        <button type="submit" form="sh-checkout" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
                          @if (busy()) {
                            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                          }
                          {{ 'shop.checkout.submit' | t }}
                        </button>
                      } @else if (cart().length) {
                        <span></span>
                        <button type="button" class="btn btn-primary" (click)="checkout()">{{ 'shop.cart.toCheckout' | t }}<app-icon name="arrow-right" [size]="16" /></button>
                      }
                    </div>
                  </div>
                </section>
              </div>
            </div>
          }
        }
        @default {
          <div class="card shadow-sm">
            <div class="card-body d-flex flex-column align-items-center gap-2 py-4 text-center" role="status">
              <app-icon [name]="state() === 'limited' ? 'clock' : 'info'" [size]="36" class="text-body-tertiary" />
              <h1 #heading class="h4 mb-0" tabindex="-1">{{ stateTitle() | t }}</h1>
              <p class="text-body-secondary mb-0">{{ stateBody() | t }}</p>
              @if (state() === 'limited') {
                <button type="button" class="btn btn-outline-secondary mt-2" (click)="retry()"><app-icon name="refresh" [size]="16" />{{ 'shop.retry' | t }}</button>
              }
            </div>
          </div>
        }
      }
    </app-public-page>
  `,
})
export class PublicShop {
  private readonly api = inject(ShopApi);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly page = viewChild(PublicPage);

  /** The workshop, from the route `/w/:slug/shop`. */
  readonly slug = input.required<string>();

  protected readonly searchMax = SEARCH_MAX;
  protected readonly maxQuantity = MAX_QUANTITY;
  protected readonly state = signal<PageState>('ok');
  protected readonly step = signal<ShopStep>('shop');
  protected readonly query = signal('');
  /** The list shown (the search result); null while the first list loads. */
  protected readonly products = signal<PublicProduct[] | null>(null);
  protected readonly searching = signal(false);
  /** Every product seen on this page, so cart lines keep their name and price while a search narrows the list. */
  private readonly known = signal<ReadonlyMap<string, PublicProduct>>(new Map());
  readonly cart = signal<CartLine[]>([]);
  /** Lines the server refused at checkout, by product id. */
  protected readonly lineProblems = signal<ReadonlyMap<string, LineProblem>>(new Map());
  protected readonly placed = signal<OrderPlaced | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly privacyOpen = signal(false);
  protected readonly copied = signal(false);
  /** The workshop's country when the public branding says it (it does not today); the NIF check then runs here. */
  private readonly country = computed(() => (this.page()?.branding() as { country?: string | null } | null)?.country ?? null);
  /** Mirrors the form's status so the OnPush view follows touched / invalid. */
  private readonly formTick = signal(0);
  protected readonly form = checkoutForm(() => this.country());

  protected readonly units = computed(() => cartUnits(this.cart()));
  protected readonly totalCents = computed(() => cartTotalCents(this.cart(), this.known()));
  protected readonly cartView = computed(() => {
    const known = this.known();
    const problems = this.lineProblems();
    return this.cart().map((l) => {
      const product = known.get(l.productId) ?? null;
      const refused = problems.get(l.productId);
      const problem: MessageKey | null = refused ? LINE_TEXT[refused] : product?.availability === 'OUT' ? 'shop.cart.lineOut' : null;
      return { ...l, product, name: product?.name ?? null, totalCents: l.quantity * (product?.unitPriceWithVatCents ?? 0), problem };
    });
  });
  protected readonly link = computed(() => {
    const p = this.placed();
    return p ? statusLink(this.document.location?.origin ?? '', this.slug(), p.statusToken) : '';
  });
  protected readonly stateTitle = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'shop.limited.title' : this.state() === 'unavailable' ? 'shop.unavailable.title' : 'shop.notFound.title',
  );
  protected readonly stateBody = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'shop.limited.body' : this.state() === 'unavailable' ? 'shop.unavailable.body' : 'shop.notFound.body',
  );

  private readonly searches = new Subject<string>();
  private request: Subscription | null = null;

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => {
        this.cart.set(readCart(slug));
        this.query.set('');
        this.load(slug, '');
      });
    });
    this.searches
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.load(this.slug(), q));
    this.form.events.pipe(takeUntilDestroyed()).subscribe(() => this.formTick.update((n) => n + 1));
  }

  protected workshopName(): string {
    return this.page()?.branding()?.name ?? '';
  }

  protected platformPrivacyUrl(): string | null {
    return this.page()?.branding()?.privacyUrl ?? null;
  }

  protected availabilityKey(a: Availability): MessageKey {
    return `shop.availability.${a}`;
  }

  protected availabilityBadge(a: Availability): string {
    return AVAILABILITY_BADGE[a];
  }

  protected inCart(id: string): number {
    return this.cart().find((l) => l.productId === id)?.quantity ?? 0;
  }

  /** The hold time of the receipt, in the workshop's time zone. */
  protected when(instant: string, timeZone: string): string {
    return formatInstant(instant, this.i18n.lang(), LONG_DATE_TIME, timeZone);
  }

  search(q: string): void {
    const query = (q ?? '').slice(0, SEARCH_MAX);
    this.query.set(query);
    this.searches.next(query.trim());
  }

  add(p: PublicProduct): void {
    this.applyChange(addToCart(this.cart(), p));
    this.clearProblem(p.id);
  }

  changeQuantity(productId: string, quantity: number, input?: HTMLInputElement): void {
    const change = setQuantity(this.cart(), productId, quantity);
    this.applyChange(change);
    this.clearProblem(productId);
    // An empty or capped input shows the stored quantity again.
    if (input) input.value = String(this.inCart(productId));
  }

  remove(productId: string): void {
    this.store(removeFromCart(this.cart(), productId));
    this.clearProblem(productId);
    this.notice.set(null);
  }

  checkout(): void {
    this.error.set(null);
    if (!this.cart().length) return;
    if (this.cartView().some((l) => l.problem)) {
      this.error.set(this.i18n.t('shop.cart.fix'));
      return;
    }
    this.go('checkout');
  }

  protected back(): void {
    this.error.set(null);
    this.go('shop');
  }

  protected message(field: 'name' | 'phone' | 'email' | 'nif' | 'privacyAccepted'): MessageKey | null {
    this.formTick();
    const required: MessageKey = field === 'name' ? 'shop.checkout.nameRequired' : 'shop.checkout.privacyRequired';
    return fieldMessage(this.form.controls[field], {
      required,
      plainName: 'shop.checkout.namePlain',
      pattern: 'shop.checkout.phoneInvalid',
      email: 'customers.emailInvalid',
      nif: 'shop.checkout.nifInvalid',
      maxlength: 'shop.checkout.tooLong',
    });
  }

  protected contactMissing(): boolean {
    this.formTick();
    const f = this.form;
    return f.hasError('phoneOrEmail') && (f.controls.phone.touched || f.controls.email.touched);
  }

  submit(): void {
    if (!this.cart().length) {
      this.go('shop');
      return;
    }
    if (this.cartView().some((l) => l.problem)) {
      this.error.set(this.i18n.t('shop.cart.fix'));
      this.go('shop');
      return;
    }
    this.form.markAllAsTouched();
    this.formTick.update((n) => n + 1);
    if (this.form.invalid) {
      this.error.set(this.i18n.t('error.validation'));
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.placeOrder(this.slug(), toOrderRequest(this.cart(), this.form, this.i18n.lang())).subscribe({
      next: (placed) => {
        this.busy.set(false);
        this.placed.set(placed);
        this.store([]);
        this.lineProblems.set(new Map());
        this.go('done');
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.onOrderFailed(e);
      },
    });
  }

  protected retry(): void {
    this.state.set('ok');
    this.load(this.slug(), this.query().trim());
  }

  /** After the receipt: an empty cart and a fresh product list. */
  protected restart(): void {
    this.placed.set(null);
    this.copied.set(false);
    this.form.reset();
    this.query.set('');
    this.go('shop');
    this.load(this.slug(), '');
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.link());
      this.copied.set(true);
    } catch {
      // No clipboard access: select the text so it can be copied by hand.
      (this.document.getElementById('sh-link') as HTMLInputElement | null)?.select();
    }
  }

  private onOrderFailed(e: unknown): void {
    const outcome = checkoutOutcome(e);
    switch (outcome.kind) {
      case 'line': {
        // Mark the line, show the cart, and read the products again (prices, availability, what is still offered).
        if (outcome.productId) {
          const id = outcome.productId;
          this.lineProblems.update((m) => new Map(m).set(id, outcome.problem));
        }
        this.error.set(this.i18n.t(outcome.productId ? 'shop.cart.fix' : outcome.problem === 'insufficient-stock' ? 'error.insufficient-stock' : 'error.product-not-listed'));
        this.query.set('');
        this.go('shop');
        this.load(this.slug(), '');
        return;
      }
      case 'page':
        this.state.set(outcome.state);
        this.focusHeading();
        return;
      case 'fields': {
        const marked = applyProblem(this.form, e, { 'privacy-consent-required': 'privacyAccepted', 'invalid-nif': 'nif' });
        this.formTick.update((n) => n + 1);
        if (outcome.cart) this.error.set(this.i18n.t('shop.cart.invalid'));
        else this.error.set(marked && problemCode(e) !== 'validation' ? null : this.i18n.error(e));
        return;
      }
      case 'message':
        this.error.set(
          outcome.code === 'too-many-requests' ? `${this.i18n.t('shop.limited.title')}. ${this.i18n.t('shop.limited.body')}` : this.i18n.error(e),
        );
    }
  }

  private applyChange(change: { cart: CartLine[]; limit: CartLimit | null }): void {
    this.store(change.cart);
    this.notice.set(change.limit ? this.i18n.t(LIMIT_TEXT[change.limit]) : null);
  }

  /** Keeps the cart in this browser; when storage fails it still works for this visit. */
  private store(cart: CartLine[]): void {
    this.cart.set(cart);
    writeCart(this.slug(), cart);
  }

  private clearProblem(productId: string): void {
    if (!this.lineProblems().has(productId)) return;
    this.lineProblems.update((m) => {
      const next = new Map(m);
      next.delete(productId);
      return next;
    });
    if (!this.lineProblems().size && this.error() === this.i18n.t('shop.cart.fix')) this.error.set(null);
  }

  private load(slug: string, q: string): void {
    this.request?.unsubscribe();
    this.searching.set(true);
    this.request = this.api.products(slug, q).subscribe({
      next: (list) => {
        this.searching.set(false);
        this.products.set(list);
        this.known.update((m) => {
          const next = new Map(m);
          for (const p of list) next.set(p.id, p);
          return next;
        });
        if (!q && list.length < LIST_LIMIT) this.prune(list);
      },
      error: (e: unknown) => {
        this.searching.set(false);
        const state = pageStateOf(e);
        if (state) {
          this.state.set(state);
          this.focusHeading();
        } else {
          this.error.set(this.i18n.error(e));
          if (!this.products()) this.products.set([]);
        }
      },
    });
  }

  /** Drops cart lines no longer offered, after reading the full list, and says so. */
  private prune(offered: PublicProduct[]): void {
    const { cart, removed } = pruneCart(this.cart(), offered);
    if (!removed.length) return;
    this.store(cart);
    this.lineProblems.update((m) => {
      const next = new Map(m);
      for (const r of removed) next.delete(r.productId);
      return next;
    });
    this.notice.set(this.i18n.t('shop.cart.removed', { count: removed.length }));
    if (!this.lineProblems().size && this.error() === this.i18n.t('shop.cart.fix')) this.error.set(null);
  }

  private go(step: ShopStep): void {
    this.step.set(step);
    this.focusHeading();
  }

  /** Moves focus to the new step's heading, so keyboard and screen reader users start there. */
  private focusHeading(): void {
    setTimeout(() => this.heading()?.nativeElement.focus());
  }
}
