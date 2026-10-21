import { AbstractControl, FormControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { PlateEra } from '../core/api/models';
import { fieldMessage } from '../core/api/problem';
import { MessageKey } from '../core/i18n/messages.en';

/** Portuguese plate layouts, newest first (`A` = letter, `0` = digit); see docs/api/release-2.md §7. */
export const PT_LAYOUTS: readonly { era: PlateEra; layout: string; pattern: RegExp }[] = [
  { era: 'PT_2020', layout: 'AA-00-AA', pattern: /^[A-Z]{2}\d{2}[A-Z]{2}$/ },
  { era: 'PT_2005', layout: '00-AA-00', pattern: /^\d{2}[A-Z]{2}\d{2}$/ },
  { era: 'PT_1992', layout: '00-00-AA', pattern: /^\d{4}[A-Z]{2}$/ },
  { era: 'PT_1937', layout: 'AA-00-00', pattern: /^[A-Z]{2}\d{4}$/ },
];

/** Error key the plate validator sets when a Portuguese plate fits none of the layouts. */
export const PT_PLATE_ERROR = 'ptPlate';
/** Error key the registration month validator sets. */
export const MONTH_ERROR = 'registrationMonth';
/** Earliest registration month the API accepts. */
export const MONTH_MIN = '1937-01';
/** The first registration month that shows a date strip on a 1992–2005 plate. */
const DATE_STRIP_FROM_1992 = '1998-01';

/** The plate as stored by the API: upper case, without spaces, hyphens or other separators. */
export function normalizePlate(input: string): string {
  return input.toUpperCase().replace(/[^0-9A-Z]/g, '');
}

/** The Portuguese layout a plate fits (any case, spaces or hyphens allowed), or null. */
export function ptPlateEra(input: string): PlateEra | null {
  const plate = normalizePlate(input);
  return PT_LAYOUTS.find((l) => l.pattern.test(plate))?.era ?? null;
}

/**
 * The plate as printed: `XX-XX-XX` upper case for a Portuguese plate that fits a layout, otherwise the input upper
 * case and trimmed (other countries are free text).
 */
export function formatPlate(input: string, country: string | null | undefined): string {
  if (isPortugal(country) && ptPlateEra(input)) {
    const p = normalizePlate(input);
    return `${p.slice(0, 2)}-${p.slice(2, 4)}-${p.slice(4)}`;
  }
  return input.trim().toUpperCase();
}

export function isPortugal(country: string | null | undefined): boolean {
  return (country ?? '').trim().toUpperCase() === 'PT';
}

/** A registration as stored; see `plateValidator`. */
export interface StoredRegistration {
  plate: string;
  country: string;
}

/**
 * Checks a plate against the layouts of the registration country `country()` returns; empty values pass. Like the
 * API, it does not re-check a plate whose plate and country are both unchanged from `stored()` (plates saved before
 * the check may fit no layout).
 */
export function plateValidator(country: () => string | null | undefined, stored: () => StoredRegistration | null = () => null): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const value = control.value ?? '';
    const code = (country() ?? '').toUpperCase();
    if (!value.trim() || !isPortugal(code) || ptPlateEra(value)) return null;
    const kept = stored();
    if (kept && normalizePlate(kept.plate) === normalizePlate(value) && kept.country.toUpperCase() === code) return null;
    return { [PT_PLATE_ERROR]: true };
  };
}

/**
 * The message key to show under a plate field: the server's mark (`invalid-plate`, `duplicate-plate`), "required",
 * or the layout error listing the Portuguese layouts. The layout error waits while the user is still typing the
 * first six characters, so it does not flash on the first keystroke.
 */
export function plateMessage(plate: AbstractControl<string>): MessageKey | null {
  if (plate.errors?.[PT_PLATE_ERROR] && !plate.touched && normalizePlate(plate.value ?? '').length < 6) return null;
  return fieldMessage(plate, { required: 'common.required', [PT_PLATE_ERROR]: 'error.invalid-plate' });
}

/** The current month as `YYYY-MM` in the browser's time zone. */
export function currentMonth(now = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** Optional registration month: `YYYY-MM`, from 1937-01 up to the current month (never in the future). */
export function registrationMonthValidator(now: () => Date = () => new Date()): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const value = control.value ?? '';
    if (!value) return null;
    const ok = /^\d{4}-(0[1-9]|1[0-2])$/.test(value) && value >= MONTH_MIN && value <= currentMonth(now());
    return ok ? null : { [MONTH_ERROR]: true };
  };
}

/** The three registration controls every vehicle form shares. Changing the country re-checks the plate. */
export interface RegistrationControls {
  registrationCountry: FormControl<string>;
  plate: FormControl<string>;
  registrationDate: FormControl<string>;
}

/**
 * `stored` returns the registration of the vehicle being edited, if any: its unchanged plate is not re-checked.
 * Plate searches pass `plateRequired: false`.
 */
export function registrationControls(
  country = 'PT',
  { stored = () => null, plateRequired = true }: { stored?: () => StoredRegistration | null; plateRequired?: boolean } = {},
): RegistrationControls {
  const registrationCountry = new FormControl(country, { nonNullable: true, validators: Validators.pattern(/^[A-Za-z]{2}$/) });
  const plateRules = [plateValidator(() => registrationCountry.value, stored)];
  const plate = new FormControl('', { nonNullable: true, validators: plateRequired ? [Validators.required, ...plateRules] : plateRules });
  const registrationDate = new FormControl('', { nonNullable: true, validators: registrationMonthValidator() });
  registrationCountry.valueChanges.subscribe(() => plate.updateValueAndValidity());
  return { registrationCountry, plate, registrationDate };
}

/** How a plate is drawn. */
export type PlateKind = 'eu' | 'classic' | 'neutral';

export interface PlateVariant {
  kind: PlateKind;
  /** The yellow strip on the right: two-digit year over two-digit month. */
  dateStrip: { year: string; month: string } | null;
}

/**
 * Maps the era to the plate's look:
 * - `PT_2020`: white EU plate, no date strip;
 * - `PT_2005`: white EU plate with the date strip when the registration month is known;
 * - `PT_1992`: white EU plate; the date strip only from a 1998-01 registration on;
 * - `PT_1937`: black classic plate, no EU band, no strip;
 * - anything else: a neutral plate with the country code.
 */
export function plateVariant(era: PlateEra | null | undefined, registrationDate: string | null | undefined): PlateVariant {
  const date = /^\d{4}-\d{2}$/.test(registrationDate ?? '') ? registrationDate! : null;
  const strip = date ? { year: date.slice(2, 4), month: date.slice(5, 7) } : null;
  switch (era) {
    case 'PT_2020':
      return { kind: 'eu', dateStrip: null };
    case 'PT_2005':
      return { kind: 'eu', dateStrip: strip };
    case 'PT_1992':
      return { kind: 'eu', dateStrip: date && date >= DATE_STRIP_FROM_1992 ? strip : null };
    case 'PT_1937':
      return { kind: 'classic', dateStrip: null };
    default:
      return { kind: 'neutral', dateStrip: null };
  }
}
