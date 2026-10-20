import { DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { STATUS_LABELS, Vehicle, WorkOrder } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

/** Every work order a vehicle has had, newest first. */
@Component({
  selector: 'app-vehicle-history',
  imports: [RouterLink, DatePipe, MoneyPipe],
  template: `
    @if (vehicle(); as v) {
      <h1><span class="plate">{{ v.plate }}</span> {{ v.make }} {{ v.model }} {{ v.year }}</h1>
      <p class="muted">VIN {{ v.vin || '—' }} · {{ v.mileage ?? '—' }} km · {{ v.fuelType || '' }}</p>
    }
    <h2>Service history</h2>
    <div class="timeline">
      @for (w of history(); track w.id) {
        <a class="card" [routerLink]="['/work-orders', w.id]">
          <div class="row">
            <strong>#{{ w.number }}</strong>
            <span class="muted small">{{ w.statusChangedAt | date: 'dd/MM/yyyy' }}</span>
            <span class="spacer"></span>
            <span class="badge">{{ labels[w.status] }}</span>
          </div>
          <div>{{ w.complaint || 'No complaint recorded' }}</div>
          <div class="muted small">
            {{ w.mileageIn ?? '—' }} km · {{ w.lines.length }} lines
            @if (auth.canSeePrices()) { · {{ w.totalCents | money }} }
          </div>
        </a>
      } @empty {
        <p class="muted">No work orders yet.</p>
      }
    </div>
  `,
})
export class VehicleHistory implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly labels = STATUS_LABELS;
  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly history = signal<WorkOrder[]>([]);

  ngOnInit(): void {
    this.api.vehicle(this.id()).subscribe((v) => this.vehicle.set(v));
    this.api.vehicleHistory(this.id()).subscribe((h) => this.history.set(h));
  }
}
