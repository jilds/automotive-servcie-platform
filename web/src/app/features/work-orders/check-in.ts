import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { User, Vehicle } from '../../core/models';
import { errorMessage } from '../../shared/errors';

/** Opens a work order when a car arrives: find it by plate, record mileage, fuel and damage. */
@Component({
  selector: 'app-check-in',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <h1>Check in a vehicle</h1>
    <form class="row" (submit)="$event.preventDefault(); find(plateInput.value)">
      <input #plateInput class="plate-input" placeholder="Plate, e.g. AA-00-AA" [value]="plate() ?? ''" />
      <button type="submit">Find</button>
    </form>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (notFound()) {
      <p class="muted">No vehicle with that plate. <a routerLink="/customers">Register the customer and vehicle first.</a></p>
    }

    @if (vehicle(); as v) {
      <div class="card">
        <strong class="plate">{{ v.plate }}</strong> {{ v.make }} {{ v.model }} {{ v.year }}
        <span class="muted"> · last mileage {{ v.mileage ?? '—' }} km</span>
      </div>
      <form [formGroup]="form" (ngSubmit)="submit(v)" class="card grid">
        <label>Mileage now (km) <input type="number" formControlName="mileageIn" /></label>
        <label>Fuel level: {{ form.value.fuelLevel }}/8 <input type="range" min="0" max="8" formControlName="fuelLevel" /></label>
        <label>Mechanic
          <select formControlName="assignedMechanicId">
            <option [ngValue]="null">Not assigned</option>
            @for (m of mechanics(); track m.id) {
              <option [ngValue]="m.id">{{ m.name }}</option>
            }
          </select>
        </label>
        <label class="wide">Customer complaint <textarea formControlName="complaint" rows="2"></textarea></label>
        <label class="wide">Existing damage <textarea formControlName="damageNotes" rows="2" placeholder="Scratches, dents, broken lights"></textarea></label>
        <button type="submit" [disabled]="busy()">Open work order</button>
      </form>
    }
  `,
})
export class CheckIn implements OnInit {
  /** Optional ?plate= query parameter, set when coming from a customer's page. */
  readonly plate = input<string>();
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
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

  ngOnInit(): void {
    const plate = this.plate();
    if (plate) this.find(plate);
    // Only owners can list users; the front desk simply sees no mechanic list.
    this.api.users().subscribe({ next: (u) => this.mechanics.set(u.filter((x) => x.role === 'MECHANIC')), error: () => {} });
  }

  find(plate: string): void {
    this.error.set(null);
    this.notFound.set(false);
    this.vehicle.set(null);
    if (!plate.trim()) return;
    this.api.vehicleByPlate(plate).subscribe({
      next: (v) => {
        this.vehicle.set(v);
        this.form.patchValue({ mileageIn: v.mileage });
      },
      error: (e) => (e.status === 404 ? this.notFound.set(true) : this.error.set(errorMessage(e))),
    });
  }

  submit(v: Vehicle): void {
    this.busy.set(true);
    const f = this.form.getRawValue();
    this.api.checkIn({ vehicleId: v.id, ...f, complaint: f.complaint || null, damageNotes: f.damageNotes || null }).subscribe({
      next: (wo) => this.router.navigate(['/work-orders', wo.id]),
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
