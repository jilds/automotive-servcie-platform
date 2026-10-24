import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup, DragStartDelay } from '@angular/cdk/drag-drop';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Board as BoardData, BoardCard, WorkOrderStatus } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { Plate } from '../shared/plate';
import { allowedDropTargets, allowedMoves, BOARD_COLUMNS, canMoveCard, Mover } from './board-moves';

/** A touch has to rest this long before a card lifts, so a swipe still scrolls the board. */
const START_DELAY: DragStartDelay = { touch: 200, mouse: 0 };

interface Dragging {
  card: BoardCard;
  from: WorkOrderStatus;
}

/**
 * Kanban board of every car in the workshop. Mechanics only see their own jobs.
 * Cards move by drag and drop or, from the keyboard, with the "Move to…" select on each card. Both offer only the
 * moves the backend allows (see `board-moves.ts`); the server stays the authority and a refused move rolls back.
 */
@Component({
  selector: 'app-board',
  imports: [RouterLink, DatePipe, TranslatePipe, Alert, Icon, Plate, CdkDropListGroup, CdkDropList, CdkDrag, CdkScrollable],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-0">{{ 'board.title' | t }}</h1>
        <p class="small text-body-secondary mb-0 mt-1">{{ 'board.hint' | t }}</p>
      </div>
      <button type="button" class="btn btn-outline-secondary" (click)="load()"><app-icon name="refresh" [size]="16" />{{ 'common.refresh' | t }}</button>
    </header>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    @if (moveError()) {
      <app-alert>{{ moveError() }}</app-alert>
    }
    <p class="visually-hidden" aria-live="polite" aria-atomic="true">{{ notice() }}</p>
    <div class="board" cdkDropListGroup cdkScrollable [class.is-dragging]="dragging()" [attr.aria-busy]="!board() && !error()">
      @for (col of columns; track col) {
        <section
          class="column"
          cdkDropList
          cdkDropListSortingDisabled
          [cdkDropListData]="col"
          [cdkDropListEnterPredicate]="canEnter"
          (cdkDropListDropped)="dropped($event)"
          [class.drop-allowed]="dropTargets().includes(col)"
          [class.drop-blocked]="dragging() && dragging()!.from !== col && !dropTargets().includes(col)"
          [attr.data-status]="col"
          [attr.aria-labelledby]="'col-' + col"
        >
          <h2 class="column-header" [id]="'col-' + col">
            {{ statusKey(col) | t }}
            <span class="badge rounded-pill border bg-body text-body-secondary ms-auto">{{ board()?.columns?.[col]?.length ?? 0 }}</span>
          </h2>
          @if (!board() && !error()) {
            <span class="placeholder-glow" aria-hidden="true"><span class="placeholder placeholder-block w-100"></span></span>
          } @else {
            @for (card of board()?.columns?.[col] ?? []; track card.id) {
              <article
                class="card wo-card"
                cdkDrag
                [cdkDragData]="card"
                [cdkDragDisabled]="!canDrag(card) || pending().has(card.id)"
                [cdkDragStartDelay]="startDelay"
                (cdkDragStarted)="dragging.set({ card, from: col })"
                (cdkDragEnded)="dragging.set(null)"
                [class.is-movable]="canDrag(card)"
                [class.is-pending]="pending().has(card.id)"
                [attr.aria-busy]="pending().has(card.id)"
              >
                <div class="card-body vstack gap-2">
                  <span class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <app-plate [plate]="card.plateDisplay ?? card.plate" [era]="card.plateEra" [country]="card.registrationCountry" [registrationDate]="card.registrationDate" size="sm" />
                    <span class="small fw-semibold text-body-secondary">#{{ card.number }}</span>
                  </span>
                  <a class="stretched-link small fw-medium text-body text-break text-decoration-none" [routerLink]="['/work-orders', card.id]">{{ card.vehicle }}</a>
                  <span class="d-flex align-items-center gap-1 small text-body-secondary">
                    <app-icon name="clock" [size]="14" />{{ 'board.since' | t: { time: card.statusChangedAt | date: 'dd/MM HH:mm' } }}
                  </span>
                  @let targets = moves(card, col);
                  @if (targets.length) {
                    <select
                      class="form-select form-select-sm wo-move"
                      [attr.aria-label]="'board.moveLabel' | t: { number: card.number }"
                      [disabled]="pending().has(card.id)"
                      (change)="choose(card, col, $event)"
                    >
                      <option value="" selected>{{ 'board.moveTo' | t }}</option>
                      @for (s of targets; track s) {
                        <option [value]="s">{{ statusKey(s) | t }}</option>
                      }
                    </select>
                  }
                </div>
              </article>
            } @empty {
              <p class="column-empty">{{ 'board.empty' | t }}</p>
            }
          }
        </section>
      }
    </div>
  `,
})
export class Board implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  protected readonly columns = BOARD_COLUMNS;
  protected readonly statusKey = statusKey;
  protected readonly startDelay = START_DELAY;
  protected readonly board = signal<BoardData | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly moveError = signal<string | null>(null);
  /** Screen-reader announcement of the last move. */
  protected readonly notice = signal('');
  /** Cards whose move request is in flight. */
  protected readonly pending = signal<ReadonlySet<string>>(new Set());
  protected readonly dragging = signal<Dragging | null>(null);

  private readonly mover = computed<Mover | null>(() => {
    const user = this.auth.user();
    return user ? { role: user.role, userId: user.id } : null;
  });

  /** Columns the card being dragged may be dropped in. */
  protected readonly dropTargets = computed(() => {
    const d = this.dragging();
    return d ? allowedDropTargets(d.from, this.mover(), d.card.assignedMechanicId) : [];
  });

  /** CDK enter predicate: the card's own column (to put it back) or an allowed target. */
  readonly canEnter = (drag: CdkDrag<BoardCard>, drop: CdkDropList<WorkOrderStatus>): boolean => {
    const from = drag.dropContainer.data as WorkOrderStatus;
    return drop.data === from || allowedDropTargets(from, this.mover(), drag.data.assignedMechanicId).includes(drop.data);
  };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.board().subscribe({
      next: (b) => {
        this.error.set(null);
        this.board.set(b);
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }

  protected canDrag(card: BoardCard): boolean {
    return canMoveCard(this.mover(), card.assignedMechanicId);
  }

  protected moves(card: BoardCard, from: WorkOrderStatus): WorkOrderStatus[] {
    return allowedMoves(from, this.mover(), card.assignedMechanicId);
  }

  dropped(event: CdkDragDrop<WorkOrderStatus, WorkOrderStatus, BoardCard>): void {
    if (event.container === event.previousContainer) return;
    this.move(event.item.data, event.previousContainer.data, event.container.data, event.currentIndex);
  }

  protected choose(card: BoardCard, from: WorkOrderStatus, event: Event): void {
    const select = event.target as HTMLSelectElement;
    const to = select.value as WorkOrderStatus | '';
    select.value = '';
    if (to) this.move(card, from, to);
  }

  /** Moves the card right away, then asks the server; a refusal puts it back where it was. */
  move(card: BoardCard, from: WorkOrderStatus, to: WorkOrderStatus, index = 0): void {
    if (from === to || this.pending().has(card.id) || !this.moves(card, from).includes(to)) return;
    const origin = this.relocate(card, from, to, index);
    this.setPending(card.id, true);
    this.moveError.set(null);
    this.api.move(card.id, to).subscribe({
      next: (w) => {
        this.setPending(card.id, false);
        this.replace(to, { ...card, statusChangedAt: w.statusChangedAt });
        this.notice.set(this.i18n.t('board.moved', { number: card.number, status: this.i18n.t(statusKey(to)) }));
      },
      error: (e) => {
        this.setPending(card.id, false);
        this.relocate(card, to, from, origin);
        const message = this.i18n.t('board.moveFailed', { number: card.number, reason: this.i18n.error(e) });
        this.moveError.set(message);
        this.notice.set(message);
        // A conflict means this board is out of date (someone else moved or closed the order).
        if (e instanceof HttpErrorResponse && e.status === 409) this.load();
      },
    });
  }

  /** Takes the card out of one column and puts it in another (if that is on the board); returns where it was. */
  private relocate(card: BoardCard, from: WorkOrderStatus, to: WorkOrderStatus, index: number): number {
    let origin = 0;
    this.board.update((b) => {
      if (!b) return b;
      const source = [...(b.columns[from] ?? [])];
      origin = Math.max(0, source.findIndex((c) => c.id === card.id));
      const columns = { ...b.columns, [from]: source.filter((c) => c.id !== card.id) };
      if (BOARD_COLUMNS.includes(to)) {
        const target = (b.columns[to] ?? []).filter((c) => c.id !== card.id);
        target.splice(Math.min(index, target.length), 0, card);
        columns[to] = target;
      }
      return { columns };
    });
    return origin;
  }

  private replace(status: WorkOrderStatus, card: BoardCard): void {
    this.board.update((b) => {
      const cards = b?.columns[status];
      if (!b || !cards) return b;
      return { columns: { ...b.columns, [status]: cards.map((c) => (c.id === card.id ? card : c)) } };
    });
  }

  private setPending(id: string, on: boolean): void {
    this.pending.update((p) => {
      const next = new Set(p);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }
}
