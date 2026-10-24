import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { BackofficeApi } from '../backoffice/backoffice-api.service';
import { Toasts } from '../backoffice/toasts';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { CatalogueApi } from './catalogue-api.service';
import { StockApi } from '../stock/stock-api.service';
import { CatalogueService, CataloguePackage, PackageProductChoice } from './catalogue.models';
import { groupByCategory } from './catalogue-groups';
import { PackageDialog } from './package-dialog';
import { ServiceDialog } from './service-dialog';

type Tab = 'services' | 'packages';

/**
 * Backoffice → Services: the owner's catalogue of standard services (grouped by category) and packages. Each can be
 * edited or duplicated into a new one; hourly services show their price at the current labour rate.
 */
@Component({
  selector: 'app-catalogue-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, MoneyPipe, Alert, Icon, ServiceDialog, PackageDialog],
  styles: `
    .row-group th {
      background: var(--bs-tertiary-bg);
      font-size: 0.8125rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--bs-secondary-color);
    }
    @media (max-width: 575.98px) {
      .table-stack tbody tr.row-group {
        margin: 1rem 0 0.5rem;
        padding: 0;
        border: 0;
        background: none;
        box-shadow: none;
      }
      .table-stack tbody tr.row-group th {
        display: block;
        padding: 0;
        background: none;
        border: 0;
      }
    }
  `,
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'catalogue.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'catalogue.subtitle' | t }}</p>
      </div>
      @if (tab() === 'services') {
        <button type="button" class="btn btn-primary" (click)="serviceDialog.open(null)"><app-icon name="plus" [size]="16" />{{ 'catalogue.newService' | t }}</button>
      } @else {
        <button type="button" class="btn btn-primary" (click)="packageDialog.open(null, allServices(), products())"><app-icon name="plus" [size]="16" />{{ 'catalogue.newPackage' | t }}</button>
      }
    </header>

    <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
      <div class="btn-group" role="group" [attr.aria-label]="'catalogue.sections' | t">
        <button type="button" class="btn" [class.btn-primary]="tab() === 'services'" [class.btn-outline-secondary]="tab() !== 'services'" [attr.aria-pressed]="tab() === 'services'" (click)="tab.set('services')">{{ 'catalogue.services' | t }}</button>
        <button type="button" class="btn" [class.btn-primary]="tab() === 'packages'" [class.btn-outline-secondary]="tab() !== 'packages'" [attr.aria-pressed]="tab() === 'packages'" (click)="tab.set('packages')">{{ 'catalogue.packages' | t }}</button>
      </div>
      <div class="input-group flex-grow-1" style="max-width: 24rem">
        <span class="input-group-text"><app-icon name="search" [size]="16" /></span>
        <input #q type="search" class="form-control" [attr.aria-label]="'catalogue.search' | t" [placeholder]="'catalogue.search' | t" (input)="search(q.value)" />
      </div>
      <div class="form-check">
        <input id="cat-inactive" class="form-check-input" type="checkbox" [checked]="showInactive()" (change)="toggleInactive()" />
        <label class="form-check-label" for="cat-inactive">{{ 'catalogue.showInactive' | t }}</label>
      </div>
    </div>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    @if (tab() === 'services') {
      <div class="card table-stack mb-4">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>{{ 'catalogue.name' | t }}</th>
                <th class="text-end">{{ 'catalogue.hours' | t }}</th>
                <th class="text-end">{{ 'catalogue.priceShort' | t }}</th>
                <th>{{ 'catalogue.status' | t }}</th>
                <th><span class="visually-hidden">{{ 'common.edit' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (g of groups(); track g.category) {
                <tr class="row-group">
                  <th colspan="5" scope="colgroup">{{ g.category ?? ('catalogue.noCategory' | t) }}</th>
                </tr>
                @for (s of g.services; track s.id) {
                <tr [class.row-muted]="!s.active">
                  <td class="cell-title">{{ s.name }}</td>
                  <td class="text-end" [attr.data-label]="'catalogue.hours' | t">{{ 'catalogue.hoursValue' | t: { hours: hours(s.standardHours) } }}</td>
                  <td class="text-end" [attr.data-label]="'catalogue.priceShort' | t">
                    <div>{{ s.effectivePriceCents | money }}</div>
                    @if (s.pricing === 'HOURLY') {
                      <div class="small text-body-secondary">{{ 'catalogue.pricing.HOURLY' | t }}</div>
                    }
                  </td>
                  <td [attr.data-label]="'catalogue.status' | t">
                    <span class="d-inline-flex flex-wrap gap-1">
                      <span [class]="s.active ? 'badge bg-success-subtle text-success-emphasis' : 'badge bg-secondary-subtle text-secondary-emphasis'">{{ (s.active ? 'catalogue.active' : 'catalogue.inactive') | t }}</span>
                      @if (s.listedOnline) {
                        <span class="badge bg-info-subtle text-info-emphasis">{{ 'catalogue.online' | t }}</span>
                      }
                    </span>
                  </td>
                  <td class="cell-actions">
                    <button type="button" class="btn btn-sm btn-link" (click)="serviceDialog.open(s, true)" [attr.aria-label]="'catalogue.duplicateNamed' | t: { name: s.name }">{{ 'catalogue.duplicate' | t }}</button>
                    <button type="button" class="btn btn-sm btn-link" (click)="serviceDialog.open(s)">{{ 'common.edit' | t }}</button>
                  </td>
                </tr>
                }
              } @empty {
                <tr>
                  <td colspan="5">
                    <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                      <app-icon name="wrench" [size]="36" class="text-body-tertiary" />
                      <p class="fw-semibold text-body mb-0">{{ 'catalogue.emptyServices' | t }}</p>
                      <p class="mb-0">{{ 'catalogue.emptyServicesHint' | t }}</p>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    } @else {
      <div class="card table-stack mb-4">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>{{ 'catalogue.name' | t }}</th>
                <th>{{ 'catalogue.items' | t }}</th>
                <th class="text-end">{{ 'catalogue.priceShort' | t }}</th>
                <th>{{ 'catalogue.status' | t }}</th>
                <th><span class="visually-hidden">{{ 'common.edit' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (p of packages(); track p.id) {
                <tr [class.row-muted]="!p.active">
                  <td class="cell-title">{{ p.name }}</td>
                  <td [attr.data-label]="'catalogue.items' | t">
                    <ul class="list-unstyled small mb-0">
                      @for (i of p.items; track i.kind + (i.serviceId ?? i.productId)) {
                        <li [class.text-body-secondary]="!i.active">
                          {{ i.quantity > 1 ? ('catalogue.itemLine' | t: { name: i.name, quantity: i.quantity }) : i.name }}
                          @if (i.kind === 'PRODUCT') {
                            <span class="badge bg-secondary-subtle text-secondary-emphasis ms-1">{{ 'catalogue.partBadge' | t }}</span>
                          }
                        </li>
                      }
                    </ul>
                  </td>
                  <td class="text-end" [attr.data-label]="'catalogue.priceShort' | t">
                    <div>{{ p.priceCents | money }}</div>
                    @if (p.listPriceCents > p.priceCents) {
                      <div class="small text-success-emphasis">{{ 'catalogue.saving' | t: { amount: ((p.listPriceCents - p.priceCents) | money) } }}</div>
                    }
                  </td>
                  <td [attr.data-label]="'catalogue.status' | t">
                    <span class="d-inline-flex flex-wrap gap-1">
                      <span [class]="p.active ? 'badge bg-success-subtle text-success-emphasis' : 'badge bg-secondary-subtle text-secondary-emphasis'">{{ (p.active ? 'catalogue.active' : 'catalogue.inactive') | t }}</span>
                      @if (p.listedOnline) {
                        <span class="badge bg-info-subtle text-info-emphasis">{{ 'catalogue.online' | t }}</span>
                      }
                    </span>
                  </td>
                  <td class="cell-actions">
                    <button type="button" class="btn btn-sm btn-link" (click)="packageDialog.open(p, allServices(), products(), true)" [attr.aria-label]="'catalogue.duplicateNamed' | t: { name: p.name }">{{ 'catalogue.duplicate' | t }}</button>
                    <button type="button" class="btn btn-sm btn-link" (click)="packageDialog.open(p, allServices(), products())">{{ 'common.edit' | t }}</button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5">
                    <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                      <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
                      <p class="fw-semibold text-body mb-0">{{ 'catalogue.emptyPackages' | t }}</p>
                      <p class="mb-0">{{ 'catalogue.emptyPackagesHint' | t }}</p>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }

    <app-service-dialog #serviceDialog [labourRateCents]="labourRateCents()" (saved)="serviceSaved($event)" />
    <app-package-dialog #packageDialog (saved)="packageSaved($event)" />
  `,
})
export class CataloguePage implements OnInit, OnDestroy {
  private readonly api = inject(CatalogueApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  private readonly backoffice = inject(BackofficeApi);
  private readonly stock = inject(StockApi);
  protected readonly lang = this.i18n.lang;
  protected readonly tab = signal<Tab>('services');
  protected readonly services = signal<CatalogueService[]>([]);
  /** The services by category (A to Z, services without one last), each by name. */
  protected readonly groups = computed(() => groupByCategory(this.services(), this.lang()));
  /** The workshop's labour rate, for the price of hourly services in the dialog; null until loaded or if it fails. */
  protected readonly labourRateCents = signal<number | null>(null);
  protected readonly packages = signal<CataloguePackage[]>([]);
  /** Every service, inactive ones too: the package dialog needs them to show what a package holds. */
  protected readonly allServices = signal<CatalogueService[]>([]);
  /** Every stock product, inactive ones too, for the package dialog; empty when stock cannot be read. */
  protected readonly products = signal<PackageProductChoice[]>([]);
  protected readonly showInactive = signal(false);
  protected readonly error = signal<string | null>(null);
  private query = '';
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.load();
    this.backoffice.settings().subscribe({ next: (s) => this.labourRateCents.set(s.labourRateCents), error: () => {} });
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  /** Hours in the UI language, for example 0,75 in Portuguese. */
  protected hours(value: number): string {
    return new Intl.NumberFormat(this.lang(), { maximumFractionDigits: 2 }).format(value);
  }

  protected search(q: string): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.query = q;
      this.load();
    }, 250);
  }

  protected toggleInactive(): void {
    this.showInactive.update((v) => !v);
    this.load();
  }

  protected serviceSaved(s: CatalogueService): void {
    this.toasts.show(this.i18n.t('catalogue.savedService', { name: s.name }));
    this.load();
  }

  protected packageSaved(p: CataloguePackage): void {
    this.toasts.show(this.i18n.t('catalogue.savedPackage', { name: p.name }));
    this.load();
  }

  private load(): void {
    this.error.set(null);
    const activeOnly = !this.showInactive();
    const fail = (e: unknown) => this.error.set(this.i18n.error(e));
    this.api.services(this.query, activeOnly).subscribe({ next: (list) => this.services.set(list), error: fail });
    this.api.packages(this.query, activeOnly).subscribe({ next: (list) => this.packages.set(list), error: fail });
    this.api.services(undefined, false).subscribe({ next: (list) => this.allServices.set(list), error: fail });
    this.stock.products(undefined, { activeOnly: false }).subscribe({ next: (list) => this.products.set(list), error: () => this.products.set([]) });
  }
}
