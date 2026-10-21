import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { AuthService } from '../core/auth/auth.service';
import { Vehicle, WorkOrder } from '../core/api/models';
import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { VehicleEditDialog } from '../customer/vehicle-edit-dialog';
import { OwnerChange, VehicleOwnerDialog } from '../customer/vehicle-owner-dialog';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { KmPipe } from '../shared/km.pipe';
import { Plate } from '../shared/plate';
import { MoneyPipe } from '../shared/money.pipe';

/** Every work order a vehicle has had, newest first. */
@Component({
  selector: 'app-vehicle-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, MoneyPipe, KmPipe, TranslatePipe, Icon, Alert, Plate, VehicleEditDialog, VehicleOwnerDialog],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        @if (vehicle(); as v) {
          <h1 class="d-flex flex-wrap align-items-center gap-2 mb-0"><app-plate [plate]="v.plateDisplay ?? v.plate" [era]="v.plateEra" [country]="v.registrationCountry" [registrationDate]="v.registrationDate" /> {{ v.make }} {{ v.model }} {{ v.year }}</h1>
          <ul class="list-inline d-flex flex-wrap gap-2 column-gap-4 mt-3 mb-0 small text-body-secondary">
            <li>{{ 'vehicles.vin' | t }} {{ v.vin || '—' }}</li>
            <li class="d-inline-flex align-items-center gap-2"><app-icon name="gauge" [size]="16" />{{ v.mileage | km: lang() }}</li>
            @if (v.fuelType) {
              <li class="d-inline-flex align-items-center gap-2"><app-icon name="fuel" [size]="16" />{{ v.fuelType }}</li>
            }
            @if (canEdit()) {
              <li class="d-inline-flex align-items-center gap-2"><app-icon name="user" [size]="16" /><a [routerLink]="['/customers', v.customerId]">{{ 'vehicles.owner' | t }}</a></li>
            }
          </ul>
        } @else {
          <span class="placeholder-glow d-block" aria-hidden="true"><span class="placeholder placeholder-lg col-6"></span></span>
        }
      </div>
      @if (canEdit() && vehicle(); as v) {
        <div class="d-flex flex-wrap gap-2">
          <button type="button" class="btn btn-outline-secondary" (click)="vehicleDialog.open(v)">{{ 'vehicles.edit' | t }}</button>
          <button type="button" class="btn btn-outline-secondary" (click)="ownerDialog.open(v)">{{ 'vehicles.changeOwner' | t }}</button>
        </div>
      }
    </header>
    @if (notice(); as n) {
      <app-alert tone="success">
        {{ 'vehicles.ownerChanged' | t: { plate: n.vehicle.plateDisplay ?? n.vehicle.plate, from: n.from?.name ?? '—', to: n.to.name } }}
        <a [routerLink]="['/customers', n.to.id]">{{ 'vehicles.ownerOpen' | t }}</a>
      </app-alert>
    }
    <h2 class="mt-5 mb-3">{{ 'history.title' | t }}</h2>
    <div class="list-group shadow-sm">
      @for (w of history(); track w.id) {
        <a class="list-group-item list-group-item-action p-3" [routerLink]="['/work-orders', w.id]">
          <div class="d-flex flex-wrap align-items-center gap-2 column-gap-3 mb-2">
            <strong>#{{ w.number }}</strong>
            <span class="text-body-secondary small">{{ w.statusChangedAt | date: 'dd/MM/yyyy' }}</span>
            <span class="badge status-badge ms-auto" [attr.data-status]="w.status">{{ statusKey(w.status) | t }}</span>
          </div>
          <p class="mb-2">{{ w.complaint || ('history.noComplaint' | t) }}</p>
          <ul class="list-inline d-flex flex-wrap gap-2 column-gap-4 mb-0 small text-body-secondary">
            <li>{{ w.mileageIn | km: lang() }}</li>
            <li>{{ 'history.lines' | t: { count: w.lines.length } }}</li>
            @if (auth.canSeePrices()) {
              <li><strong class="text-body">{{ w.totalCents | money }}</strong></li>
            }
          </ul>
        </a>
      } @empty {
        <div class="list-group-item d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
          <app-icon name="history" [size]="36" class="text-body-tertiary" />
          <p class="fw-semibold text-body mb-0">{{ 'history.empty' | t }}</p>
        </div>
      }
    </div>

    <app-vehicle-edit-dialog #vehicleDialog (saved)="vehicle.set($event)" />
    <app-vehicle-owner-dialog #ownerDialog (transferred)="ownerChanged($event)" />
  `,
})
export class VehicleHistory implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly lang = inject(I18nService).lang;
  protected readonly statusKey = statusKey;
  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly history = signal<WorkOrder[]>([]);
  /** Set after the vehicle moved to another customer, to link to the new owner. */
  protected readonly notice = signal<OwnerChange | null>(null);
  /** Mechanics see the history but never an edit action on vehicle or customer data. */
  protected readonly canEdit = computed(() => this.auth.hasRole('OWNER', 'FRONT_DESK'));

  ngOnInit(): void {
    this.api.vehicle(this.id()).subscribe((v) => this.vehicle.set(v));
    this.api.vehicleHistory(this.id()).subscribe((h) => this.history.set(h));
  }

  protected ownerChanged(change: OwnerChange): void {
    this.vehicle.set(change.vehicle);
    this.notice.set(change);
  }
}
