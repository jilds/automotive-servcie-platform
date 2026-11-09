import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, Injectable, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from '../shared/icon';
import { FloorApi } from './floor-api.service';
import { clock } from './floor-logic';
import { RunningTimer } from './floor.models';

/** The signed-in mechanic's running timer (any order), so a banner can show it on every screen. */
@Injectable({ providedIn: 'root' })
export class RunningTimerService {
  private readonly api = inject(FloorApi);
  /** The last answer; null before the first one, and `entry` null when nothing runs. */
  readonly current = signal<RunningTimer | null>(null);
  /** Seconds the entry had run when the answer came, and the local time of the answer. */
  readonly baseSeconds = signal(0);
  readonly fetchedAtMs = signal(0);

  /** Asks the server again. A failure keeps what is shown (the banner is a convenience, not a gate). */
  refresh(): void {
    this.api.running().subscribe({
      next: (r) => {
        this.baseSeconds.set(r.entry?.seconds ?? 0);
        this.fetchedAtMs.set(Date.now());
        this.current.set(r);
      },
      error: () => undefined,
    });
  }

  clear(): void {
    this.current.set(null);
  }
}

/**
 * A strip under the top bar of a mechanic's screens while a timer runs: the order, the running time, a link to the
 * order. Refreshed on every navigation and after a start or stop.
 */
@Component({
  selector: 'app-running-timer-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, Icon],
  template: `
    @if (entry(); as e) {
      <a class="alert alert-info d-flex flex-wrap align-items-center gap-2 py-2 mb-3 text-decoration-none" data-test="banner"
        [routerLink]="['/work-orders', e.workOrderId]" [attr.aria-label]="'floor.banner.open' | t: { number: number() }">
        <app-icon name="clock" [size]="18" />
        <span class="fw-semibold">{{ 'floor.banner.running' | t: { number: number(), plate: plate() } }}</span>
        <span class="ms-auto font-monospace fw-semibold" data-test="banner-clock">{{ clock(seconds()) }}</span>
      </a>
    }
  `,
})
export class RunningTimerBanner {
  private readonly timer = inject(RunningTimerService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly clock = clock;
  protected readonly entry = computed(() => (this.auth.hasRole('MECHANIC') ? (this.timer.current()?.entry ?? null) : null));
  protected readonly number = computed(() => String(this.timer.current()?.workOrderNumber ?? ''));
  protected readonly plate = computed(() => this.timer.current()?.plateDisplay ?? '');
  private readonly now = signal(Date.now());
  protected readonly seconds = computed(() => this.timer.baseSeconds() + Math.max(0, Math.floor((this.now() - this.timer.fetchedAtMs()) / 1000)));

  constructor() {
    effect(() => {
      if (this.auth.hasRole('MECHANIC')) this.timer.refresh();
      else this.timer.clear();
    });
    const nav = this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      if (this.auth.hasRole('MECHANIC')) this.timer.refresh();
    });
    effect((onCleanup) => {
      if (!this.entry()) return;
      const id = setInterval(() => this.now.set(Date.now()), 1000);
      onCleanup(() => clearInterval(id));
    });
    inject(DestroyRef).onDestroy(() => nav.unsubscribe());
  }
}
