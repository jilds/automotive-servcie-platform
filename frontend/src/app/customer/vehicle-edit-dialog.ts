import { ChangeDetectionStrategy, Component, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { Vehicle, VehicleUpdateRequest } from '../core/api/models';
import { applyProblem, fieldMessage, isStale, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { KmPipe } from '../shared/km.pipe';
import { registrationControls, StoredRegistration } from '../shared/plate-format';
import { RegistrationFields } from '../shared/registration-fields';
import { FuelOption, fuelOptions, VIN_LENGTH, YEAR_MAX, YEAR_MIN } from './vehicle-fields';

type Field = 'vin' | 'year' | 'mileage';

/** Problem codes that belong to one field, so they show next to it. */
export const VEHICLE_CODE_FIELDS = { 'duplicate-plate': 'plate', 'invalid-plate': 'plate', 'mileage-decrease': 'mileage' };

/** Modal form that edits a vehicle's details (PUT /vehicles/{id}). Open it with `open(vehicle)`. */
@Component({
  selector: 'app-vehicle-edit-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, KmPipe, Alert, Icon, RegistrationFields],
  template: `
    <dialog #dialog class="modal" aria-labelledby="vehicle-edit-title">
      <div class="modal-dialog">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="vehicle-edit-title">{{ 'vehicles.edit' | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            <div class="row g-3">
              @if (error()) {
                <app-alert class="col-12 mb-0">{{ error() }}</app-alert>
              }
              <app-registration-fields [controls]="form.controls" prefix="ve" />
              <div class="col-sm-6">
                <label class="form-label" for="ve-vin">{{ 'vehicles.vin' | t }}</label>
                <input id="ve-vin" class="form-control" formControlName="vin" [maxlength]="vinLength" autocapitalize="characters" spellcheck="false" [class.is-invalid]="!!message('vin')" [attr.aria-invalid]="!!message('vin')" />
                @if (message('vin'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="ve-make">{{ 'vehicles.make' | t }}</label>
                <input id="ve-make" class="form-control" formControlName="make" />
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="ve-model">{{ 'vehicles.model' | t }}</label>
                <input id="ve-model" class="form-control" formControlName="model" />
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="ve-year">{{ 'vehicles.year' | t }}</label>
                <input id="ve-year" class="form-control" formControlName="year" type="number" [min]="yearMin" [max]="yearMax" [class.is-invalid]="!!message('year')" [attr.aria-invalid]="!!message('year')" />
                @if (message('year'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="ve-fuel">{{ 'vehicles.fuel' | t }}</label>
                <select id="ve-fuel" class="form-select" formControlName="fuelType">
                  <option value="">—</option>
                  @for (f of fuels(); track f.value) {
                    <option [value]="f.value">{{ f.key ? (f.key | t) : f.value }}</option>
                  }
                </select>
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="ve-mileage">{{ 'vehicles.mileageKm' | t }}</label>
                <input id="ve-mileage" class="form-control" formControlName="mileage" type="number" min="0" [class.is-invalid]="!!message('mileage')" [attr.aria-invalid]="!!message('mileage')" />
                @if (message('mileage'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t: { km: ((recordedMileage() ?? 0) | km: lang()) } }}</div>
                } @else {
                  <div class="form-text">{{ 'vehicles.mileageKeep' | t }}</div>
                }
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'common.save' | t }}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class VehicleEditDialog {
  /** Emits the vehicle as saved by the server. */
  readonly saved = output<Vehicle>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly lang = this.i18n.lang;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private vehicleId: string | null = null;
  /** Version the form was opened with; the API refuses the save (409 `stale-data`) if the vehicle changed since. */
  private version = 0;
  /** The registration as opened: an unchanged plate is not re-checked (the API does the same). */
  private stored: StoredRegistration | null = null;
  protected readonly vinLength = VIN_LENGTH;
  protected readonly yearMin = YEAR_MIN;
  protected readonly yearMax = YEAR_MAX;
  protected readonly fuels = signal<FuelOption[]>(fuelOptions());
  protected readonly recordedMileage = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Mirrors the backend VehicleUpdateRequest rules; the mileage may not go below the recorded reading. */
  readonly form = inject(FormBuilder).nonNullable.group({
    ...registrationControls('PT', { stored: () => this.stored }),
    vin: ['', [Validators.minLength(VIN_LENGTH), Validators.maxLength(VIN_LENGTH)]],
    make: [''],
    model: [''],
    year: [null as number | null, [Validators.min(YEAR_MIN), Validators.max(YEAR_MAX)]],
    fuelType: [''],
    mileage: [null as number | null, Validators.min(0)],
  });

  open(vehicle: Vehicle): void {
    this.error.set(null);
    this.busy.set(false);
    this.fill(vehicle);
    this.dialog().nativeElement.showModal();
  }

  /** Puts the vehicle as stored into the form, with the version to send back. */
  private fill(vehicle: Vehicle): void {
    this.vehicleId = vehicle.id;
    this.version = vehicle.version;
    const country = vehicle.registrationCountry ?? 'PT';
    this.stored = { plate: vehicle.plate, country };
    this.fuels.set(fuelOptions(vehicle.fuelType));
    this.recordedMileage.set(vehicle.mileage);
    this.form.controls.mileage.setValidators(Validators.min(vehicle.mileage ?? 0));
    this.form.reset({
      registrationCountry: country,
      plate: vehicle.plateDisplay ?? vehicle.plate,
      registrationDate: vehicle.registrationDate ?? '',
      vin: vehicle.vin ?? '',
      make: vehicle.make ?? '',
      model: vehicle.model ?? '',
      year: vehicle.year,
      fuelType: vehicle.fuelType ?? '',
      mileage: vehicle.mileage,
    });
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  /** The PUT body: blank optional fields become null; a null mileage keeps the recorded one. */
  request(): VehicleUpdateRequest {
    const v = this.form.getRawValue();
    return {
      plate: v.plate.trim(),
      vin: v.vin.trim() || null,
      make: v.make.trim() || null,
      model: v.model.trim() || null,
      year: v.year,
      fuelType: v.fuelType || null,
      mileage: v.mileage,
      registrationCountry: v.registrationCountry.toUpperCase(),
      registrationDate: v.registrationDate || null,
      version: this.version,
    };
  }

  save(): void {
    const id = this.vehicleId;
    if (!id) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.updateVehicle(id, this.request()).subscribe({
      next: (vehicle) => {
        this.busy.set(false);
        this.saved.emit(vehicle);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        if (isStale(e)) {
          // Someone else saved it (or a check-in raised the mileage): show the stored values and say why.
          this.error.set(this.i18n.error(e));
          this.api.vehicle(id).subscribe({ next: (v) => this.fill(v), error: () => {} });
          return;
        }
        const marked = applyProblem(this.form, e, VEHICLE_CODE_FIELDS);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected message(field: Field) {
    return fieldMessage(this.form.controls[field], {
      minlength: 'vehicles.vinInvalid',
      maxlength: 'vehicles.vinInvalid',
      min: field === 'year' ? 'vehicles.yearInvalid' : 'vehicles.mileageBelowRecorded',
      max: 'vehicles.yearInvalid',
    });
  }
}
