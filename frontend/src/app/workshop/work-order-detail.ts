import { DecimalPipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { AuthService } from '../core/auth/auth.service';
import { LineType, WorkOrder, WorkOrderStatus } from '../core/api/models';
import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { MoneyPipe } from '../shared/money.pipe';

@Component({
  selector: 'app-work-order-detail',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe, DecimalPipe, TranslatePipe],
  template: `
    <a routerLink="/board" class="muted">{{ 'workOrder.back' | t }}</a>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (wo(); as w) {
      <div class="row">
        <h1>#{{ w.number }} <span class="plate">{{ w.plate }}</span></h1>
        <span class="badge">{{ statusKey(w.status) | t }}</span>
      </div>
      <p class="muted">
        {{ w.mileageIn ?? '—' }} km · {{ 'workOrder.fuel' | t: { level: w.fuelLevel ?? '—' } }} ·
        <a [routerLink]="['/vehicles', w.vehicleId]">{{ 'workOrder.serviceHistory' | t }}</a>
      </p>
      @if (w.complaint) {
        <p><strong>{{ 'workOrder.complaint' | t }}</strong> {{ w.complaint }}</p>
      }
      @if (w.damageNotes) {
        <p><strong>{{ 'workOrder.damage' | t }}</strong> {{ w.damageNotes }}</p>
      }

      <div class="row wrap">
        @for (s of w.allowedNext; track s) {
          <button [class.secondary]="s === 'CANCELLED'" (click)="move(s)">{{ 'workOrder.moveTo' | t: { status: i18n.t(statusKey(s)) } }}</button>
        }
      </div>

      <h2>{{ 'workOrder.lines' | t }}</h2>
      <table class="table">
        <thead>
          <tr>
            <th>{{ 'workOrder.type' | t }}</th><th>{{ 'workOrder.description' | t }}</th><th class="num">{{ 'workOrder.qty' | t }}</th>
            @if (auth.canSeePrices()) {
              <th class="num">{{ 'workOrder.unit' | t }}</th><th class="num">{{ 'workOrder.discount' | t }}</th><th class="num">{{ 'workOrder.vat' | t }}</th><th class="num">{{ 'workOrder.net' | t }}</th><th></th>
            }
          </tr>
        </thead>
        <tbody>
          @for (l of w.lines; track l.id) {
            <tr>
              <td>{{ (l.type === 'LABOR' ? 'line.LABOR' : 'line.PART') | t }}</td>
              <td>{{ l.description }}</td>
              <td class="num">{{ l.quantity | number: '1.0-2' }}</td>
              @if (auth.canSeePrices()) {
                <td class="num">{{ l.unitPriceCents | money }}</td>
                <td class="num">{{ l.discountPercent }}%</td>
                <td class="num">{{ l.vatRate }}%</td>
                <td class="num">{{ l.netCents | money }}</td>
                <td>@if (editable(w.status)) { <button class="link" (click)="removeLine(l.id)">{{ 'workOrder.remove' | t }}</button> }</td>
              }
            </tr>
          } @empty {
            <tr><td colspan="8" class="muted">{{ 'workOrder.noLines' | t }}</td></tr>
          }
        </tbody>
        @if (auth.canSeePrices() && w.lines.length) {
          <tfoot>
            <tr><td colspan="6" class="num">{{ 'workOrder.net' | t }}</td><td class="num">{{ w.netCents | money }}</td><td></td></tr>
            <tr><td colspan="6" class="num">{{ 'workOrder.vat' | t }}</td><td class="num">{{ w.vatCents | money }}</td><td></td></tr>
            <tr><td colspan="6" class="num"><strong>{{ 'workOrder.total' | t }}</strong></td><td class="num"><strong>{{ w.totalCents | money }}</strong></td><td></td></tr>
          </tfoot>
        }
      </table>

      @if (auth.canSeePrices() && editable(w.status)) {
        <form [formGroup]="lineForm" (ngSubmit)="addLine()" class="card grid">
          <label>{{ 'workOrder.type' | t }}
            <select formControlName="type"><option value="LABOR">{{ 'line.LABOR' | t }}</option><option value="PART">{{ 'line.PART' | t }}</option></select>
          </label>
          <label class="wide">{{ 'workOrder.description' | t }} <input formControlName="description" /></label>
          <label>{{ 'workOrder.quantity' | t }} <input type="number" step="0.25" formControlName="quantity" /></label>
          <label>{{ 'workOrder.unitPrice' | t }} <input type="number" step="0.01" formControlName="unitPrice" /></label>
          <label>{{ 'workOrder.discountPercent' | t }} <input type="number" formControlName="discountPercent" /></label>
          <label>{{ 'workOrder.vatPercent' | t }} <input type="number" formControlName="vatRate" /></label>
          <button type="submit" [disabled]="lineForm.invalid">{{ 'workOrder.addLine' | t }}</button>
        </form>
      }
    }
  `,
})
export class WorkOrderDetail implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly statusKey = statusKey;
  protected readonly wo = signal<WorkOrder | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly lineForm = inject(FormBuilder).nonNullable.group({
    type: ['LABOR' as LineType],
    description: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
    unitPrice: [0, [Validators.required, Validators.min(0)]],
    discountPercent: [0, [Validators.min(0), Validators.max(100)]],
    vatRate: [23, [Validators.required, Validators.min(0), Validators.max(100)]],
  });

  ngOnInit(): void {
    this.api.workOrder(this.id()).subscribe({ next: (w) => this.wo.set(w), error: (e) => this.error.set(this.i18n.error(e)) });
  }

  editable(status: WorkOrderStatus): boolean {
    return ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS'].includes(status);
  }

  move(status: WorkOrderStatus): void {
    this.update(this.api.move(this.id(), status));
  }

  addLine(): void {
    const { unitPrice, ...rest } = this.lineForm.getRawValue();
    this.update(this.api.addLine(this.id(), { ...rest, unitPriceCents: Math.round(unitPrice * 100) }), () =>
      this.lineForm.patchValue({ description: '', quantity: 1, unitPrice: 0, discountPercent: 0 }),
    );
  }

  removeLine(lineId: string): void {
    this.update(this.api.removeLine(this.id(), lineId));
  }

  private update(call: ReturnType<ApiService['workOrder']>, done?: () => void): void {
    this.error.set(null);
    call.subscribe({
      next: (w) => {
        this.wo.set(w);
        done?.();
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
