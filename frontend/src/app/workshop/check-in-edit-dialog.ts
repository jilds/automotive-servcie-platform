import { ChangeDetectionStrategy, Component, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { CheckInUpdateRequest, WorkOrder } from '../core/api/models';
import { applyProblem, fieldMessage } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';

/** Fuel level drawn when none was recorded; it is only sent once the user moves the slider. */
const DEFAULT_FUEL = 4;

/**
 * Modal form that corrects the data taken at check-in (PUT /work-orders/{id}/check-in): mileage in, fuel level,
 * complaint and damage notes. A 409 (order locked or changed meanwhile) is reported through `conflict`.
 */
@Component({
  selector: 'app-check-in-edit-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="check-in-edit-title">
      <div class="modal-dialog">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="check-in-edit-title">{{ 'workOrder.editCheckIn' | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            <div class="row g-3">
              @if (error()) {
                <app-alert class="col-12 mb-0">{{ error() }}</app-alert>
              }
              <div class="col-sm-6">
                <label class="form-label" for="cie-mileage">{{ 'checkIn.mileageNow' | t }}</label>
                <input id="cie-mileage" class="form-control" type="number" min="0" formControlName="mileageIn" [class.is-invalid]="!!message('mileageIn')" [attr.aria-invalid]="!!message('mileageIn')" />
                @if (message('mileageIn'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="cie-fuel">{{ 'checkIn.fuelLevel' | t: { level: fuelLabel() } }}</label>
                <input id="cie-fuel" class="form-range mt-2" type="range" min="0" max="8" formControlName="fuelLevel" (input)="fuelTouched.set(true)" [class.is-invalid]="!!message('fuelLevel')" />
                @if (message('fuelLevel'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-12">
                <label class="form-label" for="cie-complaint">{{ 'checkIn.complaint' | t }}</label>
                <textarea id="cie-complaint" class="form-control" formControlName="complaint" rows="2"></textarea>
              </div>
              <div class="col-12">
                <label class="form-label" for="cie-damage">{{ 'checkIn.damage' | t }}</label>
                <textarea id="cie-damage" class="form-control" formControlName="damageNotes" rows="2" [placeholder]="'checkIn.damagePlaceholder' | t"></textarea>
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
export class CheckInEditDialog {
  /** Emits the work order as saved by the server. */
  readonly saved = output<WorkOrder>();
  /** Emits when the server answered 409: the order was closed or changed meanwhile and should be reloaded. */
  readonly conflict = output<void>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private workOrderId: string | null = null;
  /** False while the order has no recorded fuel level and the slider was not moved. */
  protected readonly fuelTouched = signal(false);
  protected readonly fuelLevel = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Mirrors the backend rules: mileage ≥ 0, fuel level 0..8. */
  readonly form = inject(FormBuilder).nonNullable.group({
    mileageIn: [null as number | null, Validators.min(0)],
    fuelLevel: [DEFAULT_FUEL, [Validators.min(0), Validators.max(8)]],
    complaint: [''],
    damageNotes: [''],
  });

  constructor() {
    this.form.controls.fuelLevel.valueChanges.subscribe((v) => this.fuelLevel.set(Number(v)));
  }

  open(wo: WorkOrder): void {
    this.workOrderId = wo.id;
    this.error.set(null);
    this.busy.set(false);
    this.fuelTouched.set(wo.fuelLevel !== null);
    this.form.reset({
      mileageIn: wo.mileageIn,
      fuelLevel: wo.fuelLevel ?? DEFAULT_FUEL,
      complaint: wo.complaint ?? '',
      damageNotes: wo.damageNotes ?? '',
    });
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  /** The PUT body; blank texts and an untouched, never-recorded fuel level are sent as null. */
  request(): CheckInUpdateRequest {
    const v = this.form.getRawValue();
    return {
      mileageIn: v.mileageIn,
      fuelLevel: this.fuelTouched() ? Number(v.fuelLevel) : null,
      complaint: v.complaint.trim() || null,
      damageNotes: v.damageNotes.trim() || null,
    };
  }

  save(): void {
    if (!this.workOrderId) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.updateCheckIn(this.workOrderId, this.request()).subscribe({
      next: (wo) => {
        this.busy.set(false);
        this.saved.emit(wo);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        if (e?.status === 409) {
          this.close();
          this.conflict.emit();
          return;
        }
        applyProblem(this.form, e);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  protected fuelLabel(): string | number {
    return this.fuelTouched() ? (this.fuelLevel() ?? DEFAULT_FUEL) : '—';
  }

  protected message(field: 'mileageIn' | 'fuelLevel') {
    return fieldMessage(this.form.controls[field], { min: 'common.notNegative', max: 'error.field' });
  }
}
