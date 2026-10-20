import { DecimalPipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LineType, STATUS_LABELS, WorkOrder, WorkOrderStatus } from '../../core/models';
import { errorMessage } from '../../shared/errors';
import { MoneyPipe } from '../../shared/money.pipe';

@Component({
  selector: 'app-work-order-detail',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe, DecimalPipe],
  template: `
    <a routerLink="/board" class="muted">← Board</a>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (wo(); as w) {
      <div class="row">
        <h1>#{{ w.number }} <span class="plate">{{ w.plate }}</span></h1>
        <span class="badge">{{ labels[w.status] }}</span>
      </div>
      <p class="muted">
        {{ w.mileageIn ?? '—' }} km · fuel {{ w.fuelLevel ?? '—' }}/8 ·
        <a [routerLink]="['/vehicles', w.vehicleId]">service history</a>
      </p>
      @if (w.complaint) {
        <p><strong>Complaint:</strong> {{ w.complaint }}</p>
      }
      @if (w.damageNotes) {
        <p><strong>Existing damage:</strong> {{ w.damageNotes }}</p>
      }

      <div class="row wrap">
        @for (s of w.allowedNext; track s) {
          <button [class.secondary]="s === 'CANCELLED'" (click)="move(s)">Move to {{ labels[s] }}</button>
        }
      </div>

      <h2>Lines</h2>
      <table class="table">
        <thead>
          <tr>
            <th>Type</th><th>Description</th><th class="num">Qty</th>
            @if (auth.canSeePrices()) {
              <th class="num">Unit</th><th class="num">Disc.</th><th class="num">VAT</th><th class="num">Net</th><th></th>
            }
          </tr>
        </thead>
        <tbody>
          @for (l of w.lines; track l.id) {
            <tr>
              <td>{{ l.type === 'LABOR' ? 'Labor' : 'Part' }}</td>
              <td>{{ l.description }}</td>
              <td class="num">{{ l.quantity | number: '1.0-2' }}</td>
              @if (auth.canSeePrices()) {
                <td class="num">{{ l.unitPriceCents | money }}</td>
                <td class="num">{{ l.discountPercent }}%</td>
                <td class="num">{{ l.vatRate }}%</td>
                <td class="num">{{ l.netCents | money }}</td>
                <td>@if (editable(w.status)) { <button class="link" (click)="removeLine(l.id)">Remove</button> }</td>
              }
            </tr>
          } @empty {
            <tr><td colspan="8" class="muted">No lines yet</td></tr>
          }
        </tbody>
        @if (auth.canSeePrices() && w.lines.length) {
          <tfoot>
            <tr><td colspan="6" class="num">Net</td><td class="num">{{ w.netCents | money }}</td><td></td></tr>
            <tr><td colspan="6" class="num">VAT</td><td class="num">{{ w.vatCents | money }}</td><td></td></tr>
            <tr><td colspan="6" class="num"><strong>Total</strong></td><td class="num"><strong>{{ w.totalCents | money }}</strong></td><td></td></tr>
          </tfoot>
        }
      </table>

      @if (auth.canSeePrices() && editable(w.status)) {
        <form [formGroup]="lineForm" (ngSubmit)="addLine()" class="card grid">
          <label>Type
            <select formControlName="type"><option value="LABOR">Labor</option><option value="PART">Part</option></select>
          </label>
          <label class="wide">Description <input formControlName="description" /></label>
          <label>Quantity / hours <input type="number" step="0.25" formControlName="quantity" /></label>
          <label>Unit price (€) <input type="number" step="0.01" formControlName="unitPrice" /></label>
          <label>Discount (%) <input type="number" formControlName="discountPercent" /></label>
          <label>VAT (%) <input type="number" formControlName="vatRate" /></label>
          <button type="submit" [disabled]="lineForm.invalid">Add line</button>
        </form>
      }
    }
  `,
})
export class WorkOrderDetail implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly labels = STATUS_LABELS;
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
    this.api.workOrder(this.id()).subscribe({ next: (w) => this.wo.set(w), error: (e) => this.error.set(errorMessage(e)) });
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
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
