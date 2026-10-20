import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { User, Vehicle } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/** Opens a work order when a car arrives: find it by plate, record mileage, fuel and damage. */
@Component({
  selector: 'app-check-in',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <h1>{{ 'checkIn.title' | t }}</h1>
    <form class="row" (submit)="$event.preventDefault(); find(plateInput.value)">
      <input #plateInput class="plate-input" [placeholder]="'checkIn.platePlaceholder' | t" [value]="plate() ?? ''" />
      <button type="submit">{{ 'checkIn.find' | t }}</button>
    </form>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (notFound()) {
      <p class="muted">{{ 'checkIn.notFound' | t }} <a routerLink="/customers">{{ 'checkIn.registerFirst' | t }}</a></p>
    }

    @if (vehicle(); as v) {
      <div class="card">
        <strong class="plate">{{ v.plate }}</strong> {{ v.make }} {{ v.model }} {{ v.year }}
        <span class="muted"> · {{ 'checkIn.lastMileage' | t: { km: v.mileage ?? '—' } }}</span>
      </div>
      <form [formGroup]="form" (ngSubmit)="submit(v)" class="card grid">
        <label>{{ 'checkIn.mileageNow' | t }} <input type="number" formControlName="mileageIn" /></label>
        <label>{{ 'checkIn.fuelLevel' | t: { level: form.value.fuelLevel } }} <input type="range" min="0" max="8" formControlName="fuelLevel" /></label>
        <label>{{ 'checkIn.mechanic' | t }}
          <select formControlName="assignedMechanicId">
            <option [ngValue]="null">{{ 'checkIn.notAssigned' | t }}</option>
            @for (m of mechanics(); track m.id) {
              <option [ngValue]="m.id">{{ m.name }}</option>
            }
          </select>
        </label>
        <label class="wide">{{ 'checkIn.complaint' | t }} <textarea formControlName="complaint" rows="2"></textarea></label>
        <label class="wide">{{ 'checkIn.damage' | t }} <textarea formControlName="damageNotes" rows="2" [placeholder]="'checkIn.damagePlaceholder' | t"></textarea></label>
        <button type="submit" [disabled]="busy()">{{ 'checkIn.submit' | t }}</button>
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
      error: (e) => (e.status === 404 ? this.notFound.set(true) : this.error.set(this.i18n.error(e))),
    });
  }

  submit(v: Vehicle): void {
    this.busy.set(true);
    const f = this.form.getRawValue();
    this.api.checkIn({ vehicleId: v.id, ...f, complaint: f.complaint || null, damageNotes: f.damageNotes || null }).subscribe({
      next: (wo) => this.router.navigate(['/work-orders', wo.id]),
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }
}
