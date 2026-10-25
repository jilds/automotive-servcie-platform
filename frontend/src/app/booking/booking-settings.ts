import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';

import { Toasts } from '../backoffice/toasts';
import { HasUnsavedChanges } from '../backoffice/unsaved-changes.guard';
import { fieldMessage, isStale, problemCode } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { TenantThemeService } from '../shared/theme.service';
import { BookingApi } from './booking-api.service';
import { weekdayName } from './booking-calendar';
import { closureGroup, fillSettings, openingHoursProblems, rangeGroup, settingsForm, toSettings } from './booking-forms';
import { BookingSettings, OpeningRange, SLOT_LENGTHS } from './booking.models';

type NumberField = 'carsPerSlot' | 'leadTimeHours' | 'horizonDays' | 'cancelUntilHours';

/** Lowest value of each number field (the message says it). */
const MIN: Record<NumberField, number> = { carsPerSlot: 1, leadTimeHours: 0, horizonDays: 1, cancelUntilHours: 0 };

/**
 * The owner's online booking settings (`/backoffice/booking`): slot length, cars per slot, notice, how far ahead,
 * the customer's cancel deadline, opening hours per weekday (several ranges) and closures.
 */
@Component({
  selector: 'app-booking-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
  styles: `
    .day-row {
      display: grid;
      grid-template-columns: minmax(0, 9rem) minmax(0, 1fr);
      gap: 8px 16px;
      padding: 12px 0;
      border-top: 1px solid var(--bs-border-color);
    }
    .range {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
    }
    .range input {
      width: 7.5rem;
    }
    @media (max-width: 575.98px) {
      .day-row {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'booking.settings.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'booking.settings.subtitle' | t }}</p>
      </div>
      @if (publicPath(); as path) {
        <a class="btn btn-outline-secondary" [href]="path" target="_blank" rel="noopener">{{ 'booking.settings.publicPage' | t }}</a>
      }
    </header>

    <div aria-live="polite">
      @if (error()) {
        <app-alert>{{ error() }}</app-alert>
      }
    </div>

    @if (!loaded()) {
      @if (!error()) {
        <div class="card shadow-sm" aria-busy="true">
          <div class="card-body placeholder-glow vstack gap-2" aria-hidden="true">
            <span class="placeholder placeholder-lg col-5"></span>
            <span class="placeholder col-10"></span>
          </div>
        </div>
      }
    } @else {
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <section class="card shadow-sm mb-4">
          <div class="card-body">
            <h2 class="card-title mb-3">{{ 'booking.settings.slots' | t }}</h2>
            <div class="row g-3">
              <div class="col-sm-6 col-lg-4">
                <label class="form-label" for="bs-slot">{{ 'booking.settings.slotMinutes' | t }}</label>
                <select id="bs-slot" class="form-select" formControlName="slotMinutes">
                  @for (m of slotLengths; track m) {
                    <option [ngValue]="m">{{ 'booking.duration.minutes' | t: { minutes: m } }}</option>
                  }
                </select>
              </div>
              <div class="col-sm-6 col-lg-4">
                <label class="form-label" for="bs-cars">{{ 'booking.settings.carsPerSlot' | t }}</label>
                <input id="bs-cars" class="form-control" type="number" inputmode="numeric" min="1" step="1" formControlName="carsPerSlot" [class.is-invalid]="invalid('carsPerSlot')" aria-describedby="bs-cars-help" />
                <div [class]="invalid('carsPerSlot') ? 'invalid-feedback' : 'form-text'" id="bs-cars-help">{{ hint('carsPerSlot', 'booking.settings.carsHint') }}</div>
              </div>
              <div class="col-sm-6 col-lg-4">
                <label class="form-label" for="bs-lead">{{ 'booking.settings.leadTime' | t }}</label>
                <input id="bs-lead" class="form-control" type="number" inputmode="numeric" min="0" step="1" formControlName="leadTimeHours" [class.is-invalid]="invalid('leadTimeHours')" aria-describedby="bs-lead-help" />
                <div [class]="invalid('leadTimeHours') ? 'invalid-feedback' : 'form-text'" id="bs-lead-help">{{ hint('leadTimeHours', 'booking.settings.leadTimeHint') }}</div>
              </div>
              <div class="col-sm-6 col-lg-4">
                <label class="form-label" for="bs-horizon">{{ 'booking.settings.horizon' | t }}</label>
                <input id="bs-horizon" class="form-control" type="number" inputmode="numeric" min="1" step="1" formControlName="horizonDays" [class.is-invalid]="invalid('horizonDays')" aria-describedby="bs-horizon-help" />
                <div [class]="invalid('horizonDays') ? 'invalid-feedback' : 'form-text'" id="bs-horizon-help">{{ hint('horizonDays', 'booking.settings.horizonHint') }}</div>
              </div>
              <div class="col-sm-6 col-lg-4">
                <label class="form-label" for="bs-cancel">{{ 'booking.settings.cancelUntil' | t }}</label>
                <input id="bs-cancel" class="form-control" type="number" inputmode="numeric" min="0" step="1" formControlName="cancelUntilHours" [class.is-invalid]="invalid('cancelUntilHours')" aria-describedby="bs-cancel-help" />
                <div [class]="invalid('cancelUntilHours') ? 'invalid-feedback' : 'form-text'" id="bs-cancel-help">{{ hint('cancelUntilHours', 'booking.settings.cancelUntilHint') }}</div>
              </div>
            </div>
          </div>
        </section>

        <section class="card shadow-sm mb-4" aria-labelledby="bs-hours-title">
          <div class="card-body">
            <h2 class="card-title mb-1" id="bs-hours-title">{{ 'booking.settings.hours' | t }}</h2>
            <p class="small text-body-secondary mb-2">{{ 'booking.settings.hoursHint' | t }}</p>
            <div formArrayName="openingHours">
              @for (d of weekdays(); track d.weekday) {
                <div class="day-row" role="group" [attr.aria-label]="d.name">
                  <span class="fw-semibold pt-2">{{ d.name }}</span>
                  <div class="vstack gap-2">
                    @for (i of d.indexes; track i) {
                      <div class="range" [formGroupName]="i">
                        <input type="time" class="form-control" formControlName="opens" [attr.aria-label]="'booking.settings.opens' | t" [class.is-invalid]="!!problem(i)" [attr.aria-invalid]="!!problem(i)" />
                        <span aria-hidden="true">–</span>
                        <input type="time" class="form-control" formControlName="closes" [attr.aria-label]="'booking.settings.closes' | t" [class.is-invalid]="!!problem(i)" [attr.aria-invalid]="!!problem(i)" />
                        <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'booking.settings.removeRange' | t: { day: d.name }" (click)="removeRange(i)"><app-icon name="trash" [size]="18" /></button>
                        @if (problem(i); as p) {
                          <div class="invalid-feedback d-block w-100 mt-0"><app-icon name="alert-circle" [size]="14" /> {{ p | t }}</div>
                        }
                      </div>
                    } @empty {
                      <span class="text-body-secondary pt-2">{{ 'booking.settings.closed' | t }}</span>
                    }
                    <div>
                      <button type="button" class="btn btn-sm btn-link px-0" (click)="addRange(d.weekday)"><app-icon name="plus" [size]="14" />{{ 'booking.settings.addRange' | t }}</button>
                    </div>
                  </div>
                </div>
              }
            </div>
          </div>
        </section>

        <section class="card shadow-sm mb-4" aria-labelledby="bs-closures-title">
          <div class="card-body">
            <h2 class="card-title mb-1" id="bs-closures-title">{{ 'booking.settings.closures' | t }}</h2>
            <p class="small text-body-secondary">{{ 'booking.settings.closuresHint' | t }}</p>
            <div formArrayName="closures" class="vstack gap-2">
              @for (c of form.controls.closures.controls; track c; let i = $index) {
                <div class="row g-2 align-items-start" [formGroupName]="i">
                  <div class="col-sm-4">
                    <input type="date" class="form-control" formControlName="day" [attr.aria-label]="'booking.settings.closureDay' | t" [class.is-invalid]="c.controls.day.invalid && c.controls.day.touched" />
                    @if (c.controls.day.invalid && c.controls.day.touched) {
                      <div class="invalid-feedback">{{ 'booking.settings.closureDayRequired' | t }}</div>
                    }
                  </div>
                  <div class="col">
                    <input class="form-control" formControlName="reason" maxlength="200" [attr.aria-label]="'booking.settings.closureReason' | t" [placeholder]="'booking.settings.closureReason' | t" />
                  </div>
                  <div class="col-auto">
                    <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'booking.settings.removeClosure' | t" (click)="removeClosure(i)"><app-icon name="trash" [size]="18" /></button>
                  </div>
                </div>
              } @empty {
                <p class="text-body-secondary mb-0">{{ 'booking.settings.closuresEmpty' | t }}</p>
              }
            </div>
            <button type="button" class="btn btn-sm btn-link px-0 mt-2" (click)="addClosure()"><app-icon name="plus" [size]="14" />{{ 'booking.settings.addClosure' | t }}</button>
          </div>
        </section>

        <div class="d-flex flex-wrap justify-content-end gap-2">
          <button type="button" class="btn btn-outline-secondary" [disabled]="!dirty() || busy()" (click)="discard()">{{ 'backoffice.settings.discard' | t }}</button>
          <button type="submit" class="btn btn-primary" [disabled]="!dirty() || busy()" [attr.aria-busy]="busy()">
            @if (busy()) {
              <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
            }
            {{ 'common.save' | t }}
          </button>
        </div>
      </form>
    }

    <dialog #leaveDialog class="modal" aria-labelledby="bs-leave-title" (close)="resolveLeave(leaveDialog.returnValue === 'leave')">
      <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content" method="dialog">
          <div class="modal-header">
            <h2 class="modal-title" id="bs-leave-title">{{ 'backoffice.settings.leaveTitle' | t }}</h2>
          </div>
          <div class="modal-body"><p class="mb-0">{{ 'backoffice.settings.leaveBody' | t }}</p></div>
          <div class="modal-footer">
            <button class="btn btn-outline-secondary" value="stay">{{ 'backoffice.settings.stay' | t }}</button>
            <button class="btn btn-danger" value="leave">{{ 'backoffice.settings.leave' | t }}</button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class BookingSettingsPage implements OnInit, HasUnsavedChanges {
  private readonly api = inject(BookingApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  private readonly theme = inject(TenantThemeService);
  private readonly leaveDialog = viewChild.required<ElementRef<HTMLDialogElement>>('leaveDialog');
  private leaveResolver: ((leave: boolean) => void) | null = null;

  protected readonly slotLengths = SLOT_LENGTHS;
  protected readonly form = settingsForm();
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dirty = signal(false);
  /** Bumped on every form change, so the OnPush view re-reads the ranges and their problems. */
  private readonly tick = signal(0);
  private readonly saved = signal<BookingSettings | null>(null);

  protected readonly publicPath = computed(() => {
    const slug = this.theme.branding()?.slug;
    return slug ? `/w/${slug}/book` : null;
  });

  /** Monday to Sunday with the indexes of their ranges in the form array. */
  protected readonly weekdays = computed(() => {
    this.tick();
    const lang = this.i18n.lang();
    const ranges = this.form.controls.openingHours.getRawValue();
    return [1, 2, 3, 4, 5, 6, 7].map((weekday) => ({
      weekday,
      name: weekdayName(weekday, lang),
      indexes: ranges
        .map((r, i) => ({ r, i }))
        .filter(({ r }) => r.weekday === weekday)
        .sort((a, b) => a.r.opens.localeCompare(b.r.opens))
        .map(({ i }) => i),
    }));
  });
  private readonly problems = computed(() => {
    this.tick();
    return openingHoursProblems(this.form.controls.openingHours.getRawValue() as OpeningRange[]);
  });

  constructor() {
    this.form.events.pipe(takeUntilDestroyed()).subscribe(() => {
      this.dirty.set(this.form.dirty);
      this.tick.update((n) => n + 1);
    });
  }

  ngOnInit(): void {
    this.api.settings().subscribe({
      next: (s) => this.reset(s),
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }

  confirmLeave(): boolean | Promise<boolean> {
    if (!this.form.dirty) return true;
    const dialog = this.leaveDialog().nativeElement;
    if (typeof dialog.showModal !== 'function') return window.confirm(this.i18n.t('backoffice.settings.leaveBody'));
    dialog.returnValue = '';
    dialog.showModal();
    return new Promise((resolve) => (this.leaveResolver = resolve));
  }

  protected resolveLeave(leave: boolean): void {
    this.leaveResolver?.(leave);
    this.leaveResolver = null;
  }

  protected onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.form.dirty) event.preventDefault();
  }

  protected problem(index: number): MessageKey | null {
    const p = this.problems().get(index);
    return p === 'order' ? 'booking.settings.rangeOrder' : p === 'overlap' ? 'booking.settings.rangeOverlap' : null;
  }

  protected invalid(field: NumberField): boolean {
    this.tick();
    const c = this.form.controls[field];
    return c.invalid && (c.touched || c.dirty);
  }

  protected hint(field: NumberField, help: MessageKey): string {
    if (!this.invalid(field)) return this.i18n.t(help);
    const server = fieldMessage(this.form.controls[field]);
    return server && server !== 'error.field' ? this.i18n.t(server) : this.i18n.t('booking.settings.numberInvalid', { min: MIN[field] });
  }

  addRange(weekday: number): void {
    const day = this.form.controls.openingHours.getRawValue().filter((r) => r.weekday === weekday);
    const last = day.map((r) => r.closes).sort().at(-1);
    // After the day's last range, else a usual morning.
    const opens = last && last < '22:00' ? last : '09:00';
    const closes = last && last < '22:00' ? addHours(last, 2) : '13:00';
    this.form.controls.openingHours.push(rangeGroup({ weekday, opens, closes }));
    this.form.markAsDirty();
    this.dirty.set(true);
  }

  removeRange(index: number): void {
    this.form.controls.openingHours.removeAt(index);
    this.form.markAsDirty();
    this.dirty.set(true);
  }

  addClosure(): void {
    this.form.controls.closures.push(closureGroup({ day: '', reason: null }));
    this.form.markAsDirty();
    this.dirty.set(true);
  }

  removeClosure(index: number): void {
    this.form.controls.closures.removeAt(index);
    this.form.markAsDirty();
    this.dirty.set(true);
  }

  protected discard(): void {
    const s = this.saved();
    if (s) this.reset(s);
  }

  save(): void {
    this.form.markAllAsTouched();
    this.tick.update((n) => n + 1);
    if (this.form.invalid) {
      this.error.set(this.i18n.t(this.form.controls.openingHours.invalid ? 'error.invalid-opening-hours' : 'error.validation'));
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.saveSettings(toSettings(this.form, this.saved()?.version ?? 0)).subscribe({
      next: (s) => {
        this.reset(s);
        this.busy.set(false);
        this.toasts.show(this.i18n.t('booking.settings.saved'));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
        if (isStale(e)) {
          // Saved elsewhere since this page loaded: show the stored settings.
          this.api.settings().subscribe({ next: (s) => this.reset(s), error: () => {} });
        } else if (problemCode(e) === 'invalid-opening-hours') {
          this.tick.update((n) => n + 1);
        }
      },
    });
  }

  private reset(s: BookingSettings): void {
    this.saved.set(s);
    fillSettings(this.form, s);
    this.dirty.set(false);
    this.loaded.set(true);
    this.tick.update((n) => n + 1);
  }
}

/** `HH:mm` plus some hours, at most 23:59. */
export function addHours(time: string, hours: number): string {
  const [h, m] = time.split(':').map(Number);
  const total = Math.min(h * 60 + m + hours * 60, 23 * 60 + 59);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}
