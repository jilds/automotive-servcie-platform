import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal, effect, untracked } from '@angular/core';

import { isStale } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { FloorApi } from '../workshop/floor-api.service';
import { ChecklistTemplate } from '../workshop/floor.models';
import {
  EditorItem,
  MAX_TEMPLATE_ITEMS,
  moveItem,
  TEMPLATE_LABEL_MAX,
  templateChanged,
  toEditorItems,
  toTemplateItems,
  validateTemplate,
} from './checklist-template-logic';

/**
 * One checklist template (check-in or delivery): an ordered list of items with a "required" switch, added, removed and
 * moved up or down. Saving replaces the whole list with the stored `version`; a stale version reloads the saved list.
 */
@Component({
  selector: 'app-checklist-template-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" [attr.aria-labelledby]="'tpl-title-' + template().kind">
      <div class="card-body">
        <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
          <h2 class="card-title h5 me-auto mb-0" [id]="'tpl-title-' + template().kind">{{ kindKey() | t }}</h2>
          <span class="badge text-bg-secondary">{{ 'backoffice.checklists.count' | t: { count: items().length, max: max } }}</span>
        </div>
        @if (!template().custom) {
          <p class="small text-body-secondary" data-test="default">{{ 'backoffice.checklists.default' | t }}</p>
        }
        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }
        @if (validation().list; as l) {
          <app-alert tone="warning"><span data-test="list-problem">{{ (l === 'empty' ? 'backoffice.checklists.itemsRequired' : 'backoffice.checklists.itemsTooMany') | t }}</span></app-alert>
        }

        <ol class="list-unstyled vstack gap-2 mb-3">
          @for (item of items(); track item.key; let i = $index; let first = $first; let last = $last) {
            <li class="border rounded-3 p-2" data-test="item">
              <div class="d-flex gap-2 align-items-start">
                <span class="text-body-secondary pt-2 small" aria-hidden="true">{{ i + 1 }}.</span>
                <div class="flex-grow-1">
                  <label class="visually-hidden" [for]="'tpl-' + template().kind + '-' + item.key">{{ 'backoffice.checklists.itemLabel' | t: { n: i + 1 } }}</label>
                  <input [id]="'tpl-' + template().kind + '-' + item.key" class="form-control" type="text" [value]="item.label"
                    [attr.maxlength]="labelMax + 20" [placeholder]="'backoffice.checklists.labelPlaceholder' | t"
                    [class.is-invalid]="!!validation().items[item.key]" (input)="setLabel(item.key, $event)" />
                  @if (validation().items[item.key]; as p) {
                    <div class="invalid-feedback d-block">{{ (p === 'blank' ? 'backoffice.checklists.labelRequired' : 'backoffice.checklists.labelTooLong') | t }}</div>
                  }
                </div>
              </div>
              <div class="d-flex flex-wrap align-items-center gap-2 mt-2">
                <div class="form-check form-switch me-auto mb-0">
                  <input class="form-check-input" type="checkbox" role="switch" [id]="'req-' + template().kind + '-' + item.key" [checked]="item.required"
                    (change)="setRequired(item.key, $event)" />
                  <label class="form-check-label" [for]="'req-' + template().kind + '-' + item.key">{{ 'backoffice.checklists.required' | t }}</label>
                </div>
                <button type="button" class="btn btn-sm btn-outline-secondary" data-test="up" [disabled]="first" [attr.aria-label]="'backoffice.checklists.moveUp' | t: { n: i + 1 }" (click)="move(i, -1)">
                  <app-icon name="chevron-up" [size]="16" />
                </button>
                <button type="button" class="btn btn-sm btn-outline-secondary" data-test="down" [disabled]="last" [attr.aria-label]="'backoffice.checklists.moveDown' | t: { n: i + 1 }" (click)="move(i, 1)">
                  <app-icon name="chevron-down" [size]="16" />
                </button>
                <button type="button" class="btn btn-sm btn-outline-danger" data-test="remove" [attr.aria-label]="'backoffice.checklists.remove' | t: { n: i + 1 }" (click)="remove(item.key)">
                  <app-icon name="trash" [size]="16" />
                </button>
              </div>
            </li>
          }
        </ol>

        <div class="d-flex flex-wrap gap-2 justify-content-between">
          <button type="button" class="btn btn-outline-primary" data-test="add" [disabled]="items().length >= max" (click)="add()">
            <app-icon name="plus" [size]="16" />{{ 'backoffice.checklists.add' | t }}
          </button>
          <span class="d-inline-flex gap-2">
            <button type="button" class="btn btn-outline-secondary" data-test="discard" [disabled]="!dirty() || busy()" (click)="discard()">{{ 'backoffice.settings.discard' | t }}</button>
            <button type="button" class="btn btn-primary" data-test="save" [disabled]="!dirty() || busy()" [attr.aria-busy]="busy()" (click)="save()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'backoffice.checklists.save' | t }}
            </button>
          </span>
        </div>
      </div>
    </section>
  `,
})
export class ChecklistTemplateEditor {
  readonly template = input.required<ChecklistTemplate>();
  /** Emits the template as saved. */
  readonly saved = output<ChecklistTemplate>();

  private readonly api = inject(FloorApi);
  private readonly i18n = inject(I18nService);

  protected readonly max = MAX_TEMPLATE_ITEMS;
  protected readonly labelMax = TEMPLATE_LABEL_MAX;
  protected readonly items = signal<EditorItem[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Shows the problems only after a save was tried, so a fresh row does not scold. */
  private readonly tried = signal(false);
  private nextKey = 1;
  /** The saved template the list was built from (changes when the parent passes a new one). */
  private readonly base = signal<ChecklistTemplate | null>(null);

  protected readonly kindKey = computed<MessageKey>(() => `backoffice.checklists.kind.${this.template().kind}`);
  protected readonly validation = computed(() => {
    const v = validateTemplate(this.items());
    return this.tried() ? v : { ...v, list: null, items: {} };
  });
  protected readonly dirty = computed(() => {
    const b = this.base();
    return !!b && templateChanged(b.items, this.items());
  });

  constructor() {
    effect(() => {
      const t = this.template();
      untracked(() => this.reset(t));
    });
  }

  private reset(t: ChecklistTemplate): void {
    this.base.set(t);
    this.items.set(toEditorItems(t.items, this.nextKey));
    this.nextKey += t.items.length;
    this.tried.set(false);
  }

  protected setLabel(key: number, event: Event): void {
    const label = (event.target as HTMLInputElement).value;
    this.items.update((l) => l.map((i) => (i.key === key ? { ...i, label } : i)));
  }

  protected setRequired(key: number, event: Event): void {
    const required = (event.target as HTMLInputElement).checked;
    this.items.update((l) => l.map((i) => (i.key === key ? { ...i, required } : i)));
  }

  protected add(): void {
    if (this.items().length >= this.max) return;
    this.items.update((l) => [...l, { key: this.nextKey++, label: '', required: true }]);
  }

  protected remove(key: number): void {
    this.items.update((l) => l.filter((i) => i.key !== key));
  }

  protected move(index: number, delta: -1 | 1): void {
    this.items.update((l) => moveItem(l, index, delta));
  }

  protected discard(): void {
    const b = this.base();
    if (b) this.reset(b);
    this.error.set(null);
  }

  protected save(): void {
    this.tried.set(true);
    if (!validateTemplate(this.items()).valid || this.busy()) return;
    const current = this.base() ?? this.template();
    this.busy.set(true);
    this.error.set(null);
    this.api.saveTemplate(current.kind, { items: toTemplateItems(this.items()), version: current.version }).subscribe({
      next: (t) => {
        this.busy.set(false);
        this.reset(t);
        this.saved.emit(t);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
        if (isStale(e)) this.reloadStored(current.kind);
      },
    });
  }

  /** Someone saved the template since this page loaded: show the stored list. */
  private reloadStored(kind: ChecklistTemplate['kind']): void {
    this.api.templates().subscribe({
      next: (list) => {
        const stored = list.find((t) => t.kind === kind);
        if (stored) this.reset(stored);
      },
      error: () => undefined,
    });
  }
}
