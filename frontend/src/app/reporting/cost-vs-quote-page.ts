import { ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { BackofficeApi } from '../backoffice/backoffice-api.service';
import { TeamMember } from '../backoffice/backoffice.models';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { ReportError } from './report-error';
import { CsvExport, ReportFormat, ReportPeriod } from './report-state';
import { basisKey } from './reporting-keys';
import { approvedQuoteFilter, DASH, isBusy } from './reporting-logic';
import { ReportingApi } from './reporting-api.service';
import type { CostVsQuoteQuery, CostVsQuoteReport } from './reporting.models';

export const PAGE_SIZES = [25, 50, 100, 200] as const;

/**
 * Cost vs quote: the delivered orders of the period, the approved quote next to the final total, standard next to
 * tracked hours, and the margin; a summary of the whole filtered set above the page.
 */
@Component({
  selector: 'app-cost-vs-quote-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, Alert, ReportError, Icon],
  providers: [CsvExport],
  template: `
    <div class="d-flex flex-wrap align-items-end gap-3 mb-3">
      <div class="me-auto">
        <h2 class="h4 mb-1">{{ 'reports.cvq.title' | t }}</h2>
        <p class="text-body-secondary small mb-0">{{ 'reports.cvq.subtitle' | t }}</p>
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

    <div class="row g-3 mb-3" data-test="filters">
      <div class="col-12 col-sm-6 col-lg-4">
        <label class="form-label" for="cvq-mechanic">{{ 'reports.filter.mechanic' | t }}</label>
        <select id="cvq-mechanic" class="form-select" [value]="mechanicId()" (change)="setMechanic($event)">
          <option value="">{{ 'reports.filter.allMechanics' | t }}</option>
          @for (m of mechanics(); track m.id) {
            <option [value]="m.id">{{ m.name }}</option>
          }
        </select>
      </div>
      <div class="col-6 col-lg-4">
        <label class="form-label" for="cvq-quote">{{ 'reports.filter.quote' | t }}</label>
        <select id="cvq-quote" class="form-select" [value]="quoteFilter()" (change)="setQuote($event)">
          <option value="">{{ 'reports.filter.quoteAll' | t }}</option>
          <option value="true">{{ 'reports.filter.quoteWith' | t }}</option>
          <option value="false">{{ 'reports.filter.quoteWithout' | t }}</option>
        </select>
      </div>
      <div class="col-6 col-lg-4">
        <label class="form-label" for="cvq-size">{{ 'reports.filter.pageSize' | t }}</label>
        <select id="cvq-size" class="form-select" [value]="size()" (change)="setSize($event)">
          @for (s of sizes; track s) {
            <option [value]="s" [selected]="s === size()">{{ s }}</option>
          }
        </select>
      </div>
    </div>

    <div [attr.aria-busy]="loading()">
      @if (report(); as r) {
        <p class="small text-body-secondary mb-2" data-test="scope">
          {{ 'reports.scope' | t: { from: r.from, to: r.to, zone: r.timezone } }}
          · {{ 'reports.cvq.basis' | t: { basis: (basisKey(r.marginBasis) | t) } }}
        </p>
        @if (!r.labourCostSet) {
          <app-alert tone="info"><span data-test="parts-only">{{ 'reports.partsOnly' | t }} <a routerLink="/backoffice/settings">{{ 'reports.partsOnlyLink' | t }}</a></span></app-alert>
        }

        <div class="row g-3 mb-3" data-test="summary" role="group" [attr.aria-label]="'reports.cvq.summary' | t">
          <div class="col-6 col-lg-3">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-1 py-3">
              <span class="small fw-medium text-body-secondary">{{ 'reports.cvq.orders' | t }}</span>
              <span class="stat-value" data-test="sum-orders">{{ fmt.count(r.summary.orders) }}</span>
              <span class="small text-body-secondary">{{ 'reports.cvq.withQuote' | t: { n: fmt.count(r.summary.ordersWithApprovedQuote) } }}</span>
            </div></div>
          </div>
          <div class="col-6 col-lg-3">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-1 py-3">
              <span class="small fw-medium text-body-secondary">{{ 'reports.cvq.quoteVsFinal' | t }}</span>
              <span class="stat-value text-break" data-test="sum-diff">{{ fmt.signedMoney(r.summary.differenceCents) }}</span>
              <span class="small text-body-secondary">{{ fmt.signedPercent(r.summary.differencePercent) }}</span>
              <span class="small text-body-secondary">{{ 'reports.cvq.quoteFinal' | t: { quote: fmt.money(r.summary.quoteTotalCents), final: fmt.money(r.summary.finalTotalCents) } }}</span>
            </div></div>
          </div>
          <div class="col-6 col-lg-3">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-1 py-3">
              <span class="small fw-medium text-body-secondary">{{ 'reports.cvq.hours' | t }}</span>
              <span class="stat-value" data-test="sum-tracked">{{ fmt.hours(r.summary.trackedHours) }}</span>
              <span class="small text-body-secondary">{{ 'reports.cvq.standardOf' | t: { standard: fmt.hours(r.summary.standardHours) } }}</span>
            </div></div>
          </div>
          <div class="col-6 col-lg-3">
            <div class="card shadow-sm h-100"><div class="card-body vstack gap-1 py-3">
              <span class="small fw-medium text-body-secondary">{{ 'reports.cvq.margin' | t }}</span>
              <span class="stat-value text-break" data-test="sum-margin">{{ fmt.money(r.summary.marginCents) }}</span>
              <span class="small text-body-secondary" data-test="sum-margin-percent">{{ fmt.percent(r.summary.marginPercent) }}</span>
              <span class="small text-body-secondary text-break">{{ 'reports.cvq.costs' | t: { net: fmt.money(r.summary.netCents), parts: fmt.money(r.summary.partsCostCents), labour: fmt.money(r.summary.labourCostCents) } }}</span>
            </div></div>
          </div>
        </div>
      }

      <div class="card table-stack mb-3">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>{{ 'reports.cvq.col.order' | t }}</th>
                <th>{{ 'reports.cvq.col.delivered' | t }}</th>
                <th>{{ 'reports.cvq.col.mechanic' | t }}</th>
                <th class="text-end">{{ 'reports.cvq.col.quote' | t }}</th>
                <th class="text-end">{{ 'reports.cvq.col.final' | t }}</th>
                <th class="text-end">{{ 'reports.cvq.col.difference' | t }}</th>
                <th class="text-end" [attr.title]="'reports.standardHint' | t">{{ 'reports.cvq.col.hours' | t }}</th>
                <th class="text-end">{{ 'reports.cvq.col.parts' | t }}</th>
                <th class="text-end">{{ 'reports.cvq.col.labour' | t }}</th>
                <th class="text-end">{{ 'reports.cvq.col.margin' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (i of items(); track i.workOrderId) {
                <tr data-test="row">
                  <td class="cell-title">
                    <a class="text-break" [routerLink]="['/work-orders', i.workOrderId]">#{{ i.number }}</a>
                    <span class="ms-2 fw-normal small text-body-secondary">{{ i.plate }}</span>
                  </td>
                  <td [attr.data-label]="'reports.cvq.col.delivered' | t" class="text-nowrap">{{ fmt.instant(i.deliveredAt, report()?.timezone) }}</td>
                  <td [attr.data-label]="'reports.cvq.col.mechanic' | t">{{ i.mechanicName ?? dash }}</td>
                  <td class="text-end" [attr.data-label]="'reports.cvq.col.quote' | t">
                    @if (i.quote; as q) {
                      <span class="text-nowrap">{{ fmt.money(q.totalCents) }}</span><span class="d-block small text-body-secondary">{{ 'reports.cvq.quoteNumber' | t: { number: q.number } }}</span>
                    } @else {
                      <span class="text-body-secondary" data-test="no-quote">{{ 'reports.cvq.noQuote' | t }}</span>
                    }
                  </td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.cvq.col.final' | t">{{ fmt.money(i.finalTotalCents) }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.cvq.col.difference' | t" data-test="difference">{{ fmt.signedMoney(i.differenceCents) }}<span class="d-block small text-body-secondary">{{ fmt.signedPercent(i.differencePercent) }}</span></td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.cvq.col.hours' | t">{{ 'reports.cvq.hoursPair' | t: { tracked: fmt.hours(i.trackedHours), standard: fmt.hours(i.standardHours) } }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.cvq.col.parts' | t">{{ fmt.money(i.partsCostCents) }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.cvq.col.labour' | t" data-test="labour">{{ fmt.money(i.labourCostCents) }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.cvq.col.margin' | t">{{ fmt.money(i.marginCents) }}<span class="d-block small text-body-secondary">{{ fmt.percent(i.marginPercent) }}</span></td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="10" class="cell-full">
                    <div class="py-4 text-center text-body-secondary" data-test="empty">{{ (loading() ? 'common.loading' : 'reports.cvq.empty') | t }}</div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <p class="small text-body-secondary" data-test="standard-hint">{{ 'reports.standardHint' | t }}</p>

      @if (total() > 0) {
        <nav class="d-flex flex-wrap align-items-center justify-content-between gap-2" [attr.aria-label]="'reports.page.nav' | t">
          <span class="small text-body-secondary" data-test="range">{{ 'reports.page.range' | t: { from: rangeFrom(), to: rangeTo(), total: total() } }}</span>
          <span class="d-flex gap-2">
            <button type="button" class="btn btn-outline-secondary" data-test="prev" [disabled]="page() === 0 || loading()" (click)="go(page() - 1)">{{ 'reports.page.prev' | t }}</button>
            <button type="button" class="btn btn-outline-secondary" data-test="next" [disabled]="rangeTo() >= total() || loading()" (click)="go(page() + 1)">{{ 'reports.page.next' | t }}</button>
          </span>
        </nav>
      }
    </div>
  `,
})
export class CostVsQuotePage implements OnInit {
  private readonly api = inject(ReportingApi);
  private readonly backoffice = inject(BackofficeApi);
  private readonly i18n = inject(I18nService);
  private readonly period = inject(ReportPeriod);
  protected readonly csv = inject(CsvExport);
  protected readonly fmt = inject(ReportFormat);
  protected readonly dash = DASH;
  protected readonly basisKey = basisKey;
  protected readonly sizes = PAGE_SIZES;

  protected readonly mechanicId = signal('');
  protected readonly quoteFilter = signal('');
  protected readonly size = signal<number>(50);
  protected readonly page = signal(0);
  protected readonly mechanics = signal<TeamMember[]>([]);
  protected readonly report = signal<CostVsQuoteReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  private seq = 0;
  private last: CostVsQuoteQuery | null = null;

  protected readonly items = computed(() => this.report()?.items ?? []);
  protected readonly total = computed(() => this.report()?.totalElements ?? 0);
  protected readonly rangeFrom = computed(() => (this.total() ? this.page() * this.size() + 1 : 0));
  protected readonly rangeTo = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));

  /** What the screen is showing and what the export downloads (without paging). */
  private readonly filters = computed<CostVsQuoteQuery>(() => ({
    ...this.period.applied(),
    mechanicId: this.mechanicId() || undefined,
    hasApprovedQuote: approvedQuoteFilter(this.quoteFilter()),
  }));

  constructor() {
    effect(() => {
      const q: CostVsQuoteQuery = { ...this.filters(), page: this.page(), size: this.size() };
      untracked(() => this.load(q));
    });
  }

  ngOnInit(): void {
    this.backoffice.users().subscribe({
      next: (users) => this.mechanics.set(users.filter((u) => u.role === 'MECHANIC')),
      error: () => this.mechanics.set([]),
    });
  }

  private load(q: CostVsQuoteQuery): void {
    this.last = q;
    const seq = ++this.seq;
    this.loading.set(true);
    this.error.set(null);
    this.busy.set(false);
    this.api.costVsQuote(q).subscribe({
      next: (r) => {
        if (seq !== this.seq) return; // an older answer arriving late must not replace the newer one
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

  protected setMechanic(event: Event): void {
    this.mechanicId.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
  }

  protected setQuote(event: Event): void {
    this.quoteFilter.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
  }

  protected setSize(event: Event): void {
    this.size.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
  }

  protected go(page: number): void {
    this.page.set(Math.max(0, page));
  }

  protected retry(): void {
    if (this.last) this.load(this.last);
  }

  protected export(): void {
    this.csv.run('cost-vs-quote', this.filters());
  }
}
