import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { Mechanic, MechanicAssignment, WorkOrder } from '../core/api/models';
import { applyProblem, fieldMessage, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';

/** Longest reason the API keeps (`WorkOrder.MAX_REASON_LENGTH`). */
export const MAX_REASON_LENGTH = 500;

/**
 * True when the change needs a reason: an assigned mechanic is replaced or removed. A first assignment does not.
 * Mirrors the backend rule (`reason-required`).
 */
export function reasonRequired(current: string | null, next: string | null): boolean {
  return current !== null && current !== next;
}

/**
 * Who works on a car (owner and front desk): the current mechanic, a dialog to hand the job to another mechanic or to
 * nobody with the reason why, and the history of changes. Delivered and cancelled orders keep their mechanic.
 */
@Component({
  selector: 'app-mechanic-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, TranslatePipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="mechanic-title">
      <div class="card-body">
        <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
          <h2 class="card-title me-auto mb-0" id="mechanic-title">{{ 'mechanic.title' | t }}</h2>
          @if (canChange()) {
            <button type="button" class="btn btn-sm btn-outline-secondary" (click)="open()">{{ 'mechanic.change' | t }}</button>
          }
        </div>
        <p class="d-flex align-items-center gap-2 mb-0">
          <app-icon name="user" [size]="18" />
          <strong>{{ assignment()?.mechanicName ?? ('checkIn.notAssigned' | t) }}</strong>
        </p>

        @if (assignment()?.changes?.length) {
          <h3 class="h6 mt-4 mb-2">{{ 'mechanic.history' | t }}</h3>
          <ol class="list-unstyled vstack gap-2 mb-0 small">
            @for (c of history(); track $index) {
              <li class="border-start border-3 ps-3">
                <div class="fw-semibold">
                  {{ c.fromName ?? ('checkIn.notAssigned' | t) }} <app-icon name="arrow-right" [size]="14" /> {{ c.toName ?? ('checkIn.notAssigned' | t) }}
                </div>
                @if (c.reason) {
                  <div class="text-break">{{ 'mechanic.reasonShown' | t: { reason: c.reason } }}</div>
                }
                <div class="text-body-secondary">{{ 'mechanic.changedBy' | t: { name: c.changedByName ?? '—', date: (c.changedAt | date: 'dd/MM/yyyy HH:mm') ?? '' } }}</div>
              </li>
            }
          </ol>
        }
      </div>
    </section>

    <dialog #dialog class="modal" aria-labelledby="mechanic-dialog-title">
      <div class="modal-dialog">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="mechanic-dialog-title">{{ 'mechanic.change' | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body vstack gap-3">
            @if (error()) {
              <app-alert class="mb-0">{{ error() }}</app-alert>
            }
            <div>
              <label class="form-label" for="mc-mechanic">{{ 'mechanic.new' | t }}</label>
              <select id="mc-mechanic" class="form-select" formControlName="mechanicId">
                <option [ngValue]="null">{{ 'checkIn.notAssigned' | t }}</option>
                @for (m of mechanics(); track m.id) {
                  <option [ngValue]="m.id">{{ m.name }}</option>
                }
              </select>
            </div>
            <div>
              <label class="form-label" for="mc-reason">
                {{ 'mechanic.reason' | t }}
                @if (!needsReason()) {
                  <span class="text-body-secondary fw-normal">{{ 'mechanic.optional' | t }}</span>
                }
              </label>
              <textarea id="mc-reason" class="form-control" rows="3" formControlName="reason" [attr.maxlength]="maxReason"
                [class.is-invalid]="!!reasonMessage()" [attr.aria-invalid]="!!reasonMessage()" aria-describedby="mc-reason-help"
                [placeholder]="'mechanic.reasonPlaceholder' | t"></textarea>
              @if (reasonMessage(); as m) {
                <div class="invalid-feedback" id="mc-reason-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              } @else {
                <div class="form-text" id="mc-reason-help">{{ 'mechanic.reasonHelp' | t }}</div>
              }
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" [disabled]="busy() || unchanged()" [attr.aria-busy]="busy()">
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
export class MechanicCard {
  readonly workOrder = input.required<WorkOrder>();
  /** Emits the work order as saved, with its new mechanic. */
  readonly changed = output<WorkOrder>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  protected readonly maxReason = MAX_REASON_LENGTH;
  protected readonly assignment = signal<MechanicAssignment | null>(null);
  protected readonly mechanics = signal<Mechanic[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Newest change first. */
  protected readonly history = computed(() => [...(this.assignment()?.changes ?? [])].reverse());
  protected readonly canChange = computed(() => !['DELIVERED', 'CANCELLED'].includes(this.workOrder().status));

  readonly form = new FormGroup({
    mechanicId: new FormControl<string | null>(null),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(MAX_REASON_LENGTH)] }),
  });
  private readonly selected = signal<string | null>(null);
  protected readonly needsReason = computed(() => reasonRequired(this.assignment()?.mechanicId ?? null, this.selected()));
  protected readonly unchanged = computed(() => (this.assignment()?.mechanicId ?? null) === this.selected());

  constructor() {
    // Reload when the page shows another work order or the order's mechanic changed elsewhere (board, check-in).
    effect(() => {
      const w = this.workOrder();
      untracked(() => this.load(w.id));
    });
    this.form.controls.mechanicId.valueChanges.subscribe((id) => {
      this.selected.set(id);
      this.form.controls.reason.updateValueAndValidity();
    });
    this.form.controls.reason.addValidators((c) => (this.needsReason() && !String(c.value).trim() ? { required: true } : null));
  }

  open(): void {
    this.error.set(null);
    this.busy.set(false);
    const current = this.assignment()?.mechanicId ?? null;
    this.form.reset({ mechanicId: current, reason: '' });
    this.selected.set(current);
    this.api.mechanics().subscribe({ next: (list) => this.mechanics.set(list), error: (e) => this.error.set(this.i18n.error(e)) });
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  }

  close(): void {
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }

  save(): void {
    this.form.controls.reason.updateValueAndValidity();
    if (this.form.invalid || this.unchanged()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api.assign(this.workOrder().id, v.mechanicId, v.reason.trim() || null).subscribe({
      next: (w) => {
        this.busy.set(false);
        this.close();
        this.changed.emit(w);
      },
      error: (e) => {
        this.busy.set(false);
        const marked = applyProblem(this.form, e, { 'reason-required': 'reason', 'invalid-mechanic': 'mechanicId' });
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected reasonMessage() {
    return fieldMessage(this.form.controls.reason, { required: 'mechanic.reasonRequired', maxlength: 'mechanic.reasonTooLong' });
  }

  private load(id: string): void {
    this.api.mechanicAssignment(id).subscribe({ next: (a) => this.assignment.set(a), error: (e) => this.error.set(this.i18n.error(e)) });
  }
}
