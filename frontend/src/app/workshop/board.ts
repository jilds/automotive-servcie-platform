import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Board as BoardData, WorkOrderStatus } from '../core/api/models';
import { I18nService, statusKey } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { Plate } from '../shared/plate';

const COLUMNS: WorkOrderStatus[] = ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS', 'READY'];

/** Kanban board of every car in the workshop. Mechanics only see their own jobs. */
@Component({
  selector: 'app-board',
  imports: [RouterLink, DatePipe, TranslatePipe, Alert, Icon, Plate],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <h1 class="me-auto mb-0">{{ 'board.title' | t }}</h1>
      <button type="button" class="btn btn-outline-secondary" (click)="load()"><app-icon name="refresh" [size]="16" />{{ 'common.refresh' | t }}</button>
    </header>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    <div class="board" [attr.aria-busy]="!board() && !error()">
      @for (col of columns; track col) {
        <section class="column" [attr.data-status]="col" [attr.aria-labelledby]="'col-' + col">
          <h2 class="column-header" [id]="'col-' + col">
            {{ statusKey(col) | t }}
            <span class="badge rounded-pill border bg-body text-body-secondary ms-auto">{{ board()?.columns?.[col]?.length ?? 0 }}</span>
          </h2>
          @if (!board() && !error()) {
            <span class="placeholder-glow" aria-hidden="true"><span class="placeholder placeholder-block w-100"></span></span>
          } @else {
            @for (card of board()?.columns?.[col] ?? []; track card.id) {
              <a class="card wo-card text-body" [routerLink]="['/work-orders', card.id]">
                <span class="card-body vstack gap-2">
                  <span class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <app-plate [plate]="card.plateDisplay ?? card.plate" [era]="card.plateEra" [country]="card.registrationCountry" [registrationDate]="card.registrationDate" size="sm" />
                    <span class="small fw-semibold text-body-secondary">#{{ card.number }}</span>
                  </span>
                  <span class="small fw-medium text-break">{{ card.vehicle }}</span>
                  <span class="d-flex align-items-center gap-1 small text-body-secondary">
                    <app-icon name="clock" [size]="14" />{{ 'board.since' | t: { time: card.statusChangedAt | date: 'dd/MM HH:mm' } }}
                  </span>
                </span>
              </a>
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
