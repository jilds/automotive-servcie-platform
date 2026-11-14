import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { Note } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from './alert';

/**
 * Dated observations with their author: on a vehicle (every role) or notes on a customer (owner and front desk).
 * Added once, newest first; there is no edit or delete.
 */
@Component({
  selector: 'app-notes-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, TranslatePipe, Alert],
  template: `
    <section class="card shadow-sm mb-4" [attr.aria-labelledby]="'notes-' + kind()">
      <div class="card-body vstack gap-3">
        <div>
          <h2 class="h5 mb-1" [id]="'notes-' + kind()">{{ (kind() === 'vehicle' ? 'observations.title' : 'notes.title') | t }}</h2>
          <p class="small text-body-secondary mb-0">{{ (kind() === 'vehicle' ? 'observations.hint' : 'notes.hint') | t }}</p>
        </div>
        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }
        <form class="vstack gap-2" [formGroup]="form" (ngSubmit)="add()">
          <label class="form-label visually-hidden" [for]="'note-body-' + kind()">{{ 'notes.label' | t }}</label>
          <textarea class="form-control" rows="2" maxlength="2000" [id]="'note-body-' + kind()" formControlName="body" [placeholder]="'notes.label' | t"></textarea>
          <button type="submit" class="btn btn-primary align-self-start" [disabled]="form.invalid || saving()">{{ 'notes.add' | t }}</button>
        </form>
        <ul class="list-unstyled vstack gap-3 mb-0" data-test="notes">
          @for (n of notes(); track n.id) {
            <li>
              <p class="mb-1 text-break" style="white-space: pre-wrap">{{ n.body }}</p>
              <span class="small text-body-secondary">{{ 'notes.meta' | t: { author: n.authorName || ('notes.unknownAuthor' | t), time: (n.createdAt | date: 'dd/MM/yyyy HH:mm') } }}</span>
            </li>
          } @empty {
            <li class="small text-body-secondary">{{ 'notes.empty' | t }}</li>
          }
        </ul>
      </div>
    </section>
  `,
})
export class NotesPanel {
  readonly kind = input.required<'vehicle' | 'customer'>();
  readonly subjectId = input.required<string>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly notes = signal<Note[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    body: ['', [Validators.required, Validators.maxLength(2000), Validators.pattern(/\S/)]],
  });

  constructor() {
    // The route reuses the page for another id, so load on every change.
    effect(() => {
      const id = this.subjectId();
      const kind = this.kind();
      untracked(() => this.load(kind, id));
    });
  }

  protected add(): void {
    const body = this.form.controls.body.value.trim();
    if (!body || this.saving()) return;
    this.saving.set(true);
    const request =
      this.kind() === 'vehicle' ? this.api.addVehicleObservation(this.subjectId(), body) : this.api.addCustomerNote(this.subjectId(), body);
    request.subscribe({
      next: (note) => {
        this.saving.set(false);
        this.error.set(null);
        this.notes.update((list) => [note, ...list]);
        this.form.reset({ body: '' });
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  private load(kind: 'vehicle' | 'customer', id: string): void {
    this.notes.set([]);
    this.error.set(null);
    const request = kind === 'vehicle' ? this.api.vehicleObservations(id) : this.api.customerNotes(id);
    request.subscribe({ next: (list) => this.notes.set(list), error: (e) => this.error.set(this.i18n.error(e)) });
  }
}
