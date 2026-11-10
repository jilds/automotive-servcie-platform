import type { MessageKey } from '../core/i18n/messages.en';
import { IMMEDIATE_METHODS, Invoice, InvoiceKind, InvoiceStatus, IssueOutcome, Payment, PaymentMethod, PaymentState, PaymentStatusOfInvoice } from './billing.models';

/** One request id per attempt: made when the dialog or form opens and reused on every retry of that attempt. */
export function newRequestId(): string {
  return crypto.randomUUID();
}

/** The HTTP status of an issue or credit note call as the outcome the screen reports. */
export function outcomeOf(status: number): IssueOutcome {
  return status === 201 ? 'issued' : status === 200 ? 'replay' : 'stored';
}

/** What is still open for payment: total minus what is paid and what is waiting (the server's default amount). */
export function leftCents(i: Pick<Invoice, 'totalCents' | 'paidCents' | 'pendingCents'>): number {
  return Math.max(0, i.totalCents - i.paidCents - i.pendingCents);
}

/** Balance due: the total minus what is paid, never negative. */
export function balanceDue(i: Pick<Invoice, 'totalCents' | 'paidCents'>): number {
  return Math.max(0, i.totalCents - i.paidCents);
}

/** The actions the state machine allows on a document now (the server checks them again). */
export interface InvoiceActions {
  retry: boolean;
  discard: boolean;
  void: boolean;
  creditNote: boolean;
  pay: boolean;
  pdf: boolean;
}

export function actionsOf(i: Pick<Invoice, 'kind' | 'status' | 'creditNote' | 'totalCents' | 'paidCents' | 'pendingCents'>): InvoiceActions {
  const issuedInvoice = i.kind === 'INVOICE' && i.status === 'ISSUED' && !i.creditNote;
  return {
    retry: i.status === 'PENDING' || i.status === 'FAILED',
    discard: i.status === 'FAILED',
    void: issuedInvoice,
    creditNote: issuedInvoice,
    pay: issuedInvoice && leftCents(i) > 0,
    pdf: i.status === 'ISSUED' || i.status === 'VOIDED',
  };
}

/** True when a PENDING, ISSUED or FAILED invoice (not a credit note) blocks a new one on the work order. */
export function activeInvoice(invoices: readonly Pick<Invoice, 'kind' | 'status'>[]): boolean {
  return invoices.some((i) => i.kind === 'INVOICE' && (i.status === 'PENDING' || i.status === 'ISSUED' || i.status === 'FAILED'));
}

/** True when an invoice can be started from this work order: DELIVERED with no active invoice. */
export function canIssue(orderStatus: string, invoices: readonly Pick<Invoice, 'kind' | 'status'>[]): boolean {
  return orderStatus === 'DELIVERED' && !activeInvoice(invoices);
}

export function isImmediate(method: PaymentMethod): boolean {
  return IMMEDIATE_METHODS.includes(method);
}

/** Payments still waiting for the customer (Multibanco or MB WAY). */
export function pendingPayments(payments: readonly Payment[]): Payment[] {
  return payments.filter((p) => p.status === 'PENDING');
}

/** MB WAY requests are polled every few seconds while they wait. */
export function pollable(payments: readonly Payment[]): Payment[] {
  return pendingPayments(payments).filter((p) => p.method === 'MBWAY');
}

export const POLL_MS = 3000;

/** The Multibanco reference as `123 456 789`. */
export function formatReference(reference: string): string {
  return reference.replace(/\s+/g, '').replace(/(\d{3})(?=\d)/g, '$1 ');
}

/** Euros typed by a person ("12,50" or "12.5") as cents; null when it is not an amount with at most 2 decimals. */
export function parseEuros(text: string): number | null {
  const normal = text.trim().replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normal)) return null;
  return Math.round(Number(normal) * 100);
}

/** Cents as the plain euro text a field starts with ("49,20"). */
export function eurosText(cents: number): string {
  return (cents / 100).toFixed(2).replace('.', ',');
}

export const KIND_TEXT: Record<InvoiceKind, MessageKey> = { INVOICE: 'billing.kind.INVOICE', CREDIT_NOTE: 'billing.kind.CREDIT_NOTE' };

export const STATUS_BADGE: Record<InvoiceStatus, string> = {
  PENDING: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  ISSUED: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  FAILED: 'bg-danger-subtle text-danger-emphasis border border-danger-subtle',
  VOIDED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
  DISCARDED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

export const PAYMENT_STATUS_BADGE: Record<PaymentStatusOfInvoice, string> = {
  UNPAID: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  PARTIALLY_PAID: 'bg-info-subtle text-info-emphasis border border-info-subtle',
  PAID: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  CREDITED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

export const PAYMENT_BADGE: Record<PaymentState, string> = {
  PENDING: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  PAID: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  FAILED: 'bg-danger-subtle text-danger-emphasis border border-danger-subtle',
  EXPIRED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
  CANCELLED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

export const statusText = (s: InvoiceStatus): MessageKey => `billing.status.${s}`;
export const paymentStatusText = (s: PaymentStatusOfInvoice): MessageKey => `billing.paymentStatus.${s}`;
export const paymentStateText = (s: PaymentState): MessageKey => `billing.payment.state.${s}`;
export const methodText = (m: PaymentMethod): MessageKey => `billing.method.${m}`;

/** Codes that mean the document or payment changed under this screen: reload it. */
export const RELOAD_CODES: ReadonlySet<string> = new Set([
  'stale-data',
  'invoice-not-retryable',
  'invoice-not-discardable',
  'invoice-not-voidable',
  'invoice-not-issued',
  'invoice-has-payments',
  'try-again',
  'credit-note-exists',
  'payment-not-pending',
  'payment-exceeds-balance',
]);

/** Saves a blob under a file name through a temporary link (the PDF, fetched with the token). */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** A safe file name from a document number such as `FT SANDBOX/3`. */
export function pdfName(number: string | null, id: string): string {
  const base = (number ?? id).replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '');
  return `${base || id}.pdf`;
}

/** `showModal` where the browser has it, an open attribute otherwise (old test environments). */
export function openDialog(dialog: HTMLDialogElement): void {
  if (typeof dialog.showModal === 'function') dialog.showModal();
  else dialog.setAttribute('open', '');
}

export function closeDialog(dialog: HTMLDialogElement): void {
  if (typeof dialog.close === 'function') dialog.close();
  else dialog.removeAttribute('open');
}

/** A DISCARDED document the provider issued anyway: it exists there and must be voided there. */
export function issuedAfterDiscard(i: Pick<Invoice, 'status' | 'number'> & { failure?: { code: string } | null }): boolean {
  return i.status === 'DISCARDED' && (i.failure?.code === 'issued-after-discard' || (!!i.number && i.failure === undefined));
}

/** Money to return: the late-payment wording only for a voided invoice, a neutral one for a credited invoice. */
export function refundAlertKey(status: InvoiceStatus): MessageKey {
  return status === 'VOIDED' ? 'billing.refund.alertLate' : 'billing.refund.alert';
}

/** The request id is replaced after the server says it belongs to a closed document or another order. */
export const REQUEST_ID_REUSED = 'request-id-reused';
