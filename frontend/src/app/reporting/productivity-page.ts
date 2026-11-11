import { ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { BackofficeApi } from '../backoffice/backoffice-api.service';
import { TeamMember } from '../backoffice/backoffice.models';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { ReportError } from './report-error';
import { CsvExport, ReportFormat, ReportPeriod } from './report-state';
import { isBusy } from './reporting-logic';
import { ReportingApi } from './reporting-api.service';
import type { ProductivityQuery, ProductivityReport } from './reporting.models';

/**
 * Mechanic productivity: one row per mechanic with activity in the period (tracked hours, jobs, standard hours,
 * efficiency, average time in the workshop) and the totals. Efficiency above 1 means faster than the standard time.
 */
@Component({
  selector: 'app-productivity-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Alert, ReportError, Icon],
  providers: [CsvExport],
  template: `
    <div class="d-flex flex-wrap align-items-end gap-3 mb-3">
      <div class="me-auto">
        <h2 class="h4 mb-1">{{ 'reports.prod.title' | t }}</h2>
        <p class="text-body-secondary small mb-0">{{ 'reports.prod.subtitle' | t }}</p>
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
        <label class="form-label" for="prod-mechanic">{{ 'reports.filter.mechanic' | t }}</label>
        <select id="prod-mechanic" class="form-select" [value]="mechanicId()" (change)="setMechanic($event)">
          <option value="">{{ 'reports.filter.allMechanics' | t }}</option>
          @for (m of mechanics(); track m.id) {
            <option [value]="m.id">{{ m.name }}</option>
          }
        </select>
      </div>
    </div>

    <div [attr.aria-busy]="loading()">
      @if (report(); as r) {
        <p class="small text-body-secondary mb-2" data-test="scope">{{ 'reports.scope' | t: { from: r.from, to: r.to, zone: r.timezone } }}</p>
      }

      <div class="card table-stack mb-3">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>{{ 'reports.prod.col.mechanic' | t }}</th>
                <th class="text-end">{{ 'reports.prod.col.tracked' | t }}</th>
                <th class="text-end">{{ 'reports.prod.col.jobs' | t }}</th>
                <th class="text-end" [attr.title]="'reports.standardHint' | t">{{ 'reports.prod.col.standard' | t }}</th>
                <th class="text-end">{{ 'reports.prod.col.trackedOnJobs' | t }}</th>
                <th class="text-end">{{ 'reports.prod.col.efficiency' | t }}</th>
                <th class="text-end">{{ 'reports.prod.col.average' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (m of rows(); track m.mechanicId) {
                <tr data-test="row">
                  <td class="cell-title">
                    <span class="text-break">{{ m.name }}</span>
                    @if (!m.active) {
                      <span class="badge text-bg-secondary ms-2 fw-normal">{{ 'reports.prod.inactive' | t }}</span>
                    }
                  </td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.tracked' | t">{{ fmt.hours(m.trackedHours) }}</td>
                  <td class="text-end" [attr.data-label]="'reports.prod.col.jobs' | t">{{ fmt.count(m.jobs) }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.standard' | t">{{ fmt.hours(m.standardHours) }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.trackedOnJobs' | t">{{ fmt.hours(m.trackedHoursOnJobs) }}</td>
                  <td class="text-end" [attr.data-label]="'reports.prod.col.efficiency' | t" data-test="efficiency">{{ fmt.ratio(m.efficiency) }}</td>
                  <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.average' | t" data-test="average">{{ fmt.hours(m.averageHoursInWorkshop) }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="7" class="cell-full">
                    <div class="py-4 text-center text-body-secondary" data-test="empty">{{ (loading() ? 'common.loading' : 'reports.prod.empty') | t }}</div>
                  </td>
                </tr>
              }
            </tbody>
            @if (report()?.totals; as t) {
              @if (rows().length) {
                <tfoot class="fw-semibold" data-test="totals">
                  <tr>
                    <td>{{ 'reports.prod.totals' | t }}</td>
                    <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.tracked' | t">{{ fmt.hours(t.trackedHours) }}</td>
                    <td class="text-end" [attr.data-label]="'reports.prod.col.jobs' | t">{{ fmt.count(t.jobs) }}</td>
                    <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.standard' | t">{{ fmt.hours(t.standardHours) }}</td>
                    <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.trackedOnJobs' | t">{{ fmt.hours(t.trackedHoursOnJobs) }}</td>
                    <td class="text-end" [attr.data-label]="'reports.prod.col.efficiency' | t" data-test="total-efficiency">{{ fmt.ratio(t.efficiency) }}</td>
                    <td class="text-end text-nowrap" [attr.data-label]="'reports.prod.col.average' | t">{{ fmt.hours(t.averageHoursInWorkshop) }}</td>
                  </tr>
                </tfoot>
              }
            }
          </table>
        </div>
      </div>
      <p class="small text-body-secondary">{{ 'reports.prod.help' | t }}</p>
      <p class="small text-body-secondary" data-test="standard-hint">{{ 'reports.standardHint' | t }}</p>
    </div>
  `,
})
export class ProductivityPage implements OnInit {
  private readonly api = inject(ReportingApi);
  private readonly backoffice = inject(BackofficeApi);
  private readonly i18n = inject(I18nService);
  private readonly period = inject(ReportPeriod);
  protected readonly csv = inject(CsvExport);
  protected readonly fmt = inject(ReportFormat);

  protected readonly mechanicId = signal('');
  protected readonly mechanics = signal<TeamMember[]>([]);
  protected readonly report = signal<ProductivityReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly rows = computed(() => this.report()?.mechanics ?? []);
  private seq = 0;
  private last: ProductivityQuery | null = null;

  private readonly query = computed<ProductivityQuery>(() => ({ ...this.period.applied(), mechanicId: this.mechanicId() || undefined }));

  constructor() {
    effect(() => {
      const q = this.query();
      untracked(() => this.load(q));
    });
  }

  ngOnInit(): void {
    this.backoffice.users().subscribe({
      next: (users) => this.mechanics.set(users.filter((u) => u.role === 'MECHANIC')),
      error: () => this.mechanics.set([]),
    });
  }

  private load(q: ProductivityQuery): void {
    this.last = q;
    const seq = ++this.seq;
    this.loading.set(true);
    this.error.set(null);
    this.busy.set(false);
    this.api.productivity(q).subscribe({
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

  protected setMechanic(event: Event): void {
    this.mechanicId.set((event.target as HTMLSelectElement).value);
  }

  protected retry(): void {
    if (this.last) this.load(this.last);
  }

  protected export(): void {
    this.csv.run('productivity', this.query());
  }
}
