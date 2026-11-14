import { ComponentFixture } from '@angular/core/testing';

import { BillingSettings, Invoice, InvoiceSummary, Payment } from './billing.models';

export const invoice = (over: Partial<Invoice> = {}): Invoice => ({
  id: 'i1', kind: 'INVOICE', status: 'ISSUED', workOrderId: 'w1', workOrderNumber: 12, creditOfId: null, creditOfNumber: null,
  number: 'FT SANDBOX/3', series: 'SANDBOX', sequence: 3, atcud: 'SANDBOX-3', qrPayload: null, hashFragment: null,
  issuedAt: '2026-11-09T10:00:00Z', provider: 'sandbox', sandbox: true,
  issuer: { name: 'Oficina Azul', nif: '509999999', country: 'PT' },
  customer: { name: 'Ana Silva', nif: '123456789', finalConsumer: false, address: { street: 'Rua A 1', detail: null, postalCode: '1100-053', locality: 'Lisboa', country: 'PT' } },
  plate: 'AA-00-AA',
  lines: [{ id: 'l1', position: 1, type: 'LABOR', description: 'Mudança de óleo', quantity: 1, unitPriceCents: 4000, discountPercent: 0, vatRate: 23, netCents: 4000, vatCents: 920, totalCents: 4920 }],
  vatSummary: [{ vatRate: 23, netCents: 4000, vatCents: 920 }],
  netCents: 4000, vatCents: 920, totalCents: 4920, paidCents: 0, pendingCents: 0, balanceCents: 4920, overpaidCents: 0, refundDueCents: 0,
  paymentStatus: 'UNPAID', payments: [], creditNote: null, reason: null, closedAt: null, failure: null,
  createdAt: '2026-11-09T09:59:00Z', version: 2,
  ...over,
});

export const payment = (over: Partial<Payment> = {}): Payment => ({
  id: 'p1', invoiceId: 'i1', method: 'CASH', status: 'PAID', amountCents: 2000, provider: null, multibanco: null, mbway: null, instalment: null,
  expiresAt: null, paidAt: '2026-11-09T10:05:00Z', failureCode: null, note: null, createdAt: '2026-11-09T10:05:00Z', version: 0,
  ...over,
});

export const summary = (over: Partial<InvoiceSummary> = {}): InvoiceSummary => ({
  id: 'i1', kind: 'INVOICE', status: 'ISSUED', number: 'FT SANDBOX/3', workOrderId: 'w1', workOrderNumber: 12, customerName: 'Ana Silva',
  customerNif: '123456789', plate: 'AA-00-AA', issuedAt: '2026-11-09T10:00:00Z', createdAt: '2026-11-09T09:59:00Z', totalCents: 4920,
  balanceCents: 4920, refundDueCents: 0, paymentStatus: 'UNPAID', sandbox: true,
  ...over,
});

export const settings = (over: Partial<BillingSettings> = {}): BillingSettings => ({
  invoicing: { provider: 'sandbox', configured: true, sandbox: true },
  payments: { provider: 'sandbox', configured: true, sandbox: true },
  finalConsumerLimitCents: 100000, currency: 'EUR', methods: ['CASH', 'CARD', 'TRANSFER', 'INSTALMENTS', 'MULTIBANCO', 'MBWAY'],
  multibancoValidDays: 3, mbwayValidMinutes: 5,
  ...over,
});

export const text = (f: ComponentFixture<unknown>) => ((f.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
export const el = <T extends Element = HTMLElement>(f: ComponentFixture<unknown>, selector: string) => (f.nativeElement as HTMLElement).querySelector(selector) as T | null;
export const typeInto = (input: HTMLInputElement | HTMLTextAreaElement, value: string) => {
  input.value = value;
  input.dispatchEvent(new Event('input'));
};
