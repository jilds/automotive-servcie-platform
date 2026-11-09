import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, input, signal, untracked } from '@angular/core';

import { WorkOrder } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { FloorApi } from './floor-api.service';
import { canAnswerChecklist, mergeAnswer, deliveryBlocked, progress, RESULT_BADGE, RESULT_OUTLINE, withChecklist } from './floor-logic';
import { CHECKLIST_KINDS, CHECKLIST_RESULTS, Checklist, ChecklistItem, ChecklistKind, ChecklistResult } from './floor.models';

/** Longest note the API keeps. */
export const NOTE_MAX = 300;

/**
 * Check-in and delivery checklists of a work order. Every role reads them; the assigned mechanic, the owner and the
 * front desk start a checklist (a copy of the workshop's template) and answer its items OK, Attention or N/A with a
 * note, while the order is not delivered or cancelled. When the workshop requires the delivery checklist, the screen
 * says so, and explains `checklist-incomplete` after a refused move to Delivered.
 */
@Component({
  selector: 'app-checklist-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="checklists-title" id="floor-checklists">
      <div class="card-body">
        <h2 class="card-title" id="checklists-title">{{ 'floor.check.title' | t }}</h2>

        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }
        @if (gateInfo()) {
          <app-alert [tone]="gateRefused() ? 'warning' : 'info'"><span data-test="gate">{{ (gateRefused() ? 'floor.check.gateRefused' : 'floor.check.gate') | t }}</span></app-alert>
        }

        @for (kind of kinds; track kind) {
          <div class="mb-4" [attr.data-kind]="kind">
            <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
              <h3 class="h5 me-auto mb-0">{{ kindKey(kind) | t }}</h3>
              @if (find(kind); as c) {
                <span class="badge" [class]="progress(c).complete ? 'text-bg-success' : 'text-bg-secondary'" data-test="progress">
                  {{ 'floor.check.progress' | t: { done: progress(c).answered, total: progress(c).total } }}
                </span>
              } @else if (canAnswer()) {
                <button type="button" class="btn btn-sm btn-primary" [attr.data-test]="'start-' + kind" [disabled]="starting()" (click)="start(kind)">
                  <app-icon name="check-in" [size]="16" />{{ startKey(kind) | t }}
                </button>
              }
            </div>

            @if (find(kind); as c) {
              <div class="progress mb-3" role="progressbar" [attr.aria-label]="kindKey(kind) | t" [attr.aria-valuenow]="progress(c).percent" aria-valuemin="0" aria-valuemax="100">
                <div class="progress-bar" [class.bg-success]="progress(c).complete" [style.width.%]="progress(c).percent"></div>
              </div>
              <ul class="list-group">
                @for (item of c.items; track item.id) {
                  <li class="list-group-item" [attr.data-item]="item.id">
                    <div class="d-flex flex-wrap align-items-start gap-2 mb-2">
                      <span class="me-auto text-break fw-medium">{{ item.label }}</span>
                      <span class="badge" [class]="item.required ? 'text-bg-light border' : 'text-bg-secondary'">{{ (item.required ? 'floor.check.required' : 'floor.check.optional') | t }}</span>
                    </div>
                    <div class="btn-group w-100" role="group" [attr.aria-label]="item.label">
                      @for (r of results; track r) {
                        <button type="button" class="btn" [class]="item.result === r ? resultOn[r] : resultOff[r]" [attr.data-result]="r"
                          [attr.aria-pressed]="item.result === r" [disabled]="!canAnswer() || saving().has(item.id)" (click)="answer(c, item, r)">
                          {{ resultKey(r) | t }}
                        </button>
                      }
                    </div>
                    @if (canAnswer()) {
                      <label class="visually-hidden" [for]="'note-' + item.id">{{ 'floor.check.note' | t }}</label>
                      <input [id]="'note-' + item.id" class="form-control form-control-sm mt-2" type="text" [attr.maxlength]="noteMax"
                        [value]="noteOf(item)" [placeholder]="'floor.check.notePlaceholder' | t" [disabled]="saving().has(item.id)"
                        (input)="draft(item, $event)" (blur)="saveNote(c, item)" (keydown.enter)="saveNote(c, item)" />
                    } @else if (item.note) {
                      <p class="small mt-2 mb-0 text-break">{{ item.note }}</p>
                    }
                    @if (item.answeredByName) {
                      <div class="small text-body-secondary mt-1">{{ 'floor.check.answeredBy' | t: { name: item.answeredByName, date: (item.answeredAt | date: 'dd/MM/yyyy HH:mm') ?? '' } }}</div>
                    }
                  </li>
                }
              </ul>
            } @else if (!canAnswer()) {
              <p class="small text-body-secondary mb-0">{{ 'floor.check.notStarted' | t }}</p>
            }
          </div>
        }
      </div>
    </section>
  `,
})
export class ChecklistPanel {
  readonly workOrder = input.required<WorkOrder>();
  /** Set by the screen when the move to Delivered was refused with `checklist-incomplete`. */
  readonly gateRefused = input(false);

  private readonly api = inject(FloorApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly kinds = CHECKLIST_KINDS;
  protected readonly results = CHECKLIST_RESULTS;
  protected readonly resultOn = RESULT_BADGE;
  protected readonly resultOff = RESULT_OUTLINE;
  protected readonly noteMax = NOTE_MAX;
  protected readonly progress = progress;

  protected readonly checklists = signal<Checklist[]>([]);
  protected readonly required = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly starting = signal(false);
  /** Items being saved: only the same item is locked, so quick taps on other items are not lost. */
  protected readonly saving = signal<ReadonlySet<string>>(new Set());
  /** Notes typed and not yet saved, by item id. */
  private readonly drafts = signal<Record<string, string>>({});

  private readonly orderId = computed(() => this.workOrder().id);
  protected readonly canAnswer = computed(() =>
    canAnswerChecklist({ role: this.auth.account()?.role ?? null, userId: this.auth.account()?.id ?? null }, this.workOrder()),
  );
  /** The workshop requires the delivery checklist and it is not complete yet. */
  protected readonly gateInfo = computed(() => deliveryBlocked(this.required(), this.checklists()) && this.workOrder().status !== 'DELIVERED' && this.workOrder().status !== 'CANCELLED');

  constructor() {
    // Keyed by the order's id, so a reload of the order (a new object) does not read the checklists again.
    effect(() => {
      const id = this.orderId();
      untracked(() => this.load(id));
    });
  }

  /** Scrolls to the panel and moves focus to its heading, for the "open the delivery checklist" shortcut. */
  reveal(): void {
    const el = this.host.nativeElement.querySelector<HTMLElement>('#checklists-title');
    el?.setAttribute('tabindex', '-1');
    el?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
    el?.focus({ preventScroll: true });
  }

  /** Reads the checklists again (the screen calls it after a refused move). */
  reload(): void {
    this.load(this.workOrder().id);
  }

  private load(workOrderId: string): void {
    this.api.checklists(workOrderId).subscribe({
      next: (r) => {
        this.required.set(r.deliveryChecklistRequired);
        this.checklists.set(r.checklists);
      },
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }

  protected find(kind: ChecklistKind): Checklist | undefined {
    return this.checklists().find((c) => c.kind === kind);
  }

  protected kindKey(kind: ChecklistKind): MessageKey {
    return `floor.check.kind.${kind}`;
  }

  protected startKey(kind: ChecklistKind): MessageKey {
    return `floor.check.start.${kind}`;
  }

  protected resultKey(r: ChecklistResult): MessageKey {
    return `floor.result.${r}`;
  }

  protected noteOf(item: ChecklistItem): string {
    return this.drafts()[item.id] ?? item.note ?? '';
  }

  protected draft(item: ChecklistItem, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.drafts.update((d) => ({ ...d, [item.id]: value }));
  }

  start(kind: ChecklistKind): void {
    if (this.starting()) return;
    this.starting.set(true);
    this.error.set(null);
    this.api.startChecklist(this.workOrder().id, kind).subscribe({
      next: (c) => {
        this.starting.set(false);
        this.checklists.update((l) => withChecklist(l, c));
      },
      error: (e: unknown) => {
        this.starting.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  private setSaving(id: string, on: boolean): void {
    this.saving.update((s) => {
      const next = new Set(s);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  /** Answers one item with the note typed for it (or the stored one). */
  answer(c: Checklist, item: ChecklistItem, result: ChecklistResult): void {
    if (this.saving().has(item.id)) return;
    const note = this.noteOf(item).trim() || null;
    this.setSaving(item.id, true);
    this.error.set(null);
    this.api.answer(this.workOrder().id, c.kind, item.id, { result, note }).subscribe({
      next: (saved) => {
        this.setSaving(item.id, false);
        this.drafts.update((d) => {
          const { [item.id]: _gone, ...rest } = d;
          return rest;
        });
        // Take only this item from the answer: answers to other items may be in flight and their results must stay.
        this.checklists.update((l) => withChecklist(l, mergeAnswer(l.find((x) => x.kind === saved.kind), saved, item.id)));
      },
      error: (e: unknown) => {
        this.setSaving(item.id, false);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  /** A note edited after the item was answered is saved with the same result; before that it waits for the answer. */
  protected saveNote(c: Checklist, item: ChecklistItem): void {
    const draft = this.drafts()[item.id];
    if (draft === undefined || !item.result) return;
    if (draft.trim() === (item.note ?? '')) return;
    this.answer(c, item, item.result);
  }
}
