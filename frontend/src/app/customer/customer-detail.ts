import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Customer, Vehicle } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { formatAddress } from './address';

/** Fuel types as stored on the vehicle; the labels are translated. */
const FUELS = ['Gasoline', 'Diesel', 'Hybrid', 'Electric', 'LPG'] as const;

@Component({
  selector: 'app-customer-detail',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <a routerLink="/customers" class="muted">{{ 'customers.back' | t }}</a>
    @if (customer(); as c) {
      <h1>{{ c.name }}</h1>
      <p class="muted">{{ 'common.nif' | t }} {{ c.nif || '—' }} · {{ c.phone || ('customers.noPhone' | t) }} · {{ c.email || ('customers.noEmail' | t) }}</p>
      <p class="muted">{{ address() || ('customers.noAddress' | t) }}</p>
    }
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <h2>{{ 'vehicles.title' | t }}</h2>
    <table class="table">
      <thead><tr><th>{{ 'vehicles.plate' | t }}</th><th>{{ 'vehicles.vehicle' | t }}</th><th>{{ 'vehicles.year' | t }}</th><th>{{ 'vehicles.mileage' | t }}</th><th></th></tr></thead>
      <tbody>
        @for (v of vehicles(); track v.id) {
          <tr>
            <td class="plate">{{ v.plate }}</td>
            <td>{{ v.make }} {{ v.model }}</td>
            <td>{{ v.year }}</td>
            <td>{{ v.mileage ?? '—' }} km</td>
            <td><a [routerLink]="['/vehicles', v.id]">{{ 'vehicles.history' | t }}</a> · <a [routerLink]="['/check-in']" [queryParams]="{ plate: v.plate }">{{ 'nav.checkIn' | t }}</a></td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="muted">{{ 'vehicles.empty' | t }}</td></tr>
        }
      </tbody>
    </table>

    <h3>{{ 'vehicles.add' | t }}</h3>
    <form [formGroup]="form" (ngSubmit)="addVehicle()" class="card grid">
      <label>{{ 'vehicles.plate' | t }} <input formControlName="plate" placeholder="AA-00-AA" /></label>
      <label>{{ 'vehicles.vin' | t }} <input formControlName="vin" maxlength="17" /></label>
      <label>{{ 'vehicles.make' | t }} <input formControlName="make" /></label>
      <label>{{ 'vehicles.model' | t }} <input formControlName="model" /></label>
      <label>{{ 'vehicles.year' | t }} <input formControlName="year" type="number" /></label>
      <label>{{ 'vehicles.fuel' | t }}
        <select formControlName="fuelType">
          <option value="">—</option>
          @for (f of fuels; track f.value) {
            <option [value]="f.value">{{ f.key | t }}</option>
          }
        </select>
      </label>
      <label>{{ 'vehicles.mileageKm' | t }} <input formControlName="mileage" type="number" /></label>
      <button type="submit" [disabled]="form.invalid">{{ 'vehicles.add' | t }}</button>
    </form>
  `,
})
export class CustomerDetail implements OnInit {
  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly fuels = FUELS.map((value) => ({ value, key: `fuel.${value}` as const }));
  protected readonly customer = signal<Customer | null>(null);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly address = computed(() => {
    const a = this.customer()?.address;
    if (!a) return null;
    const names = new Intl.DisplayNames([this.i18n.lang()], { type: 'region' });
    return formatAddress(a, (code) => names.of(code) ?? code);
  });
  protected readonly form = inject(FormBuilder).group({
    plate: ['', Validators.required],
    vin: ['', [Validators.minLength(17), Validators.maxLength(17)]],
    make: [''],
    model: [''],
    year: [null as number | null],
    fuelType: [''],
    mileage: [null as number | null],
  });

  ngOnInit(): void {
    this.api.customer(this.id()).subscribe({ next: (c) => this.customer.set(c), error: (e) => this.error.set(this.i18n.error(e)) });
    this.api.vehiclesOf(this.id()).subscribe((v) => this.vehicles.set(v));
  }

  addVehicle(): void {
    const v = this.form.getRawValue();
    this.api
      .createVehicle({
        customerId: this.id(),
        plate: v.plate!,
        vin: v.vin || null,
        make: v.make || null,
        model: v.model || null,
        year: v.year,
        fuelType: v.fuelType || null,
        mileage: v.mileage,
      })
      .subscribe({
        next: (created) => {
          this.vehicles.update((list) => [...list, created]);
          this.form.reset();
        },
        error: (e) => this.error.set(this.i18n.error(e)),
      });
  }
}
