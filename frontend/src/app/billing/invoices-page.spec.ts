import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { Shell } from '../shared/shell';
import { roleGuard } from '../core/auth/guards';
import { el, settings, summary, text, typeInto } from './billing-test-utils';
import { InvoicesPage, NO_FILTERS, toQuery } from './invoices-page';

describe('InvoicesPage', () => {
  let http: HttpTestingController;

  function open() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(InvoicesPage);
    f.detectChanges();
    http.expectOne('/api/v1/billing/settings').flush(settings());
    return f;
  }

  const list = (query: (p: URLSearchParams) => boolean = () => true) => http.expectOne((r) => r.url === '/api/v1/invoices' && query(new URLSearchParams(r.params.toString())));

  afterEach(() => http.verify());

  it('lists documents with badges, money and links, and the sandbox banner', () => {
    const f = open();
    list().flush({
      items: [
        summary(),
        summary({ id: 'i2', status: 'FAILED', number: null, paymentStatus: null, totalCents: 1000, balanceCents: 0 }),
        summary({ id: 'i3', kind: 'CREDIT_NOTE', number: 'NC SANDBOX/1', paymentStatus: null, sandbox: false }),
      ],
      page: 0, size: 20, total: 3,
    });
    f.detectChanges();
    const t = text(f);
    expect(t).toContain('FT SANDBOX/3');
    expect(t).toContain('Not numbered yet');
    expect(t).toContain('NC SANDBOX/1');
    expect(t).toMatch(/49[.,]20/);
    expect(t).toContain('Unpaid');
    expect(t).toContain('Failed');
    expect(el(f, '[data-test="sandbox"]')!.textContent).toContain('not a fiscal document');
    expect(el<HTMLAnchorElement>(f, 'tbody a')!.getAttribute('href')).toBe('/invoices/i1');
    expect(el(f, '[data-test="range"]')!.textContent).toContain('1-3 of 3');
    // The table stacks into cards on phones: cells carry their captions.
    expect(el(f, 'div.table-stack')).not.toBeNull();
    expect(el(f, 'td[data-label]')).not.toBeNull();
  });

  it('flags money to return and a document issued after its discard', () => {
    const f = open();
    list().flush({
      items: [
        summary({ id: 'v1', status: 'VOIDED', paymentStatus: null, refundDueCents: 2000 }),
        summary({ id: 'd1', status: 'DISCARDED', number: 'FT SANDBOX/9', paymentStatus: null }),
        summary({ id: 'd2', status: 'DISCARDED', number: null, paymentStatus: null }),
      ],
      page: 0, size: 20, total: 3,
    });
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelectorAll('[data-test="refund"]').length).toBe(1);
    expect(el(f, '[data-test="refund"]')!.textContent).toMatch(/To return .*20[.,]00/);
    expect((f.nativeElement as HTMLElement).querySelectorAll('[data-test="issued-after-discard"]').length).toBe(1);
  });

  it('keeps the newest answer when an older one arrives late', () => {
    const f = open();
    const first = list();
    f.componentInstance['draft'].set({ ...NO_FILTERS, q: 'ana' });
    el<HTMLFormElement>(f, '[data-test="filters"]')!.dispatchEvent(new Event('submit'));
    const second = list((p) => p.get('q') === 'ana');
    second.flush({ items: [summary({ id: 'new', number: 'FT NEW/1' })], page: 0, size: 20, total: 1 });
    f.detectChanges();
    first.flush({ items: [summary({ id: 'old', number: 'FT OLD/1' })], page: 0, size: 20, total: 1 });
    f.detectChanges();
    expect(text(f)).toContain('FT NEW/1');
    expect(text(f)).not.toContain('FT OLD/1');
  });

  it('shows an empty state', () => {
    const f = open();
    list().flush({ items: [], page: 0, size: 20, total: 0 });
    f.detectChanges();
    expect(el(f, '[data-test="empty"]')!.textContent).toContain('No documents match these filters.');
    expect(el(f, '[data-test="range"]')).toBeNull();
  });

  it('applies the filters to the query and goes back to the first page', () => {
    const f = open();
    list().flush({ items: [], page: 0, size: 20, total: 0 });
    f.detectChanges();

    const select = (id: string, value: string) => {
      const s = el<HTMLSelectElement>(f, id)!;
      s.value = value;
      s.dispatchEvent(new Event('change'));
    };
    select('#f-status', 'FAILED');
    select('#f-kind', 'INVOICE');
    select('#f-pay', 'PARTIALLY_PAID');
    const from = el<HTMLInputElement>(f, '#f-from')!;
    from.value = '2026-11-01';
    from.dispatchEvent(new Event('change'));
    typeInto(el<HTMLInputElement>(f, '#f-q')!, 'ana');
    el<HTMLFormElement>(f, '[data-test="filters"]')!.dispatchEvent(new Event('submit'));

    const req = list();
    expect(req.request.params.getAll('status')).toEqual(['FAILED']);
    expect(req.request.params.get('kind')).toBe('INVOICE');
    expect(req.request.params.get('paymentStatus')).toBe('PARTIALLY_PAID');
    expect(req.request.params.get('from')).toBe('2026-11-01');
    expect(req.request.params.get('q')).toBe('ana');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ items: [], page: 0, size: 20, total: 0 });

    el<HTMLButtonElement>(f, '[data-test="reset"]')!.click();
    const cleared = list();
    expect(cleared.request.params.has('status')).toBe(false);
    expect(cleared.request.params.has('q')).toBe(false);
    cleared.flush({ items: [], page: 0, size: 20, total: 0 });
  });

  it('pages through the results', () => {
    const f = open();
    list().flush({ items: [summary()], page: 0, size: 20, total: 45 });
    f.detectChanges();
    expect(el<HTMLButtonElement>(f, '[data-test="prev"]')!.disabled).toBe(true);
    expect(el(f, '[data-test="range"]')!.textContent).toContain('1-20 of 45');
    el<HTMLButtonElement>(f, '[data-test="next"]')!.click();
    const second = list((p) => p.get('page') === '1');
    second.flush({ items: [summary()], page: 1, size: 20, total: 45 });
    f.detectChanges();
    expect(el(f, '[data-test="range"]')!.textContent).toContain('21-40 of 45');
    el<HTMLButtonElement>(f, '[data-test="next"]')!.click();
    list((p) => p.get('page') === '2').flush({ items: [summary()], page: 2, size: 20, total: 45 });
    f.detectChanges();
    expect(el<HTMLButtonElement>(f, '[data-test="next"]')!.disabled).toBe(true);
  });

  it('shows the translated error of a failed load', () => {
    const f = open();
    list().flush({ code: 'validation', status: 400 }, { status: 500, statusText: 'Server Error' });
    f.detectChanges();
    expect(el(f, '[role="alert"]')).not.toBeNull();
  });

  it('builds the query without empty filters and ignores a search of one character', () => {
    expect(toQuery(NO_FILTERS, 0)).toMatchObject({ status: undefined, kind: '', from: undefined, q: undefined, page: 0, size: 20 });
    expect(toQuery({ ...NO_FILTERS, status: 'ISSUED', q: 'a' }, 1)).toMatchObject({ status: ['ISSUED'], q: undefined, page: 1 });
  });
});

describe('Invoices menu and guard', () => {
  function shell(role: string) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AuthService, useValue: { user: signal({ id: 'u1', name: 'Rita', role, tenantId: 't1' }), hasRole: (...r: string[]) => r.includes(role), logout: () => {} } },
      ],
    });
    TestBed.inject(I18nService).use('en');
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(Shell);
    f.detectChanges();
    http.expectOne('/api/v1/branding').flush({ slug: 's', name: 'S', addOns: { booking: false, shop: false }, version: 1 });
    if (role === 'MECHANIC') http.expectOne('/api/v1/time/running').flush({ entry: null, workOrderNumber: null, plateDisplay: null });
    f.detectChanges();
    http.verify();
    return el<HTMLAnchorElement>(f, 'a[href="/invoices"]');
  }

  it('shows Invoices to the owner and the front desk', () => {
    expect(shell('OWNER')!.textContent).toContain('Invoices');
  });

  it('shows Invoices to the front desk', () => {
    expect(shell('FRONT_DESK')).not.toBeNull();
  });

  it('never shows it to a mechanic', () => {
    expect(shell('MECHANIC')).toBeNull();
  });

  it('sends a mechanic back to the board from the guarded route', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { hasRole: (...r: string[]) => r.includes('MECHANIC') } }] });
    const result = TestBed.runInInjectionContext(() => roleGuard('OWNER', 'FRONT_DESK')({} as never, {} as never));
    expect(result).not.toBe(true);
    expect(String(result)).toBe('/board');
  });
});
