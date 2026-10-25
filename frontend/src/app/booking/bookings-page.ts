import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { Plate } from '../shared/plate';
import { formatPlate, ptPlateEra } from '../shared/plate-format';
import { BookingApi } from './booking-api.service';
import { addDays, dayIn, formatDay, formatInstant, STATUS_BADGE, statusText, TIME } from './booking-calendar';
import { BookingChange, BookingDialog } from './booking-dialog';
import { Booking } from './booking.models';
import { WorkshopZone } from './workshop-zone';

/** How far ahead the "to confirm" list looks. */
export const PENDING_DAYS = 90;
/** How far back: requests whose slot passed unanswered stay listed, so none is lost. */
export const PENDING_PAST_DAYS = 30;

/** The bookings of one calendar day in the workshop's time zone (browser's without one), whatever `to` includes. */
export function onDay(list: readonly Booking[], day: string, timeZone?: string | null): Booking[] {
  return list.filter((b) => dayIn(new Date(b.slotStart), timeZone) === day);
}

/**
 * Bookings for the owner and the front desk: requests still to confirm, and the agenda of a day (with a day
 * picker). A booking opens in a dialog with its actions.
 */
@Component({
  selector: 'app-bookings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Alert, Icon, Plate, BookingDialog],
  styles: `
    .time {
      min-width: 3.25rem;
      font-weight: 650;
      font-variant-numeric: tabular-nums;
    }
    .cap::first-letter {
      text-transform: uppercase;
    }
  `,
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'booking.staff.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'booking.staff.subtitle' | t }}</p>
      </div>
      <button type="button" class="btn btn-outline-secondary" [disabled]="loading()" [attr.aria-busy]="loading()" (click)="load()">
        @if (loading()) {
          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
        } @else {
          <app-icon name="refresh" [size]="16" />
        }
        {{ 'common.refresh' | t }}
      </button>
    </header>

    <div aria-live="polite">
      @if (notice()) {
        <app-alert tone="success">{{ notice() }}</app-alert>
      }
    </div>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    <div class="row g-4">
      <section class="col-lg-5" aria-labelledby="bk-pending-title">
        <h2 class="mb-3 d-flex align-items-center gap-2" id="bk-pending-title">
          {{ 'booking.staff.toConfirm' | t }}
          @if (pending().length) {
            <span class="badge bg-warning-subtle text-warning-emphasis">{{ pending().length }}</span>
          }
        </h2>
        <div class="list-group">
          @for (b of pending(); track b.id) {
            <button type="button" class="list-group-item list-group-item-action d-flex gap-3 align-items-start" [attr.aria-label]="'booking.staff.open' | t: { number: b.number }" (click)="open(b)">
              <span class="d-flex flex-column flex-grow-1 overflow-hidden gap-1">
                <span class="d-flex flex-wrap justify-content-between gap-2">
                  <span class="fw-semibold text-break">{{ b.name }}</span>
                  <span class="small text-body-secondary">#{{ b.number }}</span>
                </span>
                <span class="small cap">{{ dayAndTime(b.slotStart) }}</span>
                <span class="d-flex flex-wrap align-items-center gap-2 small text-body-secondary">
                  <app-plate [plate]="plate(b)" [era]="era(b)" [country]="era(b) ? 'PT' : null" size="sm" />
                  {{ 'booking.staff.services' | t: { count: b.services.length } }}
                </span>
              </span>
            </button>
          } @empty {
            <div class="list-group-item text-body-secondary py-4 text-center">
              @if (loading()) {
                {{ 'common.loading' | t }}
              } @else {
                {{ 'booking.staff.toConfirmEmpty' | t }}
              }
            </div>
          }
        </div>
      </section>

      <section class="col-lg-7" aria-labelledby="bk-agenda-title">
        <h2 class="mb-3" id="bk-agenda-title">{{ 'booking.staff.agenda' | t }}</h2>
        <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
          <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'booking.staff.prevDay' | t" (click)="setDay(addDays(day(), -1))"><app-icon name="chevron-left" /></button>
          <input type="date" class="form-control w-auto" [attr.aria-label]="'booking.staff.day' | t" [value]="day()" (change)="setDay($any($event.target).value)" />
          <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'booking.staff.nextDay' | t" (click)="setDay(addDays(day(), 1))"><app-icon name="chevron-left" style="transform: scaleX(-1)" /></button>
          <button type="button" class="btn btn-outline-secondary" [disabled]="day() === today()" (click)="setDay(today())">{{ 'booking.staff.today' | t }}</button>
        </div>
        <p class="fw-semibold cap mb-2" aria-live="polite">{{ dayLabel() }}</p>
        <div class="list-group" [attr.aria-busy]="agendaLoading()">
          @for (b of agenda(); track b.id) {
            <button type="button" class="list-group-item list-group-item-action d-flex gap-3 align-items-center" [attr.aria-label]="'booking.staff.open' | t: { number: b.number }" (click)="open(b)">
              <span class="time">{{ time(b.slotStart) }}</span>
              <span class="d-flex flex-column flex-grow-1 overflow-hidden gap-1">
                <span class="fw-semibold text-break">{{ b.name }}</span>
                <span class="d-flex flex-wrap align-items-center gap-2 small text-body-secondary">
                  <app-plate [plate]="plate(b)" [era]="era(b)" [country]="era(b) ? 'PT' : null" size="sm" />
                  {{ 'booking.staff.services' | t: { count: b.services.length } }}
                </span>
              </span>
              <span class="badge flex-shrink-0" [class]="badge(b)">{{ status(b) | t }}</span>
            </button>
          } @empty {
            <div class="list-group-item text-body-secondary py-4 text-center">
              @if (agendaLoading()) {
                {{ 'common.loading' | t }}
              } @else {
                {{ 'booking.staff.agendaEmpty' | t }}
              }
            </div>
          }
        </div>
      </section>
    </div>

    <app-booking-dialog (changed)="changed($event)" />
  `,
})
export class BookingsPage implements OnInit {
  private readonly api = inject(BookingApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required(BookingDialog);

  private readonly zones = inject(WorkshopZone);
  /** The workshop's time zone: today, the agenda's day and the times follow it. */
  protected readonly zone = this.zones.zone;
  protected readonly today = signal(dayIn(new Date(), this.zone()));
  protected readonly addDays = addDays;
  protected readonly day = signal(this.today());
  protected readonly pending = signal<Booking[]>([]);
  private readonly dayList = signal<Booking[]>([]);
  protected readonly agenda = computed(() => onDay(this.dayList(), this.day(), this.zone()));
  protected readonly loading = signal(false);
  protected readonly agendaLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly dayLabel = computed(() => formatDay(this.day(), this.i18n.lang(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }));

  ngOnInit(): void {
    // Today is the workshop's today: read its zone first (the browser's when it cannot be read).
    this.zones.load().subscribe((zone) => {
      this.today.set(dayIn(new Date(), zone));
      this.day.set(this.today());
      this.load();
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.bookings({ status: 'REQUESTED', from: addDays(this.today(), -PENDING_PAST_DAYS), to: addDays(this.today(), PENDING_DAYS) }).subscribe({
      next: (list) => {
        this.pending.set(list);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
      },
    });
    this.loadDay();
  }

  setDay(day: string): void {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day ?? '')) return;
    this.day.set(day);
    this.loadDay();
  }

  open(b: Booking): void {
    this.notice.set(null);
    this.dialog().open(b.id);
  }

  protected changed(change: BookingChange): void {
    this.notice.set(change.message);
    this.load();
  }

  protected plate(b: Booking): string {
    return formatPlate(b.plate, 'PT');
  }

  protected era(b: Booking) {
    return ptPlateEra(b.plate);
  }

  protected badge(b: Booking): string {
    return STATUS_BADGE[b.status];
  }

  protected status(b: Booking): MessageKey {
    return statusText(b.status);
  }

  protected time(instant: string): string {
    return formatInstant(instant, this.i18n.lang(), TIME, this.zone());
  }

  protected dayAndTime(instant: string): string {
    const lang = this.i18n.lang();
    return `${formatInstant(instant, lang, { weekday: 'short', day: 'numeric', month: 'short' }, this.zone())}, ${formatInstant(instant, lang, TIME, this.zone())}`;
  }

  /** The day's bookings; the next day is asked too, so the list is complete whether `to` is inclusive or not. */
  private loadDay(): void {
    const day = this.day();
    this.agendaLoading.set(true);
    this.api.bookings({ from: day, to: addDays(day, 1) }).subscribe({
      next: (list) => {
        if (day !== this.day()) return;
        this.dayList.set(list);
        this.agendaLoading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(this.i18n.error(e));
        this.agendaLoading.set(false);
      },
    });
  }
}
