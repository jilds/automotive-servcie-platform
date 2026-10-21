import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Customer, Vehicle } from '../core/api/models';
import { applyProblem, shownOnFields } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { KmPipe } from '../shared/km.pipe';
import { Plate } from '../shared/plate';
import { registrationControls } from '../shared/plate-format';
import { RegistrationFields } from '../shared/registration-fields';
import { WorkshopCountry } from '../shared/workshop-country';
import { formatAddress } from './address';
import { CustomerEditDialog } from './customer-edit-dialog';
import { FUELS } from './vehicle-fields';
import { VEHICLE_CODE_FIELDS, VehicleEditDialog } from './vehicle-edit-dialog';
import { OwnerChange, VehicleOwnerDialog } from './vehicle-owner-dialog';

@Component({
  selector: 'app-customer-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, KmPipe, Alert, Icon, Plate, RegistrationFields, CustomerEditDialog, VehicleEditDialog, VehicleOwnerDialog],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <a routerLink="/customers" class="d-inline-block mb-2 small fw-medium text-body-secondary text-decoration-none">{{ 'customers.back' | t }}</a>
        @if (customer(); as c) {
          <h1 class="mb-0 text-break">{{ c.name }}</h1>
        } @else if (!error()) {
          <span class="placeholder-glow d-block" aria-hidden="true"><span class="placeholder placeholder-lg col-6"></span></span>
        }
      </div>
      @if (canEdit() && customer(); as c) {
        <button type="button" class="btn btn-outline-secondary" (click)="customerDialog.open(c)">{{ 'customers.edit' | t }}</button>
      }
    </header>
    @if (notice(); as n) {
      <app-alert tone="success">
        {{ 'vehicles.ownerChanged' | t: { plate: n.vehicle.plateDisplay ?? n.vehicle.plate, from: n.from?.name ?? '—', to: n.to.name } }}
        <a [routerLink]="['/customers', n.to.id]">{{ 'vehicles.ownerOpen' | t }}</a>
      </app-alert>
    }
    @if (customer(); as c) {
      <div class="card shadow-sm mb-4">
        <div class="card-body"><dl class="row g-3 mb-0">
          <div class="col-sm-6 col-xl-4"><dt class="small text-body-secondary fw-semibold">{{ 'common.nif' | t }}</dt><dd class="mb-0 text-break">{{ c.nif || '—' }}</dd></div>
          <div class="col-sm-6 col-xl-4"><dt class="small text-body-secondary fw-semibold">{{ 'common.phone' | t }}</dt><dd class="mb-0 text-break">{{ c.phone || ('customers.noPhone' | t) }}</dd></div>
          <div class="col-sm-6 col-xl-4"><dt class="small text-body-secondary fw-semibold">{{ 'common.email' | t }}</dt><dd class="mb-0 text-break">{{ c.email || ('customers.noEmail' | t) }}</dd></div>
          <div class="col-sm-6 col-xl-4"><dt class="small text-body-secondary fw-semibold">{{ 'address.title' | t }}</dt><dd class="mb-0 text-break">{{ address() || ('customers.noAddress' | t) }}</dd></div>
          <div class="col-sm-6 col-xl-4"><dt class="small text-body-secondary fw-semibold">{{ 'customers.marketing' | t }}</dt><dd class="mb-0">{{ (c.marketingConsent ? 'common.yes' : 'common.no') | t }}</dd></div>
        </dl></div>
      </div>
    }
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <h2 class="mt-5 mb-3">{{ 'vehicles.title' | t }}</h2>
    <div class="card table-stack mb-4">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead><tr><th>{{ 'vehicles.plate' | t }}</th><th>{{ 'vehicles.vehicle' | t }}</th><th>{{ 'vehicles.year' | t }}</th><th class="text-end">{{ 'vehicles.mileage' | t }}</th><th></th></tr></thead>
          <tbody>
            @for (v of vehicles(); track v.id) {
              <tr>
                <td class="cell-title"><app-plate [plate]="v.plateDisplay ?? v.plate" [era]="v.plateEra" [country]="v.registrationCountry" [registrationDate]="v.registrationDate" /></td>
                <td [attr.data-label]="'vehicles.vehicle' | t">{{ v.make }} {{ v.model }}</td>
                <td [attr.data-label]="'vehicles.year' | t">{{ v.year }}</td>
                <td class="text-end" [attr.data-label]="'vehicles.mileage' | t">{{ v.mileage | km: lang() }}</td>
                <td class="cell-actions">
                  <a class="btn btn-sm btn-link" [routerLink]="['/vehicles', v.id]">{{ 'vehicles.history' | t }}</a>
                  <a class="btn btn-sm btn-link" [routerLink]="['/check-in']" [queryParams]="{ plate: v.plate }">{{ 'nav.checkIn' | t }}</a>
                  @if (canEdit()) {
                    <button type="button" class="btn btn-sm btn-link" (click)="vehicleDialog.open(v)">{{ 'common.edit' | t }}</button>
                    <button type="button" class="btn btn-sm btn-link" (click)="ownerDialog.open(v, customer())">{{ 'vehicles.changeOwner' | t }}</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="cell-full">
                  <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                    <app-icon name="car" [size]="36" class="text-body-tertiary" />
                    <p class="fw-semibold text-body mb-0">{{ 'vehicles.empty' | t }}</p>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    <h2 class="mt-5 mb-3">{{ 'vehicles.add' | t }}</h2>
    <form [formGroup]="form" (ngSubmit)="addVehicle()" class="card shadow-sm mb-4">
      <div class="card-body">
        <div class="row g-3">
        <app-registration-fields [controls]="form.controls" prefix="va" col="col-sm-6 col-lg-3" />
        <div class="col-sm-6 col-lg-3">
          <label class="form-label" for="va-vin">{{ 'vehicles.vin' | t }}</label>
          <input id="va-vin" class="form-control" formControlName="vin" maxlength="17" [class.is-invalid]="form.controls.vin.invalid && form.controls.vin.touched" />
        </div>
        <div class="col-sm-6 col-lg-3">
          <label class="form-label" for="va-make">{{ 'vehicles.make' | t }}</label>
          <input id="va-make" class="form-control" formControlName="make" />
        </div>
        <div class="col-sm-6 col-lg-3">
          <label class="form-label" for="va-model">{{ 'vehicles.model' | t }}</label>
          <input id="va-model" class="form-control" formControlName="model" />
        </div>
        <div class="col-sm-6 col-lg-3">
          <label class="form-label" for="va-year">{{ 'vehicles.year' | t }}</label>
          <input id="va-year" class="form-control" formControlName="year" type="number" />
        </div>
        <div class="col-sm-6 col-lg-3">
          <label class="form-label" for="va-fuel">{{ 'vehicles.fuel' | t }}</label>
          <select id="va-fuel" class="form-select" formControlName="fuelType">
            <option value="">—</option>
            @for (f of fuels; track f.value) {
              <option [value]="f.value">{{ f.key | t }}</option>
            }
          </select>
        </div>
        <div class="col-sm-6 col-lg-3">
          <label class="form-label" for="va-mileage">{{ 'vehicles.mileageKm' | t }}</label>
          <input id="va-mileage" class="form-control" formControlName="mileage" type="number" />
        </div>
        <div class="col-12 d-flex justify-content-end">
          <button type="submit" class="btn btn-primary" [disabled]="form.invalid"><app-icon name="plus" [size]="16" />{{ 'vehicles.add' | t }}</button>
        </div>
        </div>
      </div>
    </form>

    <app-customer-edit-dialog #customerDialog (saved)="customer.set($event)" />
    <app-vehicle-edit-dialog #vehicleDialog (saved)="replaceVehicle($event)" />
    <app-vehicle-owner-dialog #ownerDialog (transferred)="ownerChanged($event)" />
  `,
})
export class CustomerDetail {
  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly lang = this.i18n.lang;
  private readonly auth = inject(AuthService);
  private readonly workshop = inject(WorkshopCountry);
  protected readonly fuels = FUELS.map((value) => ({ value, key: `fuel.${value}` as const }));
  protected readonly customer = signal<Customer | null>(null);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly error = signal<string | null>(null);
  /** Set after a vehicle moved to another customer, to link to the new owner. */
  protected readonly notice = signal<OwnerChange | null>(null);
  /** Mechanics never get an edit action on customer data. */
  protected readonly canEdit = computed(() => this.auth.hasRole('OWNER', 'FRONT_DESK'));
  protected readonly address = computed(() => {
    const a = this.customer()?.address;
    if (!a) return null;
    const names = new Intl.DisplayNames([this.i18n.lang()], { type: 'region' });
    return formatAddress(a, (code) => names.of(code) ?? code);
  });
  protected readonly form = inject(FormBuilder).nonNullable.group({
    ...registrationControls(this.workshop.country()),
    vin: ['', [Validators.minLength(17), Validators.maxLength(17)]],
    make: [''],
    model: [''],
    year: [null as number | null],
    fuelType: [''],
    mileage: [null as number | null],
  });
  constructor() {
    // The route reuses this component when following a link to another customer, so load on every id change.
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
    // New vehicles default to the workshop's country, once it is known, unless the user already picked one.
    this.workshop.load();
    effect(() => {
      const country = this.workshop.country();
      const control = this.form.controls.registrationCountry;
      if (control.pristine) untracked(() => control.setValue(country));
    });
  }

  addVehicle(): void {
    const v = this.form.getRawValue();
    this.api
      .createVehicle({
        customerId: this.id(),
        plate: v.plate.trim(),
        vin: v.vin.trim() || null,
        make: v.make.trim() || null,
        model: v.model.trim() || null,
        year: v.year,
        fuelType: v.fuelType || null,
        mileage: v.mileage,
        registrationCountry: v.registrationCountry.toUpperCase(),
        registrationDate: v.registrationDate || null,
      })
      .subscribe({
        next: (created) => {
          this.vehicles.update((list) => [...list, created]);
          this.form.reset({ registrationCountry: this.workshop.country() });
        },
        error: (e) => {
          const marked = applyProblem(this.form, e, VEHICLE_CODE_FIELDS);
          this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
        },
      });
  }

  protected replaceVehicle(updated: Vehicle): void {
    this.vehicles.update((list) => list.map((v) => (v.id === updated.id ? updated : v)));
  }

  protected ownerChanged(change: OwnerChange): void {
    this.vehicles.update((list) => list.filter((v) => v.id !== change.vehicle.id));
    this.notice.set(change);
  }

  private load(id: string): void {
    this.customer.set(null);
    this.vehicles.set([]);
    this.error.set(null);
    this.notice.set(null);
    this.api.customer(id).subscribe({ next: (c) => this.customer.set(c), error: (e) => this.error.set(this.i18n.error(e)) });
    this.api.vehiclesOf(id).subscribe((v) => this.vehicles.set(v));
  }
}
