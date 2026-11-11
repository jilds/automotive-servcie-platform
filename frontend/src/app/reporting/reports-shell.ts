import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from '../shared/icon';
import { ReportPeriod } from './report-state';

/**
 * `/backoffice/reports` frame: the three report tabs and the period filter they share (two local dates in the
 * workshop's time zone, both inclusive; empty = the server's default, the current month so far).
 */
@Component({
  selector: 'app-reports-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, Icon],
  providers: [ReportPeriod],
  template: `
    <header class="mb-3">
      <h1 class="mb-2">{{ 'reports.title' | t }}</h1>
      <p class="text-body-secondary mb-0">{{ 'reports.subtitle' | t }}</p>
    </header>

    <nav class="nav nav-pills flex-nowrap overflow-x-auto mb-3 pb-1" [attr.aria-label]="'reports.tabs' | t">
      <a class="nav-link text-nowrap" routerLink="/backoffice/reports" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page" data-test="tab-cost">{{ 'reports.tab.costVsQuote' | t }}</a>
      <a class="nav-link text-nowrap" routerLink="/backoffice/reports/productivity" routerLinkActive="active" ariaCurrentWhenActive="page" data-test="tab-productivity">{{ 'reports.tab.productivity' | t }}</a>
      <a class="nav-link text-nowrap" routerLink="/backoffice/reports/kpis" routerLinkActive="active" ariaCurrentWhenActive="page" data-test="tab-kpis">{{ 'reports.tab.kpis' | t }}</a>
    </nav>

    <form class="card shadow-sm mb-3" novalidate (submit)="$event.preventDefault(); apply()" data-test="period" [attr.aria-label]="'reports.period.title' | t">
      <div class="card-body">
        <div class="row g-3 align-items-end">
          <div class="col-6 col-md-3">
            <label class="form-label" for="rp-from">{{ 'reports.period.from' | t }}</label>
            <input id="rp-from" class="form-control" type="date" [value]="period.shown().from" [class.is-invalid]="!!period.problem()" [attr.aria-describedby]="period.problem() ? 'rp-problem' : null" (input)="set('from', $event)" />
          </div>
          <div class="col-6 col-md-3">
            <label class="form-label" for="rp-to">{{ 'reports.period.to' | t }}</label>
            <input id="rp-to" class="form-control" type="date" [value]="period.shown().to" [class.is-invalid]="!!period.problem()" (input)="set('to', $event)" />
          </div>
          <div class="col-12 col-md-6 d-flex flex-wrap gap-2">
            <button type="submit" class="btn btn-primary" data-test="apply"><app-icon name="search" [size]="16" />{{ 'reports.period.apply' | t }}</button>
            <button type="button" class="btn btn-outline-secondary" data-test="reset" (click)="period.reset()">{{ 'reports.period.reset' | t }}</button>
          </div>
          @if (period.problem(); as problem) {
            <div class="col-12">
              <div class="text-danger small d-flex align-items-center gap-1" id="rp-problem" role="alert" data-test="period-problem">
                <app-icon name="alert-circle" [size]="14" />{{ (problem === 'from-after-to' ? 'reports.period.fromAfterTo' : 'reports.period.invalid') | t }}
              </div>
            </div>
          } @else {
            <div class="col-12 form-text mt-2">{{ 'reports.period.help' | t }}</div>
          }
        </div>
      </div>
    </form>

    <router-outlet />
  `,
})
export class ReportsShell {
  protected readonly period = inject(ReportPeriod);

  protected set(field: 'from' | 'to', event: Event): void {
    this.period.set(field, (event.target as HTMLInputElement).value);
  }

  protected apply(): void {
    this.period.apply();
  }
}
