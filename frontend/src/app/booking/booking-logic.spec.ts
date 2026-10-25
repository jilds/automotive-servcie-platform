import { HttpErrorResponse } from '@angular/common/http';

import { addDays, dayTiles, durationKey, isoWeekday, localDay, pickDay, weekdayName } from './booking-calendar';
import { contactForm, fillSettings, hhmm, openingHoursProblems, settingsForm, toBookingRequest, toSettings } from './booking-forms';
import { BookingSettings, SlotDay } from './booking.models';
import { bookingActions } from './booking-dialog';
import { onDay } from './bookings-page';
import { addHours } from './booking-settings';
import { pageStateOf, statusLink } from './public-booking';
import { Booking } from './booking.models';

const slot = (date: string, time: string, free = 1) => ({ start: `${date}T${time}:00Z`, time, free });

describe('booking calendar', () => {
  it('adds days across months, years and daylight-saving changes', () => {
    expect(addDays('2026-10-24', 1)).toBe('2026-10-25');
    expect(addDays('2026-10-25', 1)).toBe('2026-10-26'); // the Sunday clocks go back in Portugal
    expect(addDays('2026-12-30', 7)).toBe('2027-01-06');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('knows ISO weekdays and names them in the UI language', () => {
    expect(isoWeekday('2026-11-02')).toBe(1);
    expect(isoWeekday('2026-11-08')).toBe(7);
    expect(weekdayName(1, 'en')).toBe('Monday');
    expect(weekdayName(7, 'pt-PT')).toBe('domingo');
  });

  it('builds a week strip from the slots answer: missing or full days are closed, free places add up', () => {
    const answer: SlotDay[] = [
      { date: '2026-11-02', slots: [slot('2026-11-02', '09:00', 2), slot('2026-11-02', '09:30', 1)] },
      { date: '2026-11-03', slots: [] },
      { date: '2026-11-04', slots: [slot('2026-11-04', '10:00', 0)] },
    ];
    const tiles = dayTiles('2026-11-02', answer, 7);
    expect(tiles.map((t) => t.date)).toEqual(['2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06', '2026-11-07', '2026-11-08']);
    expect(tiles[0]).toMatchObject({ open: true, free: 3 });
    expect(tiles[1].open).toBe(false);
    expect(tiles[2]).toMatchObject({ open: false, slots: [] });
    expect(tiles[6].open).toBe(false);
  });

  it('opens on the wanted day when it has room, else on the first open day, else on the first day', () => {
    const tiles = dayTiles('2026-11-02', [
      { date: '2026-11-03', slots: [slot('2026-11-03', '09:00')] },
      { date: '2026-11-05', slots: [slot('2026-11-05', '09:00')] },
    ]);
    expect(pickDay(tiles, '2026-11-05')).toBe('2026-11-05');
    expect(pickDay(tiles, '2026-11-04')).toBe('2026-11-03');
    expect(pickDay(tiles, null)).toBe('2026-11-03');
    expect(pickDay(dayTiles('2026-11-02', []), null)).toBe('2026-11-02');
    expect(pickDay([], null)).toBeNull();
  });

  it('splits durations into hours and minutes', () => {
    expect(durationKey(45)).toEqual({ key: 'booking.duration.minutes', params: { hours: 0, minutes: 45 } });
    expect(durationKey(120)).toEqual({ key: 'booking.duration.hours', params: { hours: 2, minutes: 0 } });
    expect(durationKey(90)).toEqual({ key: 'booking.duration.hoursMinutes', params: { hours: 1, minutes: 30 } });
  });

  it('keeps the agenda to one local day, whether the server includes the next day or not', () => {
    const at = (d: Date) => ({ id: d.toISOString(), slotStart: d.toISOString() }) as Booking;
    const day = localDay(new Date(2026, 10, 2, 12));
    const list = [at(new Date(2026, 10, 2, 9)), at(new Date(2026, 10, 2, 17, 30)), at(new Date(2026, 10, 3, 9))];
    expect(onDay(list, day).map((b) => b.slotStart)).toEqual([list[0].slotStart, list[1].slotStart]);
  });
});

describe('opening hours check', () => {
  it('accepts ranges that touch, such as a lunch break, and the same hours on other days', () => {
    const problems = openingHoursProblems([
      { weekday: 1, opens: '08:30', closes: '12:30' },
      { weekday: 1, opens: '12:30', closes: '18:00' },
      { weekday: 2, opens: '08:30', closes: '12:30' },
    ]);
    expect(problems.size).toBe(0);
  });

  it('flags a range that closes before it opens, and both ranges of an overlap on the same day', () => {
    const problems = openingHoursProblems([
      { weekday: 1, opens: '09:00', closes: '13:00' },
      { weekday: 1, opens: '12:00', closes: '18:00' },
      { weekday: 2, opens: '14:00', closes: '14:00' },
      { weekday: 3, opens: '18:00', closes: '09:00' },
      { weekday: 3, opens: '10:00', closes: '11:00' },
    ]);
    expect(Object.fromEntries(problems)).toEqual({ 0: 'overlap', 1: 'overlap', 2: 'order', 3: 'order' });
  });

  it('reads times with seconds as HH:mm', () => {
    expect(hhmm('08:30:00')).toBe('08:30');
    expect(openingHoursProblems([{ weekday: 1, opens: '08:30:00', closes: '08:30' }]).get(0)).toBe('order');
  });

  it('adds hours for a new range without passing midnight', () => {
    expect(addHours('14:00', 2)).toBe('16:00');
    expect(addHours('22:30', 2)).toBe('23:59');
  });
});

describe('booking forms', () => {
  it('needs a phone or an e-mail, and the privacy notice accepted', () => {
    const form = contactForm();
    form.patchValue({ name: 'Ana', plate: 'aa-00-aa' });
    expect(form.hasError('phoneOrEmail')).toBe(true);
    form.patchValue({ email: 'ana@example.pt' });
    expect(form.hasError('phoneOrEmail')).toBe(false);
    expect(form.controls.privacyAccepted.hasError('required')).toBe(true);
    form.patchValue({ privacyAccepted: true });
    expect(form.valid).toBe(true);
  });

  it('sends trimmed texts, blanks as null, the plate upper case and the hidden field as typed ("" for people)', () => {
    const form = contactForm();
    form.patchValue({ name: ' Ana Silva ', phone: ' 912345678 ', email: ' ', plate: ' aa-00-aa ', note: '  ', privacyAccepted: true, marketingConsent: true });
    expect(toBookingRequest(['s1', 's2'], '2026-11-02T09:00:00Z', form)).toEqual({
      serviceIds: ['s1', 's2'], slotStart: '2026-11-02T09:00:00Z', name: 'Ana Silva', phone: '912345678', email: null,
      plate: 'AA-00-AA', note: null, privacyAccepted: true, marketingConsent: true, website: '',
    });
  });

  it('round-trips the settings: ranges sorted by day and time, closures by day, blank reasons as null', () => {
    const stored: BookingSettings = {
      slotMinutes: 30, carsPerSlot: 2, leadTimeHours: 24, horizonDays: 30, cancelUntilHours: 12,
      openingHours: [
        { weekday: 2, opens: '14:00:00', closes: '18:00:00' },
        { weekday: 1, opens: '14:00', closes: '18:00' },
        { weekday: 1, opens: '09:00', closes: '13:00' },
      ],
      closures: [{ day: '2026-12-25', reason: 'Natal' }, { day: '2026-12-08', reason: null }],
      version: 3,
    };
    const form = settingsForm();
    fillSettings(form, stored);
    expect(form.pristine).toBe(true);
    expect(form.valid).toBe(true);
    form.controls.closures.at(1).controls.reason.setValue('  ');
    expect(toSettings(form, 3)).toEqual({
      slotMinutes: 30, carsPerSlot: 2, leadTimeHours: 24, horizonDays: 30, cancelUntilHours: 12,
      openingHours: [
        { weekday: 1, opens: '09:00', closes: '13:00' },
        { weekday: 1, opens: '14:00', closes: '18:00' },
        { weekday: 2, opens: '14:00', closes: '18:00' },
      ],
      closures: [{ day: '2026-12-08', reason: null }, { day: '2026-12-25', reason: null }],
      version: 3,
    });
  });

  it('refuses overlapping ranges and numbers below their minimum', () => {
    const form = settingsForm();
    fillSettings(form, {
      slotMinutes: 15, carsPerSlot: 1, leadTimeHours: 0, horizonDays: 1, cancelUntilHours: 0,
      openingHours: [{ weekday: 1, opens: '09:00', closes: '13:00' }, { weekday: 1, opens: '12:00', closes: '14:00' }],
      closures: [], version: 0,
    });
    expect(form.controls.openingHours.hasError('openingHours')).toBe(true);
    form.controls.carsPerSlot.setValue(0);
    expect(form.controls.carsPerSlot.hasError('min')).toBe(true);
    form.controls.horizonDays.setValue(1.5);
    expect(form.controls.horizonDays.hasError('pattern')).toBe(true);
  });
});

describe('public booking helpers', () => {
  const problem = (status: number, code?: string) => new HttpErrorResponse({ status, error: code ? { code } : null });

  it('turns 404 and 429 answers into page states, and leaves other errors to the page', () => {
    expect(pageStateOf(problem(404, 'add-on-not-available'))).toBe('unavailable');
    expect(pageStateOf(problem(404, 'workshop-not-found'))).toBe('not-found');
    expect(pageStateOf(problem(429, 'too-many-requests'))).toBe('limited');
    expect(pageStateOf(problem(404, 'booking-not-found'))).toBeNull();
    expect(pageStateOf(problem(409, 'slot-unavailable'))).toBeNull();
  });

  it('builds the status link on this address', () => {
    expect(statusLink('https://asp.example/', 'oficina-azul', 'abc_-1')).toBe('https://asp.example/w/oficina-azul/booking/abc_-1');
  });

  it('offers the actions the status allows', () => {
    const now = new Date('2026-11-02T12:00:00Z');
    const b = (status: Booking['status'], slotStart = '2026-11-02T09:00:00Z') => bookingActions({ status, slotStart } as Booking, now);
    expect(b('REQUESTED')).toEqual({ confirm: true, decline: true, move: true, noShow: false, checkIn: false });
    expect(b('CONFIRMED')).toEqual({ confirm: false, decline: false, move: true, noShow: true, checkIn: true });
    expect(b('CONFIRMED', '2026-11-03T09:00:00Z').noShow).toBe(false);
    expect(b('CHECKED_IN')).toEqual({ confirm: false, decline: false, move: false, noShow: false, checkIn: false });
  });
});
