import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { I18nService } from '../core/i18n/i18n.service';
import { Booking } from './booking.models';
import { addDays, dayIn } from './booking-calendar';
import { BookingsPage, onDay } from './bookings-page';

const requested: Booking = {
  id: 'b1', number: 12, status: 'REQUESTED', slotStart: '2030-11-04T09:00:00Z', name: 'Ana Silva', phone: '912345678',
  email: 'ana@example.pt', plate: 'AA00AA', note: 'Barulho ao travar', marketingConsent: false, createdAt: '2030-11-01T10:00:00Z',
  services: [{ serviceId: 's1', name: 'Mudança de óleo', durationMinutes: 60, priceCents: 4000, vatRate: 23 }],
  vehicleId: null, workOrderId: null, decidedByName: null, decidedAt: null, declineReason: null, version: 1,
};

/** The workshop's zone in these tests: Lisbon, unless a test needs one far from the test runner's. */
const ZONE = 'Europe/Lisbon';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

describe('BookingsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!;
  const button = (f: ComponentFixture<unknown>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent!.trim() === label) as HTMLButtonElement;

  /** Answers the "to confirm" list and the day's agenda. */
  /** Answers the "to confirm" list and the day's agenda; "today" is the workshop's. */
  function flushLists(pending: Booking[], day: Booking[] = [], zone: string | null = ZONE) {
    // Also requests whose slot already passed (30 days back), so none is lost.
    const today = dayIn(new Date(), zone);
    http
      .expectOne((r) => r.url === '/api/v1/bookings' && r.params.get('status') === 'REQUESTED' && r.params.get('from') === addDays(today, -30) && r.params.get('to') === addDays(today, 90))
      .flush(pending);
    http.expectOne((r) => r.url === '/api/v1/bookings' && !r.params.has('status')).flush(day);
  }

  function openWithBooking(b: Booking = requested, zone = ZONE) {
    const f = TestBed.createComponent(BookingsPage);
    f.detectChanges();
    http.expectOne('/api/v1/booking/settings').flush({ timeZone: zone });
    flushLists([b], [], zone);
    f.detectChanges();
    expect(text(f)).toContain('Ana Silva');
    (f.nativeElement.querySelector('.list-group-item-action') as HTMLButtonElement).click();
    http.expectOne('/api/v1/bookings/b1').flush(b);
    f.detectChanges();
    return f;
  }

  it('lists the requests to confirm and confirms one with its version', () => {
    const f = openWithBooking();
    expect(text(f)).toContain('Booking no. 12');
    expect(text(f)).toContain('Barulho ao travar');
    expect(text(f)).toContain('New vehicle');

    button(f, 'Confirm').click();
    const req = http.expectOne('/api/v1/bookings/b1/confirm');
    expect(req.request.body).toEqual({ version: 1 });
    req.flush({ ...requested, status: 'CONFIRMED', version: 2 });
    flushLists([]);
    f.detectChanges();
    expect(text(f)).toContain('Booking no. 12 confirmed.');
    expect(text(f)).toContain('No bookings waiting for an answer.');
  });

  it('declines only with a reason, and sends the reason with the version', () => {
    const f = openWithBooking();
    button(f, 'Decline').click();
    f.detectChanges();
    button(f, 'Decline booking').click();
    f.detectChanges();
    expect(text(f)).toContain('Say why the booking is declined.');

    const reason = f.nativeElement.querySelector('#bd-reason') as HTMLTextAreaElement;
    reason.value = 'Sem peças nesse dia';
    reason.dispatchEvent(new Event('input'));
    button(f, 'Decline booking').click();
    const req = http.expectOne('/api/v1/bookings/b1/decline');
    expect(req.request.body).toEqual({ reason: 'Sem peças nesse dia', version: 1 });
    req.flush({ ...requested, status: 'DECLINED', declineReason: 'Sem peças nesse dia', version: 2 });
    flushLists([]);
    f.detectChanges();
    expect(text(f)).toContain('Booking no. 12 declined.');
  });

  it('shows the server verdict under the reason (400 decline-reason-required)', () => {
    const f = openWithBooking();
    button(f, 'Decline').click();
    f.detectChanges();
    const reason = f.nativeElement.querySelector('#bd-reason') as HTMLTextAreaElement;
    reason.value = 'x';
    reason.dispatchEvent(new Event('input'));
    button(f, 'Decline booking').click();
    http.expectOne('/api/v1/bookings/b1/decline').flush({ code: 'decline-reason-required', errors: { reason: 'blank' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(reason.classList).toContain('is-invalid');
    expect(f.nativeElement.querySelector('#bd-reason-help').textContent).toContain('Say why the booking is declined.');
  });

  it('moves a booking to a free slot from the staff slots', () => {
    const f = openWithBooking();
    button(f, 'Change time').click();
    f.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/bookings/slots');
    const from = req.request.params.get('from')!;
    expect(req.request.params.get('days')).toBe('7');
    req.flush([{ date: from, slots: [{ start: from + 'T15:00:00Z', time: '15:00', free: 1 }] }]);
    f.detectChanges();
    (f.nativeElement.querySelector('.slot-btn') as HTMLButtonElement).click();
    f.detectChanges();
    [...f.nativeElement.querySelectorAll('section button')].find((b: HTMLButtonElement) => b.textContent!.trim() === 'Change time').click();
    const move = http.expectOne('/api/v1/bookings/b1/move');
    expect(move.request.body).toEqual({ slotStart: from + 'T15:00:00Z', version: 1 });
    move.flush({ ...requested, status: 'CONFIRMED', slotStart: from + 'T15:00:00Z', version: 2 });
    flushLists([]);
    f.detectChanges();
    expect(text(f)).toContain('Booking no. 12 moved to');
  });

  it('reloads the booking and says why when someone else changed it (409 stale-data)', () => {
    const f = openWithBooking();
    button(f, 'Confirm').click();
    http.expectOne('/api/v1/bookings/b1/confirm').flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/bookings/b1').flush({ ...requested, status: 'DECLINED', declineReason: 'x', version: 2 });
    f.detectChanges();
    expect(text(f)).toContain(TestBed.inject(I18nService).t('error.stale-data'));
    expect(text(f)).toContain('Declined');
    expect(button(f, 'Confirm')).toBeUndefined();
  });

  it('shows times and the day cut in the workshop time zone, not the browser zone', () => {
    // UTC+14: 20:00 UTC on 4 November is 10:00 on 5 November there, whatever zone the tests run in.
    const f = openWithBooking({ ...requested, slotStart: '2030-11-04T20:00:00Z' }, 'Pacific/Kiritimati');
    expect(text(f)).toContain('Tuesday, November 5, 10:00 AM');
    expect(onDay([{ ...requested, slotStart: '2030-11-04T20:00:00Z' }], '2030-11-05', 'Pacific/Kiritimati')).toHaveLength(1);
    expect(onDay([{ ...requested, slotStart: '2030-11-04T20:00:00Z' }], '2030-11-04', 'Pacific/Kiritimati')).toHaveLength(0);
  });

  it('links phone and e-mail safely', () => {
    const f = openWithBooking({ ...requested, phone: '+351 912 345 678; x', email: 'ana+test@example.pt?cc=x' });
    expect(f.nativeElement.querySelector('a[href^="tel:"]').getAttribute('href')).toBe('tel:+351912345678');
    expect(f.nativeElement.querySelector('a[href^="mailto:"]').getAttribute('href')).toBe('mailto:ana%2Btest%40example.pt%3Fcc%3Dx');
  });

  it('checks in a confirmed booking of a known vehicle on the check-in screen', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const f = openWithBooking({ ...requested, status: 'CONFIRMED', vehicleId: 'v1' });
    button(f, 'Check in').click();
    expect(navigate).toHaveBeenCalledWith(['/check-in'], { queryParams: { booking: 'b1' } });
  });

  it('registers the customer, then the vehicle, prefilled from the booking, before checking in a new plate', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const f = openWithBooking({ ...requested, status: 'CONFIRMED' });
    button(f, 'Check in').click();
    f.detectChanges();
    expect((f.nativeElement.querySelector('#ce-name') as HTMLInputElement).value).toBe('Ana Silva');
    expect((f.nativeElement.querySelector('#ce-phone') as HTMLInputElement).value).toBe('912345678');

    button(f, 'Save').click();
    const customer = http.expectOne('/api/v1/customers');
    expect(customer.request.method).toBe('POST');
    expect(customer.request.body).toMatchObject({ name: 'Ana Silva', phone: '912345678', email: 'ana@example.pt' });
    expect(customer.request.body.version).toBeUndefined();
    customer.flush({ id: 'c1', name: 'Ana Silva', nif: null, phone: '912345678', email: 'ana@example.pt', address: null, marketingConsent: false, version: 0 });
    f.detectChanges();

    expect((f.nativeElement.querySelector('#ve-plate') as HTMLInputElement).value).toBe('AA-00-AA');
    [...f.nativeElement.querySelectorAll('app-vehicle-edit-dialog button')].find((b: HTMLButtonElement) => b.textContent!.trim() === 'Save').click();
    const vehicle = http.expectOne('/api/v1/vehicles');
    expect(vehicle.request.method).toBe('POST');
    expect(vehicle.request.body).toMatchObject({ customerId: 'c1', plate: 'AA-00-AA', registrationCountry: 'PT' });
    vehicle.flush({ id: 'v9' });
    expect(navigate).toHaveBeenCalledWith(['/check-in'], { queryParams: { booking: 'b1' } });
  });
});
