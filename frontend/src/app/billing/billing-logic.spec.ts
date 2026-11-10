import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BillingApi } from './billing-api.service';
import { invoice, payment } from './billing-test-utils';
import {
  actionsOf,
  activeInvoice,
  balanceDue,
  canIssue,
  eurosText,
  formatReference,
  leftCents,
  newRequestId,
  outcomeOf,
  parseEuros,
  pdfName,
  pollable,
  issuedAfterDiscard,
} from './billing-logic';
import { customerErrors, EMPTY_CUSTOMER, serverFields, toIssueRequest } from './issue-form';
import { I18nService } from '../core/i18n/i18n.service';

describe('billing logic', () => {
  it('computes what is left and the balance due, never below zero', () => {
    expect(leftCents({ totalCents: 4920, paidCents: 2000, pendingCents: 1000 })).toBe(1920);
    expect(leftCents({ totalCents: 4920, paidCents: 5000, pendingCents: 0 })).toBe(0);
    expect(balanceDue({ totalCents: 4920, paidCents: 2000 })).toBe(2920);
    expect(balanceDue({ totalCents: 4920, paidCents: 6000 })).toBe(0);
  });

  it('allows per state only what the state machine allows', () => {
    const issued = actionsOf(invoice());
    expect(issued).toEqual({ retry: false, discard: false, void: true, creditNote: true, pay: true, pdf: true });
    expect(actionsOf(invoice({ status: 'PENDING', number: null }))).toEqual({ retry: true, discard: false, void: false, creditNote: false, pay: false, pdf: false });
    expect(actionsOf(invoice({ status: 'FAILED' }))).toMatchObject({ retry: true, discard: true, void: false, pdf: false });
    expect(actionsOf(invoice({ status: 'VOIDED' }))).toMatchObject({ retry: false, void: false, pay: false, pdf: true });
    expect(actionsOf(invoice({ status: 'DISCARDED' }))).toMatchObject({ retry: false, discard: false, pdf: false });
    // A credit note is never voided, credited or paid.
    expect(actionsOf(invoice({ kind: 'CREDIT_NOTE' }))).toMatchObject({ void: false, creditNote: false, pay: false, pdf: true });
  });

  it('blocks void, credit note and payments once a credit note exists, and payments once everything is covered', () => {
    const credited = actionsOf(invoice({ creditNote: { id: 'c1', number: 'NC SANDBOX/1', status: 'ISSUED' } }));
    expect(credited).toMatchObject({ void: false, creditNote: false, pay: false });
    expect(actionsOf(invoice({ paidCents: 4920, balanceCents: 0 })).pay).toBe(false);
    expect(actionsOf(invoice({ pendingCents: 4920 })).pay).toBe(false);
    expect(actionsOf(invoice({ paidCents: 1000, balanceCents: 3920 })).pay).toBe(true);
  });

  it('offers a new invoice only for a delivered order without an active invoice', () => {
    expect(canIssue('DELIVERED', [])).toBe(true);
    expect(canIssue('IN_PROGRESS', [])).toBe(false);
    for (const status of ['PENDING', 'ISSUED', 'FAILED'] as const) expect(canIssue('DELIVERED', [{ kind: 'INVOICE', status }])).toBe(false);
    // After a discarded or voided invoice, and with only a credit note around, the order can be invoiced again.
    expect(canIssue('DELIVERED', [{ kind: 'INVOICE', status: 'DISCARDED' }, { kind: 'INVOICE', status: 'VOIDED' }])).toBe(true);
    expect(activeInvoice([{ kind: 'CREDIT_NOTE', status: 'ISSUED' }])).toBe(false);
  });

  it('maps the HTTP status of an issue to its outcome', () => {
    expect(outcomeOf(201)).toBe('issued');
    expect(outcomeOf(200)).toBe('replay');
    expect(outcomeOf(202)).toBe('stored');
  });

  it('makes a new request id per attempt', () => {
    const a = newRequestId();
    expect(a).toMatch(/^[0-9a-f-]{36}$/);
    expect(newRequestId()).not.toBe(a);
  });

  it('polls only the MB WAY requests that still wait', () => {
    const list = [payment({ id: 'a', method: 'MBWAY', status: 'PENDING' }), payment({ id: 'b', method: 'MULTIBANCO', status: 'PENDING' }), payment({ id: 'c', method: 'MBWAY', status: 'PAID' })];
    expect(pollable(list).map((p) => p.id)).toEqual(['a']);
  });

  it('formats a reference, euros and file names', () => {
    expect(formatReference('123456789')).toBe('123 456 789');
    expect(formatReference('123 456 789')).toBe('123 456 789');
    expect(parseEuros('25,50')).toBe(2550);
    expect(parseEuros('25.5')).toBe(2550);
    expect(parseEuros('0,10')).toBe(10);
    expect(parseEuros('25,555')).toBeNull();
    expect(parseEuros('abc')).toBeNull();
    expect(parseEuros('-3')).toBeNull();
    expect(eurosText(4920)).toBe('49,20');
    expect(pdfName('FT SANDBOX/3', 'i1')).toBe('FT-SANDBOX-3.pdf');
    expect(pdfName(null, 'i1')).toBe('i1.pdf');
  });
});

describe('issued after discard', () => {
  it('flags a discarded document the provider issued anyway', () => {
    expect(issuedAfterDiscard(invoice({ status: 'DISCARDED', failure: { code: 'issued-after-discard', message: 'm', attempts: 1, lastAttemptAt: 'x' } }))).toBe(true);
    expect(issuedAfterDiscard(invoice({ status: 'DISCARDED', number: null, failure: null }))).toBe(false);
    expect(issuedAfterDiscard(invoice({ status: 'ISSUED' }))).toBe(false);
    // A list item has no failure: a discarded document with a number is the sign.
    expect(issuedAfterDiscard({ status: 'DISCARDED', number: 'FT SANDBOX/9' })).toBe(true);
    expect(issuedAfterDiscard({ status: 'DISCARDED', number: null })).toBe(false);
  });
});

describe('issue form', () => {
  it('sends only the request id for the owner, finalConsumer for a final consumer, the typed data for another customer', () => {
    expect(toIssueRequest('r1', 'owner', EMPTY_CUSTOMER)).toEqual({ requestId: 'r1' });
    expect(toIssueRequest('r1', 'final', EMPTY_CUSTOMER)).toEqual({ requestId: 'r1', finalConsumer: true });
    const v = { ...EMPTY_CUSTOMER, name: ' Ana ', nif: '123 456 789', street: ' Rua A ', postalCode: '1100-053', locality: 'Lisboa', country: 'pt' };
    expect(toIssueRequest('r1', 'another', v)).toEqual({
      requestId: 'r1',
      finalConsumer: false,
      customer: { name: 'Ana', nif: '123456789', address: { street: 'Rua A', detail: null, postalCode: '1100-053', locality: 'Lisboa', country: 'PT' } },
    });
    // No country typed: none is sent.
    const noCountry = toIssueRequest('r1', 'another', { ...EMPTY_CUSTOMER, name: 'Ana', street: 'Rua A', postalCode: '1100-053', locality: 'Lisboa' });
    expect(noCountry.customer?.address).toEqual({ street: 'Rua A', detail: null, postalCode: '1100-053', locality: 'Lisboa' });
    expect(toIssueRequest('r1', 'another', { ...EMPTY_CUSTOMER, name: 'Ana' }).customer).toEqual({ name: 'Ana', nif: null, address: null });
  });

  it('checks the name, the NIF and an address that is filled in part', () => {
    expect(customerErrors(EMPTY_CUSTOMER)).toEqual({ name: 'billing.issue.nameRequired' });
    expect(customerErrors({ ...EMPTY_CUSTOMER, name: 'Ana', nif: '123456780', country: 'PT' }).nif).toBe('billing.issue.nifInvalid');
    expect(customerErrors({ ...EMPTY_CUSTOMER, name: 'Ana', nif: '12' }).nif).toBe('billing.issue.nifDigits');
    expect(customerErrors({ ...EMPTY_CUSTOMER, name: 'Ana', nif: '123456789' })).toEqual({});
    // A Portuguese postal code needs 0000-000; another country does not.
    expect(customerErrors({ ...EMPTY_CUSTOMER, name: 'Ana', street: 'R', locality: 'L', postalCode: '1100', country: 'PT' }).postalCode).toBe('billing.issue.postalInvalid');
    expect(customerErrors({ ...EMPTY_CUSTOMER, name: 'Ana', street: 'R', locality: 'L', postalCode: 'SW1A', country: 'GB' })).toEqual({});
    expect(customerErrors({ ...EMPTY_CUSTOMER, name: 'Ana', street: 'Rua' })).toMatchObject({ locality: 'billing.issue.required', postalCode: 'billing.issue.required' });
  });

  it('finds the fields a failed issue is about', () => {
    expect(serverFields('customer-nif-required', undefined)).toEqual({ nif: 'customer-nif-required' });
    expect(serverFields('customer-address-required', undefined)).toEqual({ street: 'customer-address-required' });
    expect(serverFields('validation', { 'customer.address.postalCode': 'x' })).toEqual({ postalCode: 'validation' });
    expect(serverFields('customer-name-too-long', { 'customer.name': 'x' })).toEqual({ name: 'customer-name-too-long' });
    expect(serverFields('provider-unavailable', undefined)).toEqual({});
  });
});

describe('BillingApi', () => {
  let http: HttpTestingController;
  let api: BillingApi;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(BillingApi);
  });

  afterEach(() => http.verify());

  it('reports 201, 200 and 202 of an issue as different outcomes', () => {
    const outcomes: string[] = [];
    for (const status of [201, 200, 202]) {
      api.issue('w1', { requestId: 'r1' }).subscribe((r) => outcomes.push(r.outcome));
      const req = http.expectOne('/api/v1/work-orders/w1/invoices');
      expect(req.request.body).toEqual({ requestId: 'r1' });
      req.flush(invoice(), { status, statusText: 'x' });
    }
    expect(outcomes).toEqual(['issued', 'replay', 'stored']);
  });

  it('builds the list query: repeatable status, filters, a search of 2+ characters, paging', () => {
    api.list({ status: ['ISSUED', 'FAILED'], kind: 'INVOICE', paymentStatus: 'UNPAID', from: '2026-11-01', to: '2026-11-09', q: ' ana ', page: 2, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/invoices');
    expect(req.request.params.getAll('status')).toEqual(['ISSUED', 'FAILED']);
    expect(req.request.params.get('kind')).toBe('INVOICE');
    expect(req.request.params.get('paymentStatus')).toBe('UNPAID');
    expect(req.request.params.get('q')).toBe('ana');
    expect(req.request.params.get('page')).toBe('2');
    req.flush({ items: [], page: 2, size: 20, total: 0 });

    api.list({ q: 'a' }).subscribe();
    const short = http.expectOne((r) => r.url === '/api/v1/invoices');
    expect(short.request.params.has('q')).toBe(false);
    short.flush({ items: [], page: 0, size: 20, total: 0 });
  });

  it('fetches the PDF as a blob and turns a failed blob back into a problem with its code', async () => {
    let blob: Blob | undefined;
    api.pdf('i1').subscribe((b) => (blob = b));
    const ok = http.expectOne('/api/v1/invoices/i1/pdf');
    expect(ok.request.responseType).toBe('blob');
    ok.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
    expect(blob?.type).toBe('application/pdf');

    const failed = new Promise<unknown>((resolve) => api.pdf('i2').subscribe({ error: resolve }));
    http.expectOne('/api/v1/invoices/i2/pdf').flush(new Blob([JSON.stringify({ code: 'invoice-not-issued', status: 409 })], { type: 'application/problem+json' }), { status: 409, statusText: 'Conflict' });
    const err = await failed;
    expect(TestBed.inject(I18nService).error(err)).toBe('This only works on an issued invoice that has no credit note.');
  });
});
