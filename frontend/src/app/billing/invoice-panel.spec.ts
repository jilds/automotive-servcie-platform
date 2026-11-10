import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { WorkOrder } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { WorkOrderDetail } from '../workshop/work-order-detail';
import { flushFloor } from '../workshop/floor-test-utils';
import { el, invoice, settings, text, typeInto } from './billing-test-utils';
import { InvoicePanel } from './invoice-panel';

const order = (over: Partial<WorkOrder> = {}): WorkOrder => ({
  id: 'w1', number: 12, vehicleId: 'v1', plate: 'AA00AA', plateDisplay: 'AA-00-AA', plateEra: 'PT_2020',
  registrationCountry: 'PT', registrationDate: null, status: 'DELIVERED', allowedNext: [], mileageIn: 1000,
  fuelLevel: 4, complaint: null, damageNotes: null, assignedMechanicId: null, statusChangedAt: '2026-11-09T09:00:00Z',
  lines: [], netCents: 4000, vatCents: 920, totalCents: 4920, readOnly: false, quote: null,
  ...over,
});

describe('InvoicePanel', () => {
  let http: HttpTestingController;

  function open(w: WorkOrder, documents: unknown[] = []) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(InvoicePanel);
    f.componentRef.setInput('workOrder', w);
    f.detectChanges();
    if (w.status === 'DELIVERED') http.expectOne('/api/v1/work-orders/w1/invoices').flush(documents);
    f.detectChanges();
    return f;
  }

  /** Opens the issue dialog and returns its form. */
  function openDialog(f: ComponentFixture<InvoicePanel>) {
    el<HTMLButtonElement>(f, '[data-test="issue"]')!.click();
    http.expectOne('/api/v1/billing/settings').flush(settings());
    f.detectChanges();
  }

  const submit = (f: ComponentFixture<InvoicePanel>) => {
    el<HTMLFormElement>(f, 'dialog form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  };

  afterEach(() => http.verify());

  it('is not shown, and asks for nothing, before the order is delivered', () => {
    const f = open(order({ status: 'IN_PROGRESS' }));
    expect(el(f, 'section')).toBeNull();
  });

  it('offers Issue invoice on a delivered order without an invoice', () => {
    const f = open(order());
    expect(el(f, '[data-test="issue"]')).not.toBeNull();
    expect(text(f)).toContain('This work order has no invoice yet.');
  });

  it('lists the documents with state, hides Issue while one is active, and shows the sandbox banner', () => {
    const f = open(order(), [invoice(), invoice({ id: 'i0', status: 'DISCARDED', number: null, sandbox: true })]);
    expect(el(f, '[data-test="issue"]')).toBeNull();
    expect(text(f)).toContain('FT SANDBOX/3');
    expect(text(f)).toContain('Issued');
    expect(text(f)).toContain('Discarded');
    expect(el(f, '[data-test="sandbox"]')!.textContent).toContain('not a fiscal document');
    expect(el(f, '[data-test="pdf"]')).not.toBeNull();
  });

  it('issues an invoice for the owner with one request id; 201 says it was issued', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    const req = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.requestId).toMatch(/^[0-9a-f-]{36}$/);
    expect(Object.keys(req.request.body)).toEqual(['requestId']);
    req.flush(invoice(), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
    f.detectChanges();
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('Document FT SANDBOX/3 issued.');
    expect(el(f, '[data-test="issue"]')).toBeNull();
  });

  it('says nothing was invoiced twice on a 200 replay', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    http.expectOne('/api/v1/work-orders/w1/invoices').flush(invoice(), { status: 200, statusText: 'OK' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
    f.detectChanges();
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('already issued');
  });

  it('keeps a 202 PENDING or FAILED document and offers Try again with the provider message', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    const failed = invoice({ status: 'FAILED', number: null, issuedAt: null, failure: { code: 'sandbox-rejected', message: 'Rejected by the sandbox', attempts: 1, lastAttemptAt: '2026-11-09T10:00:00Z' } });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush(failed, { status: 202, statusText: 'Accepted' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([failed]);
    f.detectChanges();
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('provider refused it');
    expect(el(f, '[data-test="failure"]')!.textContent).toContain('Rejected by the sandbox');
    expect(el(f, '[data-test="issue"]')).toBeNull();

    el<HTMLButtonElement>(f, '[data-test="retry"]')!.click();
    const retry = http.expectOne('/api/v1/invoices/i1/retry');
    expect(retry.request.method).toBe('POST');
    retry.flush(invoice());
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
    f.detectChanges();
    expect(el(f, '[data-test="notice"]')!.textContent).toContain('issued');
  });

  it('shows a PENDING document as being issued', () => {
    const f = open(order(), [invoice({ status: 'PENDING', number: null, issuedAt: null })]);
    expect(el(f, '[data-test="status"]')!.textContent).toContain('Being issued');
    expect(el(f, '[data-test="retry"]')).not.toBeNull();
    expect(el(f, '[data-test="pdf"]')).toBeNull();
  });

  it('sends the typed customer for another customer and checks the form first', () => {
    const f = open(order());
    openDialog(f);
    el<HTMLInputElement>(f, '#iid-mode-another')!.click();
    f.detectChanges();
    submit(f);
    http.expectNone('/api/v1/work-orders/w1/invoices');
    expect(text(f)).toContain("Enter the customer's name.");

    typeInto(el<HTMLInputElement>(f, '#iid-name')!, 'Ana Silva');
    typeInto(el<HTMLInputElement>(f, '#iid-nif')!, '123456789');
    submit(f);
    const req = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(req.request.body).toMatchObject({ finalConsumer: false, customer: { name: 'Ana Silva', nif: '123456789', address: null } });
    req.flush(invoice(), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
  });

  it('sends finalConsumer: true for a final consumer', () => {
    const f = open(order());
    openDialog(f);
    el<HTMLInputElement>(f, '#iid-mode-final')!.click();
    f.detectChanges();
    submit(f);
    const req = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(req.request.body).toMatchObject({ finalConsumer: true });
    req.flush(invoice(), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
  });

  it('sends the same request id again after a failed attempt, so nothing is invoiced twice', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    const first = http.expectOne('/api/v1/work-orders/w1/invoices');
    const id = first.request.body.requestId;
    first.flush(null, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(el(f, '[data-test="safe-retry"]')).not.toBeNull();

    submit(f);
    const second = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(second.request.body.requestId).toBe(id);
    second.flush(invoice(), { status: 200, statusText: 'OK' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);

    // A new opening is a new attempt.
    f.componentInstance['invoices'].set([]);
    f.detectChanges();
    openDialog(f);
    submit(f);
    const third = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(third.request.body.requestId).not.toBe(id);
    third.flush(invoice(), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
  });

  it('makes a new request id after request-id-reused', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    const first = http.expectOne('/api/v1/work-orders/w1/invoices');
    const id = first.request.body.requestId;
    first.flush({ code: 'request-id-reused', status: 409 }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(text(f)).toContain('A new one was prepared');
    submit(f);
    const second = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(second.request.body.requestId).not.toBe(id);
    second.flush(invoice(), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
  });

  it('shows the issued-after-discard warning and money to return on the order documents', () => {
    const f = open(order(), [
      invoice({ id: 'd', status: 'DISCARDED', paymentStatus: null, failure: { code: 'issued-after-discard', message: 'm', attempts: 1, lastAttemptAt: 'x' } }),
      invoice({ id: 'v', status: 'VOIDED', paymentStatus: null, refundDueCents: 2000, paidCents: 2000 }),
    ]);
    expect(el(f, '[data-test="issued-after-discard"]')).not.toBeNull();
    expect(el(f, '[data-test="refund"]')!.textContent).toMatch(/20[.,]00.*arrived after/);
  });

  it('uses a neutral text for money to return on a credited invoice', () => {
    const f = open(order(), [invoice({ creditNote: { id: 'c1', number: 'NC SANDBOX/1', status: 'ISSUED' }, paymentStatus: 'CREDITED', paidCents: 2000, refundDueCents: 2000 })]);
    const t = el(f, '[data-test="refund"]')!.textContent!;
    expect(t).toMatch(/Money to return to the customer: .*20[.,]00/);
    expect(t).not.toContain('arrived after');
  });

  it('shows the name-too-long error on the name field and the postal code error on its field', () => {
    const f = open(order());
    openDialog(f);
    el<HTMLInputElement>(f, '#iid-mode-another')!.click();
    f.detectChanges();
    typeInto(el<HTMLInputElement>(f, '#iid-name')!, 'Ana');
    submit(f);
    http.expectOne('/api/v1/work-orders/w1/invoices').flush({ code: 'customer-name-too-long', status: 400, errors: { 'customer.name': 'x' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(el(f, '#iid-name')!.classList).toContain('is-invalid');
    expect(text(f)).toContain('The name can have at most 120 characters.');

    typeInto(el<HTMLInputElement>(f, '#iid-street')!, 'Rua');
    typeInto(el<HTMLInputElement>(f, '#iid-locality')!, 'Lisboa');
    typeInto(el<HTMLInputElement>(f, '#iid-postal')!, '12');
    submit(f);
    const req = http.expectOne('/api/v1/work-orders/w1/invoices');
    expect(req.request.body.customer.address).toEqual({ street: 'Rua', detail: null, postalCode: '12', locality: 'Lisboa' });
    req.flush({ code: 'validation', status: 400, errors: { 'customer.address.postalCode': 'bad' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(el(f, '#iid-postal')!.classList).toContain('is-invalid');
  });

  it('asks for the NIF with a link to the customer when the amount needs it', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    http
      .expectOne('/api/v1/work-orders/w1/invoices')
      .flush({ code: 'customer-nif-required', status: 400, errors: { 'customer.nif': 'required' } }, { status: 400, statusText: 'Bad Request' });
    http.expectOne('/api/v1/vehicles/v1').flush({ id: 'v1', customerId: 'c9' });
    f.detectChanges();
    expect(text(f)).toContain("needs the customer's NIF");
    expect(el(f, '[data-test="needs-data"]')).not.toBeNull();
    expect(el<HTMLAnchorElement>(f, '[data-test="customer-link"]')!.getAttribute('href')).toBe('/customers/c9');
    // The form for another customer opens, with the NIF field marked.
    expect(el(f, '[data-test="other-fields"]')).not.toBeNull();
    expect(el(f, '#iid-nif')!.classList).toContain('is-invalid');
  });

  it('asks for the address when the server says so', () => {
    const f = open(order());
    openDialog(f);
    el<HTMLInputElement>(f, '#iid-mode-final')!.click();
    f.detectChanges();
    submit(f);
    http.expectOne('/api/v1/work-orders/w1/invoices').flush({ code: 'customer-address-required', status: 400 }, { status: 400, statusText: 'Bad Request' });
    http.expectOne('/api/v1/vehicles/v1').flush({ id: 'v1', customerId: 'c9' });
    f.detectChanges();
    expect(text(f)).toContain("needs the customer's address");
    expect(el(f, '#iid-street')!.classList).toContain('is-invalid');
  });

  it('reloads when the order was invoiced in the meantime', () => {
    const f = open(order());
    openDialog(f);
    submit(f);
    http.expectOne('/api/v1/work-orders/w1/invoices').flush({ code: 'already-invoiced', invoiceId: 'i1', invoiceStatus: 'ISSUED' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([invoice()]);
    f.detectChanges();
    expect(el(f, '[data-test="issue"]')).toBeNull();
  });

  it('downloads the PDF with the token as a blob', () => {
    const create = vi.fn(() => 'blob:x');
    const revoke = vi.fn();
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const f = open(order(), [invoice()]);
    el<HTMLButtonElement>(f, '[data-test="pdf"]')!.click();
    const req = http.expectOne('/api/v1/invoices/i1/pdf');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
    expect(create).toHaveBeenCalledTimes(1);
  });
});

describe('Invoice panel on the work order screen', () => {
  const auth = (role: string) => ({
    hasRole: (...roles: string[]) => roles.includes(role),
    canSeePrices: signal(role !== 'MECHANIC'),
    user: signal({ id: 'u1', role }),
    account: signal({ id: 'u1', role }),
  });

  function screen(role: string) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: auth(role) }] });
    TestBed.inject(I18nService).use('en');
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(WorkOrderDetail);
    f.componentRef.setInput('id', 'w1');
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1').flush(order());
    f.detectChanges();
    return { f, http };
  }

  it('shows the invoice panel to the front desk', () => {
    const { f, http } = screen('FRONT_DESK');
    http.expectOne('/api/v1/work-orders/w1/quotes').flush([]);
    http.expectOne('/api/v1/work-orders/w1/invoices').flush([]);
    flushFloor(http);
    http.match(() => true);
    f.detectChanges();
    expect(el(f, 'app-invoice-panel')).not.toBeNull();
    expect(el(f, '[data-test="issue"]')).not.toBeNull();
  });

  it('never shows or loads it for a mechanic', () => {
    const { f, http } = screen('MECHANIC');
    flushFloor(http);
    f.detectChanges();
    expect(el(f, 'app-invoice-panel')).toBeNull();
    http.expectNone('/api/v1/work-orders/w1/invoices');
  });
});
