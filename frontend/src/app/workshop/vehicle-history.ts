import { DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { AuthService } from '../core/auth/auth.service';
import { Vehicle, WorkOrder } from '../core/api/models';
import { statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { MoneyPipe } from '../shared/money.pipe';

/** Every work order a vehicle has had, newest first. */
@Component({
  selector: 'app-vehicle-history',
  imports: [RouterLink, DatePipe, MoneyPipe, TranslatePipe],
  template: `
    @if (vehicle(); as v) {
      <h1><span class="plate">{{ v.plate }}</span> {{ v.make }} {{ v.model }} {{ v.year }}</h1>
      <p class="muted">{{ 'vehicles.vin' | t }} {{ v.vin || '—' }} · {{ v.mileage ?? '—' }} km · {{ v.fuelType || '' }}</p>
    }
    <h2>{{ 'history.title' | t }}</h2>
    <div class="timeline">
      @for (w of history(); track w.id) {
        <a class="card" [routerLink]="['/work-orders', w.id]">
          <div class="row">
            <strong>#{{ w.number }}</strong>
            <span class="muted small">{{ w.statusChangedAt | date: 'dd/MM/yyyy' }}</span>
            <span class="spacer"></span>
            <span class="badge">{{ statusKey(w.status) | t }}</span>
          </div>
          <div>{{ w.complaint || ('history.noComplaint' | t) }}</div>
          <div class="muted small">
            {{ w.mileageIn ?? '—' }} km · {{ 'history.lines' | t: { count: w.lines.length } }}
            @if (auth.canSeePrices()) { · {{ w.totalCents | money }} }
          </div>
        </a>
      } @empty {
        <p class="muted">{{ 'history.empty' | t }}</p>
      }
    </div>
  `,
})
export class VehicleHistory implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly statusKey = statusKey;
  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly history = signal<WorkOrder[]>([]);

  ngOnInit(): void {
    this.api.vehicle(this.id()).subscribe((v) => this.vehicle.set(v));
    this.api.vehicleHistory(this.id()).subscribe((h) => this.history.set(h));
  }
}
