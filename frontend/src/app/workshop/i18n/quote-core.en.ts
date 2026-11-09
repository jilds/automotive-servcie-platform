/**
 * English quote texts shown outside the quote routes: the quote badge on the board card and the error codes.
 * Eager: spread into `messages.en.ts`, so keep it small (initial bundle budget).
 */
export const quoteCoreEn = {
  'quote.badge.SENT': 'Quote sent',
  'quote.badge.APPROVED': 'Quote approved',
  'quote.badge.REJECTED': 'Quote rejected',
  'quote.badge.SUPERSEDED': 'Quote out of date',
  'quote.badge.EXPIRED': 'Quote expired',
  'error.quote-not-found': 'Quote not found.',
  'error.quote-not-open': 'This quote has already been decided or replaced and can no longer change.',
  'error.quote-expired': 'This quote has expired.',
  'error.work-order-not-quotable': 'A quote can only be sent in Diagnostics or Awaiting approval, with at least one line.',
  'error.approval-pending': 'This job cannot leave Awaiting approval until the customer has answered the quote. Only the owner can override.',
  'workOrder.approvalPending': "Waiting for the customer's answer to the quote. Only the owner can move this job before then.",
  'error.decision-note-required': 'Add a note saying how the customer decided.',
} as const;

export type QuoteCoreKey = keyof typeof quoteCoreEn;
