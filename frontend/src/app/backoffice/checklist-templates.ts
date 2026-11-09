import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { FloorApi } from '../workshop/floor-api.service';
import { ChecklistTemplate } from '../workshop/floor.models';
import { ChecklistTemplateEditor } from './checklist-template-editor';
import { Toasts } from './toasts';

/** The workshop's check-in and delivery checklist templates (owner). Work orders copy a template when a checklist starts. */
@Component({
  selector: 'app-checklist-templates',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Alert, ChecklistTemplateEditor],
  template: `
    <header class="mb-4">
      <h1 class="mb-2">{{ 'backoffice.checklists.title' | t }}</h1>
      <p class="text-body-secondary mb-0">{{ 'backoffice.checklists.subtitle' | t }}</p>
    </header>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    @if (templates().length === 0 && !error()) {
      <div class="card shadow-sm" aria-busy="true">
        <div class="card-body placeholder-glow vstack gap-2" aria-hidden="true">
          <span class="placeholder placeholder-lg col-5"></span>
          <span class="placeholder col-10"></span>
        </div>
      </div>
    }
    @for (t of templates(); track t.kind) {
      <app-checklist-template-editor [template]="t" (saved)="saved($event)" />
    }
  `,
})
export class ChecklistTemplates implements OnInit {
  private readonly api = inject(FloorApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);

  protected readonly templates = signal<ChecklistTemplate[]>([]);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.templates().subscribe({
      next: (list) => this.templates.set(list),
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }

  protected saved(t: ChecklistTemplate): void {
    this.templates.update((l) => l.map((x) => (x.kind === t.kind ? t : x)));
    this.toasts.show(this.i18n.t('backoffice.checklists.saved'));
  }
}
