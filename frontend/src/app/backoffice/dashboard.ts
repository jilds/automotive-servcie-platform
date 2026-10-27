import { ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of, Subject, switchMap, tap } from 'rxjs';

import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { BookingApi } from '../booking/booking-api.service';
import { ShopApi } from '../shop/shop-api.service';
import { TenantThemeService } from '../shared/theme.service';
import { StockApi } from '../stock/stock-api.service';
import { BackofficeApi } from './backoffice-api.service';
import { Dashboard as DashboardData } from './backoffice.models';
import { monthDate, statusBars, workloadBars } from './dashboard.model';

/** Owner dashboard: cars in the shop, this month's deliveries and revenue, open orders per status and mechanic load. */
@Component({
  selector: 'app-backoffice-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'backoffice.dashboard.title' | t }}</h1>
        <p class="text-body-secondary mb-0">
          @if (monthLabel(); as month) {
            {{ 'backoffice.dashboard.subtitle' | t: { month } }}
          } @else {
            {{ 'backoffice.dashboard.subtitleNoMonth' | t }}
          }
        </p>
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

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <div [attr.aria-busy]="loading()">
      @if (data(); as d) {
        <div class="row g-3 mb-4">
          <div class="col-sm-6 col-xl-4">
            <div class="card shadow-sm h-100">
              <div class="card-body vstack gap-1 py-3">
                <span class="d-flex align-items-center gap-2 small fw-medium text-body-secondary"><app-icon name="car" [size]="16" />{{ 'backoffice.dashboard.carsInShop' | t }}</span>
                <span class="stat-value">{{ d.carsInShop }}</span>
                <span class="small fw-semibold text-body-secondary">{{ 'backoffice.dashboard.carsInShopHint' | t }}</span>
              </div>
            </div>
          </div>
          <div class="col-sm-6 col-xl-4">
            <div class="card shadow-sm h-100">
              <div class="card-body vstack gap-1 py-3">
                <span class="d-flex align-items-center gap-2 small fw-medium text-body-secondary"><app-icon name="check-circle" [size]="16" />{{ 'backoffice.dashboard.delivered' | t }}</span>
                <span class="stat-value">{{ d.deliveredThisMonth }}</span>
                <span class="small fw-semibold text-body-secondary">{{ 'backoffice.dashboard.thisMonth' | t }}</span>
              </div>
            </div>
          </div>
          <div class="col-sm-12 col-xl-4">
            <div class="card shadow-sm h-100">
              <div class="card-body vstack gap-1 py-3">
                <span class="d-flex align-items-center gap-2 small fw-medium text-body-secondary"><app-icon name="briefcase" [size]="16" />{{ 'backoffice.dashboard.revenue' | t }}</span>
                <span class="stat-value text-break">{{ d.revenueThisMonth.grossCents | money }}</span>
                <span class="small fw-semibold text-body-secondary">{{
                  'backoffice.dashboard.revenueSplit' | t: { net: (d.revenueThisMonth.netCents | money), vat: (d.revenueThisMonth.vatCents | money) }
                }}</span>
              </div>
            </div>
          </div>
        </div>

        @if (lowStock(); as low) {
          <a class="card shadow-sm border-warning-subtle text-decoration-none d-block mb-4" routerLink="/stock" [queryParams]="{ low: 1 }">
            <div class="card-body d-flex flex-wrap align-items-center gap-3 py-3">
              <span class="text-warning-emphasis d-inline-flex"><app-icon name="alert-triangle" /></span>
              <span class="d-flex flex-column me-auto">
                <span class="fw-semibold text-body">{{ 'stock.dashboard.lowStock' | t }}: {{ low }}</span>
                <span class="small text-body-secondary">{{ 'stock.dashboard.lowStockHint' | t }}</span>
              </span>
              <span class="small fw-semibold d-inline-flex align-items-center gap-1">{{ 'stock.dashboard.open' | t }}<app-icon name="arrow-right" [size]="14" /></span>
            </div>
          </a>
        }

        @if (pendingBookings(); as pending) {
          <a class="card shadow-sm border-warning-subtle text-decoration-none d-block mb-4" routerLink="/bookings">
            <div class="card-body d-flex flex-wrap align-items-center gap-3 py-3">
              <span class="text-warning-emphasis d-inline-flex"><app-icon name="clock" /></span>
              <span class="d-flex flex-column me-auto">
                <span class="fw-semibold text-body">{{ 'booking.dashboard.pending' | t }}: {{ pending }}</span>
                <span class="small text-body-secondary">{{ 'booking.dashboard.pendingHint' | t }}</span>
              </span>
              <span class="small fw-semibold d-inline-flex align-items-center gap-1">{{ 'booking.dashboard.open' | t }}<app-icon name="arrow-right" [size]="14" /></span>
            </div>
          </a>
        }

        @if (pendingShopOrders(); as pending) {
          <a class="card shadow-sm border-warning-subtle text-decoration-none d-block mb-4" routerLink="/shop-orders">
            <div class="card-body d-flex flex-wrap align-items-center gap-3 py-3">
              <span class="text-warning-emphasis d-inline-flex"><app-icon name="bag" /></span>
              <span class="d-flex flex-column me-auto">
                <span class="fw-semibold text-body">{{ 'shop.dashboard.pending' | t }}: {{ pending }}</span>
                <span class="small text-body-secondary">{{ 'shop.dashboard.pendingHint' | t }}</span>
              </span>
              <span class="small fw-semibold d-inline-flex align-items-center gap-1">{{ 'shop.dashboard.open' | t }}<app-icon name="arrow-right" [size]="14" /></span>
            </div>
          </a>
        }

        <div class="row g-3">
          <div class="col-lg-6">
            <section class="card shadow-sm h-100">
              <div class="card-body">
                <h2 class="card-title mb-3">{{ 'backoffice.dashboard.openByStatus' | t }}</h2>
                @if (d.carsInShop > 0) {
                  <ul class="list-unstyled vstack gap-3 mb-0">
                    @for (b of byStatus(); track b.item) {
                      <li class="d-flex flex-wrap justify-content-between align-items-baseline column-gap-3 row-gap-1" [attr.data-status]="b.item">
                        <span>{{ statusKey(b.item) | t }}</span>
                        <span class="fw-semibold">{{ b.value }}</span>
                        <div class="progress w-100" aria-hidden="true"><div class="progress-bar" [style.width.%]="b.percent"></div></div>
                      </li>
                    }
                  </ul>
                } @else {
                  <div class="d-flex flex-column align-items-center gap-2 py-4 text-center text-body-secondary">
                    <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
                    <p class="fw-semibold text-body mb-0">{{ 'backoffice.dashboard.noOpenOrders' | t }}</p>
                    <a class="btn btn-outline-secondary mt-2" routerLink="/check-in">{{ 'nav.checkIn' | t }}</a>
                  </div>
                }
              </div>
            </section>
          </div>

          <div class="col-lg-6">
            <section class="card shadow-sm h-100">
              <div class="card-body">
                <h2 class="card-title mb-3">{{ 'backoffice.dashboard.workload' | t }}</h2>
                @if (workload().length) {
                  <ul class="list-unstyled vstack gap-3 mb-0">
                    @for (b of workload(); track b.item.mechanicId) {
                      <li class="d-flex flex-wrap justify-content-between align-items-baseline column-gap-3 row-gap-1">
                        <span class="text-break">{{ b.item.name }}</span>
                        <span class="fw-semibold">{{ 'backoffice.dashboard.openOrders' | t: { count: b.value } }}</span>
                        <div class="progress w-100" aria-hidden="true"><div class="progress-bar" [style.width.%]="b.percent"></div></div>
                      </li>
                    }
                  </ul>
                } @else {
                  <div class="d-flex flex-column align-items-center gap-2 py-4 text-center text-body-secondary">
                    <app-icon name="users" [size]="36" class="text-body-tertiary" />
                    <p class="fw-semibold text-body mb-0">{{ 'backoffice.dashboard.noMechanics' | t }}</p>
                    <a class="btn btn-outline-secondary mt-2" routerLink="/backoffice/team">{{ 'backoffice.dashboard.goToTeam' | t }}</a>
                  </div>
                }
              </div>
            </section>
          </div>
        </div>
      } @else if (loading()) {
        <div class="row g-3 mb-4 placeholder-glow" aria-hidden="true">
          <div class="col-sm-6 col-xl-4"><span class="placeholder placeholder-block w-100"></span></div>
          <div class="col-sm-6 col-xl-4"><span class="placeholder placeholder-block w-100"></span></div>
          <div class="col-sm-12 col-xl-4"><span class="placeholder placeholder-block w-100"></span></div>
        </div>
        <div class="row g-3 placeholder-glow" aria-hidden="true">
          <div class="col-lg-6"><span class="placeholder placeholder-block w-100"></span></div>
          <div class="col-lg-6"><span class="placeholder placeholder-block w-100"></span></div>
        </div>
      }
    </div>
  `,
})
export class Dashboard implements OnInit {
  private readonly api = inject(BackofficeApi);
  private readonly i18n = inject(I18nService);
  protected readonly statusKey = statusKey;

  protected readonly data = signal<DashboardData | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly stock = inject(StockApi);
  /** How many active products are below their minimum stock; null until known. */
  protected readonly lowStock = signal<number | null>(null);
  private readonly bookings = inject(BookingApi);
  private readonly theme = inject(TenantThemeService);
  /** Online bookings waiting for the front desk; null when the add-on is off or nothing waits. */
  protected readonly pendingBookings = signal<number | null>(null);
  /** The shell loads the add-ons with the branding; the tile follows when they arrive after this page. */
  private readonly bookingOn = computed(() => !!this.theme.branding()?.addOns?.booking);
  private readonly shop = inject(ShopApi);
  /** Online shop orders still to prepare; null when the add-on is off or nothing waits. */
  protected readonly pendingShopOrders = signal<number | null>(null);
  private readonly shopOn = computed(() => !!this.theme.branding()?.addOns?.shop);
  private readonly refreshes = signal(0);

  protected readonly byStatus = computed(() => statusBars(this.data()?.openByStatus ?? {}));
  protected readonly workload = computed(() => workloadBars(this.data() ?? { mechanicWorkload: [] }));
  protected readonly monthLabel = computed(() => {
    const date = monthDate(this.data()?.month ?? '');
    return date ? new Intl.DateTimeFormat(this.i18n.lang(), { month: 'long', year: 'numeric' }).format(date) : null;
  });

  /** Each request cancels the one before, so a slow older answer can never overwrite a newer one. */
  private readonly reloads = new Subject<void>();

  constructor() {
    effect(() => {
      this.refreshes();
      if (this.bookingOn()) untracked(() => this.loadPendingBookings());
    });
    effect(() => {
      this.refreshes();
      if (this.shopOn()) untracked(() => this.loadPendingShopOrders());
      else this.pendingShopOrders.set(null);
    });
    this.reloads
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap(() =>
          this.api.dashboard().pipe(
            catchError((e: unknown) => {
              this.error.set(this.i18n.error(e));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((d) => {
        if (d) this.data.set(d);
        this.loading.set(false);
      });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.reloads.next();
    // Low-stock tile: hidden when nothing is low or the list cannot be read.
    this.stock.lowStock().subscribe({ next: (list) => this.lowStock.set(list.length), error: () => this.lowStock.set(null) });
    this.refreshes.update((n) => n + 1);
  }

  /** Shop tile: only with the shop add-on; hidden when nothing waits or the count cannot be read. */
  private loadPendingShopOrders(): void {
    this.shop.pendingCount().subscribe({ next: (r) => this.pendingShopOrders.set(r.count || null), error: () => this.pendingShopOrders.set(null) });
  }

  /** Bookings tile: only with the booking add-on; hidden when nothing waits or the count cannot be read. */
  private loadPendingBookings(): void {
    this.bookings.pendingCount().subscribe({ next: (r) => this.pendingBookings.set(r.count || null), error: () => this.pendingBookings.set(null) });
  }
}
