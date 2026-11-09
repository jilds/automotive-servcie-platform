import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';

import { WorkOrder } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { FloorApi } from './floor-api.service';
import { canCorrectTime, canStopTimer, clock, clockOffset, elapsedSeconds, entrySeconds, liveTotal, timerAccess } from './floor-logic';
import { TimeEntry, TimeEntryChange, TimeSummary } from './floor.models';
import { RunningTimerService } from './running-timer';
import { TimeEntryDialog } from './time-entry-dialog';

/**
 * Time on a work order, for every role to read. The assigned mechanic (and the owner, for them) gets big start and stop
 * buttons and a running clock; owner and front desk correct entries and add missed time, always with a reason. The
 * clock runs from the server's `now`: it shows `now - startedAt` and ticks on this device.
 */
@Component({
  selector: 'app-time-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, Alert, Icon, TimeEntryDialog],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="time-title" id="floor-time">
      <div class="card-body">
        <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
          <h2 class="card-title me-auto mb-0" id="time-title">{{ 'floor.time.title' | t }}</h2>
          @if (canCorrect()) {
            <button type="button" class="btn btn-sm btn-outline-secondary" data-test="add-missed" (click)="dialog().open(null)">
              <app-icon name="plus" [size]="16" />{{ 'floor.missed.add' | t }}
            </button>
          }
        </div>

        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }

        @if (summary(); as s) {
          <div class="text-center border rounded-3 p-3 mb-3" [class.border-success]="!!s.running" data-test="timer">
            <div class="font-monospace display-5 fw-semibold lh-1" role="timer" [attr.aria-label]="'floor.time.clock' | t" data-test="clock">
              {{ clock(runningSeconds()) }}
            </div>
            <div class="small text-body-secondary mt-1" aria-live="polite">
              @if (s.running; as r) {
                {{ 'floor.time.runningFor' | t: { name: r.mechanicName ?? '—', line: lineName(r.lineId) } }}
              } @else {
                {{ 'floor.time.notRunning' | t }}
              }
            </div>

            @if (access().allowed || canStop()) {
              @if (!s.running) {
                <div class="mt-3 text-start">
                  <label class="form-label small" for="tp-line">{{ 'floor.time.line' | t }}</label>
                  <select id="tp-line" class="form-select" [value]="lineId() ?? ''" (change)="pickLine($event)" data-test="line">
                    <option value="">{{ 'floor.time.noLine' | t }}</option>
                    @for (l of workOrder().lines; track l.id) {
                      <option [value]="l.id" [selected]="l.id === lineId()">{{ l.description }}</option>
                    }
                  </select>
                </div>
              }
              <div class="d-grid d-sm-flex justify-content-sm-center gap-2 mt-3">
                @if (s.running) {
                  @if (canStop()) {
                    <button type="button" class="btn btn-danger btn-lg px-5 py-3" data-test="stop" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="stop()">
                      <app-icon name="stop" [size]="22" />{{ 'floor.time.stop' | t }}
                    </button>
                  }
                } @else if (access().allowed) {
                  <button type="button" class="btn btn-success btn-lg px-5 py-3" data-test="start" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="start()">
                    <app-icon name="play" [size]="22" />{{ 'floor.time.start' | t }}
                  </button>
                }
              </div>
            }
            @if (blockKey(); as k) {
              <p class="small text-body-secondary mb-0 mt-3" data-test="block">{{ k | t }}</p>
            }
          </div>

          <dl class="row g-2 mb-3" data-test="totals">
            <div class="col-6"><dt class="small text-body-secondary fw-semibold">{{ 'floor.time.total' | t }}</dt><dd class="fs-5 fw-semibold font-monospace mb-0" data-test="total">{{ clock(total()) }}</dd></div>
            @if (s.unassignedSeconds > 0) {
              <div class="col-6"><dt class="small text-body-secondary fw-semibold">{{ 'floor.time.unassigned' | t }}</dt><dd class="mb-0 font-monospace">{{ clock(s.unassignedSeconds) }}</dd></div>
            }
          </dl>

          @if (perLine().length) {
            <h3 class="h6 mb-2">{{ 'floor.time.perLine' | t }}</h3>
            <ul class="list-group mb-3" data-test="per-line">
              @for (l of perLine(); track l.id) {
                <li class="list-group-item d-flex gap-2 justify-content-between">
                  <span class="text-break">{{ l.description }}</span>
                  <span class="font-monospace text-nowrap">{{ clock(l.seconds) }}</span>
                </li>
              }
            </ul>
          }

          <h3 class="h6 mb-2">{{ 'floor.time.entries' | t }}</h3>
          <ul class="list-group" data-test="entries">
            @for (e of s.entries; track e.id) {
              <li class="list-group-item">
                <div class="d-flex flex-wrap align-items-center gap-2">
                  <span class="fw-semibold">{{ e.mechanicName ?? '—' }}</span>
                  @if (e.running) {
                    <span class="badge text-bg-success">{{ 'floor.time.badge.running' | t }}</span>
                  }
                  @if (e.autoStopped) {
                    <span class="badge text-bg-secondary">{{ 'floor.time.badge.auto' | t }}</span>
                  }
                  <span class="ms-auto font-monospace fw-semibold">{{ clock(entrySecs(e)) }}</span>
                  @if (canCorrect()) {
                    <button type="button" class="btn btn-sm btn-outline-secondary" data-test="correct" (click)="dialog().open(e)">
                      <app-icon name="pencil" [size]="14" />{{ 'floor.time.correct' | t }}
                    </button>
                  }
                </div>
                <div class="small text-body-secondary">
                  {{ e.startedAt | date: 'dd/MM/yyyy HH:mm' }} – {{ e.stoppedAt ? (e.stoppedAt | date: 'dd/MM/yyyy HH:mm') : '…' }} · {{ lineName(e.lineId) }}
                </div>
                @if (e.editReason) {
                  <div class="small text-break" data-test="edited">
                    {{ (e.version === 0 ? 'floor.time.added' : 'floor.time.edited') | t: { name: e.editedByName ?? '—', date: (e.editedAt | date: 'dd/MM/yyyy HH:mm') ?? '', reason: e.editReason } }}
                  </div>
                  @if (canCorrect()) {
                    <details class="small mt-1" data-test="history" (toggle)="toggleHistory(e, $event)">
                      <summary class="text-primary">{{ 'floor.time.history' | t }}</summary>
                      @for (c of history()[e.id] ?? []; track c.id) {
                        <div class="border-start border-3 ps-2 my-1 text-break" data-test="history-row">
                          <div>{{ 'floor.time.historyBefore' | t: { start: (c.oldStartedAt | date: 'dd/MM/yyyy HH:mm') ?? '', stop: c.oldStoppedAt ? (c.oldStoppedAt | date: 'dd/MM/yyyy HH:mm') ?? '' : '…', line: lineName(c.oldLineId) } }}</div>
                          <div class="text-body-secondary">{{ 'floor.time.historyBy' | t: { name: c.changedByName ?? '—', date: (c.changedAt | date: 'dd/MM/yyyy HH:mm') ?? '', reason: c.reason } }}</div>
                        </div>
                      } @empty {
                        <div class="text-body-secondary">{{ (historyFailed()[e.id] ? 'floor.time.historyFailed' : 'floor.time.historyEmpty') | t }}</div>
                      }
                    </details>
                  }
                }
              </li>
            } @empty {
              <li class="list-group-item small text-body-secondary">{{ 'floor.time.empty' | t }}</li>
            }
          </ul>
        } @else if (!error()) {
          <div class="placeholder-glow" aria-busy="true"><span class="placeholder col-12 placeholder-lg"></span></div>
        }
      </div>
    </section>
    <app-time-entry-dialog [workOrder]="workOrder()" (saved)="reload()" (conflict)="reload()" />
  `,
})
export class TimePanel {
  readonly workOrder = input.required<WorkOrder>();

  private readonly api = inject(FloorApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly runningTimer = inject(RunningTimerService);
  protected readonly dialog = viewChild.required(TimeEntryDialog);
  protected readonly clock = clock;

  protected readonly summary = signal<TimeSummary | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly lineId = signal<string | null>(null);
  /** Earlier values of corrected entries, read when the disclosure opens (by entry id, with the entry's version). */
  protected readonly history = signal<Record<string, TimeEntryChange[]>>({});
  protected readonly historyFailed = signal<Record<string, boolean>>({});
  private readonly historyOf = new Map<string, number>();

  /** Server time minus local time at the last answer, and the local clock that ticks while an entry runs. */
  private readonly offset = signal(0);
  private readonly localNow = signal(Date.now());
  private readonly serverNow = computed(() => this.localNow() + this.offset());

  private readonly loadKey = computed(() => {
    const w = this.workOrder();
    return `${w.id}|${w.status}|${w.assignedMechanicId ?? ''}`;
  });
  private readonly actor = computed(() => ({ role: this.auth.account()?.role ?? null, userId: this.auth.account()?.id ?? null }));
  protected readonly access = computed(() => timerAccess(this.actor(), this.workOrder()));
  protected readonly canStop = computed(() => canStopTimer(this.actor(), this.workOrder()));
  protected readonly canCorrect = computed(() => canCorrectTime(this.actor()));

  protected readonly runningSeconds = computed(() => {
    const r = this.summary()?.running;
    return r ? elapsedSeconds(r.startedAt, this.serverNow()) : 0;
  });
  protected readonly total = computed(() => {
    const s = this.summary();
    return s ? liveTotal(s.totalSeconds, !!s.running, Date.parse(s.now), this.serverNow()) : 0;
  });
  protected readonly perLine = computed(() => {
    const s = this.summary();
    if (!s) return [];
    return s.byLine.flatMap((t) => {
      const line = this.workOrder().lines.find((l) => l.id === t.lineId);
      return line ? [{ id: line.id, description: line.description, seconds: t.seconds + this.extraFor(t.lineId) }] : [];
    });
  });
  /** Why there are no start/stop buttons, as a message. Null when the buttons show. */
  protected readonly blockKey = computed<MessageKey | null>(() => {
    const running = !!this.summary()?.running;
    if (running && this.canStop()) return null;
    const { allowed, block } = this.access();
    if (allowed) return null;
    switch (block) {
      case 'closed':
        return 'floor.time.block.closed';
      case 'no-mechanic':
        return 'floor.time.block.noMechanic';
      case 'front-desk':
        return 'floor.time.block.frontDesk';
      default:
        return 'floor.time.block.notAssigned';
    }
  });

  constructor() {
    // Read the time again whenever the order, its status (a move auto-stops the timer) or its mechanic changes.
    effect(() => {
      const key = this.loadKey();
      untracked(() => this.load(key));
    });
    // Tick once a second, only while something runs.
    effect((onCleanup) => {
      if (!this.summary()?.running) return;
      this.localNow.set(Date.now());
      const id = setInterval(() => this.localNow.set(Date.now()), 1000);
      onCleanup(() => clearInterval(id));
    });
  }

  private loadedKey = '';

  private load(key: string): void {
    if (key === this.loadedKey) return;
    this.loadedKey = key;
    this.reload();
  }

  /** Reads the summary again. */
  protected reload(): void {
    this.api.time(this.workOrder().id).subscribe({
      next: (s) => {
        this.offset.set(clockOffset(s.now, Date.now()));
        this.localNow.set(Date.now());
        this.summary.set(s);
      },
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }

  /** A line's seconds that the running entry added since the answer, so per-line totals grow with the clock. */
  private extraFor(lineId: string): number {
    const r = this.summary()?.running;
    const s = this.summary();
    if (!r || !s || r.lineId !== lineId) return 0;
    return Math.max(0, Math.floor((this.serverNow() - Date.parse(s.now)) / 1000));
  }

  protected lineName(lineId: string | null): string {
    const line = lineId ? this.workOrder().lines.find((l) => l.id === lineId) : null;
    return line ? line.description : this.i18n.t('floor.time.noLine');
  }

  protected entrySecs(e: TimeEntry): number {
    return entrySeconds(e, this.serverNow());
  }

  /** Opening the "history" of a corrected entry reads its earlier values once per version of the entry. */
  protected toggleHistory(e: TimeEntry, event: Event): void {
    if (!(event.target as HTMLDetailsElement).open || this.historyOf.get(e.id) === e.version) return;
    this.historyOf.set(e.id, e.version);
    this.api.timeChanges(this.workOrder().id, e.id).subscribe({
      next: (list) => this.history.update((h) => ({ ...h, [e.id]: list })),
      error: () => {
        this.historyOf.delete(e.id);
        this.historyFailed.update((h) => ({ ...h, [e.id]: true }));
      },
    });
  }

  protected pickLine(event: Event): void {
    this.lineId.set((event.target as HTMLSelectElement).value || null);
  }

  protected start(): void {
    this.act(this.api.startTimer(this.workOrder().id, { lineId: this.lineId() }));
  }

  protected stop(): void {
    this.act(this.api.stopTimer(this.workOrder().id));
  }

  private act(call: ReturnType<FloorApi['stopTimer']>): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.reload();
        this.runningTimer.refresh();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
        // The screen was out of date (closed order, timer stopped elsewhere): show the real state.
        this.reload();
        this.runningTimer.refresh();
      },
    });
  }
}
