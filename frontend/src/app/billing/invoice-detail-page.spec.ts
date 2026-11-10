import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { I18nService } from '../core/i18n/i18n.service';
import { el, invoice, payment, settings, text, typeInto } from './billing-test-utils';
import { Invoice } from './billing.models';
import { InvoiceDetailPage } from './invoice-detail-page';

describe('InvoiceDetailPage', () => {
  let http: HttpTestingController;

  function open(doc: Invoice, billing = settings()) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(InvoiceDetailPage);
    f.componentRef.setInput('id', doc.id);
    f.detectChanges();
    http.expectOne('/api/v1/billing/settings').flush(billing);
    http.expectOne(`/api/v1/invoices/${doc.id}`).flush(doc);
    f.detectChanges();
    return f;
  }

  /** The page reloads the document after a change. */
  const reload = (f: ReturnType<typeof open>, doc: Invoice) => {
    http.expectOne(`/api/v1/invoices/${doc.id}`).flush(doc);
    f.detectChanges();
  };
  const submitReason = (f: ReturnType<typeof open>, reason: string) => {
    typeInto(el<HTMLTextAreaElement>(f, '#inv-reason')!, reason);
    el<HTMLFormElement>(f, '[data-test="reason-form"]')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  };

  afterEach(() => http.verify());

  it('shows the customer snapshot, the lines, the totals and the sandbox banner', () => {
    const f = open(invoice());
    const t = text(f);
    expect(t).toContain('FT SANDBOX/3');
    expect(el(f, '[data-test="customer-name"]')!.textContent).toContain('Ana Silva');
    expect(t).toContain('123456789');
    expect(t).toContain('Rua A 1');
    expect(t).toContain('1100-053 Lisboa');
    expect(t).toContain('Mudança de óleo');
    expect(el(f, '[data-test="total"]')!.textContent).toMatch(/49[.,]20/);
    expect(el(f, '[data-test="sandbox"]')!.textContent).toContain('not a fiscal document');
    expect(el(f, '[data-test="status"]')!.textContent).toContain('Issued');
    expect(el(f, '[data-test="payment-status"]')!.textContent).toContain('Unpaid');
    expect(el(f, 'div.table-stack')).not.toBeNull();
  });

  it('shows a final consumer without NIF or address', () => {
    const f = open(invoice({ customer: { name: 'Consumidor final', nif: null, finalConsumer: true, address: null } }));
    expect(el(f, '[data-test="customer-name"]')!.textContent).toContain('Final consumer');
    expect(text(f)).toContain('No NIF');
    expect(text(f)).toContain('No address');
  });

  it('offers void, credit note and payments on an issued invoice, and nothing but the PDF on a voided one', () => {
    const f = open(invoice());
    expect(el(f, '[data-test="void"]')).not.toBeNull();
    expect(el(f, '[data-test="credit"]')).not.toBeNull();
    expect(el(f, '[data-test="discard"]')).toBeNull();
    expect(el(f, 'app-payments-panel')).not.toBeNull();
  });

  it('voids an invoice with a reason and the version, then reloads', () => {
    const f = open(invoice());
    el<HTMLButtonElement>(f, '[data-test="void"]')!.click();
    f.detectChanges();
    submitReason(f, '');
    http.expectNone('/api/v1/invoices/i1/void');
    expect(text(f)).toContain('Say why.');

    submitReason(f, '  Wrong customer  ');
    const req = http.expectOne('/api/v1/invoices/i1/void');
    expect(req.request.body).toEqual({ reason: 'Wrong customer', version: 2 });
    req.flush(invoice({ status: 'VOIDED', reason: 'Wrong customer', version: 3 }));
    reload(f, invoice({ status: 'VOIDED', reason: 'Wrong customer', version: 3, paymentStatus: null }));
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('Invoice voided.');
    expect(el(f, '[data-test="status"]')!.textContent).toContain('Voided');
    expect(el(f, '[data-test="void"]')).toBeNull();
    expect(el(f, '[data-test="pdf"]')).not.toBeNull();
    expect(el(f, '[data-test="reason"]')!.textContent).toContain('Wrong customer');
  });

  it('issues a credit note with a request id that stays the same when the attempt is sent again', () => {
    const f = open(invoice());
    el<HTMLButtonElement>(f, '[data-test="credit"]')!.click();
    f.detectChanges();
    submitReason(f, 'Customer returned the part');
    const first = http.expectOne('/api/v1/invoices/i1/credit-notes');
    const id = first.request.body.requestId;
    expect(first.request.body.reason).toBe('Customer returned the part');
    first.flush({ code: 'provider-unavailable', status: 503 }, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(text(f)).toContain('The provider did not answer');

    el<HTMLFormElement>(f, '[data-test="reason-form"]')!.dispatchEvent(new Event('submit'));
    const second = http.expectOne('/api/v1/invoices/i1/credit-notes');
    expect(second.request.body.requestId).toBe(id);
    second.flush(invoice({ id: 'c1', kind: 'CREDIT_NOTE', number: 'NC SANDBOX/1' }), { status: 201, statusText: 'Created' });
    reload(f, invoice({ creditNote: { id: 'c1', number: 'NC SANDBOX/1', status: 'ISSUED' }, paymentStatus: 'CREDITED', balanceCents: 0 }));
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('Credit note NC SANDBOX/1 issued.');
    expect(el(f, '[data-test="credit-note"]')!.textContent).toContain('NC SANDBOX/1');
    expect(el(f, '[data-test="void"]')).toBeNull();
    expect(el(f, '[data-test="credit"]')).toBeNull();
    expect(el(f, '[data-test="credited"]')).not.toBeNull();
  });

  it('reports a 202 credit note as saved but not confirmed', () => {
    const f = open(invoice());
    el<HTMLButtonElement>(f, '[data-test="credit"]')!.click();
    f.detectChanges();
    submitReason(f, 'x');
    http.expectOne('/api/v1/invoices/i1/credit-notes').flush(invoice({ id: 'c1', kind: 'CREDIT_NOTE', status: 'PENDING', number: null }), { status: 202, statusText: 'Accepted' });
    reload(f, invoice({ creditNote: { id: 'c1', number: null, status: 'PENDING' } }));
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('not confirmed yet');
  });

  it('shows a warning, not a success, when a retry ends FAILED or PENDING', () => {
    for (const status of ['FAILED', 'PENDING'] as const) {
      TestBed.resetTestingModule();
      const failed = invoice({ status: 'FAILED', number: null, issuedAt: null, paymentStatus: null });
      const f = open(failed);
      el<HTMLButtonElement>(f, '[data-test="retry"]')!.click();
      http.expectOne('/api/v1/invoices/i1/retry').flush({ ...failed, status });
      reload(f, { ...failed, status });
      expect(el(f, 'app-alert.alert-warning [data-test="notice"]'), status).not.toBeNull();
      expect(el(f, 'app-alert.alert-success')).toBeNull();
    }
  });

  it('shows a success when a retry ends ISSUED', () => {
    const failed = invoice({ status: 'FAILED', number: null, issuedAt: null, paymentStatus: null });
    const f = open(failed);
    el<HTMLButtonElement>(f, '[data-test="retry"]')!.click();
    http.expectOne('/api/v1/invoices/i1/retry').flush(invoice());
    reload(f, invoice());
    expect(el(f, 'app-alert.alert-success [data-test="notice"]')).not.toBeNull();
  });

  it('refuses to void when a payment appeared, reloading the document', () => {
    const f = open(invoice());
    el<HTMLButtonElement>(f, '[data-test="void"]')!.click();
    f.detectChanges();
    submitReason(f, 'x');
    http.expectOne('/api/v1/invoices/i1/void').flush({ code: 'invoice-has-payments', status: 409 }, { status: 409, statusText: 'Conflict' });
    reload(f, invoice({ paidCents: 2000, balanceCents: 2920, paymentStatus: 'PARTIALLY_PAID', payments: [payment()] }));
    expect(text(f)).toContain('Issue a credit note instead of voiding it.');
  });

  it('retries and discards a failed document', () => {
    const failed = invoice({ status: 'FAILED', number: null, issuedAt: null, paymentStatus: null, failure: { code: 'sandbox-rejected', message: 'Rejected', attempts: 1, lastAttemptAt: '2026-11-09T10:00:00Z' } });
    const f = open(failed);
    expect(text(f)).toContain('Provider message: Rejected (attempts: 1)');
    expect(el(f, '[data-test="pdf"]')).toBeNull();
    expect(el(f, 'app-payments-panel')).toBeNull();
    el<HTMLButtonElement>(f, '[data-test="retry"]')!.click();
    http.expectOne('/api/v1/invoices/i1/retry').flush(failed);
    reload(f, failed);

    el<HTMLButtonElement>(f, '[data-test="discard"]')!.click();
    f.detectChanges();
    submitReason(f, 'Customer data was wrong');
    const req = http.expectOne('/api/v1/invoices/i1/discard');
    expect(req.request.body).toEqual({ reason: 'Customer data was wrong', version: 2 });
    req.flush(invoice({ status: 'DISCARDED' }));
    reload(f, invoice({ status: 'DISCARDED', number: null, paymentStatus: null }));
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('Document discarded.');
    expect(el(f, '[data-test="retry"]')).toBeNull();
  });

  it('shows money to return on a voided invoice with late money, with its payment', () => {
    const f = open(invoice({ status: 'VOIDED', paymentStatus: null, paidCents: 2000, refundDueCents: 2000, balanceCents: 0, payments: [payment()] }));
    expect(el(f, '[data-test="refund-alert"]')!.textContent).toMatch(/Money to return to the customer: .*20[.,]00.*arrived after the document was closed/);
    expect(el(f, '[data-test="refund"]')!.textContent).toMatch(/20[.,]00/);
    expect(el(f, '[data-method="CASH"]')).toBeNull();
  });

  it('uses a neutral text for money to return on a credited invoice', () => {
    const f = open(invoice({ creditNote: { id: 'c1', number: 'NC SANDBOX/1', status: 'ISSUED' }, paymentStatus: 'CREDITED', paidCents: 2000, refundDueCents: 2000, balanceCents: 0, payments: [payment()] }));
    const t = el(f, '[data-test="refund-alert"]')!.textContent!;
    expect(t).toMatch(/Money to return to the customer: .*20[.,]00/);
    expect(t).not.toContain('arrived after');
  });

  it('warns prominently about a document issued after it was discarded', () => {
    const f = open(invoice({ status: 'DISCARDED', paymentStatus: null, failure: { code: 'issued-after-discard', message: 'x', attempts: 1, lastAttemptAt: '2026-11-09T10:00:00Z' } }));
    expect(el(f, '[data-test="issued-after-discard"]')!.textContent).toContain('must be voided there');
    expect(el(f, '[data-test="retry"]')).toBeNull();
  });

  it('shows the provider message of a total mismatch and keeps retry and discard', () => {
    const f = open(invoice({ status: 'FAILED', number: null, issuedAt: null, paymentStatus: null, failure: { code: 'provider-total-mismatch', message: 'Total 50.00 differs', attempts: 1, lastAttemptAt: '2026-11-09T10:00:00Z' } }));
    expect(el(f, '[data-test="mismatch"]')).not.toBeNull();
    expect(text(f)).toContain('Total 50.00 differs');
    expect(el(f, '[data-test="retry"]')).not.toBeNull();
    expect(el(f, '[data-test="discard"]')).not.toBeNull();
  });

  it('reloads and says to try again when a discard meets a provider attempt under way', () => {
    const failed = invoice({ status: 'FAILED', number: null, issuedAt: null, paymentStatus: null });
    const f = open(failed);
    el<HTMLButtonElement>(f, '[data-test="discard"]')!.click();
    f.detectChanges();
    submitReason(f, 'x');
    http.expectOne('/api/v1/invoices/i1/discard').flush({ code: 'try-again', status: 409 }, { status: 409, statusText: 'Conflict' });
    reload(f, failed);
    expect(text(f)).toContain('Try again in a moment');
    expect(el(f, '[data-test="reason-form"]')).toBeNull();
  });

  it('makes a new request id for the credit note after request-id-reused', () => {
    const f = open(invoice());
    el<HTMLButtonElement>(f, '[data-test="credit"]')!.click();
    f.detectChanges();
    submitReason(f, 'x');
    const first = http.expectOne('/api/v1/invoices/i1/credit-notes');
    const id = first.request.body.requestId;
    first.flush({ code: 'request-id-reused', status: 409 }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(text(f)).toContain('A new one was prepared');
    expect(el(f, '[data-test="reason-form"]')).not.toBeNull();
    el<HTMLFormElement>(f, '[data-test="reason-form"]')!.dispatchEvent(new Event('submit'));
    const second = http.expectOne('/api/v1/invoices/i1/credit-notes');
    expect(second.request.body.requestId).not.toBe(id);
    second.flush(invoice({ id: 'c1', kind: 'CREDIT_NOTE' }), { status: 201, statusText: 'Created' });
    reload(f, invoice());
  });

  it('links a credit note back to its invoice', () => {
    const f = open(invoice({ kind: 'CREDIT_NOTE', number: 'NC SANDBOX/1', creditOfId: 'i0', creditOfNumber: 'FT SANDBOX/3', paymentStatus: null, payments: [] }));
    expect(el<HTMLAnchorElement>(f, '[data-test="credit-of"]')!.getAttribute('href')).toBe('/invoices/i0');
    expect(el(f, '[data-test="void"]')).toBeNull();
    expect(el(f, 'app-payments-panel')).toBeNull();
  });

  it('shows the not-found error of an unknown document', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(InvoiceDetailPage);
    f.componentRef.setInput('id', 'nope');
    f.detectChanges();
    http.expectOne('/api/v1/billing/settings').flush(settings());
    http.expectOne('/api/v1/invoices/nope').flush({ code: 'invoice-not-found', status: 404 }, { status: 404, statusText: 'Not Found' });
    f.detectChanges();
    expect(text(f)).toContain('Invoice not found.');
  });
});

describe('PaymentsPanel', () => {
  let http: HttpTestingController;

  function open(doc: Invoice = invoice(), billing = settings()) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(InvoiceDetailPage);
    f.componentRef.setInput('id', doc.id);
    f.detectChanges();
    http.expectOne('/api/v1/billing/settings').flush(billing);
    http.expectOne(`/api/v1/invoices/${doc.id}`).flush(doc);
    f.detectChanges();
    return f;
  }

  const pick = (f: ReturnType<typeof open>, method: string) => {
    el<HTMLButtonElement>(f, `[data-method="${method}"]`)!.click();
    f.detectChanges();
  };
  const send = (f: ReturnType<typeof open>) => {
    el<HTMLFormElement>(f, '[data-test="pay-form"]')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  };
  const reload = (f: ReturnType<typeof open>, doc: Invoice) => {
    http.expectOne(`/api/v1/invoices/${doc.id}`).flush(doc);
    f.detectChanges();
  };

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('shows the balance and records cash at once for the amount left', () => {
    const f = open(invoice({ paidCents: 2000, balanceCents: 2920, paymentStatus: 'PARTIALLY_PAID', payments: [payment()] }));
    expect(el(f, '[data-test="paid"]')!.textContent).toMatch(/20[.,]00/);
    expect(el(f, '[data-test="balance"]')!.textContent).toMatch(/29[.,]20/);
    expect(text(f)).toContain('Cash');
    pick(f, 'CASH');
    expect(el<HTMLInputElement>(f, '#pay-amount')!.value).toBe('29,20');
    typeInto(el<HTMLInputElement>(f, '#pay-note')!, 'Paid at the counter');
    send(f);
    const req = http.expectOne('/api/v1/invoices/i1/payments');
    expect(req.request.body).toEqual({ method: 'CASH', amountCents: 2920, note: 'Paid at the counter' });
    req.flush(payment({ amountCents: 2920 }));
    reload(f, invoice({ paidCents: 4920, balanceCents: 0, paymentStatus: 'PAID', payments: [payment(), payment({ id: 'p2', amountCents: 2920 })] }));
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('recorded');
    expect(el(f, '[data-method="CASH"]')).toBeNull();
  });

  it('refuses an amount above what is left without calling the server', () => {
    const f = open();
    pick(f, 'CARD');
    typeInto(el<HTMLInputElement>(f, '#pay-amount')!, '100');
    send(f);
    http.expectNone('/api/v1/invoices/i1/payments');
    expect(text(f)).toContain('more than what is left to pay');
    typeInto(el<HTMLInputElement>(f, '#pay-amount')!, '25,50');
    send(f);
    const req = http.expectOne('/api/v1/invoices/i1/payments');
    expect(req.request.body).toEqual({ method: 'CARD', amountCents: 2550 });
    req.flush(payment({ method: 'CARD', amountCents: 2550 }));
    reload(f, invoice({ paidCents: 2550, balanceCents: 2370, paymentStatus: 'PARTIALLY_PAID' }));
  });

  it('shows the Multibanco entity, reference, amount and validity to give to the customer', () => {
    const f = open();
    pick(f, 'MULTIBANCO');
    send(f);
    const req = http.expectOne('/api/v1/invoices/i1/payments');
    expect(req.request.body).toEqual({ method: 'MULTIBANCO', amountCents: 4920 });
    const mb = payment({ id: 'p9', method: 'MULTIBANCO', status: 'PENDING', amountCents: 4920, paidAt: null, provider: 'sandbox', multibanco: { entity: '99999', reference: '123456789' }, expiresAt: '2026-11-12T10:00:00Z' });
    req.flush(mb);
    reload(f, invoice({ pendingCents: 4920, balanceCents: 4920, payments: [mb] }));
    expect(el(f, '[data-test="entity"]')!.textContent).toBe('99999');
    expect(el(f, '[data-test="reference"]')!.textContent).toBe('123 456 789');
    expect(el(f, '[data-test="pending"]')!.textContent).toMatch(/49[.,]20/);
    expect(text(f)).toContain('Valid until 12/11/2026');
    expect(text(f)).toContain('Waiting');
  });

  it('asks for the MB WAY phone, shows the pending state, polls, and refreshes when it is paid', () => {
    vi.useFakeTimers({ now: new Date('2026-11-09T10:00:00Z') });
    const f = open();
    pick(f, 'MBWAY');
    send(f);
    http.expectNone('/api/v1/invoices/i1/payments');
    expect(text(f)).toContain("Enter the customer's mobile number.");
    typeInto(el<HTMLInputElement>(f, '#pay-phone')!, '912 345 678');
    send(f);
    const req = http.expectOne('/api/v1/invoices/i1/payments');
    expect(req.request.body).toEqual({ method: 'MBWAY', amountCents: 4920, phone: '912 345 678' });
    const mw = payment({ id: 'p8', method: 'MBWAY', status: 'PENDING', amountCents: 4920, paidAt: null, provider: 'sandbox', mbway: { phoneMasked: '+351 9** *** 678' }, expiresAt: '2026-11-09T10:10:00Z' });
    req.flush(mw);
    const waiting = invoice({ pendingCents: 4920, payments: [mw] });
    reload(f, waiting);
    expect(el(f, '[data-test="mbway-waiting"]')!.textContent).toContain('+351 9** *** 678');

    // Every 3 seconds the payment is asked for; still PENDING changes nothing.
    vi.advanceTimersByTime(3000);
    http.expectOne('/api/v1/payments/p8').flush(mw);
    vi.advanceTimersByTime(3000);
    // Paid on the phone: the invoice reloads.
    http.expectOne('/api/v1/payments/p8').flush({ ...mw, status: 'PAID' });
    reload(f, invoice({ paidCents: 4920, balanceCents: 0, paymentStatus: 'PAID', payments: [{ ...mw, status: 'PAID', paidAt: '2026-11-09T10:01:00Z' }] }));
    expect(el(f, '[data-test="paid"]')!.textContent).toMatch(/49[.,]20/);
    vi.advanceTimersByTime(9000);
    http.expectNone('/api/v1/payments/p8');
  });

  it('stops polling at the expiry with one refresh, and never overlaps calls', () => {
    vi.useFakeTimers({ now: new Date('2026-11-09T10:00:00Z') });
    const mw = payment({ id: 'p8', method: 'MBWAY', status: 'PENDING', paidAt: null, provider: 'sandbox', mbway: { phoneMasked: '+351 9** *** 678' }, expiresAt: '2026-11-09T10:00:07Z' });
    const doc = invoice({ pendingCents: 2000, payments: [mw] });
    const f = open(doc);

    vi.advanceTimersByTime(3000);
    const first = http.expectOne('/api/v1/payments/p8');
    // The first call is still in flight: the next tick asks for nothing.
    vi.advanceTimersByTime(3000);
    http.expectNone('/api/v1/payments/p8');
    first.flush(mw);
    // 9 s is past the expiry (7 s): one last refresh instead of a poll, then the invoice reloads.
    vi.advanceTimersByTime(3000);
    http.expectNone('/api/v1/payments/p8');
    http.expectOne('/api/v1/payments/p8/refresh').flush({ ...mw, status: 'EXPIRED' });
    reload(f, invoice({ payments: [{ ...mw, status: 'EXPIRED' }] }));

    // Even if the server still shows it PENDING, no more calls are made.
    vi.advanceTimersByTime(30000);
    http.expectNone(() => true);
  });

  it('shows the server verdict on a wrong MB WAY phone next to the field', () => {
    const f = open();
    pick(f, 'MBWAY');
    typeInto(el<HTMLInputElement>(f, '#pay-phone')!, '12');
    send(f);
    http.expectOne('/api/v1/invoices/i1/payments').flush({ code: 'invalid-phone', status: 400, errors: { phone: 'x' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(el(f, '#pay-phone')!.classList).toContain('is-invalid');
    expect(text(f)).toContain('Portuguese mobile number');
  });

  it('refreshes and cancels a waiting payment with its version', () => {
    const mw = payment({ id: 'p8', method: 'MBWAY', status: 'PENDING', paidAt: null, provider: 'sandbox', mbway: { phoneMasked: '+351 9** *** 678' }, version: 4 });
    const doc = invoice({ pendingCents: 2000, payments: [mw] });
    const f = open(doc, settings({ payments: { provider: 'sandbox', configured: true, sandbox: false } }));
    el<HTMLButtonElement>(f, '[data-test="refresh"]')!.click();
    const refresh = http.expectOne('/api/v1/payments/p8/refresh');
    expect(refresh.request.method).toBe('POST');
    refresh.flush(mw);
    reload(f, doc);

    el<HTMLButtonElement>(f, '[data-test="cancel"]')!.click();
    const cancel = http.expectOne('/api/v1/payments/p8/cancel');
    expect(cancel.request.body).toEqual({ version: 4 });
    cancel.flush({ ...mw, status: 'CANCELLED' });
    reload(f, invoice({ payments: [{ ...mw, status: 'CANCELLED' }] }));
    expect(text(f)).toContain('Cancelled');
  });

  it('shows the simulate controls only when the contract says the provider is a sandbox', () => {
    const mw = payment({ id: 'p8', method: 'MBWAY', status: 'PENDING', paidAt: null, provider: 'sandbox', mbway: { phoneMasked: '+351 9** *** 678' } });
    const doc = invoice({ pendingCents: 2000, payments: [mw] });
    const live = open(doc, settings({ payments: { provider: 'ifthenpay', configured: true, sandbox: false } }));
    expect(el(live, '[data-test="simulate"]')).toBeNull();
    TestBed.resetTestingModule();

    const f = open(doc);
    expect(el(f, '[data-test="simulate"]')).not.toBeNull();
    el<HTMLButtonElement>(f, '[data-outcome="PAID"]')!.click();
    const req = http.expectOne('/api/v1/payments/p8/sandbox/simulate');
    expect(req.request.body).toEqual({ outcome: 'PAID' });
    req.flush({ ...mw, status: 'PAID' });
    reload(f, doc);
  });

  it('offers only the offline methods while the provider is not configured', () => {
    const f = open(invoice(), settings({ payments: { provider: 'none', configured: false, sandbox: false }, methods: ['CASH', 'CARD', 'TRANSFER'] }));
    expect(el(f, '[data-method="CASH"]')).not.toBeNull();
    expect(el(f, '[data-method="MULTIBANCO"]')).toBeNull();
    expect(el(f, '[data-method="MBWAY"]')).toBeNull();
  });

  it('shows payment-exceeds-balance once, on the amount field, and reloads', () => {
    const f = open();
    pick(f, 'CASH');
    send(f);
    http.expectOne('/api/v1/invoices/i1/payments').flush({ code: 'payment-exceeds-balance', status: 409, leftCents: 100 }, { status: 409, statusText: 'Conflict' });
    reload(f, invoice({ paidCents: 4820, balanceCents: 100, paymentStatus: 'PARTIALLY_PAID' }));
    const occurrences = text(f).split('more than what is left to pay').length - 1;
    expect(occurrences).toBe(1);
    expect(el(f, '#pay-amount')!.classList).toContain('is-invalid');
    expect(el(f, '.alert-danger')).toBeNull();
  });

  it('never shows the provider reason of a refused payment, only the translated text', () => {
    const f = open();
    pick(f, 'MULTIBANCO');
    send(f);
    http.expectOne('/api/v1/invoices/i1/payments').flush({ code: 'payment-failed', status: 409, reason: 'sandbox-rejected', detail: 'sandbox-rejected' }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(text(f)).toContain('The payment provider refused the request.');
    expect(text(f)).not.toContain('sandbox-rejected');
  });

  it('shows the amount to refund on a credited invoice and offers no payment', () => {
    const f = open(invoice({ creditNote: { id: 'c1', number: 'NC SANDBOX/1', status: 'ISSUED' }, paymentStatus: 'CREDITED', paidCents: 2000, refundDueCents: 2000, balanceCents: 0, payments: [payment()] }));
    expect(el(f, '[data-test="refund"]')!.textContent).toMatch(/20[.,]00/);
    expect(el(f, '[data-method="CASH"]')).toBeNull();
    expect(el(f, '[data-test="credited"]')).not.toBeNull();
  });

  it('reloads and says why when the payment was no longer waiting', () => {
    const mw = payment({ id: 'p8', method: 'MULTIBANCO', status: 'PENDING', paidAt: null, provider: 'sandbox', multibanco: { entity: '1', reference: '123456789' } });
    const doc = invoice({ pendingCents: 2000, payments: [mw] });
    const f = open(doc);
    el<HTMLButtonElement>(f, '[data-test="cancel"]')!.click();
    http.expectOne('/api/v1/payments/p8/cancel').flush({ code: 'payment-not-pending', status: 409 }, { status: 409, statusText: 'Conflict' });
    reload(f, invoice({ paidCents: 2000, balanceCents: 2920, payments: [{ ...mw, status: 'PAID' }] }));
    expect(text(f)).toContain('no longer waiting');
  });
});
