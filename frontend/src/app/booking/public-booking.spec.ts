import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { BookingStatusPage } from './booking-status';
import { PublicBooking as PublicBookingData, PublicService } from './booking.models';
import { PublicBooking } from './public-booking';

const BASE = '/api/v1/public/oficina-azul/booking';
const SERVICES: PublicService[] = [
  { id: 's1', name: 'Mudança de óleo', description: 'Óleo e filtro', category: 'Manutenção', durationMinutes: 60, priceCents: 4000, vatRate: 23, priceWithVatCents: 4920 },
  { id: 's2', name: 'Pastilhas de travão', description: null, category: 'Travões', durationMinutes: 90, priceCents: 8000, vatRate: 23, priceWithVatCents: 9840 },
];

describe('PublicBooking', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.documentElement.removeAttribute('data-tenant-theme');
  });

  function open(privacyUrl: string | null = null) {
    const fixture = TestBed.createComponent(PublicBooking);
    fixture.componentRef.setInput('slug', 'oficina-azul');
    fixture.detectChanges();
    http.expectOne('/api/v1/branding/oficina-azul').flush({ slug: 'oficina-azul', name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null, accentColor: null, logoUrl: null, privacyUrl, version: 1 });
    http.expectOne(`${BASE}/services`).flush(SERVICES);
    fixture.detectChanges();
    return fixture;
  }

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  /** Answers the picker's slot request with one free 09:00 slot on its first day; returns that slot's start. */
  function flushSlots(f: ComponentFixture<PublicBooking>): string {
    const req: TestRequest = http.expectOne((r) => r.url === `${BASE}/slots`);
    const from = req.request.params.get('from')!;
    expect(req.request.params.get('days')).toBe('7');
    const start = `${from}T09:00:00Z`;
    req.flush([{ date: from, slots: [{ start, time: '09:00', free: 2 }] }]);
    f.detectChanges();
    return start;
  }

  function toContactStep(f: ComponentFixture<PublicBooking>): string {
    const box = f.nativeElement.querySelector('#bk-svc-s1') as HTMLInputElement;
    box.click();
    f.detectChanges();
    f.componentInstance.next();
    f.detectChanges();
    const start = flushSlots(f);
    const slotButton = f.nativeElement.querySelector('.slot-btn') as HTMLButtonElement;
    expect(slotButton.textContent).toContain('2 places');
    slotButton.click();
    f.detectChanges();
    const form = f.componentInstance['form'];
    form.patchValue({ name: 'Ana Silva', phone: '912345678', plate: 'aa-00-aa', note: 'Barulho ao travar', privacyAccepted: true });
    f.detectChanges();
    return start;
  }

  it('shows the services with duration and price incl. VAT, and needs one before going on', () => {
    const f = open();
    expect(text(f)).toContain('Mudança de óleo');
    expect(text(f)).toContain('1 h 30 min');
    expect(text(f)).toContain('€49.20');
    f.componentInstance.next();
    f.detectChanges();
    expect(text(f)).toContain('Choose at least one service.');
  });

  it('books a slot and shows the number, the time and the status link', () => {
    const f = open();
    const start = toContactStep(f);
    expect(text(f)).toContain('€49.20');

    f.componentInstance.submit();
    const post = http.expectOne(`${BASE}/bookings`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      serviceIds: ['s1'], slotStart: start, name: 'Ana Silva', phone: '912345678', email: null, plate: 'AA-00-AA',
      note: 'Barulho ao travar', privacyAccepted: true, marketingConsent: false, website: '',
      // The page language (this spec runs in English), for the e-mail with the status link.
      language: 'en',
    });
    post.flush({ number: 12, statusToken: 'tok_123', slotStart: start }, { status: 201, statusText: 'Created' });
    f.detectChanges();

    expect(text(f)).toContain('Booking requested');
    expect(f.nativeElement.querySelector('.ticket-number').textContent).toBe('12');
    expect(text(f)).toContain('at 09:00');
    expect((f.nativeElement.querySelector('#bk-link') as HTMLInputElement).value).toMatch(/\/w\/oficina-azul\/booking\/tok_123$/);
  });

  it('goes back to the times, freshly loaded, when the slot was taken (409 slot-unavailable)', () => {
    const f = open();
    toContactStep(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/bookings`).flush({ code: 'slot-unavailable' }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();

    expect(text(f)).toContain('That time was taken in the meantime. Choose another one.');
    expect(f.nativeElement.querySelector('[aria-current="step"]').textContent).toContain('Day and time');
    flushSlots(f);
    expect(f.nativeElement.querySelector('.slot-btn[aria-pressed="true"]')).toBeNull();
  });

  it('hides the bot trap from people, keyboards and autofill', () => {
    const f = open();
    toContactStep(f);
    const trap = f.nativeElement.querySelector('#bk-hp-x9') as HTMLInputElement;
    expect(trap.getAttribute('tabindex')).toBe('-1');
    expect(trap.getAttribute('autocomplete')).toBe('new-password');
    expect(trap.getAttribute('name')).toBe('bk-hp-x9');
    expect(trap.closest('[aria-hidden="true"]')).not.toBeNull();
    expect(f.nativeElement.querySelector('[name="website"], #bk-website')).toBeNull();
  });

  it('links the platform privacy policy in a new tab only when it is configured', () => {
    const f = open('https://asp.example/privacidade');
    toContactStep(f);
    const link = f.nativeElement.querySelector('#bk-platform-privacy') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('https://asp.example/privacidade');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
    expect(link.textContent!.trim()).toBe('Platform privacy policy');

    const without = open();
    toContactStep(without);
    expect(without.nativeElement.querySelector('#bk-platform-privacy')).toBeNull();
  });

  it('marks the privacy checkbox when the server asks for consent', () => {
    const f = open();
    toContactStep(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/bookings`).flush({ code: 'privacy-consent-required' }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect((f.nativeElement.querySelector('#bk-privacy') as HTMLElement).classList).toContain('is-invalid');
  });

  it('says booking is not available when the add-on is off, and asks to wait after too many requests', () => {
    const fixture = TestBed.createComponent(PublicBooking);
    fixture.componentRef.setInput('slug', 'oficina-azul');
    fixture.detectChanges();
    http.expectOne('/api/v1/branding/oficina-azul').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${BASE}/services`).flush({ code: 'add-on-not-available' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(text(fixture)).toContain('Online booking is not available');

    const limited = open();
    limited.componentInstance['state'].set('ok');
    limited.componentInstance.next();
    limited.componentInstance['selected'].set(['s1']);
    limited.componentInstance.next();
    limited.detectChanges();
    http.expectOne((r) => r.url === `${BASE}/slots`).flush({ code: 'too-many-requests' }, { status: 429, statusText: 'Too Many Requests' });
    limited.detectChanges();
    expect(text(limited)).toContain('Too many attempts');
  });
});

describe('BookingStatusPage', () => {
  let http: HttpTestingController;
  const booking: PublicBookingData = {
    number: 12, status: 'CONFIRMED', slotStart: '2026-11-02T09:00:00Z', workshopName: 'Oficina Azul',
    services: [{ name: 'Mudança de óleo', durationMinutes: 60, priceWithVatCents: 4920 }, { name: 'Pastilhas', durationMinutes: 30, priceWithVatCents: 1000 }],
    canCancel: true, declineReason: null, timeZone: 'Europe/Lisbon',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('pt-PT');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function open(answer: (req: TestRequest) => void) {
    const f = TestBed.createComponent(BookingStatusPage);
    f.componentRef.setInput('slug', 'oficina-azul');
    f.componentRef.setInput('token', 'tok_123');
    f.detectChanges();
    http.expectOne('/api/v1/branding/oficina-azul').flush(null, { status: 404, statusText: 'Not Found' });
    answer(http.expectOne(`${BASE}/bookings/tok_123`));
    f.detectChanges();
    return f;
  }

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  it('shows the status, the services with their total, and cancels after a confirmation', () => {
    const f = open((r) => r.flush(booking));
    expect(text(f)).toContain('Marcação n.º 12');
    expect(text(f)).toContain('Confirmada');
    expect(text(f)).toContain('59,20 €');

    const cancel = [...f.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent!.includes('Cancelar marcação')) as HTMLButtonElement;
    cancel.click();
    f.detectChanges();
    expect(text(f)).toContain('Cancelar esta marcação?');
    f.componentInstance.cancel();
    http.expectOne(`${BASE}/bookings/tok_123/cancel`).flush({ ...booking, status: 'CANCELLED', canCancel: false });
    f.detectChanges();
    expect(text(f)).toContain('A marcação foi cancelada.');
    expect(text(f)).not.toContain('Cancelar marcação');
  });

  it('shows the drop-off time in the workshop time zone', () => {
    const f = open((r) => r.flush({ ...booking, timeZone: 'America/Sao_Paulo' }));
    expect(f.nativeElement.querySelector('.when').textContent).toContain('06:00');
    expect(f.nativeElement.querySelector('.when').textContent).toContain('2 de novembro');
  });

  it('says the link leads nowhere for an unknown token', () => {
    const f = open((r) => r.flush({ code: 'booking-not-found' }, { status: 404, statusText: 'Not Found' }));
    expect(text(f)).toContain('Marcação não encontrada');
  });

  it('shows the decline reason', () => {
    const f = open((r) => r.flush({ ...booking, status: 'DECLINED', canCancel: false, declineReason: 'Sem peças nesse dia' }));
    expect(text(f)).toContain('Recusada');
    expect(text(f)).toContain('Motivo: Sem peças nesse dia');
  });
});
