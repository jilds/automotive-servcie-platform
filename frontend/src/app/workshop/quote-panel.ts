import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { firstValueFrom, Subscription } from 'rxjs';

import { WorkOrder } from '../core/api/models';
import { applyProblem, fieldMessage, problemCode, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IdentityApi } from '../identity/identity-api.service';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { TenantThemeService } from '../shared/theme.service';
import { QuoteApi } from './quote-api.service';
import { canSendQuote, copyText, decisionForm, NOTE_MAX, needsNewQuote, QUOTE_BADGE, quoteLink, toDecisionRequest } from './quote-logic';
import { Quote, QuoteStatus } from './quote.models';

/** The link of the quote just sent: the raw token is in the send answer only, so it is shown once. */
interface SentLink {
  quoteId: string;
  number: number;
  link: string | null;
  emailed: boolean;
}

/** Codes after which the stored quote changed under this screen: the work order is reloaded. */
const RELOAD_CODES = new Set(['quote-not-open', 'quote-expired', 'stale-data', 'work-order-not-quotable', 'quote-not-found']);

/**
 * Quotes of a work order, for the owner and the front desk (mechanics never get this panel or its amounts): send a
 * quote and copy the customer's link, send again when the lines changed or it expired, record a decision given by
 * phone, and the history. After a send or a decision the work order is reloaded (`changed`), since its status moves.
 */
@Component({
  selector: 'app-quote-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="quote-title">
      <div class="card-body">
        <div class="d-flex flex-wrap align-items-center gap-3 mb-2">
          <h2 class="card-title me-auto mb-0" id="quote-title">{{ 'quote.panel.title' | t }}</h2>
          @if (!stale()) {
            <button type="button" class="btn btn-sm btn-primary" data-test="send" [disabled]="!canSend() || busy()" [attr.aria-busy]="busy()" (click)="send()">
              <app-icon name="send" [size]="16" />{{ (latest()?.status === 'SENT' ? 'quote.sendNew' : 'quote.send') | t }}
            </button>
          }
        </div>
        @if (!canSend()) {
          <p class="small text-body-secondary mb-3">{{ 'quote.sendDisabled' | t }}</p>
        } @else if (latest()?.status === 'SENT') {
          <p class="small text-body-secondary mb-3">{{ 'quote.sendNewHelp' | t }}</p>
        } @else if (!latest()) {
          <p class="small text-body-secondary mb-3">{{ 'quote.panel.intro' | t }}</p>
        }

        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }
        @if (notice()) {
          <app-alert tone="success">{{ notice() }}</app-alert>
        }

        @if (shownLink(); as s) {
          <div class="border rounded-3 p-3 mb-3 bg-body-tertiary" data-test="sent">
            <p class="fw-semibold mb-2">{{ 'quote.sent.title' | t: { number: s.number } }}</p>
            @if (s.link; as link) {
              <label class="form-label small" for="quote-link">{{ 'quote.sent.link' | t }}</label>
              <div class="input-group mb-2">
                <input id="quote-link" class="form-control font-monospace" type="text" readonly [value]="link" (focus)="select($event)" />
                <button type="button" class="btn btn-outline-primary" data-test="copy" (click)="copy(link)"><app-icon name="copy" [size]="16" />{{ 'quote.sent.copy' | t }}</button>
              </div>
              <p class="small mb-1" aria-live="polite">{{ copied() === true ? ('quote.sent.copied' | t) : copied() === false ? ('quote.sent.copyFailed' | t) : '' }}</p>
            } @else {
              <app-alert tone="warning">{{ 'quote.sent.noLink' | t }}</app-alert>
            }
            <p class="small mb-1" data-test="emailed">{{ (s.emailed ? 'quote.sent.emailed' : 'quote.sent.notEmailed') | t }}</p>
            <p class="small text-body-secondary mb-0">{{ 'quote.sent.once' | t }}</p>
          </div>
        }

        @if (stale(); as q) {
          <app-alert tone="warning">
            <div class="d-flex flex-wrap align-items-center gap-2" data-test="stale">
              <span class="me-auto">{{ staleKey(q.status) | t: { number: q.number } }}</span>
              <button type="button" class="btn btn-sm btn-warning" data-test="send-again" [disabled]="!canSend() || busy()" (click)="send()">
                <app-icon name="send" [size]="16" />{{ 'quote.sendAgain' | t }}
              </button>
            </div>
          </app-alert>
        }

        @if (openQuote(); as q) {
          <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
            <span class="small me-auto">{{ 'quote.open' | t: { number: q.number, date: (q.validUntil | date: 'dd/MM/yyyy HH:mm') } }}</span>
            @if (!deciding()) {
              <button type="button" class="btn btn-sm btn-outline-secondary" data-test="record" (click)="startDecision()">{{ 'quote.decision.record' | t }}</button>
            }
          </div>
          @if (deciding()) {
            <form [formGroup]="decision" (ngSubmit)="decide(q)" class="border rounded-3 p-3 mb-3" novalidate data-test="decision-form">
              <fieldset class="mb-3">
                <legend class="form-label fs-6 fw-semibold mb-1">{{ 'quote.decision.title' | t: { number: q.number } }}</legend>
                <p class="small text-body-secondary mb-2">{{ 'quote.decision.help' | t }}</p>
                <div class="d-flex flex-wrap gap-3">
                  @for (d of decisions; track d) {
                    <div class="form-check">
                      <input class="form-check-input" type="radio" [id]="'qd-' + d" formControlName="decision" [value]="d" [class.is-invalid]="invalid('decision')" />
                      <label class="form-check-label" [for]="'qd-' + d">{{ decisionKey(d) | t }}</label>
                    </div>
                  }
                </div>
                @if (invalid('decision')) {
                  <div class="invalid-feedback d-block">{{ 'quote.decision.choiceRequired' | t }}</div>
                }
              </fieldset>
              <label class="form-label" for="qd-note">{{ 'quote.decision.note' | t }}</label>
              <textarea id="qd-note" class="form-control" rows="2" formControlName="note" [attr.maxlength]="noteMax"
                [placeholder]="'quote.decision.notePlaceholder' | t" [class.is-invalid]="invalid('note')"></textarea>
              @if (invalid('note')) {
                <div class="invalid-feedback">{{ noteMessage() | t }}</div>
              }
              <div class="d-flex flex-wrap justify-content-end gap-2 mt-3">
                <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="deciding.set(false)">{{ 'common.cancel' | t }}</button>
                <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">{{ 'quote.decision.save' | t }}</button>
              </div>
            </form>
          }
        }

        <h3 class="h6 mt-3 mb-2">{{ 'quote.history' | t }}</h3>
        <ul class="list-group" data-test="history">
          @for (q of quotes(); track q.id) {
            <li class="list-group-item">
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="fw-semibold">{{ 'quote.number' | t: { number: q.number } }}</span>
                <span class="badge" [class]="badge(q.status)">{{ statusKey(q.status) | t }}</span>
                <span class="ms-auto fw-semibold text-nowrap">{{ q.totalCents | money }}</span>
              </div>
              <div class="small text-body-secondary">
                {{ 'quote.sentAt' | t: { date: (q.sentAt | date: 'dd/MM/yyyy HH:mm'), name: q.sentByName ?? '—' } }} ·
                {{ 'quote.validUntil' | t: { date: (q.validUntil | date: 'dd/MM/yyyy HH:mm') } }} ·
                {{ 'quote.lines' | t: { count: q.lines.length } }}
              </div>
              @if (q.decidedChannel && q.decidedAt) {
                <div class="small mt-1">
                  {{ (q.decidedChannel === 'CUSTOMER' ? 'quote.decidedBy.CUSTOMER' : 'quote.decidedBy.STAFF') | t: { date: (q.decidedAt | date: 'dd/MM/yyyy HH:mm'), name: (q.decidedChannel === 'CUSTOMER' ? q.decidedName : q.decidedByName) ?? '—' } }}
                </div>
              }
              @if (q.decidedNote) {
                <div class="small text-break">{{ 'quote.note' | t: { note: q.decidedNote } }}</div>
              }
            </li>
          } @empty {
            <li class="list-group-item small text-body-secondary">{{ (loading() ? 'common.loading' : 'quote.history.empty') | t }}</li>
          }
        </ul>
      </div>
    </section>
  `,
})
export class QuotePanel {
  private readonly api = inject(QuoteApi);
  private readonly identity = inject(IdentityApi);
  private readonly theme = inject(TenantThemeService);
  private readonly i18n = inject(I18nService);

  readonly workOrder = input.required<WorkOrder>();
  /** Asks the parent to reload the work order (its status and quote summary moved). */
  readonly changed = output<void>();

  protected readonly decisions = ['APPROVED', 'REJECTED'] as const;
  protected readonly noteMax = NOTE_MAX;
  protected readonly quotes = signal<Quote[]>([]);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly sent = signal<SentLink | null>(null);
  /** null: not tried; true / false: the last copy worked or not. */
  protected readonly copied = signal<boolean | null>(null);
  protected readonly deciding = signal(false);
  protected readonly decision = decisionForm();

  protected readonly canSend = computed(() => canSendQuote(this.workOrder()));
  /** The latest quote's summary on the work order (the authority on its status). */
  protected readonly latest = computed(() => this.workOrder().quote ?? null);
  /** The latest quote, when it no longer stands and a new one is needed. */
  protected readonly stale = computed(() => {
    const q = this.latest();
    return q && needsNewQuote(q.status) ? q : null;
  });
  /** The latest quote while it waits for the customer, with its version for a decision. */
  protected readonly openQuote = computed(() => {
    const latest = this.latest();
    if (latest?.status !== 'SENT') return null;
    return this.quotes().find((q) => q.id === latest.id) ?? null;
  });
  /** The link of the quote just sent, while that quote is still the open one. */
  protected readonly shownLink = computed(() => {
    const s = this.sent();
    const latest = this.latest();
    return s && latest?.id === s.quoteId && latest.status === 'SENT' ? s : null;
  });

  private request: Subscription | null = null;

  constructor() {
    // Reload the history whenever the latest quote changes (a send, a decision, or a line change superseding it).
    effect(() => {
      const w = this.workOrder();
      const key = `${w.id}|${w.quote?.id ?? ''}|${w.quote?.status ?? ''}`;
      untracked(() => this.loadHistory(w.id, key));
    });
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  private loadedKey = '';

  private loadHistory(workOrderId: string, key: string): void {
    if (key === this.loadedKey) return;
    this.loadedKey = key;
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.api.list(workOrderId).subscribe({
      next: (list) => {
        this.loading.set(false);
        this.quotes.set(list);
      },
      error: (e: unknown) => {
        this.loading.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  /** Reads the history again even when the work order's quote summary did not change (for example `stale-data`). */
  private reloadHistory(): void {
    this.loadedKey = '';
    const w = this.workOrder();
    this.loadHistory(w.id, `${w.id}|${w.quote?.id ?? ''}|${w.quote?.status ?? ''}`);
  }

  protected statusKey(status: QuoteStatus): MessageKey {
    return `quote.status.${status}`;
  }

  protected staleKey(status: QuoteStatus): MessageKey {
    return status === 'EXPIRED' ? 'quote.stale.EXPIRED' : 'quote.stale.SUPERSEDED';
  }

  protected decisionKey(d: 'APPROVED' | 'REJECTED'): MessageKey {
    return `quote.decision.${d}`;
  }

  protected badge(status: QuoteStatus): string {
    return QUOTE_BADGE[status];
  }

  protected select(event: Event): void {
    (event.target as HTMLInputElement).select();
  }

  async send(): Promise<void> {
    if (this.busy() || !this.canSend()) return;
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.copied.set(null);
    this.deciding.set(false);
    try {
      const answer = await firstValueFrom(this.api.send(this.workOrder().id));
      const slug = await this.slug();
      this.sent.set({
        quoteId: answer.quote.id,
        number: answer.quote.number,
        link: slug ? quoteLink(location.origin, slug, answer.statusToken) : null,
        emailed: answer.customerHasEmail,
      });
      this.changed.emit();
    } catch (e: unknown) {
      this.error.set(this.i18n.error(e));
      if (RELOAD_CODES.has(problemCode(e) ?? '')) this.changed.emit();
    } finally {
      this.busy.set(false);
    }
  }

  async copy(link: string): Promise<void> {
    this.copied.set(await copyText(link));
  }

  protected startDecision(): void {
    this.decision.reset({ decision: null, note: '' });
    this.notice.set(null);
    this.error.set(null);
    this.deciding.set(true);
  }

  protected invalid(control: 'decision' | 'note'): boolean {
    const c = this.decision.controls[control];
    return c.invalid && (c.touched || c.dirty);
  }

  protected noteMessage(): MessageKey {
    return (
      fieldMessage(this.decision.controls.note, { required: 'quote.decision.noteRequired', maxlength: 'quote.decision.noteTooLong' }) ??
      'quote.decision.noteRequired'
    );
  }

  decide(q: Quote): void {
    this.decision.markAllAsTouched();
    if (this.decision.invalid || this.busy()) return;
    const body = toDecisionRequest(this.decision, q.version);
    this.busy.set(true);
    this.error.set(null);
    this.api.decide(this.workOrder().id, q.id, body).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.deciding.set(false);
        this.notice.set(this.i18n.t(saved.status === 'REJECTED' ? 'quote.decision.saved.REJECTED' : 'quote.decision.saved.APPROVED', { number: saved.number }));
        this.changed.emit();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const marked = applyProblem(this.decision, e, { 'decision-note-required': 'note' });
        if (!shownOnFields(e, marked)) this.error.set(this.i18n.error(e));
        if (RELOAD_CODES.has(problemCode(e) ?? '')) {
          this.deciding.set(false);
          this.reloadHistory();
          this.changed.emit();
        }
      },
    });
  }

  /** The workshop's slug for the link: from the signed-in branding the shell loaded, else asked for once. */
  private async slug(): Promise<string | null> {
    const known = this.theme.branding()?.slug;
    if (known) return known;
    try {
      return (await firstValueFrom(this.identity.currentBranding())).slug ?? null;
    } catch {
      return null;
    }
  }
}
