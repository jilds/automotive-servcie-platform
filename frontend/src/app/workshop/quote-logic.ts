import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import type { QuoteStatus, WorkOrder, WorkOrderStatus } from '../core/api/models';
import type { MessageKey } from '../core/i18n/messages.en';
import type { DecisionRequest, QuoteDecision } from './quote.models';

/** Longest staff note or customer rejection reason the server takes. */
export const NOTE_MAX = 500;

/** Work order statuses a quote can be sent from (else 409 `work-order-not-quotable`). */
export const QUOTABLE: readonly WorkOrderStatus[] = ['DIAGNOSTICS', 'AWAITING_APPROVAL'];

/** The server's rule for sending: a quotable status and at least one line. */
export function canSendQuote(w: Pick<WorkOrder, 'status' | 'lines'>): boolean {
  return QUOTABLE.includes(w.status) && w.lines.length > 0;
}

/** The latest quote no longer stands (lines changed, or it ran out): the customer needs a new one. */
export function needsNewQuote(status: QuoteStatus | null | undefined): boolean {
  return status === 'SUPERSEDED' || status === 'EXPIRED';
}

/** The customer's link: `<origin>/w/{slug}/quote/{token}`. */
export function quoteLink(origin: string, slug: string, token: string): string {
  return `${origin.replace(/\/$/, '')}/w/${encodeURIComponent(slug)}/quote/${encodeURIComponent(token)}`;
}

/** Badge colours of a quote status (Bootstrap tint pairs that follow the colour mode). */
export const QUOTE_BADGE: Record<QuoteStatus, string> = {
  SENT: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  APPROVED: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  REJECTED: 'bg-danger-subtle text-danger-emphasis border border-danger-subtle',
  SUPERSEDED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
  EXPIRED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

/** Label of the quote badge on the work order and the board ("Quote sent", ...): core texts. */
export function quoteBadgeKey(status: QuoteStatus): MessageKey {
  return `quote.badge.${status}`;
}

/** The board shows a badge for every status except SUPERSEDED (the front desk sees that on the work order). */
export function boardQuoteStatus(status: QuoteStatus | null | undefined): QuoteStatus | null {
  return status && status !== 'SUPERSEDED' ? status : null;
}

/** Rejects text that is only whitespace (the server trims and requires 1..500 characters). */
export const notBlank: ValidatorFn = (c: AbstractControl): ValidationErrors | null =>
  typeof c.value === 'string' && c.value.length > 0 && !c.value.trim() ? { required: true } : null;

/** A decision the front desk records for the customer: approved or rejected, with a required note. */
export function decisionForm() {
  return new FormGroup({
    decision: new FormControl<QuoteDecision | null>(null, Validators.required),
    note: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(NOTE_MAX)] }),
  });
}

export type DecisionForm = ReturnType<typeof decisionForm>;

export function toDecisionRequest(form: DecisionForm, version: number): DecisionRequest {
  const v = form.getRawValue();
  return { decision: v.decision as QuoteDecision, note: v.note.trim(), version };
}

/** Copies text to the clipboard; false when the browser refuses (the user then copies it by hand). */
export async function copyText(text: string, clipboard: Pick<Clipboard, 'writeText'> | undefined = globalThis.navigator?.clipboard): Promise<boolean> {
  if (!clipboard) return false;
  try {
    await clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
