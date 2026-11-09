import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { Mechanic, WorkOrder } from '../core/api/models';
import { fieldMessage, problemOf, SERVER_ERROR } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { FloorApi } from './floor-api.service';
import { fromLocalInput, rangeProblem, toLocalInput } from './floor-logic';
import { TimeEntry } from './floor.models';

/** Longest reason the API keeps. */
export const TIME_REASON_MAX = 500;

const notBlank = (c: AbstractControl): ValidationErrors | null => (typeof c.value === 'string' && !c.value.trim() ? { required: true } : null);

export function timeEntryForm() {
  return new FormGroup({
    mechanicId: new FormControl<string | null>(null),
    lineId: new FormControl<string | null>(null),
    startedAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    stoppedAt: new FormControl('', { nonNullable: true }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(TIME_REASON_MAX)] }),
  });
}

/**
 * Corrects a time entry or adds a missed one (owner and front desk). The reason is always required; times may not be
 * in the future and the stop may not be before the start. The server says the same (`invalid-time-range`).
 */
@Component({
  selector: 'app-time-entry-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="te-title">
      <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate data-test="time-form">
          <div class="modal-header">
            <h2 class="modal-title fs-5" id="te-title">{{ (entry() ? 'floor.correct.title' : 'floor.missed.title') | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body vstack gap-3">
            @if (error()) {
              <app-alert class="mb-0">{{ error() }}</app-alert>
            }
            @if (!entry()) {
              <div>
                <label class="form-label" for="te-mechanic">{{ 'floor.missed.mechanic' | t }}</label>
                <select id="te-mechanic" class="form-select" formControlName="mechanicId" [class.is-invalid]="shown('mechanicId')">
                  @for (m of mechanics(); track m.id) {
                    <option [ngValue]="m.id">{{ m.name }}</option>
                  }
                </select>
                @if (shown('mechanicId'); as m) {
                  <div class="invalid-feedback d-block">{{ m | t }}</div>
                }
              </div>
            }
            <div>
              <label class="form-label" for="te-line">{{ 'floor.time.line' | t }}</label>
              <select id="te-line" class="form-select" formControlName="lineId">
                <option [ngValue]="null">{{ 'floor.time.noLine' | t }}</option>
                @for (l of workOrder().lines; track l.id) {
                  <option [ngValue]="l.id">{{ l.description }}</option>
                }
              </select>
            </div>
            <div class="row g-3">
              <div class="col-sm-6">
                <label class="form-label" for="te-start">{{ 'floor.correct.start' | t }}</label>
                <input id="te-start" type="datetime-local" class="form-control" formControlName="startedAt" [class.is-invalid]="!!shown('startedAt')" />
                @if (shown('startedAt'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="te-stop">{{ 'floor.correct.stop' | t }}</label>
                <input id="te-stop" type="datetime-local" class="form-control" formControlName="stoppedAt" [class.is-invalid]="!!shown('stoppedAt')" />
                @if (shown('stoppedAt'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                } @else if (entry()?.running) {
                  <div class="form-text">{{ 'floor.correct.stopHelp' | t }}</div>
                }
              </div>
            </div>
            <div>
              <label class="form-label" for="te-reason">{{ 'floor.correct.reason' | t }}</label>
              <textarea id="te-reason" class="form-control" rows="3" formControlName="reason" [attr.maxlength]="reasonMax"
                [class.is-invalid]="!!shown('reason')" [placeholder]="'floor.correct.reasonPlaceholder' | t"></textarea>
              @if (shown('reason'); as m) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              } @else {
                <div class="form-text">{{ 'floor.correct.reasonHelp' | t }}</div>
              }
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
export class TimeEntryDialog {
  readonly workOrder = input.required<WorkOrder>();
  /** Emits the entry as saved. */
  readonly saved = output<TimeEntry>();
  /** The stored entry changed under the dialog (stale data): the panel reloads. */
  readonly conflict = output<void>();

  private readonly api = inject(FloorApi);
  private readonly mechanicsApi = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly reasonMax = TIME_REASON_MAX;
  protected readonly form = timeEntryForm();
  protected readonly entry = signal<TimeEntry | null>(null);
  protected readonly mechanics = signal<Mechanic[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Opens the dialog for an entry to correct, or for a missed one (null). */
  open(entry: TimeEntry | null): void {
    this.entry.set(entry);
    this.error.set(null);
    this.form.reset({
      mechanicId: entry ? entry.mechanicId : this.workOrder().assignedMechanicId,
      lineId: entry?.lineId ?? null,
      startedAt: toLocalInput(entry?.startedAt ?? null),
      stoppedAt: toLocalInput(entry?.stoppedAt ?? null),
      reason: '',
    });
    if (!entry) {
      this.mechanicsApi.mechanics().subscribe({
        next: (list) => this.mechanics.set(list),
        error: (e: unknown) => this.error.set(this.i18n.error(e)),
      });
    }
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.showModal === 'function' && !dialog.open) dialog.showModal();
  }

  protected close(): void {
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.close === 'function') dialog.close();
  }

  protected shown(control: keyof ReturnType<typeof timeEntryForm>['controls']): MessageKey | null {
    return fieldMessage(this.form.controls[control], {
      required: control === 'reason' ? 'floor.correct.reasonRequired' : control === 'startedAt' ? 'floor.correct.startRequired' : 'floor.correct.stopRequired',
      maxlength: 'floor.correct.reasonTooLong',
      range: 'error.invalid-time-range',
    });
  }

  save(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const current = this.entry();
    const startedAt = fromLocalInput(v.startedAt, current?.startedAt);
    const stoppedAt = fromLocalInput(v.stoppedAt, current?.stoppedAt);
    const problem = rangeProblem(startedAt, stoppedAt, !current?.running, Date.now());
    if (problem) {
      const control = this.form.controls[problem.field];
      control.setErrors({ ...control.errors, [problem.problem === 'start-required' || problem.problem === 'stop-required' ? 'required' : 'range']: true });
    }
    if (this.form.invalid || !startedAt) return;
    const reason = v.reason.trim();
    this.busy.set(true);
    this.error.set(null);
    const call = current
      ? this.api.correctTime(this.workOrder().id, current.id, { startedAt, stoppedAt, lineId: v.lineId, reason, version: current.version })
      : this.api.addMissedTime(this.workOrder().id, { mechanicId: v.mechanicId ?? '', lineId: v.lineId, startedAt, stoppedAt: stoppedAt ?? '', reason });
    call.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.close();
        this.saved.emit(saved);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const problemBody = problemOf(e);
        const code = problemBody?.code;
        if (code === 'invalid-time-range') {
          const field = problemBody?.errors && 'startedAt' in problemBody.errors ? 'startedAt' : 'stoppedAt';
          this.form.controls[field].setErrors({ [SERVER_ERROR]: code });
          this.form.controls[field].markAsTouched();
        } else if (code === 'invalid-mechanic') {
          this.form.controls.mechanicId.setErrors({ [SERVER_ERROR]: code });
        } else {
          this.error.set(this.i18n.error(e));
          if (code === 'stale-data') this.conflict.emit();
        }
      },
    });
  }
}
