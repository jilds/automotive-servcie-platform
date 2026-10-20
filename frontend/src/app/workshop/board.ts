import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Board as BoardData, WorkOrderStatus } from '../core/api/models';
import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

const COLUMNS: WorkOrderStatus[] = ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS', 'READY'];

/** Kanban board of every car in the workshop. Mechanics only see their own jobs. */
@Component({
  selector: 'app-board',
  imports: [RouterLink, DatePipe, TranslatePipe],
  template: `
    <div class="row">
      <h1>{{ 'board.title' | t }}</h1>
      <span class="spacer"></span>
      <button class="secondary" (click)="load()">{{ 'common.refresh' | t }}</button>
    </div>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    <div class="board">
      @for (col of columns; track col) {
        <section class="column">
          <h2>{{ statusKey(col) | t }} <span class="count">{{ board()?.columns?.[col]?.length ?? 0 }}</span></h2>
          @for (card of board()?.columns?.[col] ?? []; track card.id) {
            <a class="card wo-card" [routerLink]="['/work-orders', card.id]">
              <strong class="plate">{{ card.plate }}</strong>
              <span>#{{ card.number }} · {{ card.vehicle }}</span>
              <span class="muted small">{{ 'board.since' | t: { time: card.statusChangedAt | date: 'dd/MM HH:mm' } }}</span>
            </a>
          } @empty {
            <p class="muted small">{{ 'board.empty' | t }}</p>
          }
        </section>
      }
    </div>
  `,
})
export class Board implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly columns = COLUMNS;
  private readonly i18n = inject(I18nService);
  protected readonly statusKey = statusKey;
  protected readonly board = signal<BoardData | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.board().subscribe({
      next: (b) => this.board.set(b),
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
