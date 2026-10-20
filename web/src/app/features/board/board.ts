import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { Board as BoardData, STATUS_LABELS, WorkOrderStatus } from '../../core/models';
import { errorMessage } from '../../shared/errors';

const COLUMNS: WorkOrderStatus[] = ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS', 'READY'];

/** Kanban board of every car in the workshop. Mechanics only see their own jobs. */
@Component({
  selector: 'app-board',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="row">
      <h1>Workshop board</h1>
      <span class="spacer"></span>
      <button class="secondary" (click)="load()">Refresh</button>
    </div>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    <div class="board">
      @for (col of columns; track col) {
        <section class="column">
          <h2>{{ labels[col] }} <span class="count">{{ board()?.columns?.[col]?.length ?? 0 }}</span></h2>
          @for (card of board()?.columns?.[col] ?? []; track card.id) {
            <a class="card wo-card" [routerLink]="['/work-orders', card.id]">
              <strong class="plate">{{ card.plate }}</strong>
              <span>#{{ card.number }} · {{ card.vehicle }}</span>
              <span class="muted small">since {{ card.statusChangedAt | date: 'dd/MM HH:mm' }}</span>
            </a>
          } @empty {
            <p class="muted small">Nothing here</p>
          }
        </section>
      }
    </div>
  `,
})
export class Board implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly columns = COLUMNS;
  protected readonly labels = STATUS_LABELS;
  protected readonly board = signal<BoardData | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.board().subscribe({
      next: (b) => this.board.set(b),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
