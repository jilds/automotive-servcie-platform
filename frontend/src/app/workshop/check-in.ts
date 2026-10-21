import { Component, computed, effect, inject, input, OnInit, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { User, Vehicle } from '../core/api/models';
import { applyProblem, fieldMessage, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { KmPipe } from '../shared/km.pipe';
import { Plate } from '../shared/plate';
import { formatPlate, plateMessage, registrationControls } from '../shared/plate-format';
import { countryOptions } from '../shared/registration-fields';
import { WorkshopCountry } from '../shared/workshop-country';

/** Problem codes that belong to one field of the check-in form, so they show next to it. */
export const CHECK_IN_CODE_FIELDS = { 'invalid-mechanic': 'assignedMechanicId' };

/** Opens a work order when a car arrives: find it by plate, record mileage, fuel and damage. */
@Component({
  selector: 'app-check-in',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, KmPipe, Alert, Icon, Plate],
  template: `
    <header class="mb-4">
      <h1 class="mb-0">{{ 'checkIn.title' | t }}</h1>
    </header>
    <form class="d-flex flex-wrap align-items-center gap-3 mb-4" (submit)="$event.preventDefault(); find(search.plate.value)">
      <select class="form-select form-select-lg w-auto" [formControl]="search.registrationCountry" [attr.aria-label]="'plate.country' | t">
        @for (c of countries(); track c.code) {
          <option [value]="c.code">{{ c.code }} · {{ c.name }}</option>
        }
      </select>
      <div class="plate-field" [class.is-invalid]="!!plateMessage()">
        <input
          [formControl]="search.plate"
          (blur)="tidy()"
          [attr.aria-label]="'vehicles.plate' | t"
          [attr.aria-invalid]="!!plateMessage()"
          aria-describedby="ci-plate-help"
          [placeholder]="'checkIn.platePlaceholder' | t"
          autocomplete="off"
          autocapitalize="characters"
          spellcheck="false"
        />
      </div>
      <button type="submit" class="btn btn-primary btn-lg"><app-icon name="search" [size]="18" />{{ 'checkIn.find' | t }}</button>
      @if (plateMessage(); as m) {
        <div class="invalid-feedback d-block w-100 mt-0" id="ci-plate-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
      }
    </form>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    @if (notFound()) {
      <app-alert tone="warning">{{ 'checkIn.notFound' | t }} <a routerLink="/customers">{{ 'checkIn.registerFirst' | t }}</a></app-alert>
    }

    @if (vehicle(); as v) {
      <div class="card shadow-sm mb-3">
        <div class="card-body d-flex flex-wrap align-items-center gap-2 column-gap-3 fs-5">
          <app-plate [plate]="v.plateDisplay ?? v.plate" [era]="v.plateEra" [country]="v.registrationCountry" [registrationDate]="v.registrationDate" />
          <strong>{{ v.make }} {{ v.model }} {{ v.year }}</strong>
          <span class="d-inline-flex align-items-center gap-1 text-body-secondary fs-6"><app-icon name="gauge" [size]="16" /> {{ 'checkIn.lastMileage' | t: { km: (v.mileage | km: lang()) } }}</span>
        </div>
      </div>
      <form [formGroup]="form" (ngSubmit)="submit(v)" class="card shadow-sm mb-4">
        <div class="card-body">
          <div class="row g-3">
            <div class="col-sm-6 col-lg-4">
              <label class="form-label" for="ci-mileage">{{ 'checkIn.mileageNow' | t }}</label>
              <input id="ci-mileage" class="form-control" type="number" formControlName="mileageIn" />
            </div>
            <div class="col-sm-6 col-lg-4">
              <label class="form-label" for="ci-fuel">{{ 'checkIn.fuelLevel' | t: { level: form.value.fuelLevel } }}</label>
              <input id="ci-fuel" class="form-range mt-2" type="range" min="0" max="8" formControlName="fuelLevel" />
            </div>
            <div class="col-sm-6 col-lg-4">
              <label class="form-label" for="ci-mechanic">{{ 'checkIn.mechanic' | t }}</label>
              <select
                id="ci-mechanic"
                class="form-select"
                formControlName="assignedMechanicId"
                [class.is-invalid]="!!mechanicMessage()"
                [attr.aria-invalid]="!!mechanicMessage()"
                aria-describedby="ci-mechanic-help"
              >
                <option [ngValue]="null">{{ 'checkIn.notAssigned' | t }}</option>
                @for (m of mechanics(); track m.id) {
                  <option [ngValue]="m.id">{{ m.name }}</option>
                }
              </select>
              @if (mechanicMessage(); as m) {
                <div class="invalid-feedback" id="ci-mechanic-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              }
            </div>
            <div class="col-12">
              <label class="form-label" for="ci-complaint">{{ 'checkIn.complaint' | t }}</label>
              <textarea id="ci-complaint" class="form-control" formControlName="complaint" rows="2"></textarea>
            </div>
            <div class="col-12">
              <label class="form-label" for="ci-damage">{{ 'checkIn.damage' | t }}</label>
              <textarea id="ci-damage" class="form-control" formControlName="damageNotes" rows="2" [placeholder]="'checkIn.damagePlaceholder' | t"></textarea>
            </div>
            <div class="col-12 d-flex justify-content-end">
              <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                }
                {{ 'checkIn.submit' | t }}
              </button>
            </div>
          </div>
        </div>
      </form>
    }
  `,
})
export class CheckIn implements OnInit {
  /** Optional ?plate= query parameter, set when coming from a customer's page. */
  readonly plate = input<string>();
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly lang = this.i18n.lang;
  private readonly workshop = inject(WorkshopCountry);
  /** Lookup by plate. A Portuguese plate is checked against the layouts, but the search still runs: plates saved
   * before the check may fit no layout. */
  protected readonly search = registrationControls(this.workshop.country(), { plateRequired: false });
  private readonly searchCountry = toSignal(this.search.registrationCountry.valueChanges, { initialValue: this.search.registrationCountry.value });
  protected readonly countries = computed(() => countryOptions(this.i18n.lang(), this.searchCountry()));
  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly mechanics = signal<User[]>([]);
  protected readonly notFound = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).group({
    mileageIn: [null as number | null],
    fuelLevel: [4],
    assignedMechanicId: [null as string | null],
    complaint: [''],
    damageNotes: [''],
  });

  constructor() {
    this.workshop.load();
    effect(() => {
      const country = this.workshop.country();
      const control = this.search.registrationCountry;
      if (control.pristine) untracked(() => control.setValue(country));
    });
  }

  ngOnInit(): void {
    const plate = this.plate();
    if (plate) {
      this.search.plate.setValue(formatPlate(plate, this.search.registrationCountry.value));
      this.find(plate);
    }
    // Only owners can list users; the front desk simply sees no mechanic list.
    this.api.users().subscribe({ next: (u) => this.mechanics.set(u.filter((x) => x.role === 'MECHANIC')), error: () => {} });
  }

  /** On blur: a Portuguese plate becomes `XX-XX-XX` upper case. */
  protected tidy(): void {
    const control = this.search.plate;
    const tidy = formatPlate(control.value, this.search.registrationCountry.value);
    if (tidy !== control.value) control.setValue(tidy);
  }

  protected plateMessage() {
    return plateMessage(this.search.plate);
  }

  protected mechanicMessage() {
    return fieldMessage(this.form.controls.assignedMechanicId);
  }

  find(plate: string): void {
    this.search.plate.markAsTouched();
    this.error.set(null);
    this.notFound.set(false);
    this.vehicle.set(null);
    if (!plate.trim()) return;
    this.api.vehicleByPlate(plate).subscribe({
      next: (v) => {
        this.vehicle.set(v);
        this.form.patchValue({ mileageIn: v.mileage });
      },
      error: (e) => (e.status === 404 ? this.notFound.set(true) : this.error.set(this.i18n.error(e))),
    });
  }

  submit(v: Vehicle): void {
    this.busy.set(true);
    const f = this.form.getRawValue();
    this.api.checkIn({ vehicleId: v.id, ...f, complaint: f.complaint || null, damageNotes: f.damageNotes || null }).subscribe({
      next: (wo) => this.router.navigate(['/work-orders', wo.id]),
      error: (e) => {
        const marked = applyProblem(this.form, e, CHECK_IN_CODE_FIELDS);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }
}
