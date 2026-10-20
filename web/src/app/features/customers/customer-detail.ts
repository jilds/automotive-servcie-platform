import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { Customer, Vehicle } from '../../core/models';
import { errorMessage } from '../../shared/errors';

@Component({
  selector: 'app-customer-detail',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <a routerLink="/customers" class="muted">← Customers</a>
    @if (customer(); as c) {
      <h1>{{ c.name }}</h1>
      <p class="muted">NIF {{ c.nif || '—' }} · {{ c.phone || 'no phone' }} · {{ c.email || 'no email' }}</p>
    }
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <h2>Vehicles</h2>
    <table class="table">
      <thead><tr><th>Plate</th><th>Vehicle</th><th>Year</th><th>Mileage</th><th></th></tr></thead>
      <tbody>
        @for (v of vehicles(); track v.id) {
          <tr>
            <td class="plate">{{ v.plate }}</td>
            <td>{{ v.make }} {{ v.model }}</td>
            <td>{{ v.year }}</td>
            <td>{{ v.mileage ?? '—' }} km</td>
            <td><a [routerLink]="['/vehicles', v.id]">History</a> · <a [routerLink]="['/check-in']" [queryParams]="{ plate: v.plate }">Check in</a></td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="muted">No vehicles yet</td></tr>
        }
      </tbody>
    </table>

    <h3>Add vehicle</h3>
    <form [formGroup]="form" (ngSubmit)="addVehicle()" class="card grid">
      <label>Plate <input formControlName="plate" placeholder="AA-00-AA" /></label>
      <label>VIN <input formControlName="vin" maxlength="17" /></label>
      <label>Make <input formControlName="make" /></label>
      <label>Model <input formControlName="model" /></label>
      <label>Year <input formControlName="year" type="number" /></label>
      <label>Fuel
        <select formControlName="fuelType">
          <option value="">—</option><option>Gasoline</option><option>Diesel</option><option>Hybrid</option><option>Electric</option><option>LPG</option>
        </select>
      </label>
      <label>Mileage (km) <input formControlName="mileage" type="number" /></label>
      <button type="submit" [disabled]="form.invalid">Add vehicle</button>
    </form>
  `,
})
export class CustomerDetail implements OnInit {
  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly customer = signal<Customer | null>(null);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly error = signal<string | null>(null);
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
    this.api.customer(this.id()).subscribe({ next: (c) => this.customer.set(c), error: (e) => this.error.set(errorMessage(e)) });
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
        error: (e) => this.error.set(errorMessage(e)),
      });
  }
}
