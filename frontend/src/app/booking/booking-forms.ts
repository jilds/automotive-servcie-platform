import { AbstractControl, FormArray, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import type { Language } from '../core/i18n/i18n.service';
import { BookingSettings, Closure, OpeningRange, PublicBookingRequest } from './booking.models';

export const NOTE_MAX = 500;

/** At least one way to reach the customer: a phone number or an e-mail address. */
export const phoneOrEmail: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const v = group.value as { phone?: string; email?: string };
  return v.phone?.trim() || v.email?.trim() ? null : { phoneOrEmail: true };
};

/** The customer's details on the public booking page. `website` is the hidden field people leave empty. */
export function contactForm() {
  return new FormGroup(
    {
      name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
      phone: new FormControl('', { nonNullable: true, validators: Validators.maxLength(40) }),
      email: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(200)] }),
      plate: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(20)] }),
      note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(NOTE_MAX) }),
      privacyAccepted: new FormControl(false, { nonNullable: true, validators: Validators.requiredTrue }),
      marketingConsent: new FormControl(false, { nonNullable: true }),
      website: new FormControl('', { nonNullable: true }),
    },
    { validators: phoneOrEmail },
  );
}

export type ContactForm = ReturnType<typeof contactForm>;

/** The POST body: texts trimmed, blank optional fields as null, the plate upper case, the page language. */
export function toBookingRequest(serviceIds: readonly string[], slotStart: string, form: ContactForm,
  language: Language): PublicBookingRequest {
  const v = form.getRawValue();
  return {
    serviceIds: [...serviceIds],
    slotStart,
    name: v.name.trim(),
    phone: v.phone.trim() || null,
    email: v.email.trim() || null,
    plate: v.plate.trim().toUpperCase(),
    note: v.note.trim() || null,
    privacyAccepted: v.privacyAccepted,
    marketingConsent: v.marketingConsent,
    website: v.website,
    language,
  };
}

/** `HH:mm`, also from an `<input type="time">` that sends seconds. */
export function hhmm(time: string): string {
  return (time ?? '').slice(0, 5);
}

/** What is wrong with one opening range: closing not after opening, or overlapping another range of its day. */
export type RangeProblem = 'order' | 'overlap';

/**
 * The problems of each range, by its index in the list (the server's rule, `invalid-opening-hours`): `opens <
 * closes`, and ranges of the same weekday must not overlap (touching, 12:30–14:00 after 08:30–12:30, is fine).
 */
export function openingHoursProblems(ranges: readonly OpeningRange[]): Map<number, RangeProblem> {
  const problems = new Map<number, RangeProblem>();
  ranges.forEach((r, i) => {
    if (!r.opens || !r.closes || hhmm(r.opens) >= hhmm(r.closes)) problems.set(i, 'order');
  });
  // Only well-formed ranges can overlap; compare against the "order" results, not the marks added below.
  const ordered = new Set([...ranges.keys()].filter((i) => !problems.has(i)));
  ranges.forEach((a, i) => {
    if (!ordered.has(i)) return;
    const clash = ranges.some(
      (b, j) => j !== i && ordered.has(j) && b.weekday === a.weekday && hhmm(a.opens) < hhmm(b.closes) && hhmm(b.opens) < hhmm(a.closes),
    );
    if (clash) problems.set(i, 'overlap');
  });
  return problems;
}

const openingHoursValidator: ValidatorFn = (array: AbstractControl): ValidationErrors | null =>
  openingHoursProblems(array.value as OpeningRange[]).size ? { openingHours: true } : null;

export function rangeGroup(r: OpeningRange) {
  return new FormGroup({
    weekday: new FormControl(r.weekday, { nonNullable: true }),
    opens: new FormControl(hhmm(r.opens), { nonNullable: true, validators: Validators.required }),
    closes: new FormControl(hhmm(r.closes), { nonNullable: true, validators: Validators.required }),
  });
}

export function closureGroup(c: Closure) {
  return new FormGroup({
    day: new FormControl(c.day, { nonNullable: true, validators: Validators.required }),
    reason: new FormControl(c.reason ?? '', { nonNullable: true, validators: Validators.maxLength(200) }),
  });
}

const whole = (min: number) => [Validators.required, Validators.min(min), Validators.pattern(/^\d+$/)];

/** The owner's booking settings. */
export function settingsForm() {
  return new FormGroup({
    slotMinutes: new FormControl(30, { nonNullable: true }),
    carsPerSlot: new FormControl<number | null>(1, whole(1)),
    leadTimeHours: new FormControl<number | null>(24, whole(0)),
    horizonDays: new FormControl<number | null>(30, whole(1)),
    cancelUntilHours: new FormControl<number | null>(24, whole(0)),
    openingHours: new FormArray<ReturnType<typeof rangeGroup>>([], openingHoursValidator),
    closures: new FormArray<ReturnType<typeof closureGroup>>([]),
  });
}

export type SettingsForm = ReturnType<typeof settingsForm>;

/** Puts stored settings into the form (replacing every range and closure). */
export function fillSettings(form: SettingsForm, s: BookingSettings): void {
  form.controls.openingHours.clear({ emitEvent: false });
  s.openingHours.forEach((r) => form.controls.openingHours.push(rangeGroup(r), { emitEvent: false }));
  form.controls.closures.clear({ emitEvent: false });
  [...s.closures].sort((a, b) => a.day.localeCompare(b.day)).forEach((c) => form.controls.closures.push(closureGroup(c), { emitEvent: false }));
  form.reset({
    slotMinutes: s.slotMinutes,
    carsPerSlot: s.carsPerSlot,
    leadTimeHours: s.leadTimeHours,
    horizonDays: s.horizonDays,
    cancelUntilHours: s.cancelUntilHours,
    openingHours: s.openingHours.map((r) => ({ ...r, opens: hhmm(r.opens), closes: hhmm(r.closes) })),
    closures: form.controls.closures.getRawValue(),
  });
}

/** The PUT body: ranges sorted by weekday and time, closures by day, blank reasons as null. */
export function toSettings(form: SettingsForm, version: number): BookingSettings {
  const v = form.getRawValue();
  return {
    slotMinutes: Number(v.slotMinutes),
    carsPerSlot: Number(v.carsPerSlot),
    leadTimeHours: Number(v.leadTimeHours),
    horizonDays: Number(v.horizonDays),
    cancelUntilHours: Number(v.cancelUntilHours),
    openingHours: v.openingHours
      .map((r) => ({ weekday: r.weekday, opens: hhmm(r.opens), closes: hhmm(r.closes) }))
      .sort((a, b) => a.weekday - b.weekday || a.opens.localeCompare(b.opens)),
    closures: v.closures.map((c) => ({ day: c.day, reason: c.reason.trim() || null })).sort((a, b) => a.day.localeCompare(b.day)),
    version,
  };
}
