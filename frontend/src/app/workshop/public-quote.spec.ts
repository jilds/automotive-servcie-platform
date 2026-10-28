import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { sendsToken } from '../core/auth/auth.interceptor';
import { I18nService } from '../core/i18n/i18n.service';
import { approveForm, PublicQuotePage, quotePageState } from './public-quote';
import { PublicQuote } from './quote.models';

const SLUG = 'oficina-azul';
const TOKEN = 'T'.repeat(43);
const BASE = `/api/v1/public/${SLUG}/quotes/${TOKEN}`;
const BRANDING = { slug: SLUG, name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null, accentColor: null, logoUrl: null, privacyUrl: null, version: 1 };

const QUOTE: PublicQuote = {
  number: 4, workOrderNumber: 12, status: 'SENT', sentAt: '2026-10-27T10:00:00Z', validUntil: '2026-11-11T23:30:00Z',
  timeZone: 'Atlantic/Azores', workshopName: 'Oficina Azul',
  vehicle: { plateDisplay: 'AA-00-AA', plateEra: 'PT_2020', registrationCountry: 'PT', label: 'Renault Clio' },
  lines: [
    { type: 'LABOR', description: 'Mudança de óleo', quantity: 1, unitPriceCents: 4000, discountPercent: 0, vatRate: 23, netCents: 4000 },
    { type: 'PART', description: 'Filtro de óleo', quantity: 2, unitPriceCents: 1000, discountPercent: 10, vatRate: 6, netCents: 1800 },
  ],
  vatByRate: [{ vatRate: 6, netCents: 1800, vatCents: 108 }, { vatRate: 23, netCents: 4000, vatCents: 920 }],
  netCents: 5800, vatCents: 1028, totalCents: 6828, decidedAt: null, decidedChannel: null, canDecide: true,
};

const problem = (status: number, body: object) => new HttpErrorResponse({ status, error: body });

describe('quote page helpers', () => {
  it('sorts the load errors', () => {
    expect(quotePageState(problem(404, { code: 'quote-not-found' }))).toBe('not-found');
    expect(quotePageState(problem(404, { code: 'workshop-not-found' }))).toBe('not-found');
    expect(quotePageState(problem(429, { code: 'too-many-requests' }))).toBe('limited');
    expect(quotePageState(problem(409, { code: 'quote-expired' }))).toBeNull();
  });

  it('checks the name like the shop and requires the acceptance', () => {
    const form = approveForm();
    form.setValue({ name: 'Ana Silva', accepted: false });
    expect(form.controls.accepted.invalid).toBe(true);
    form.setValue({ name: 'see www.spam.test', accepted: true });
    expect(form.controls.name.hasError('plainName')).toBe(true);
    form.setValue({ name: 'x'.repeat(121), accepted: true });
    expect(form.controls.name.hasError('maxlength')).toBe(true);
    form.setValue({ name: 'Ana Silva', accepted: true });
    expect(form.valid).toBe(true);
  });

  it('never sends the sign-in token to the public quote API', () => {
    expect(sendsToken(BASE)).toBe(false);
    expect(sendsToken('/api/v1/work-orders/w1/quotes')).toBe(true);
  });
});

describe('PublicQuotePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.documentElement.removeAttribute('data-tenant-theme');
  });

  const text = (f: ComponentFixture<unknown>) => ((f.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  const el = <T extends Element = HTMLElement>(f: ComponentFixture<unknown>, selector: string) => (f.nativeElement as HTMLElement).querySelector(selector) as T | null;
  const type = (f: ComponentFixture<unknown>, selector: string, value: string) => {
    const input = el<HTMLInputElement>(f, selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  function open(q: PublicQuote | null = QUOTE, err?: HttpErrorResponse) {
    const f = TestBed.createComponent(PublicQuotePage);
    f.componentRef.setInput('slug', SLUG);
    f.componentRef.setInput('token', TOKEN);
    f.detectChanges();
    http.expectOne(`/api/v1/branding/${SLUG}`).flush(BRANDING);
    const req = http.expectOne(BASE);
    if (err) req.flush(err.error, { status: err.status, statusText: 'Error' });
    else req.flush(q);
    f.detectChanges();
    return f;
  }

  it('shows the vehicle, lines, VAT by rate and totals, valid until in the workshop zone', () => {
    const f = open();
    const t = text(f);
    expect(t).toContain('Quote #4');
    expect(t).toContain('Work order #12 · Oficina Azul');
    expect(el(f, 'app-plate')).not.toBeNull();
    expect(t).toContain('Renault Clio');
    expect(t).toContain('Filtro de óleo');
    expect(t).toContain('VAT 6% on');
    expect(t).toContain('VAT 23% on');
    expect(el(f, '[data-test="total"]')!.textContent).toMatch(/68[.,]28/);
    // 23:30 UTC is 22:30 in the Azores (UTC-1 in November).
    expect(el(f, '[data-test="valid-until"]')!.textContent).toContain('10:30 PM');
  });

  it('approves with the typed name and acceptance, then shows the decision', () => {
    const f = open();
    el<HTMLFormElement>(f, '[data-test="approve-form"]')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
    http.expectNone(`${BASE}/approve`);
    expect(text(f)).toContain('Enter your name.');
    expect(text(f)).toContain('Tick the box to approve the quote.');

    type(f, '#pq-name', '  Ana Silva ');
    el<HTMLInputElement>(f, '#pq-accept')!.click();
    el<HTMLFormElement>(f, '[data-test="approve-form"]')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne(`${BASE}/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.body).toEqual({ name: 'Ana Silva', accepted: true });
    req.flush({ ...QUOTE, status: 'APPROVED', canDecide: false, decidedAt: '2026-10-28T09:00:00Z', decidedChannel: 'CUSTOMER' });
    f.detectChanges();
    expect(el(f, '[data-test="decided"]')!.textContent).toContain('You approved this quote on');
    expect(el(f, '[data-test="approve-form"]')).toBeNull();
    expect(el(f, '[data-test="status"]')!.textContent).toContain('Approved');
  });

  it('rejects with an optional reason', () => {
    const f = open();
    el<HTMLButtonElement>(f, '[data-test="reject-toggle"]')!.click();
    f.detectChanges();
    el<HTMLFormElement>(f, '[data-test="reject-form"]')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne(`${BASE}/reject`);
    expect(req.request.body).toEqual({ reason: null });
    req.flush({ ...QUOTE, status: 'REJECTED', canDecide: false, decidedAt: '2026-10-28T09:00:00Z', decidedChannel: 'CUSTOMER' });
    f.detectChanges();
    expect(el(f, '[data-test="decided"]')!.textContent).toContain('You rejected this quote on');
  });

  it('shows an expired quote without the forms', () => {
    const f = open({ ...QUOTE, status: 'EXPIRED', canDecide: false });
    expect(el(f, '[data-test="closed"]')!.textContent).toContain('This quote expired on');
    expect(el(f, '[data-test="approve-form"]')).toBeNull();
  });

  it('shows quote-expired from an approval and reloads the stored quote', () => {
    const f = open();
    type(f, '#pq-name', 'Ana Silva');
    el<HTMLInputElement>(f, '#pq-accept')!.click();
    el<HTMLFormElement>(f, '[data-test="approve-form"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${BASE}/approve`).flush({ code: 'quote-expired' }, { status: 409, statusText: 'Conflict' });
    http.expectOne(BASE).flush({ ...QUOTE, status: 'EXPIRED', canDecide: false });
    f.detectChanges();
    expect(text(f)).toContain('This quote has expired.');
    expect(el(f, '[data-test="approve-form"]')).toBeNull();
  });

  it('shows quote-not-open when it was decided or replaced meanwhile', () => {
    const f = open();
    el<HTMLButtonElement>(f, '[data-test="reject-toggle"]')!.click();
    f.detectChanges();
    el<HTMLFormElement>(f, '[data-test="reject-form"]')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${BASE}/reject`).flush({ code: 'quote-not-open' }, { status: 409, statusText: 'Conflict' });
    http.expectOne(BASE).flush({ ...QUOTE, status: 'SUPERSEDED', canDecide: false });
    f.detectChanges();
    expect(text(f)).toContain('has already been decided or replaced');
    expect(el(f, '[data-test="closed"]')!.textContent).toContain('replaced by a newer one');
  });

  it('says when the link is unknown', () => {
    const f = open(null, problem(404, { code: 'quote-not-found' }));
    expect(el(f, '[data-test="state"]')!.textContent).toContain('Quote not found');
  });

  it('is translated into Portuguese by default', () => {
    TestBed.inject(I18nService).use('pt-PT');
    const f = open();
    expect(text(f)).toContain('Orçamento n.º 4');
    expect(text(f)).toContain('Aprovar orçamento');
  });
});
