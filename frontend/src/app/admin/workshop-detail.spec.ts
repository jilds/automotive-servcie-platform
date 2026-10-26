import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { addOnsChanged, AdminWorkshop } from './admin.models';
import { WorkshopDetail } from './workshop-detail';

const WORKSHOP: AdminWorkshop = {
  id: 'w1', name: 'Oficina Azul', nif: null, country: 'PT', timezone: 'Europe/Lisbon', slug: 'oficina-azul',
  displayName: null, plan: 'PRO', status: 'ACTIVE', subscriptionUntil: null, expired: false, createdAt: '2026-10-01T10:00:00Z',
  ownerEmail: 'dono@oficina.pt', users: 2, addOns: { booking: false, shop: false }, version: 4,
};

describe('addOnsChanged', () => {
  it('is true only when a switch differs from the stored add-ons', () => {
    expect(addOnsChanged({ booking: false, shop: false }, { booking: false, shop: false })).toBe(false);
    expect(addOnsChanged({ booking: false, shop: false }, { booking: true, shop: false })).toBe(true);
    expect(addOnsChanged({ booking: true, shop: true }, { booking: true, shop: false })).toBe(true);
  });
});

describe('WorkshopDetail add-ons card', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkshopDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function open(workshop: AdminWorkshop = WORKSHOP) {
    const fixture = TestBed.createComponent(WorkshopDetail);
    fixture.componentRef.setInput('id', workshop.id);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/workshops/w1').flush(workshop);
    fixture.detectChanges();
    // The theme editor loads the workshop's branding on its own.
    for (const r of http.match('/api/v1/admin/workshops/w1/branding')) {
      r.flush({ slug: 'oficina-azul', name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null,
        accentColor: null, logoUrl: null, addOns: workshop.addOns, version: workshop.version });
    }
    fixture.detectChanges();
    return fixture;
  }

  function toggle(page: HTMLElement, id: string): void {
    const input = page.querySelector(id) as HTMLInputElement;
    input.checked = !input.checked;
    input.dispatchEvent(new Event('change'));
  }

  function saveButton(page: HTMLElement): HTMLButtonElement {
    return [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Guardar extras')) as HTMLButtonElement;
  }

  it('shows the stored add-ons and saves a change with the workshop version', () => {
    const fixture = open({ ...WORKSHOP, addOns: { booking: false, shop: true } });
    const page: HTMLElement = fixture.nativeElement;
    expect((page.querySelector('#addon-booking') as HTMLInputElement).checked).toBe(false);
    expect((page.querySelector('#addon-shop') as HTMLInputElement).checked).toBe(true);
    expect(saveButton(page).disabled).toBe(true);

    toggle(page, '#addon-booking');
    fixture.detectChanges();
    expect(saveButton(page).disabled).toBe(false);
    saveButton(page).click();

    const put = http.expectOne('/api/v1/admin/workshops/w1/add-ons');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ booking: true, shop: true, version: 4 });
    put.flush({ ...WORKSHOP, addOns: { booking: true, shop: true }, version: 5 });
    fixture.detectChanges();

    expect(saveButton(page).disabled).toBe(true);
    expect(page.textContent).toContain('Extras guardados.');
  });

  it('keeps an unsaved add-on switch when the subscription is saved, and sends the new version next', () => {
    const fixture = open();
    const page: HTMLElement = fixture.nativeElement;
    toggle(page, '#addon-shop');
    fixture.detectChanges();

    const suspend = [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Suspender')) as HTMLButtonElement;
    suspend.click();
    http.expectOne('/api/v1/admin/workshops/w1/subscription').flush({ ...WORKSHOP, status: 'SUSPENDED', version: 5 });
    fixture.detectChanges();

    expect((page.querySelector('#addon-shop') as HTMLInputElement).checked).toBe(true);
    saveButton(page).click();
    expect(http.expectOne('/api/v1/admin/workshops/w1/add-ons').request.body).toEqual({ booking: false, shop: true, version: 5 });
  });

  it('reloads the workshop as stored when someone else saved it first', () => {
    const fixture = open();
    const page: HTMLElement = fixture.nativeElement;
    toggle(page, '#addon-booking');
    fixture.detectChanges();
    saveButton(page).click();

    http.expectOne('/api/v1/admin/workshops/w1/add-ons').flush(
      { type: '/problems/stale-data', status: 409, code: 'stale-data' },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne('/api/v1/admin/workshops/w1').flush({ ...WORKSHOP, addOns: { booking: false, shop: true }, version: 7 });
    fixture.detectChanges();

    expect((page.querySelector('#addon-booking') as HTMLInputElement).checked).toBe(false);
    expect((page.querySelector('#addon-shop') as HTMLInputElement).checked).toBe(true);
    expect(page.querySelector('app-alert')?.textContent).toContain('Outra pessoa alterou');
  });

  it('shows no expired badge while the subscription runs', () => {
    const page: HTMLElement = open().nativeElement;
    expect(page.textContent).not.toContain('Expirada');
  });

  it('marks an expired subscription and says how to let the workshop back in', () => {
    const page: HTMLElement = open({ ...WORKSHOP, subscriptionUntil: '2026-09-01', expired: true }).nativeElement;
    expect(page.querySelector('header .bg-danger-subtle')?.textContent).toContain('Expirada');
    expect(page.textContent).toContain('Altere a data de fim para voltarem a entrar.');
  });
});
