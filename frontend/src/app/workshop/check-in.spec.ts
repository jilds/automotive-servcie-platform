import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { Vehicle } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { CheckIn, looksLikeVin } from './check-in';

const vehicle: Vehicle = {
  id: 'v1',
  customerId: 'c1',
  plate: '11AA11',
  plateDisplay: '11-AA-11',
  plateEra: 'PT_2005',
  registrationCountry: 'PT',
  registrationDate: null,
  vin: null,
  make: 'Renault',
  model: 'Clio',
  year: 2015,
  fuelType: null,
  mileage: 100100,
  version: 0,
};

describe('CheckIn', () => {
  let http: HttpTestingController;

  function open(lang: 'en' | 'pt-PT') {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CheckIn);
    fixture.detectChanges();
    http.expectOne('/api/v1/users').flush([{ id: 'm1', tenantId: 't', name: 'Rui', email: 'r@example.test', role: 'MECHANIC', active: true }]);
    fixture.componentInstance.find('11-AA-11');
    http.expectOne((r) => r.url === '/api/v1/vehicles').flush(vehicle);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('formats the last mileage in the UI language', () => {
    const fixture = open('pt-PT');
    expect((fixture.nativeElement.textContent as string).replace(/[\u00a0\u202f]/g, ' ')).toContain('últimos 100 100 km');
  });

  it('shows invalid-mechanic on the mechanic select instead of an alert', () => {
    const fixture = open('en');
    fixture.componentInstance['form'].patchValue({ assignedMechanicId: 'm1' });
    fixture.componentInstance.submit(vehicle);
    http.expectOne('/api/v1/work-orders').flush({ code: 'invalid-mechanic' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('#ci-mechanic') as HTMLSelectElement;
    expect(select.classList).toContain('is-invalid');
    expect(fixture.nativeElement.querySelector('#ci-mechanic-help').textContent).toContain('Choose an active mechanic of this workshop.');
    expect(fixture.nativeElement.querySelector('app-alert')).toBeNull();
  });

  it('tells the user to register the customer and vehicle first when no vehicle matches', () => {
    const fixture = open('en');
    fixture.componentInstance.find('99-ZZ-99');
    http
      .expectOne((r) => r.url === '/api/v1/vehicles' && r.params.get('plate') === '99-ZZ-99')
      .flush({ code: 'vehicle-not-found' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('No vehicle with that plate or VIN.');
    const link = fixture.nativeElement.querySelector('app-alert a') as HTMLAnchorElement;
    expect(link.textContent).toContain('Register the customer and vehicle first.');
    expect(link.getAttribute('href')).toBe('/customers');
    expect(fixture.nativeElement.querySelector('#ci-mileage')).toBeNull();
  });

  it('searches by VIN when the term has 17 letters or digits, and says which field matched', () => {
    const fixture = open('pt-PT');
    expect(fixture.nativeElement.textContent).toContain('Encontrada pela matrícula');

    fixture.componentInstance.find('vf1 rja00x 12345678');
    http
      .expectOne((r) => r.url === '/api/v1/vehicles' && r.params.get('vin') === 'vf1rja00x12345678')
      .flush({ ...vehicle, vin: 'VF1RJA00X12345678' });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Encontrada pelo VIN');
    expect(text).toContain('VF1RJA00X12345678');
  });

  it('tells a VIN from a plate', () => {
    expect(looksLikeVin('VF1RJA00X12345678')).toBe(true);
    expect(looksLikeVin(' vf1 rja00x 12345678 ')).toBe(true);
    expect(looksLikeVin('11-AA-11')).toBe(false);
    expect(looksLikeVin('VF1RJA00X1234567')).toBe(false);
  });
});

describe('CheckIn from a booking', () => {
  let http: HttpTestingController;
  const booking = {
    id: 'b1', number: 12, status: 'CONFIRMED', slotStart: '2030-11-04T09:00:00Z', name: 'Ana Silva', phone: '912345678', email: null,
    plate: '11AA11', note: 'Barulho ao travar', marketingConsent: false, createdAt: '2030-11-01T10:00:00Z',
    services: [
      { serviceId: 's1', name: 'Mudança de óleo', durationMinutes: 60, priceCents: 4000, vatRate: 23 },
      { serviceId: 's2', name: 'Pastilhas de travão', durationMinutes: 90, priceCents: 8000, vatRate: 23 },
    ],
    vehicleId: 'v1', workOrderId: null, decidedByName: 'Rita', decidedAt: '2030-11-01T11:00:00Z', declineReason: null, version: 2,
  };

  function open() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CheckIn);
    fixture.componentRef.setInput('booking', 'b1');
    fixture.detectChanges();
    http.expectOne('/api/v1/users').flush([]);
    http.expectOne('/api/v1/bookings/b1').flush(booking);
    http.expectOne((r) => r.url === '/api/v1/vehicles' && r.params.get('plate') === '11AA11').flush(vehicle);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('prefills the plate and the complaint, lists the services and sends the booking id', () => {
    const fixture = open();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Booking no. 12 · Ana Silva');
    expect(text).toContain('Mudança de óleo');
    expect(text).toContain('Pastilhas de travão');
    expect((fixture.nativeElement.querySelector('#ci-complaint') as HTMLTextAreaElement).value).toBe('Barulho ao travar');

    fixture.componentInstance.submit(vehicle);
    const req = http.expectOne('/api/v1/work-orders');
    expect(req.request.body).toMatchObject({ vehicleId: 'v1', bookingId: 'b1', complaint: 'Barulho ao travar' });
    req.flush({ id: 'w1' });
  });

  it('says so when the booking is not confirmed (409 booking-not-confirmed)', () => {
    const fixture = open();
    fixture.componentInstance.submit(vehicle);
    http.expectOne('/api/v1/work-orders').flush({ code: 'booking-not-confirmed' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-alert').textContent).toContain('Only a confirmed booking can be checked in.');
  });

  it('sends no booking id for a plain check-in', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CheckIn);
    fixture.detectChanges();
    http.expectOne('/api/v1/users').flush([]);
    fixture.componentInstance.submit(vehicle);
    const req = http.expectOne('/api/v1/work-orders');
    expect('bookingId' in req.request.body).toBe(false);
    req.flush({ id: 'w1' });
  });
});
