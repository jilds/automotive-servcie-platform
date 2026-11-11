import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { ReportError } from './report-error';
import { CsvExport, ReportFormat, ReportPeriod } from './report-state';
import { availableWithKey, basisKey, sourceKey } from './reporting-keys';
import { DASH, isBusy } from './reporting-logic';
import { ReportingApi } from './reporting-api.service';
import type { KpiReport } from './reporting.models';

/** What a KPI without data says: "Available with <feature>", the feature named in the UI language. */
@Component({
  selector: 'app-kpi-unavailable',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `<p class="text-body-secondary mb-0" data-test="unavailable">{{ 'reports.kpi.availableWith' | t: { feature: feature() } }}</p>`,
})
export class KpiUnavailable {
  /** The feature id of `availableWith`. */
  readonly featureId = input.required<string>();
  private readonly i18n = inject(I18nService);
  protected readonly feature = computed(() => {
    const key = availableWithKey(this.featureId());
    return key ? this.i18n.t(key) : this.featureId();
  });
}

/** One card per KPI of the period; a KPI whose data does not exist yet says which feature will bring it. */
@Component({
  selector: 'app-kpis-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, Alert, ReportError, Icon, KpiUnavailable],
  providers: [CsvExport],
  template: `
    <div class="d-flex flex-wrap align-items-end gap-3 mb-3">
      <div class="me-auto">
        <h2 class="h4 mb-1">{{ 'reports.kpi.title' | t }}</h2>
        <p class="text-body-secondary small mb-0">{{ 'reports.kpi.subtitle' | t }}</p>
      </div>
      <button type="button" class="btn btn-outline-secondary" data-test="export" [disabled]="csv.busy()" [attr.aria-busy]="csv.busy()" (click)="export()">
        @if (csv.busy()) {
          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
        } @else {
          <app-icon name="file-text" [size]="16" />
        }
        {{ 'reports.export' | t }}
      </button>
    </div>

    <app-report-error [message]="error()" [busy]="busy()" (retry)="retry()" />
    @if (csv.error()) {
      <app-alert><span data-test="export-error">{{ csv.error() }}</span></app-alert>
    }

    <div [attr.aria-busy]="loading()">
      @if (report(); as r) {
        <p class="small text-body-secondary mb-3" data-test="scope">
          {{ 'reports.scope' | t: { from: r.from, to: r.to, zone: r.timezone } }}
          · {{ 'reports.kpi.delivered' | t: { n: fmt.count(r.deliveredOrders) } }}
        </p>

        <div class="row g-3">
          <!-- Average time per job -->
          <section class="col-12 col-lg-6" aria-labelledby="kpi-time" data-test="kpi-time">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-2">
              <h3 class="h6 mb-0" id="kpi-time">{{ 'reports.kpi.time.title' | t }}</h3>
              @if (r.averageTimePerJob; as k) {
                @if (k.available) {
                  <span class="stat-value" data-test="time-value">{{ fmt.hours(k.hoursPerJob) }}</span>
                  <span class="small text-body-secondary">{{ 'reports.kpi.time.basis' | t: { hours: fmt.hours(k.trackedHours), orders: fmt.count(k.ordersWithTime) } }}</span>
                  @if (k.byService.length) {
                    <div class="table-responsive"><table class="table table-sm align-middle mb-0">
                      <thead><tr>
                        <th>{{ 'reports.kpi.col.service' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.orders' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.hoursPerJob' | t }}</th>
                      </tr></thead>
                      <tbody>
                        @for (s of k.byService; track s.serviceItemId) {
                          <tr><td class="text-break">{{ s.name }}</td><td class="text-end">{{ fmt.count(s.orders) }}</td><td class="text-end text-nowrap">{{ fmt.hours(s.hoursPerJob) }}</td></tr>
                        }
                      </tbody>
                    </table></div>
                  }
                } @else {
                  <app-kpi-unavailable [featureId]="k.availableWith" />
                }
              }
            </div></div>
          </section>

          <!-- Average order value -->
          <section class="col-12 col-lg-6" aria-labelledby="kpi-value" data-test="kpi-value">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-2">
              <h3 class="h6 mb-0" id="kpi-value">{{ 'reports.kpi.value.title' | t }}</h3>
              @if (r.averageOrderValue; as k) {
                @if (k.available) {
                  <div class="d-flex flex-wrap gap-4">
                    <div class="vstack"><span class="stat-value text-break" data-test="value-net">{{ fmt.money(k.averageNetCents) }}</span><span class="small text-body-secondary">{{ 'reports.kpi.value.net' | t }}</span></div>
                    <div class="vstack"><span class="stat-value text-break" data-test="value-total">{{ fmt.money(k.averageTotalCents) }}</span><span class="small text-body-secondary">{{ 'reports.kpi.value.total' | t }}</span></div>
                  </div>
                  <span class="small text-body-secondary" data-test="value-source">{{ 'reports.kpi.value.source' | t: { source: sourceText(k.source) } }}</span>
                  <span class="small text-body-secondary">{{ 'reports.kpi.value.orders' | t: { invoiced: fmt.count(k.invoicedOrders), credited: fmt.count(k.creditedOrders) } }}</span>
                  <div class="border rounded p-2 small" data-test="not-invoiced">
                    <span class="fw-semibold">{{ 'reports.kpi.value.notInvoiced' | t }}</span>
                    <span class="d-block text-body-secondary">{{ 'reports.kpi.value.notInvoicedFigures' | t: { orders: fmt.count(k.notYetInvoiced.orders), net: fmt.money(k.notYetInvoiced.netCents), total: fmt.money(k.notYetInvoiced.totalCents) } }}</span>
                  </div>
                } @else {
                  <app-kpi-unavailable [featureId]="k.availableWith" />
                }
              }
            </div></div>
          </section>

          <!-- Margin -->
          <section class="col-12" aria-labelledby="kpi-margin" data-test="kpi-margin">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-2">
              <h3 class="h6 mb-0" id="kpi-margin">{{ 'reports.kpi.margin.title' | t }}</h3>
              @if (r.margin; as k) {
                @if (k.available) {
                  <div class="d-flex flex-wrap gap-4">
                    <div class="vstack"><span class="stat-value text-break" data-test="margin-value">{{ fmt.money(k.marginCents) }}</span><span class="small text-body-secondary">{{ 'reports.kpi.margin.perOrders' | t: { n: fmt.count(k.orders) } }}</span></div>
                    <div class="vstack"><span class="stat-value" data-test="margin-percent">{{ fmt.percent(k.marginPercent) }}</span><span class="small text-body-secondary">{{ 'reports.kpi.margin.ofNet' | t: { net: fmt.money(k.netCents) } }}</span></div>
                  </div>
                  <span class="small text-body-secondary" data-test="margin-basis">{{ 'reports.kpi.margin.basis' | t: { basis: (basisKey(k.basis) | t), parts: fmt.money(k.partsCostCents), labour: fmt.money(k.labourCostCents) } }}</span>
                  @if (!k.labourCostSet) {
                    <app-alert tone="info"><span data-test="parts-only">{{ 'reports.partsOnly' | t }} <a routerLink="/backoffice/settings">{{ 'reports.partsOnlyLink' | t }}</a></span></app-alert>
                  }
                  <h4 class="h6 mt-2 mb-0">{{ 'reports.kpi.margin.byService' | t }}</h4>
                  <p class="small text-body-secondary mb-0">{{ 'reports.kpi.margin.byServiceHelp' | t }}</p>
                  @if (k.byService.length) {
                    <div class="table-responsive"><table class="table table-sm align-middle mb-0" data-test="margin-services">
                      <thead><tr>
                        <th>{{ 'reports.kpi.col.service' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.lines' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.net' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.hours' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.labour' | t }}</th>
                        <th class="text-end">{{ 'reports.kpi.col.margin' | t }}</th>
                      </tr></thead>
                      <tbody>
                        @for (s of k.byService; track s.serviceItemId) {
                          <tr>
                            <td class="text-break">{{ s.name }}</td>
                            <td class="text-end">{{ fmt.count(s.lines) }}</td>
                            <td class="text-end text-nowrap">{{ fmt.money(s.netCents) }}</td>
                            <td class="text-end text-nowrap">{{ fmt.hours(s.trackedHours) }}</td>
                            <td class="text-end text-nowrap">{{ fmt.money(s.labourCostCents) }}</td>
                            <td class="text-end text-nowrap">{{ fmt.money(s.marginCents) }}<span class="d-block small text-body-secondary">{{ fmt.percent(s.marginPercent) }}</span></td>
                          </tr>
                        }
                      </tbody>
                    </table></div>
                  } @else {
                    <span class="small text-body-secondary">{{ 'reports.kpi.noServices' | t }}</span>
                  }
                  <h4 class="h6 mt-2 mb-0">{{ 'reports.kpi.margin.parts' | t }}</h4>
                  <p class="small text-body-secondary mb-0">{{ 'reports.kpi.margin.partsHelp' | t }}</p>
                  <div class="table-responsive"><table class="table table-sm align-middle mb-0" data-test="margin-parts">
                    <thead><tr>
                      <th>{{ 'reports.kpi.col.partsGroup' | t }}</th>
                      <th class="text-end">{{ 'reports.kpi.col.lines' | t }}</th>
                      <th class="text-end">{{ 'reports.kpi.col.net' | t }}</th>
                      <th class="text-end">{{ 'reports.kpi.col.partsCost' | t }}</th>
                      <th class="text-end">{{ 'reports.kpi.col.margin' | t }}</th>
                    </tr></thead>
                    <tbody>
                      <tr data-test="package-parts">
                        <td>{{ 'reports.kpi.margin.packageParts' | t }}</td>
                        <td class="text-end">{{ fmt.count(k.packageParts.lines) }}</td>
                        <td class="text-end text-nowrap">{{ fmt.money(k.packageParts.netCents) }}</td>
                        <td class="text-end text-nowrap">{{ fmt.money(k.packageParts.partsCostCents) }}</td>
                        <td class="text-end text-nowrap">{{ fmt.money(k.packageParts.marginCents) }}<span class="d-block small text-body-secondary">{{ fmt.percent(k.packageParts.marginPercent) }}</span></td>
                      </tr>
                      <tr data-test="other-parts">
                        <td>{{ 'reports.kpi.margin.otherParts' | t }}</td>
                        <td class="text-end">{{ dash }}</td>
                        <td class="text-end text-nowrap">{{ fmt.money(k.otherParts.netCents) }}</td>
                        <td class="text-end text-nowrap">{{ fmt.money(k.otherParts.partsCostCents) }}</td>
                        <td class="text-end text-nowrap">{{ fmt.money(k.otherParts.marginCents) }}<span class="d-block small text-body-secondary">{{ fmt.percent(k.otherParts.marginPercent) }}</span></td>
                      </tr>
                    </tbody>
                  </table></div>
                } @else {
                  <app-kpi-unavailable [featureId]="k.availableWith" />
                }
              }
            </div></div>
          </section>

          <!-- Retention -->
          <section class="col-12 col-md-6" aria-labelledby="kpi-retention" data-test="kpi-retention">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-2">
              <h3 class="h6 mb-0" id="kpi-retention">{{ 'reports.kpi.retention.title' | t }}</h3>
              @if (r.retention; as k) {
                @if (k.available) {
                  <span class="stat-value" data-test="retention-value">{{ fmt.percent(k.ratePercent) }}</span>
                  @if (!k.final) {
                    <span class="badge text-bg-warning align-self-start fw-normal" data-test="provisional">{{ 'reports.kpi.retention.provisional' | t: { covered: k.monthsCovered, months: k.months } }}</span>
                  }
                  <span class="small text-body-secondary">{{ 'reports.kpi.retention.basis' | t: { retained: fmt.count(k.retained), customers: fmt.count(k.customers), months: k.months } }}</span>
                } @else {
                  <app-kpi-unavailable [featureId]="k.availableWith" />
                }
              }
            </div></div>
          </section>

          <!-- Comebacks -->
          <section class="col-12 col-md-6" aria-labelledby="kpi-comebacks" data-test="kpi-comebacks">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-2">
              <h3 class="h6 mb-0" id="kpi-comebacks">{{ 'reports.kpi.comebacks.title' | t }}</h3>
              @if (r.comebacks; as k) {
                @if (k.available) {
                  <span class="stat-value" data-test="comebacks-value">{{ fmt.percent(k.ratePercent) }}</span>
                  <span class="small text-body-secondary">{{ 'reports.kpi.comebacks.basis' | t: { orders: fmt.count(k.orders), days: k.days } }}</span>
                } @else {
                  <app-kpi-unavailable [featureId]="k.availableWith" />
                }
              }
            </div></div>
          </section>

          <!-- KPIs that wait for their feature -->
          @for (f of future(); track f.id) {
            <section class="col-12 col-md-4" [attr.aria-labelledby]="'kpi-' + f.id" [attr.data-test]="'kpi-' + f.id">
              <div class="card shadow-sm h-100"><div class="card-body vstack gap-2">
                <h3 class="h6 mb-0" [id]="'kpi-' + f.id">{{ f.title | t }}</h3>
                @if (f.kpi.available) {
                  <dl class="mb-0 small">
                    @for (e of f.figures; track e[0]) {
                      <div class="d-flex justify-content-between gap-2"><dt class="fw-normal text-body-secondary text-break">{{ e[0] }}</dt><dd class="mb-0">{{ e[1] }}</dd></div>
                    }
                  </dl>
                } @else {
                  <app-kpi-unavailable [featureId]="f.kpi.availableWith" />
                }
              </div></div>
            </section>
          }
        </div>
      }
    </div>
  `,
})
export class KpisPage {
  private readonly api = inject(ReportingApi);
  private readonly i18n = inject(I18nService);
  private readonly period = inject(ReportPeriod);
  protected readonly csv = inject(CsvExport);
  protected readonly fmt = inject(ReportFormat);
  protected readonly basisKey = basisKey;
  protected readonly dash = DASH;

  protected readonly report = signal<KpiReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  private seq = 0;
  private last: { from: string; to: string } | null = null;

  /** Occupancy, satisfaction and complaints: keyed on `available`, with their figures once a feature fills them in. */
  protected readonly future = computed(() => {
    const r = this.report();
    if (!r) return [];
    const entry = (id: 'occupancy' | 'satisfaction' | 'complaints', title: 'reports.kpi.occupancy.title' | 'reports.kpi.satisfaction.title' | 'reports.kpi.complaints.title') => {
      const kpi = r[id];
      const figures = kpi.available ? Object.entries(kpi).filter(([name, v]) => name !== 'available' && (typeof v === 'number' || typeof v === 'string')) : [];
      return { id, title, kpi, figures };
    };
    return [entry('occupancy', 'reports.kpi.occupancy.title'), entry('satisfaction', 'reports.kpi.satisfaction.title'), entry('complaints', 'reports.kpi.complaints.title')];
  });

  constructor() {
    effect(() => {
      const q = this.period.applied();
      untracked(() => this.load(q));
    });
  }

  private load(q: { from: string; to: string }): void {
    this.last = q;
    const seq = ++this.seq;
    this.loading.set(true);
    this.error.set(null);
    this.busy.set(false);
    this.api.kpis(q).subscribe({
      next: (r) => {
        if (seq !== this.seq) return;
        this.report.set(r);
        this.period.adopt(r.from, r.to);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        if (seq !== this.seq) return;
        this.busy.set(isBusy(e));
        this.error.set(isBusy(e) ? null : this.i18n.error(e));
        this.loading.set(false);
      },
    });
  }

  protected sourceText(source: string): string {
    const key = sourceKey(source);
    return key ? this.i18n.t(key) : source;
  }

  protected retry(): void {
    if (this.last) this.load(this.last);
  }

  protected export(): void {
    this.csv.run('kpis', this.period.applied());
  }
}
