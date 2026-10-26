import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';

import { isStale } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { AdminApi } from './admin-api.service';
import { Lead, LEAD_STATUSES, LeadStatus } from './admin.models';

/** Demo requests from the landing site ("Pedir proposta"): who asked, how to reach them, and where they stand. */
@Component({
  selector: 'app-lead-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DatePipe, Alert, Icon],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'admin.leads.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'admin.leads.subtitle' | t }}</p>
      </div>
      <div class="d-flex flex-wrap align-items-center gap-2">
        <label class="visually-hidden" for="lead-filter">{{ 'admin.leads.filter' | t }}</label>
        <!-- [selected] on the options, not [value] on the select: the options do not exist yet when [value] is set. -->
        <select id="lead-filter" class="form-select w-auto" (change)="setFilter($any($event.target).value)">
          <option value="" [selected]="filter() === null">{{ 'admin.leads.all' | t }}</option>
          @for (s of statuses; track s) {
            <option [value]="s" [selected]="filter() === s">{{ statusKey(s) | t }}</option>
          }
        </select>
        <button type="button" class="btn btn-outline-secondary" [disabled]="loading()" (click)="load()">
          <app-icon name="refresh" [size]="16" />{{ 'common.refresh' | t }}
        </button>
      </div>
    </header>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <div class="card table-stack">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>{{ 'admin.leads.workshop' | t }}</th>
              <th>{{ 'admin.leads.contact' | t }}</th>
              <th>{{ 'admin.leads.district' | t }}</th>
              <th>{{ 'admin.leads.vehicleType' | t }}</th>
              <th>{{ 'admin.leads.received' | t }}</th>
              <th>{{ 'admin.leads.status' | t }}</th>
            </tr>
          </thead>
          <tbody>
            @for (l of leads(); track l.id) {
              <tr>
                <td class="cell-title">
                  <span class="fw-semibold">{{ l.workshopName }}</span>
                  <div class="small text-body-secondary">{{ l.name }}</div>
                  @if (l.message) {
                    <div class="small mt-1 text-break lead-message">{{ l.message }}</div>
                  }
                </td>
                <td class="text-break" [attr.data-label]="'admin.leads.contact' | t">
                  @if (l.phone) {
                    <a class="d-block" [href]="'tel:' + encode(l.phone)">{{ l.phone }}</a>
                  }
                  @if (l.email) {
                    <a class="d-block" [href]="'mailto:' + encode(l.email)">{{ l.email }}</a>
                  }
                  @if (l.marketingConsent) {
                    <span class="badge bg-success-subtle text-success-emphasis mt-1">{{ 'admin.leads.marketing' | t }}</span>
                  }
                </td>
                <td [attr.data-label]="'admin.leads.district' | t">{{ l.district ?? '—' }}</td>
                <td [attr.data-label]="'admin.leads.vehicleType' | t">{{ typeKey(l.vehicleType) | t }}</td>
                <td [attr.data-label]="'admin.leads.received' | t">{{ l.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td [attr.data-label]="'admin.leads.status' | t">
                  <label class="visually-hidden" [for]="'lead-status-' + l.id">{{ 'admin.leads.status' | t }}</label>
                  <select class="form-select form-select-sm w-auto" [id]="'lead-status-' + l.id" [value]="l.status"
                          [disabled]="saving() === l.id" (change)="change(l, $any($event.target).value)">
                    @for (s of statuses; track s) {
                      <option [value]="s" [selected]="s === l.status">{{ statusKey(s) | t }}</option>
                    }
                  </select>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6" class="cell-full">
                  <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                    <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
                    <p class="fw-semibold text-body mb-0">{{ (loading() ? 'common.loading' : 'admin.leads.empty') | t }}</p>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `.lead-message { max-width: 36rem; white-space: pre-line; }`,
})
export class LeadList implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly i18n = inject(I18nService);

  protected readonly statuses = LEAD_STATUSES;
  protected readonly leads = signal<Lead[]>([]);
  protected readonly filter = signal<LeadStatus | null>('NEW');
  protected readonly loading = signal(false);
  protected readonly saving = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.leads(this.filter()).subscribe({
      next: (list) => {
        this.leads.set(list);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
      },
    });
  }

  protected setFilter(value: string): void {
    this.filter.set(value === '' ? null : (value as LeadStatus));
    this.load();
  }

  /** Saves the new status with the version read; someone else's change first reloads the list and says so. */
  protected change(lead: Lead, status: LeadStatus): void {
    if (status === lead.status) {
      return;
    }
    this.saving.set(lead.id);
    this.error.set(null);
    this.api.changeLeadStatus(lead.id, status, lead.version).subscribe({
      next: (saved) => {
        this.saving.set(null);
        const filter = this.filter();
        this.leads.update((list) => list.map((l) => (l.id === saved.id ? saved : l)).filter((l) => !filter || l.status === filter));
      },
      error: (e) => {
        this.saving.set(null);
        this.error.set(this.i18n.error(e));
        if (isStale(e)) {
          const message = this.error();
          this.load();
          this.error.set(message);
        } else {
          // Put the select back to the stored status.
          this.leads.update((list) => [...list]);
        }
      },
    });
  }

  protected statusKey(status: LeadStatus): `admin.leads.status.${LeadStatus}` {
    return `admin.leads.status.${status}`;
  }

  protected typeKey(type: Lead['vehicleType']): `admin.leads.type.${Lead['vehicleType']}` {
    return `admin.leads.type.${type}`;
  }

  protected encode(value: string): string {
    return encodeURIComponent(value);
  }
}
