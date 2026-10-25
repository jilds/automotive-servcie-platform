import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Toasts } from '../backoffice/toasts';
import { AuthService } from '../core/auth/auth.service';
import { Role } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { Shell } from '../shared/shell';
import { BookingSettings } from './booking.models';
import { BookingSettingsPage } from './booking-settings';

const SETTINGS: BookingSettings = {
  slotMinutes: 30, carsPerSlot: 1, leadTimeHours: 24, horizonDays: 30, cancelUntilHours: 24,
  openingHours: [{ weekday: 1, opens: '09:00', closes: '13:00' }, { weekday: 1, opens: '14:00', closes: '18:00' }],
  closures: [], version: 4,
};

describe('BookingSettingsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), Toasts] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function open() {
    const f = TestBed.createComponent(BookingSettingsPage);
    f.detectChanges();
    http.expectOne('/api/v1/booking/settings').flush(SETTINGS);
    f.detectChanges();
    return f;
  }

  it('shows the ranges per weekday and flags an overlap before saving', () => {
    const f = open();
    const text = () => f.nativeElement.textContent as string;
    expect(text()).toContain('Monday');
    expect(text()).toContain('Closed');
    expect(f.nativeElement.querySelectorAll('input[type="time"]').length).toBe(4);

    f.componentInstance['form'].controls.openingHours.at(1).controls.opens.setValue('12:00');
    f.detectChanges();
    expect(text()).toContain('These hours overlap other hours of the same day.');
    f.componentInstance.save();
    f.detectChanges();
    expect(text()).toContain(TestBed.inject(I18nService).t('error.invalid-opening-hours'));
  });

  it('saves with the version, and reloads with a message on 409 stale-data', () => {
    const f = open();
    f.componentInstance.addClosure();
    f.componentInstance['form'].controls.closures.at(0).patchValue({ day: '2026-12-25', reason: 'Natal' });
    f.componentInstance.save();
    const put = http.expectOne('/api/v1/booking/settings');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toMatchObject({ version: 4, closures: [{ day: '2026-12-25', reason: 'Natal' }] });
    put.flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/booking/settings').flush({ ...SETTINGS, carsPerSlot: 3, version: 5 });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain(TestBed.inject(I18nService).t('error.stale-data'));
    expect((f.nativeElement.querySelector('#bs-cars') as HTMLInputElement).value).toBe('3');
    expect(f.componentInstance.confirmLeave()).toBe(true);
  });
});

describe('Shell bookings menu', () => {
  function shell(role: Role, booking: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { user: () => ({ id: 'u', tenantId: 't', name: 'Rita', email: 'r@x.pt', role }), hasRole: (...r: Role[]) => r.includes(role), logout: () => {} } },
      ],
    });
    TestBed.inject(I18nService).use('pt-PT');
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(Shell);
    f.detectChanges();
    http.expectOne('/api/v1/branding').flush({ slug: 'oficina-azul', name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null, accentColor: null, logoUrl: null, addOns: { booking, shop: false }, version: 1 });
    f.detectChanges();
    return f.nativeElement.querySelector('a[href="/bookings"]') as HTMLAnchorElement | null;
  }

  it('shows "Marcações" to the front desk only when the booking add-on is on', () => {
    expect(shell('FRONT_DESK', true)?.textContent).toContain('Marcações');
  });

  it('hides it when the add-on is off', () => {
    expect(shell('OWNER', false)).toBeNull();
  });

  it('never shows it to mechanics', () => {
    expect(shell('MECHANIC', true)).toBeNull();
  });
});
