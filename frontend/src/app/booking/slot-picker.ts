import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { Observable, Subscription } from 'rxjs';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { addDays, dayIn, dayTiles, formatDay, localDay, pickDay } from './booking-calendar';
import { Slot, SlotDay } from './booking.models';

/** A chosen slot: the instant to send, and the day and clock time in the workshop's time zone to show. */
export interface PickedSlot {
  start: string;
  date: string;
  time: string;
}

/** Days per page of the strip. */
export const STRIP_DAYS = 7;

/**
 * Day and slot picker that fits a 390 px phone: a strip of seven day tiles (previous / next days), then the free
 * drop-off times of the chosen day with their free places. Days without a free slot are disabled. The parent gives
 * the `source` of slots (public page or front desk) and gets the chosen slot from `picked`.
 */
@Component({
  selector: 'app-slot-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Icon, Alert],
  styles: `
    .day-strip {
      display: grid;
      grid-template-columns: repeat(7, minmax(0, 1fr));
      gap: 4px;
    }
    .day-tile {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1px;
      min-width: 0;
      padding: 8px 0 7px;
      border: 1px solid var(--bs-border-color);
      border-radius: var(--bs-border-radius-lg);
      background: var(--asp-surface);
      color: var(--bs-body-color);
      line-height: 1.1;
    }
    .day-tile:hover:not(:disabled) {
      border-color: var(--asp-border-strong);
    }
    .day-tile:disabled {
      background: transparent;
      color: var(--bs-secondary-color);
      border-style: dashed;
      opacity: 0.7;
    }
    .day-tile[aria-pressed='true'] {
      background: var(--bs-primary);
      border-color: var(--bs-primary);
      color: var(--asp-brand-on-primary, #1b1400);
    }
    .day-dow,
    .day-mon {
      font-size: 0.75rem;
      text-transform: lowercase;
    }
    .day-num {
      font-size: 1.375rem;
      font-weight: 650;
      font-stretch: 75%;
      font-variant-numeric: tabular-nums;
    }
    .day-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: currentColor;
      opacity: 0.55;
    }
    .day-tile:disabled .day-dot {
      visibility: hidden;
    }
    .slot-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
      gap: 8px;
    }
    .slot-btn {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      padding: 8px 12px;
      border: 1px solid var(--asp-border-strong);
      border-radius: var(--bs-border-radius);
      background: var(--asp-surface);
      color: var(--bs-body-color);
      text-align: start;
    }
    .slot-btn:hover {
      border-color: var(--bs-body-color);
    }
    .slot-btn[aria-pressed='true'] {
      background: var(--bs-primary);
      border-color: var(--bs-primary);
      color: var(--asp-brand-on-primary, #1b1400);
    }
    .slot-time {
      font-size: 1.125rem;
      font-weight: 650;
      font-variant-numeric: tabular-nums;
    }
    .cap::first-letter {
      text-transform: uppercase;
    }
    .slot-free {
      font-size: 0.75rem;
      opacity: 0.85;
    }
  `,
  template: `
    <div class="d-flex align-items-center gap-2 mb-2">
      <button type="button" class="btn btn-ghost btn-icon" [disabled]="from() <= firstDay() || loading()" [attr.aria-label]="'booking.slot.prevWeek' | t" (click)="shift(-1)">
        <app-icon name="chevron-left" />
      </button>
      <span class="flex-grow-1 text-center fw-semibold cap" aria-live="polite">{{ rangeLabel() }}</span>
      <button type="button" class="btn btn-ghost btn-icon" [disabled]="loading()" [attr.aria-label]="'booking.slot.nextWeek' | t" (click)="shift(1)">
        <app-icon name="chevron-left" style="transform: scaleX(-1)" />
      </button>
    </div>

    <div class="day-strip" role="group" [attr.aria-label]="'booking.slot.days' | t" [attr.aria-busy]="loading()">
      @for (d of tiles(); track d.date) {
        <button
          type="button"
          class="day-tile"
          [disabled]="!d.open"
          [attr.aria-pressed]="d.date === day()"
          [attr.aria-label]="dayLabel(d.date) + ' · ' + (d.open ? ('booking.slot.free' | t: { count: d.free }) : ('booking.slot.noSlots' | t))"
          (click)="day.set(d.date)"
        >
          <span class="day-dow" aria-hidden="true">{{ short(d.date, 'weekday') }}</span>
          <span class="day-num" aria-hidden="true">{{ +d.date.slice(8) }}</span>
          <span class="day-mon" aria-hidden="true">{{ short(d.date, 'month') }}</span>
          <span class="day-dot" aria-hidden="true"></span>
        </button>
      }
    </div>

    @if (error()) {
      <app-alert class="mt-3">{{ error() }}</app-alert>
    } @else if (loading()) {
      <div class="slot-grid mt-3 placeholder-glow" aria-hidden="true">
        @for (i of [1, 2, 3, 4]; track i) {
          <span class="placeholder rounded" style="height: 52px"></span>
        }
      </div>
      <p class="visually-hidden" role="status">{{ 'common.loading' | t }}</p>
    } @else if (selectedTile(); as d) {
      <h3 class="h6 mt-3 mb-2 cap" [id]="id + '-times'">{{ 'booking.slot.times' | t: { date: dayLabel(d.date) } }}</h3>
      @if (d.slots.length) {
        <div class="slot-grid" role="group" [attr.aria-labelledby]="id + '-times'">
          @for (s of d.slots; track s.start) {
            <button type="button" class="slot-btn" [attr.aria-pressed]="s.start === value()" (click)="choose(d.date, s)">
              <span class="slot-time">{{ s.time }}</span>
              <span class="slot-free">{{ 'booking.slot.free' | t: { count: s.free } }}</span>
            </button>
          }
        </div>
      } @else {
        <p class="text-body-secondary mb-0">{{ (anyOpen() ? 'booking.slot.noSlots' : 'booking.slot.noneInWeek') | t }}</p>
      }
    }
  `,
})
export class SlotPicker {
  private static next = 0;
  protected readonly id = `slot-picker-${SlotPicker.next++}`;
  private readonly i18n = inject(I18nService);

  /** Loads the slots of `days` days from a day (`YYYY-MM-DD`). */
  readonly source = input.required<(from: string, days: number) => Observable<SlotDay[]>>();
  /** The chosen slot's start, highlighted. */
  readonly value = input<string | null>(null);
  /** The day to open on (defaults to today); a chosen slot's day keeps the strip on that week. */
  readonly startDay = input<string | null>(null);
  /** The workshop's time zone (IANA id), for "today"; the browser's when not given. */
  readonly timeZone = input<string | null>(null);
  readonly picked = output<PickedSlot>();
  /** A load failed; the picker shows the message, the parent may show more (429, 404). */
  readonly failed = output<unknown>();

  /** No days before today. */
  protected readonly firstDay = signal(localDay());
  protected readonly from = signal(localDay());
  protected readonly day = signal<string | null>(null);
  private readonly answer = signal<SlotDay[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private request: Subscription | null = null;
  /** True once a strip has loaded. */
  private loaded = false;

  protected readonly tiles = computed(() => dayTiles(this.from(), this.answer(), STRIP_DAYS));
  protected readonly selectedTile = computed(() => this.tiles().find((t) => t.date === this.day()) ?? null);
  protected readonly anyOpen = computed(() => this.tiles().some((t) => t.open));
  protected readonly rangeLabel = computed(() => {
    const lang = this.i18n.lang();
    const from = this.from();
    const to = addDays(from, STRIP_DAYS - 1);
    return `${formatDay(from, lang, { day: 'numeric', month: 'short' })} – ${formatDay(to, lang, { day: 'numeric', month: 'short' })}`;
  });

  constructor() {
    effect(() => {
      const start = this.startDay();
      const zone = this.timeZone();
      untracked(() => {
        const today = dayIn(new Date(), zone);
        if (today !== this.firstDay()) {
          this.firstDay.set(today);
          if (!this.loaded || this.from() < today) this.from.set(today);
        }
        // A day already on the strip (the slot just picked) needs no new load.
        if (start && this.loaded && start >= this.from() && start <= addDays(this.from(), STRIP_DAYS - 1)) return;
        if (start && start > this.firstDay()) this.from.set(start);
        this.load(start);
      });
    });
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  /** Loads the strip again (after a 409 `slot-unavailable`, the chosen day stays when it still has room). */
  refresh(): void {
    this.load(this.day());
  }

  protected shift(pages: number): void {
    const next = addDays(this.from(), pages * STRIP_DAYS);
    this.from.set(next < this.firstDay() ? this.firstDay() : next);
    this.load(null);
  }

  protected choose(date: string, slot: Slot): void {
    this.picked.emit({ start: slot.start, date, time: slot.time });
  }

  protected dayLabel(date: string): string {
    return formatDay(date, this.i18n.lang(), { weekday: 'long', day: 'numeric', month: 'long' });
  }

  protected short(date: string, part: 'weekday' | 'month'): string {
    // Three letters fit a 390 px strip in every language (pt-PT short weekdays are whole words: "segunda").
    return formatDay(date, this.i18n.lang(), { [part]: 'short' }).replace('.', '').slice(0, 3);
  }

  private load(wanted: string | null): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.source()(this.from(), STRIP_DAYS).subscribe({
      next: (days) => {
        this.answer.set(days);
        this.loaded = true;
        this.day.set(pickDay(this.tiles(), wanted));
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.answer.set([]);
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
        this.failed.emit(e);
      },
    });
  }
}
