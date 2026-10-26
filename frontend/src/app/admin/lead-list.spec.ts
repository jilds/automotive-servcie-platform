import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Lead } from './admin.models';
import { LeadList } from './lead-list';

const LEAD: Lead = {
  id: 'l1', name: 'Ana Silva', workshopName: 'Oficina Azul', phone: '+351 912 345 678', email: 'ana@oficina.pt',
  district: 'Lisboa', vehicleType: 'BOTH', message: 'Duas oficinas', marketingConsent: true, status: 'NEW',
  createdAt: '2026-10-26T10:00:00Z', statusChangedAt: null, version: 0,
};

describe('LeadList', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeadList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function open(leads: Lead[] = [LEAD]) {
    const fixture = TestBed.createComponent(LeadList);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/admin/leads' && r.params.get('status') === 'NEW').flush(leads);
    fixture.detectChanges();
    return fixture;
  }

  function choose(page: HTMLElement, selector: string, value: string): void {
    const select = page.querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  it('shows the new requests first, with encoded contact links', () => {
    const page: HTMLElement = open().nativeElement;

    expect(page.textContent).toContain('Oficina Azul');
    expect(page.textContent).toContain('Automóveis e motociclos');
    expect(page.querySelector('a[href^="tel:"]')?.getAttribute('href')).toBe('tel:%2B351%20912%20345%20678');
    expect(page.querySelector('a[href^="mailto:"]')?.getAttribute('href')).toBe('mailto:ana%40oficina.pt');
  });

  it('shows the filter it loaded with', () => {
    const page: HTMLElement = open().nativeElement;

    expect((page.querySelector('#lead-filter') as HTMLSelectElement).value).toBe('NEW');
  });

  it('saves a status with the version read and drops it from the filtered list', () => {
    const fixture = open();
    const page: HTMLElement = fixture.nativeElement;

    choose(page, '#lead-status-l1', 'CONTACTED');
    const put = http.expectOne('/api/v1/admin/leads/l1/status');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ status: 'CONTACTED', version: 0 });
    put.flush({ ...LEAD, status: 'CONTACTED', version: 1 });
    fixture.detectChanges();

    expect(page.textContent).not.toContain('Oficina Azul');
  });

  it('reloads the list and says why when someone else changed the request first', () => {
    const fixture = open();
    const page: HTMLElement = fixture.nativeElement;

    choose(page, '#lead-status-l1', 'CLOSED');
    http.expectOne('/api/v1/admin/leads/l1/status').flush(
      { type: '/problems/stale-data', status: 409, code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne((r) => r.url === '/api/v1/admin/leads').flush([{ ...LEAD, status: 'NEW', version: 2 }]);
    fixture.detectChanges();

    expect(page.querySelector('app-alert')?.textContent).toContain('Outra pessoa alterou');
  });

  it('lists every status when the filter is cleared', () => {
    const fixture = open([]);
    const page: HTMLElement = fixture.nativeElement;

    choose(page, '#lead-filter', '');
    const all = http.expectOne((r) => r.url === '/api/v1/admin/leads');
    expect(all.request.params.has('status')).toBe(false);
    all.flush([]);
    fixture.detectChanges();

    expect(page.textContent).toContain('Não há pedidos de proposta aqui.');
  });
});
