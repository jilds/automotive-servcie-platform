import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { BoardCard, User, WorkOrder } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TenantThemeService } from '../shared/theme.service';
import { Board } from './board';
import { boardQuoteStatus, canSendQuote, copyText, decisionForm, needsNewQuote, quoteLink, toDecisionRequest } from './quote-logic';
import { QuotePanel } from './quote-panel';
import { Quote, SentQuote } from './quote.models';
import { WorkOrderDetail } from './work-order-detail';

const LINE = { id: 'l1', position: 1, type: 'LABOR' as const, description: 'Mudança de óleo', quantity: 1, unitPriceCents: 4000, discountPercent: 0, vatRate: 23, netCents: 4000 };

const order = (over: Partial<WorkOrder> = {}): WorkOrder => ({
  id: 'w1', number: 12, vehicleId: 'v1', plate: 'AA00AA', plateDisplay: 'AA-00-AA', plateEra: 'PT_2020',
  registrationCountry: 'PT', registrationDate: null, status: 'DIAGNOSTICS', allowedNext: [], mileageIn: 1000,
  fuelLevel: 4, complaint: null, damageNotes: null, assignedMechanicId: null, statusChangedAt: '2026-10-27T09:00:00Z',
  lines: [LINE], netCents: 4000, vatCents: 920, totalCents: 4920, readOnly: false, quote: null,
  ...over,
});

const quote = (over: Partial<Quote> = {}): Quote => ({
  id: 'q4', number: 4, workOrderId: 'w1', status: 'SENT', sentAt: '2026-10-27T10:00:00Z', sentByName: 'Rita',
  validUntil: '2026-11-11T10:00:00Z', netCents: 4000, vatCents: 920, totalCents: 4920,
  lines: [{ position: 1, type: 'LABOR', description: 'Mudança de óleo', quantity: 1, unitPriceCents: 4000, discountPercent: 0, vatRate: 23, netCents: 4000, vatCents: 920, serviceItemId: null, productId: null }],
  vatByRate: [{ vatRate: 23, netCents: 4000, vatCents: 920 }],
  decidedAt: null, decidedChannel: null, decidedName: null, decidedByName: null, decidedNote: null, version: 3,
  ...over,
});

const summary = (q: Quote) => ({ id: q.id, number: q.number, status: q.status, validUntil: q.validUntil, decidedAt: q.decidedAt, decidedChannel: q.decidedChannel });

const text = (f: ComponentFixture<unknown>) => ((f.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
const el = <T extends Element = HTMLElement>(f: ComponentFixture<unknown>, selector: string) => (f.nativeElement as HTMLElement).querySelector(selector) as T | null;

describe('quote logic', () => {
  it('allows a send only in Diagnostics or Awaiting approval with at least one line', () => {
    expect(canSendQuote({ status: 'DIAGNOSTICS', lines: [LINE] })).toBe(true);
    expect(canSendQuote({ status: 'AWAITING_APPROVAL', lines: [LINE] })).toBe(true);
    expect(canSendQuote({ status: 'DIAGNOSTICS', lines: [] })).toBe(false);
    expect(canSendQuote({ status: 'IN_PROGRESS', lines: [LINE] })).toBe(false);
  });

  it('asks for a new quote only when the latest is superseded or expired', () => {
    expect(needsNewQuote('SUPERSEDED')).toBe(true);
    expect(needsNewQuote('EXPIRED')).toBe(true);
    expect(needsNewQuote('SENT')).toBe(false);
    expect(needsNewQuote(null)).toBe(false);
  });

  it('builds the customer link and hides superseded quotes on the board', () => {
    expect(quoteLink('https://app.example.pt/', 'oficina-azul', 'tok/en')).toBe('https://app.example.pt/w/oficina-azul/quote/tok%2Fen');
    expect(boardQuoteStatus('SUPERSEDED')).toBeNull();
    expect(boardQuoteStatus('EXPIRED')).toBe('EXPIRED');
    expect(boardQuoteStatus(undefined)).toBeNull();
  });

  it('requires a decision and a note that is not blank, at most 500 characters', () => {
    const form = decisionForm();
    expect(form.invalid).toBe(true);
    form.setValue({ decision: 'APPROVED', note: '   ' });
    expect(form.controls.note.hasError('required')).toBe(true);
    form.controls.note.setValue('x'.repeat(501));
    expect(form.controls.note.hasError('maxlength')).toBe(true);
    form.controls.note.setValue('  approved by phone ');
    expect(form.valid).toBe(true);
    expect(toDecisionRequest(form, 3)).toEqual({ decision: 'APPROVED', note: 'approved by phone', version: 3 });
  });

  it('reports whether the clipboard took the text', async () => {
    expect(await copyText('a', { writeText: () => Promise.resolve() })).toBe(true);
    expect(await copyText('a', { writeText: () => Promise.reject(new Error('denied')) })).toBe(false);
    expect(await copyText('a', undefined)).toBe(false);
  });
});

describe('QuotePanel', () => {
  let http: HttpTestingController;

  function open(w: WorkOrder, history: Quote[] = []) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18nService).use('en');
    TestBed.inject(TenantThemeService).branding.set({ slug: 'oficina-azul', name: 'Oficina Azul' } as never);
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(QuotePanel);
    f.componentRef.setInput('workOrder', w);
    const changed = vi.fn();
    f.componentInstance.changed.subscribe(changed);
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/quotes').flush(history);
    f.detectChanges();
    return { f, changed };
  }

  afterEach(() => http.verify());

  it('sends a quote, shows the link once with a copy button and whether it was e-mailed', async () => {
    const { f, changed } = open(order());
    expect(text(f)).toContain('No quote sent yet.');
    el<HTMLButtonElement>(f, '[data-test="send"]')!.click();
    const sent: SentQuote = { quote: quote(), statusToken: 'T'.repeat(43), customerHasEmail: false };
    const req = http.expectOne('/api/v1/work-orders/w1/quotes');
    expect(req.request.method).toBe('POST');
    req.flush(sent);
    await f.whenStable();
    expect(changed).toHaveBeenCalledTimes(1);

    // The parent reloads the work order: the quote is now the latest and SENT.
    f.componentRef.setInput('workOrder', order({ status: 'AWAITING_APPROVAL', quote: summary(sent.quote) }));
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/quotes').flush([sent.quote]);
    f.detectChanges();

    const input = el<HTMLInputElement>(f, '#quote-link')!;
    expect(input.readOnly).toBe(true);
    expect(input.value).toBe(`${location.origin}/w/oficina-azul/quote/${'T'.repeat(43)}`);
    expect(el(f, '[data-test="emailed"]')!.textContent).toContain('no e-mail address');
    expect(text(f)).toContain('Quote #4 sent');

    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    el<HTMLButtonElement>(f, '[data-test="copy"]')!.click();
    await f.whenStable();
    f.detectChanges();
    expect(writeText).toHaveBeenCalledWith(input.value);
    expect(text(f)).toContain('Link copied.');
  });

  it('disables sending outside Diagnostics / Awaiting approval or without lines', () => {
    const { f } = open(order({ status: 'IN_PROGRESS' }));
    expect(el<HTMLButtonElement>(f, '[data-test="send"]')!.disabled).toBe(true);
    expect(text(f)).toContain('A quote can be sent in Diagnostics or Awaiting approval');
  });

  it('warns about a superseded quote and sends again', () => {
    const old = quote({ status: 'SUPERSEDED' });
    const { f } = open(order({ status: 'AWAITING_APPROVAL', quote: summary(old) }), [old]);
    expect(el(f, '[data-test="stale"]')!.textContent).toContain('Quote #4 is out of date');
    expect(el(f, '[data-test="send"]')).toBeNull();
    el<HTMLButtonElement>(f, '[data-test="send-again"]')!.click();
    expect(http.expectOne('/api/v1/work-orders/w1/quotes').request.method).toBe('POST');
  });

  it('warns about an expired quote', () => {
    const old = quote({ status: 'EXPIRED' });
    const { f } = open(order({ status: 'AWAITING_APPROVAL', quote: summary(old) }), [old]);
    expect(el(f, '[data-test="stale"]')!.textContent).toContain('Quote #4 expired before the customer answered.');
  });

  it('records a decision only with a note, sending the version', () => {
    const open4 = quote();
    const { f, changed } = open(order({ status: 'AWAITING_APPROVAL', quote: summary(open4) }), [open4]);
    el<HTMLButtonElement>(f, '[data-test="record"]')!.click();
    f.detectChanges();

    const form = el<HTMLFormElement>(f, '[data-test="decision-form"]')!;
    form.dispatchEvent(new Event('submit'));
    f.detectChanges();
    http.expectNone('/api/v1/work-orders/w1/quotes/q4/decision');
    expect(text(f)).toContain('Choose approved or rejected.');
    expect(text(f)).toContain('Say how the customer decided.');

    el<HTMLInputElement>(f, '#qd-APPROVED')!.click();
    const note = el<HTMLTextAreaElement>(f, '#qd-note')!;
    note.value = 'Approved by phone';
    note.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/v1/work-orders/w1/quotes/q4/decision');
    expect(req.request.body).toEqual({ decision: 'APPROVED', note: 'Approved by phone', version: 3 });
    req.flush(quote({ status: 'APPROVED', decidedChannel: 'STAFF', decidedByName: 'Rita', decidedNote: 'Approved by phone', decidedAt: '2026-10-27T11:00:00Z' }));
    f.detectChanges();
    expect(changed).toHaveBeenCalledTimes(1);
    expect(text(f)).toContain('Quote #4 recorded as approved.');
  });

  it('shows the server note error on the note field', () => {
    const open4 = quote();
    const { f } = open(order({ status: 'AWAITING_APPROVAL', quote: summary(open4) }), [open4]);
    el<HTMLButtonElement>(f, '[data-test="record"]')!.click();
    f.detectChanges();
    el<HTMLInputElement>(f, '#qd-REJECTED')!.click();
    const note = el<HTMLTextAreaElement>(f, '#qd-note')!;
    note.value = 'x';
    note.dispatchEvent(new Event('input'));
    el<HTMLFormElement>(f, '[data-test="decision-form"]')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/work-orders/w1/quotes/q4/decision').flush({ code: 'decision-note-required', errors: { note: 'x' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(el(f, '#qd-note')!.classList).toContain('is-invalid');
    expect(text(f)).toContain('Add a note saying how the customer decided.');
  });

  it('lists the history with totals and the decision', () => {
    const decided = quote({ status: 'APPROVED', decidedChannel: 'CUSTOMER', decidedName: 'Ana Silva', decidedAt: '2026-10-27T11:00:00Z' });
    const { f } = open(order({ status: 'IN_PROGRESS', quote: summary(decided) }), [decided]);
    const history = el(f, '[data-test="history"]')!.textContent!.replace(/\s+/g, ' ');
    expect(history).toContain('Quote #4');
    expect(history).toContain('Approved');
    expect(history).toContain('by the customer on the link, as Ana Silva');
    expect(history).toMatch(/49[.,]20/);
  });
});

describe('Quote status for mechanics', () => {
  function mechanicAuth() {
    return { hasRole: (...roles: string[]) => roles.includes('MECHANIC'), canSeePrices: signal(false), user: signal({ id: 'm1', role: 'MECHANIC' }) };
  }

  it('shows only the badge on the work order: no panel, no amounts', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: mechanicAuth() }],
    });
    TestBed.inject(I18nService).use('en');
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(WorkOrderDetail);
    f.componentRef.setInput('id', 'w1');
    f.detectChanges();
    const q = quote({ status: 'APPROVED' });
    http.expectOne('/api/v1/work-orders/w1').flush(order({ status: 'IN_PROGRESS', assignedMechanicId: 'm1', netCents: null, vatCents: null, totalCents: null, lines: [{ ...LINE, unitPriceCents: null, netCents: null, vatRate: null, discountPercent: null }], quote: summary(q) }));
    f.detectChanges();
    expect(el(f, '[data-test="quote-badge"]')!.textContent).toContain('Quote approved');
    expect(el(f, 'app-quote-panel')).toBeNull();
    expect(text(f)).not.toMatch(/€/);
    http.expectNone('/api/v1/work-orders/w1/quotes');
    http.verify();
  });

  it('shows the quote badge on the board card, except for a superseded quote', () => {
    const card = (id: string, quoteStatus: BoardCard['quoteStatus']): BoardCard => ({
      id, number: 1, plate: 'AA00AA', plateDisplay: 'AA-00-AA', plateEra: 'PT_2020', registrationCountry: 'PT', registrationDate: null,
      vehicle: 'Renault Clio', assignedMechanicId: 'm1', statusChangedAt: '2026-10-27T08:00:00Z', quoteStatus,
    });
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: { user: signal({ id: 'm1', role: 'MECHANIC' } as User) } }],
    });
    TestBed.inject(I18nService).use('en');
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(Board);
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/board').flush({ columns: { AWAITING_APPROVAL: [card('a', 'SENT'), card('b', 'SUPERSEDED')], IN_PROGRESS: [card('c', 'APPROVED')], DIAGNOSTICS: [card('d', null)] } });
    f.detectChanges();
    const badges = [...(f.nativeElement as HTMLElement).querySelectorAll('[data-test="quote-badge"]')].map((b) => b.textContent!.trim());
    expect(badges).toEqual(['Quote sent', 'Quote approved']);
    http.verify();
  });
});
