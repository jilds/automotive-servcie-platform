import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { applyProblem } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { COUNTRIES, timeZones } from '../backoffice/settings-form';
import { CopyLink } from '../identity/copy-link';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { AdminApi } from './admin-api.service';
import { AdminWorkshop, INITIAL_STATUSES, planKey, PLANS, ProvisionedWorkshop, STATUS_BADGE, statusKey } from './admin.models';
import { toCreateRequest, workshopForm } from './workshop-form';

/** Platform admin home: every workshop with its subscription, and registering a new one with its owner. */
@Component({
  selector: 'app-workshop-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, DatePipe, Alert, Icon, CopyLink],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'admin.workshops.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'admin.workshops.subtitle' | t }}</p>
      </div>
      <div class="d-flex flex-wrap gap-2">
        <button type="button" class="btn btn-outline-secondary" [disabled]="loading()" (click)="load()">
          <app-icon name="refresh" [size]="16" />{{ 'common.refresh' | t }}
        </button>
        <button type="button" class="btn btn-primary" (click)="openCreate()"><app-icon name="plus" [size]="16" />{{ 'admin.workshops.new' | t }}</button>
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
              <th>{{ 'admin.workshops.name' | t }}</th>
              <th>{{ 'common.nif' | t }}</th>
              <th>{{ 'admin.workshops.country' | t }}</th>
              <th>{{ 'admin.workshops.plan' | t }}</th>
              <th>{{ 'admin.workshops.status' | t }}</th>
              <th>{{ 'admin.workshops.created' | t }}</th>
              <th>{{ 'admin.workshops.owner' | t }}</th>
              <th class="text-end">{{ 'admin.workshops.users' | t }}</th>
            </tr>
          </thead>
          <tbody>
            @for (w of workshops(); track w.id) {
              <tr>
                <td class="cell-title">
                  <a [routerLink]="['/admin/workshops', w.id]">{{ w.name }}</a>
                  <div class="small text-body-secondary font-monospace">/w/{{ w.slug }}</div>
                </td>
                <td [attr.data-label]="'common.nif' | t">{{ w.nif ?? '—' }}</td>
                <td [attr.data-label]="'admin.workshops.country' | t">{{ w.country }}</td>
                <td [attr.data-label]="'admin.workshops.plan' | t">{{ planKey(w.plan) | t }}</td>
                <td [attr.data-label]="'admin.workshops.status' | t"><span [class]="statusBadge[w.status]">{{ statusKey(w.status) | t }}</span></td>
                <td [attr.data-label]="'admin.workshops.created' | t">{{ w.createdAt | date: 'dd/MM/yyyy' }}</td>
                <td class="text-break" [attr.data-label]="'admin.workshops.owner' | t">{{ w.ownerEmail ?? '—' }}</td>
                <td class="text-end" [attr.data-label]="'admin.workshops.users' | t">{{ w.users }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" class="cell-full">
                  <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                    <app-icon name="briefcase" [size]="36" class="text-body-tertiary" />
                    <p class="fw-semibold text-body mb-0">{{ (loading() ? 'common.loading' : 'admin.workshops.empty') | t }}</p>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    <dialog #createDialog class="modal" aria-labelledby="create-title">
      <div class="modal-dialog modal-lg">
        @if (created(); as c) {
          <div class="modal-content">
            <div class="modal-header">
              <h2 class="modal-title" id="create-title">{{ 'admin.create.doneTitle' | t: { name: c.workshop.name } }}</h2>
              <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="createDialog.close()"></button>
            </div>
            <div class="modal-body vstack gap-3">
              <p class="mb-0">{{ 'admin.create.doneBody' | t: { email: c.workshop.ownerEmail } }}</p>
              <app-copy-link [path]="c.activationPath" [expiresAt]="c.activationExpiresAt" [recipient]="c.workshop.ownerEmail" />
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="createDialog.close()">{{ 'common.close' | t }}</button>
              <button type="button" class="btn btn-primary" (click)="openWorkshop(c.workshop.id)">{{ 'admin.create.openWorkshop' | t }}</button>
            </div>
          </div>
        } @else {
          <form class="modal-content" [formGroup]="form" (ngSubmit)="create()" novalidate>
            <div class="modal-header">
              <h2 class="modal-title" id="create-title">{{ 'admin.create.title' | t }}</h2>
              <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="createDialog.close()"></button>
            </div>
            <div class="modal-body">
              <div class="row g-3">
                @if (createError()) {
                  <app-alert class="col-12 mb-0">{{ createError() }}</app-alert>
                }
                <div class="col-sm-8">
                  <label class="form-label" for="ws-new-name">{{ 'admin.workshops.name' | t }}</label>
                  <input id="ws-new-name" class="form-control" formControlName="name" autocomplete="off" [class.is-invalid]="invalid('name')" />
                </div>
                <div class="col-sm-4">
                  <label class="form-label" for="ws-new-nif">{{ 'common.nif' | t }}</label>
                  <input id="ws-new-nif" class="form-control" formControlName="nif" inputmode="numeric" maxlength="9" [class.is-invalid]="invalid('nif')" />
                  @if (invalid('nif')) {
                    <div class="invalid-feedback">{{ (form.controls.nif.hasError('nif') ? 'common.nifCheckDigit' : 'customers.nifInvalid') | t }}</div>
                  }
                </div>
                <div class="col-sm-6">
                  <label class="form-label" for="ws-new-country">{{ 'admin.workshops.country' | t }}</label>
                  <select id="ws-new-country" class="form-select" formControlName="country">
                    @for (c of countries(); track c.code) {
                      <option [value]="c.code">{{ c.label }}</option>
                    }
                  </select>
                </div>
                <div class="col-sm-6">
                  <label class="form-label" for="ws-new-tz">{{ 'admin.create.timezone' | t }}</label>
                  <select id="ws-new-tz" class="form-select" formControlName="timezone">
                    @for (z of zones; track z) {
                      <option [value]="z">{{ z }}</option>
                    }
                  </select>
                </div>
                <div class="col-sm-4">
                  <label class="form-label" for="ws-new-plan">{{ 'admin.workshops.plan' | t }}</label>
                  <select id="ws-new-plan" class="form-select" formControlName="plan">
                    @for (p of plans; track p) {
                      <option [value]="p">{{ planKey(p) | t }}</option>
                    }
                  </select>
                </div>
                <div class="col-sm-4">
                  <label class="form-label" for="ws-new-status">{{ 'admin.workshops.status' | t }}</label>
                  <select id="ws-new-status" class="form-select" formControlName="status">
                    @for (s of initialStatuses; track s) {
                      <option [value]="s">{{ statusKey(s) | t }}</option>
                    }
                  </select>
                </div>
                <div class="col-sm-4">
                  <label class="form-label" for="ws-new-until">{{ 'admin.subscription.until' | t }}</label>
                  <input id="ws-new-until" class="form-control" type="date" formControlName="subscriptionUntil" />
                </div>
                <div class="col-12">
                  <label class="form-label" for="ws-new-slug">{{ 'branding.slug' | t }}</label>
                  <input id="ws-new-slug" class="form-control font-monospace" formControlName="slug" maxlength="40" [class.is-invalid]="invalid('slug')" />
                  @if (invalid('slug')) {
                    <div class="invalid-feedback">{{ 'branding.slugInvalid' | t }}</div>
                  } @else {
                    <div class="form-text">{{ 'admin.create.slugHelp' | t }}</div>
                  }
                </div>
                <div class="col-12"><h3 class="h6 mb-0 mt-2">{{ 'admin.create.owner' | t }}</h3></div>
                <div class="col-sm-6">
                  <label class="form-label" for="ws-new-owner">{{ 'common.name' | t }}</label>
                  <input id="ws-new-owner" class="form-control" formControlName="ownerName" autocomplete="off" [class.is-invalid]="invalid('ownerName')" />
                </div>
                <div class="col-sm-6">
                  <label class="form-label" for="ws-new-email">{{ 'common.email' | t }}</label>
                  <input id="ws-new-email" class="form-control" type="email" formControlName="ownerEmail" autocomplete="off" [class.is-invalid]="invalid('ownerEmail')" />
                  <div class="form-text">{{ 'admin.create.ownerHelp' | t }}</div>
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="createDialog.close()">{{ 'backoffice.common.cancel' | t }}</button>
              <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                }
                {{ 'admin.create.submit' | t }}
              </button>
            </div>
          </form>
        }
      </div>
    </dialog>
  `,
})
export class WorkshopList implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);
  private readonly createDialog = viewChild.required<ElementRef<HTMLDialogElement>>('createDialog');

  protected readonly planKey = planKey;
  protected readonly statusKey = statusKey;
  protected readonly statusBadge = STATUS_BADGE;
  protected readonly plans = PLANS;
  protected readonly initialStatuses = INITIAL_STATUSES;
  protected readonly zones = timeZones('Europe/Lisbon');

  protected readonly workshops = signal<AdminWorkshop[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = workshopForm();
  protected readonly busy = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected readonly created = signal<ProvisionedWorkshop | null>(null);

  protected readonly countries = computed(() => {
    const names = new Intl.DisplayNames([this.i18n.lang()], { type: 'region' });
    return COUNTRIES.map((code) => ({ code, label: names.of(code) ?? code })).sort((a, b) => a.label.localeCompare(b.label));
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.workshops().subscribe({
      next: (list) => {
        this.workshops.set(list);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
      },
    });
  }

  protected invalid(name: keyof ReturnType<typeof workshopForm>['controls']): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  protected openCreate(): void {
    this.form.reset();
    this.created.set(null);
    this.createError.set(null);
    const dialog = this.createDialog().nativeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  }

  protected create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.createError.set(null);
    this.api.create(toCreateRequest(this.form)).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.created.set(result);
        this.workshops.update((list) => [result.workshop, ...list]);
      },
      error: (e) => {
        this.busy.set(false);
        // Mark the field and still say why, since the form has no per-field server message.
        applyProblem(this.form, e, { 'email-taken': 'ownerEmail', 'slug-taken': 'slug' });
        this.createError.set(this.i18n.error(e));
      },
    });
  }

  protected openWorkshop(id: string): void {
    this.createDialog().nativeElement.close();
    this.router.navigate(['/admin/workshops', id]);
  }
}
