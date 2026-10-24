import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { AuthService } from '../core/auth/auth.service';
import { LineType, WorkOrder, WorkOrderStatus } from '../core/api/models';
import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { KmPipe } from '../shared/km.pipe';
import { Plate } from '../shared/plate';
import { MoneyPipe } from '../shared/money.pipe';
import { CheckInEditDialog } from './check-in-edit-dialog';
import { CataloguePicker } from '../catalogue/catalogue-picker';
import { StockPicker } from '../stock/stock-picker';
import { MechanicCard } from './mechanic-card';

@Component({
  selector: 'app-work-order-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe, KmPipe, DecimalPipe, TranslatePipe, Alert, Icon, Plate, CheckInEditDialog, MechanicCard, CataloguePicker, StockPicker],
  template: `
    <a routerLink="/board" class="d-inline-block mb-2 small fw-medium text-body-secondary text-decoration-none">{{ 'workOrder.back' | t }}</a>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    @if (wo(); as w) {
      <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
        <div class="me-auto">
          <h1 class="d-flex flex-wrap align-items-center gap-2 column-gap-3 mb-0">
            #{{ w.number }} <app-plate [plate]="w.plateDisplay ?? w.plate" [era]="w.plateEra" [country]="w.registrationCountry" [registrationDate]="w.registrationDate" />
            <span class="badge status-badge" [attr.data-status]="w.status">{{ statusKey(w.status) | t }}</span>
          </h1>
          <ul class="list-inline d-flex flex-wrap gap-2 column-gap-4 mt-3 mb-0 small text-body-secondary">
            <li class="d-inline-flex align-items-center gap-2"><app-icon name="gauge" [size]="16" />{{ w.mileageIn | km: i18n.lang() }}</li>
            <li class="d-inline-flex align-items-center gap-2"><app-icon name="fuel" [size]="16" />{{ 'workOrder.fuel' | t: { level: w.fuelLevel ?? '—' } }}</li>
            <li class="d-inline-flex align-items-center gap-2"><app-icon name="history" [size]="16" /><a [routerLink]="['/vehicles', w.vehicleId]">{{ 'workOrder.serviceHistory' | t }}</a></li>
          </ul>
        </div>
        @if (w.allowedNext.length) {
          <div class="d-flex flex-wrap gap-2">
            @for (s of w.allowedNext; track s) {
              <button type="button" class="btn" [class.btn-primary]="s !== 'CANCELLED'" [class.btn-outline-secondary]="s === 'CANCELLED'" (click)="move(s)">
                {{ 'workOrder.moveTo' | t: { status: i18n.t(statusKey(s)) } }}
                @if (s !== 'CANCELLED') {
                  <app-icon name="arrow-right" [size]="16" />
                }
              </button>
            }
          </div>
        }
      </header>
      @if (w.readOnly) {
        <app-alert tone="info">{{ 'workOrder.readOnly' | t }}</app-alert>
      }
      @if (notice()) {
        <app-alert tone="warning">{{ notice() }}</app-alert>
      }
      @if (canEditCheckIn()) {
        <section class="card shadow-sm mb-4">
          <div class="card-body">
            <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
              <h2 class="card-title me-auto mb-0">{{ 'workOrder.checkInData' | t }}</h2>
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="checkInDialog.open(w)">{{ 'workOrder.editCheckIn' | t }}</button>
            </div>
            <dl class="row g-3 mb-0">
              <div class="col-md-6"><dt class="small text-body-secondary fw-semibold">{{ 'workOrder.complaint' | t }}</dt><dd class="mb-0 text-break">{{ w.complaint || '—' }}</dd></div>
              <div class="col-md-6"><dt class="small text-body-secondary fw-semibold">{{ 'workOrder.damage' | t }}</dt><dd class="mb-0 text-break">{{ w.damageNotes || '—' }}</dd></div>
            </dl>
          </div>
        </section>
      } @else if (w.complaint || w.damageNotes) {
        <div class="card shadow-sm mb-4">
          <div class="card-body">
            <dl class="row g-3 mb-0">
              @if (w.complaint) {
                <div class="col-md-6"><dt class="small text-body-secondary fw-semibold">{{ 'workOrder.complaint' | t }}</dt><dd class="mb-0 text-break">{{ w.complaint }}</dd></div>
              }
              @if (w.damageNotes) {
                <div class="col-md-6"><dt class="small text-body-secondary fw-semibold">{{ 'workOrder.damage' | t }}</dt><dd class="mb-0 text-break">{{ w.damageNotes }}</dd></div>
              }
            </dl>
          </div>
        </div>
      }

      @if (auth.hasRole('OWNER', 'FRONT_DESK')) {
        <app-mechanic-card [workOrder]="w" (changed)="mechanicChanged($event)" />
      }

      <h2 class="mt-5 mb-3">{{ 'workOrder.lines' | t }}</h2>
      <div class="card table-stack mb-4">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>{{ 'workOrder.type' | t }}</th><th>{{ 'workOrder.description' | t }}</th><th class="text-end">{{ 'workOrder.qty' | t }}</th>
                @if (auth.canSeePrices()) {
                  <th class="text-end">{{ 'workOrder.unit' | t }}</th><th class="text-end">{{ 'workOrder.discount' | t }}</th><th class="text-end">{{ 'workOrder.vat' | t }}</th><th class="text-end">{{ 'workOrder.net' | t }}</th><th></th>
                }
              </tr>
            </thead>
            <tbody>
              @for (l of w.lines; track l.id) {
                <tr>
                  <td [attr.data-label]="'workOrder.type' | t">
                    @if (l.type === 'LABOR') {
                      <span class="badge bg-info-subtle text-info-emphasis">{{ 'line.LABOR' | t }}</span>
                    } @else {
                      <span class="badge bg-secondary-subtle text-secondary-emphasis">{{ 'line.PART' | t }}</span>
                    }
                  </td>
                  <td class="cell-title">
                    {{ l.description }}
                    @if (l.serviceItemId) {
                      <span class="badge rounded-pill bg-primary-subtle text-primary-emphasis fw-normal ms-1" [title]="'catalogue.lineBadgeTitle' | t">{{ 'catalogue.lineBadge' | t }}</span>
                    }
                  </td>
                  <td class="text-end" [attr.data-label]="'workOrder.qty' | t">{{ l.quantity | number: '1.0-2' }}</td>
                  @if (auth.canSeePrices()) {
                    <td class="text-end" [attr.data-label]="'workOrder.unit' | t">{{ l.unitPriceCents | money }}</td>
                    <td class="text-end" [attr.data-label]="'workOrder.discount' | t">{{ l.discountPercent }}%</td>
                    <td class="text-end" [attr.data-label]="'workOrder.vat' | t">{{ l.vatRate }}%</td>
                    <td class="text-end" [attr.data-label]="'workOrder.net' | t"><strong>{{ l.netCents | money }}</strong></td>
                    <td class="cell-actions">@if (editable(w.status)) {<button type="button" class="btn btn-sm btn-link text-danger-emphasis" (click)="removeLine(l.id)"><app-icon name="trash" [size]="16" />{{ 'workOrder.remove' | t }}</button>}</td>
                  }
                </tr>
              } @empty {
                <tr>
                  <td colspan="8" class="cell-full">
                    <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                      <app-icon name="wrench" [size]="36" class="text-body-tertiary" />
                      <p class="fw-semibold text-body mb-0">{{ 'workOrder.noLines' | t }}</p>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
            @if (auth.canSeePrices() && w.lines.length) {
              <tfoot>
                <tr class="bg-body-tertiary"><td colspan="6" class="text-end">{{ 'workOrder.net' | t }}</td><td class="text-end">{{ w.netCents | money }}</td><td></td></tr>
                <tr class="bg-body-tertiary"><td colspan="6" class="text-end">{{ 'workOrder.vat' | t }}</td><td class="text-end">{{ w.vatCents | money }}</td><td></td></tr>
                <tr class="bg-body-tertiary fs-6"><td colspan="6" class="text-end"><strong>{{ 'workOrder.total' | t }}</strong></td><td class="text-end"><strong>{{ w.totalCents | money }}</strong></td><td></td></tr>
              </tfoot>
            }
          </table>
        </div>
      </div>

      @if (auth.canSeePrices() && editable(w.status)) {
        <h3 class="mt-4 mb-3">{{ 'workOrder.addLine' | t }}</h3>
        <app-catalogue-picker [workOrderId]="w.id" (added)="catalogueAdded($event)" />
        <app-stock-picker [workOrderId]="w.id" (added)="catalogueAdded($event)" />
        <form [formGroup]="lineForm" (ngSubmit)="addLine()" class="card shadow-sm mb-4">
          <div class="card-body">
            <div class="row g-3">
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="wl-type">{{ 'workOrder.type' | t }}</label>
                <select id="wl-type" class="form-select" formControlName="type"><option value="LABOR">{{ 'line.LABOR' | t }}</option><option value="PART">{{ 'line.PART' | t }}</option></select>
              </div>
              <div class="col-sm-6 col-lg-9">
                <label class="form-label" for="wl-description">{{ 'workOrder.description' | t }}</label>
                <input id="wl-description" class="form-control" formControlName="description"
                  [class.is-invalid]="lineForm.controls.description.invalid && lineForm.controls.description.touched" />
              </div>
              <div class="col-6 col-lg-3">
                <label class="form-label" for="wl-quantity">{{ 'workOrder.quantity' | t }}</label>
                <input id="wl-quantity" class="form-control" type="number" step="0.25" formControlName="quantity"
                  [class.is-invalid]="lineForm.controls.quantity.invalid && lineForm.controls.quantity.touched" />
              </div>
              <div class="col-6 col-lg-3">
                <label class="form-label" for="wl-price">{{ 'workOrder.unitPrice' | t }}</label>
                <input id="wl-price" class="form-control" type="number" step="0.01" formControlName="unitPrice"
                  [class.is-invalid]="lineForm.controls.unitPrice.invalid && lineForm.controls.unitPrice.touched" />
              </div>
              <div class="col-6 col-lg-3">
                <label class="form-label" for="wl-discount">{{ 'workOrder.discountPercent' | t }}</label>
                <input id="wl-discount" class="form-control" type="number" formControlName="discountPercent"
                  [class.is-invalid]="lineForm.controls.discountPercent.invalid && lineForm.controls.discountPercent.touched" />
              </div>
              <div class="col-6 col-lg-3">
                <label class="form-label" for="wl-vat">{{ 'workOrder.vatPercent' | t }}</label>
                <input id="wl-vat" class="form-control" type="number" formControlName="vatRate"
                  [class.is-invalid]="lineForm.controls.vatRate.invalid && lineForm.controls.vatRate.touched" />
              </div>
              <div class="col-12 d-flex justify-content-end">
                <button type="submit" class="btn btn-primary" [disabled]="lineForm.invalid"><app-icon name="plus" [size]="16" />{{ 'workOrder.addLine' | t }}</button>
              </div>
            </div>
          </div>
        </form>
      }
    }
    <app-check-in-edit-dialog #checkInDialog (saved)="checkInSaved($event)" (conflict)="checkInConflict()" />
  `,
})
export class WorkOrderDetail implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly statusKey = statusKey;
  protected readonly wo = signal<WorkOrder | null>(null);
  protected readonly error = signal<string | null>(null);
  /** Warning shown after the order was reloaded because a check-in edit hit a conflict. */
  protected readonly notice = signal<string | null>(null);
  /** Same rule as the backend: front office only, and only while the order is still open for changes. */
  protected readonly canEditCheckIn = computed(() => {
    const w = this.wo();
    return !!w && this.editable(w.status) && this.auth.hasRole('OWNER', 'FRONT_DESK');
  });
  protected readonly lineForm = inject(FormBuilder).nonNullable.group({
    type: ['LABOR' as LineType],
    description: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
    unitPrice: [0, [Validators.required, Validators.min(0)]],
    discountPercent: [0, [Validators.min(0), Validators.max(100)]],
    vatRate: [23, [Validators.required, Validators.min(0), Validators.max(100)]],
  });

  ngOnInit(): void {
    this.reload();
  }

  protected checkInSaved(w: WorkOrder): void {
    this.notice.set(null);
    this.wo.set(w);
  }

  protected catalogueAdded(w: WorkOrder): void {
    this.error.set(null);
    this.wo.set(w);
  }

  protected mechanicChanged(w: WorkOrder): void {
    this.notice.set(null);
    this.wo.set(w);
  }

  protected checkInConflict(): void {
    this.notice.set(this.i18n.t('workOrder.checkInConflict'));
    this.reload();
  }

  private reload(): void {
    this.api.workOrder(this.id()).subscribe({ next: (w) => this.wo.set(w), error: (e) => this.error.set(this.i18n.error(e)) });
  }

  editable(status: WorkOrderStatus): boolean {
    return ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS'].includes(status);
  }

  move(status: WorkOrderStatus): void {
    this.update(this.api.move(this.id(), status));
  }

  addLine(): void {
    const { unitPrice, ...rest } = this.lineForm.getRawValue();
    this.update(this.api.addLine(this.id(), { ...rest, unitPriceCents: Math.round(unitPrice * 100) }), () =>
      this.lineForm.patchValue({ description: '', quantity: 1, unitPrice: 0, discountPercent: 0 }),
    );
  }

  removeLine(lineId: string): void {
    this.update(this.api.removeLine(this.id(), lineId));
  }

  private update(call: ReturnType<ApiService['workOrder']>, done?: () => void): void {
    this.error.set(null);
    call.subscribe({
      next: (w) => {
        this.wo.set(w);
        done?.();
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
