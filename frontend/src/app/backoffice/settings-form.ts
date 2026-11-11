import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { nifValidator } from '../shared/nif';
import { centsToEuros, eurosToCents } from '../shared/money';
import { WorkshopSettings } from './backoffice.models';

/** Countries offered in the select; a stored country outside this list is added to it. */
export const COUNTRIES = ['PT', 'ES', 'FR', 'DE', 'IT', 'NL', 'BE', 'LU', 'IE', 'GB', 'CH', 'AT', 'BR', 'AO', 'MZ', 'CV'] as const;

/** Used when the browser cannot list its time zones. */
export const FALLBACK_TIME_ZONES = [
  'Europe/Lisbon',
  'Atlantic/Azores',
  'Atlantic/Madeira',
  'Europe/Madrid',
  'Atlantic/Canary',
  'Europe/London',
  'Europe/Dublin',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Rome',
  'Europe/Amsterdam',
  'Europe/Brussels',
  'Europe/Luxembourg',
  'Europe/Zurich',
  'Europe/Vienna',
  'America/Sao_Paulo',
  'Africa/Luanda',
  'Africa/Maputo',
  'Atlantic/Cape_Verde',
  'UTC',
] as const;

/** IANA time zones of this browser, or the fallback list; always contains `current` when given. */
export function timeZones(current?: string | null, supported = supportedTimeZones()): string[] {
  const zones = supported.length ? [...supported] : [...FALLBACK_TIME_ZONES];
  if (current && !zones.includes(current)) zones.unshift(current);
  return zones;
}

function supportedTimeZones(): string[] {
  try {
    return (Intl as { supportedValuesOf?: (key: 'timeZone') => string[] }).supportedValuesOf?.('timeZone') ?? [];
  } catch {
    return [];
  }
}

/** Rejects values that are only whitespace (the backend uses @NotBlank). */
export const notBlank: ValidatorFn = (c: AbstractControl): ValidationErrors | null =>
  typeof c.value === 'string' && c.value.length > 0 && !c.value.trim() ? { required: true } : null;

/** Rejects numbers with more than `digits` decimals; empty values pass. */
export function maxDecimals(digits: number): ValidatorFn {
  const factor = 10 ** digits;
  return (c: AbstractControl): ValidationErrors | null => {
    const v = c.value;
    if (v === null || v === undefined || v === '') return null;
    const scaled = Number(v) * factor;
    return Math.abs(scaled - Math.round(scaled)) < 1e-6 ? null : { decimals: { max: digits } };
  };
}

/** Days a sent quote stays open (`quoteValidDays`, whole days 1–90, server default 15). */
export const QUOTE_VALID_DAYS_MIN = 1;
export const QUOTE_VALID_DAYS_MAX = 90;
export const QUOTE_VALID_DAYS_DEFAULT = 15;

/** Months after a delivered order in which another delivered order counts the customer as retained (1-60, default 12). */
/** The most one hour of labour may cost, in euros (the server caps it at 1 000 000 cents). */
export const LABOUR_COST_MAX_EUROS = 10_000;
export const RETENTION_MONTHS_MIN = 1;
export const RETENTION_MONTHS_MAX = 60;
export const RETENTION_MONTHS_DEFAULT = 12;

/**
 * Typed form for the workshop settings. The labour rate is edited in euros and sent in cents. A Portuguese workshop's
 * NIF must pass the check digit (`nif` error); it is re-checked when the country changes.
 */
export function settingsForm() {
  const form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank] }),
    nif: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{9}$/)] }),
    country: new FormControl('PT', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)] }),
    timezone: new FormControl('Europe/Lisbon', { nonNullable: true, validators: [Validators.required, notBlank] }),
    defaultVatRate: new FormControl<number | null>(23, [Validators.required, Validators.min(0), Validators.max(100), maxDecimals(2)]),
    labourRateEuros: new FormControl<number | null>(0, [Validators.required, Validators.min(0), maxDecimals(2)]),
    deliveryChecklistRequired: new FormControl(false, { nonNullable: true }),
    // Optional: empty = not set (the report margin counts parts only).
    labourCostEuros: new FormControl<number | null>(null, [Validators.min(0), Validators.max(LABOUR_COST_MAX_EUROS), maxDecimals(2)]),
    retentionMonths: new FormControl<number | null>(RETENTION_MONTHS_DEFAULT, [
      Validators.required,
      Validators.min(RETENTION_MONTHS_MIN),
      Validators.max(RETENTION_MONTHS_MAX),
      maxDecimals(0),
    ]),
    quoteValidDays: new FormControl<number | null>(QUOTE_VALID_DAYS_DEFAULT, [
      Validators.required,
      Validators.min(QUOTE_VALID_DAYS_MIN),
      Validators.max(QUOTE_VALID_DAYS_MAX),
      maxDecimals(0),
    ]),
  });
  form.controls.nif.setValidators(nifValidator(() => form.controls.country.value));
  form.controls.country.valueChanges.subscribe(() => form.controls.nif.updateValueAndValidity({ emitEvent: false }));
  return form;
}

export type SettingsForm = ReturnType<typeof settingsForm>;

/** Form control name for a field of the API (`errors` keys of a validation problem). */
export const FIELD_CONTROL: Record<Exclude<keyof WorkshopSettings, 'version'>, keyof SettingsForm['controls']> = {
  name: 'name',
  nif: 'nif',
  country: 'country',
  timezone: 'timezone',
  defaultVatRate: 'defaultVatRate',
  labourRateCents: 'labourRateEuros',
  quoteValidDays: 'quoteValidDays',
  deliveryChecklistRequired: 'deliveryChecklistRequired',
  labourCostPerHourCents: 'labourCostEuros',
  clearLabourCost: 'labourCostEuros',
  retentionMonths: 'retentionMonths',
};

export function toFormValue(s: WorkshopSettings): SettingsForm['value'] {
  return {
    name: s.name,
    nif: s.nif ?? '',
    country: s.country,
    timezone: s.timezone,
    defaultVatRate: s.defaultVatRate,
    labourRateEuros: s.labourRateCents / 100,
    quoteValidDays: s.quoteValidDays ?? QUOTE_VALID_DAYS_DEFAULT,
    deliveryChecklistRequired: s.deliveryChecklistRequired ?? false,
    labourCostEuros: centsToEuros(s.labourCostPerHourCents),
    retentionMonths: s.retentionMonths ?? RETENTION_MONTHS_DEFAULT,
  };
}

/**
 * The labour cost part of a PUT: a typed value is sent as cents (and never with `clearLabourCost`); an emptied field
 * sends `clearLabourCost: true` when the saved settings had a value, and nothing when they had none (nothing to clear).
 */
export function labourCostChange(euros: number | string | null | undefined, savedCents: number | null | undefined): Pick<WorkshopSettings, 'labourCostPerHourCents' | 'clearLabourCost'> {
  const cents = eurosToCents(euros);
  if (cents !== null) return { labourCostPerHourCents: cents };
  return savedCents == null ? {} : { clearLabourCost: true };
}

/** The PUT body; `version` is the workshop version the form was loaded with, `savedLabourCostCents` the stored labour cost. */
export function toSettings(form: SettingsForm, version: number, savedLabourCostCents: number | null = null): WorkshopSettings {
  const v = form.getRawValue();
  const nif = v.nif.trim();
  return {
    ...labourCostChange(v.labourCostEuros, savedLabourCostCents),
    retentionMonths: Number(v.retentionMonths),
    name: v.name.trim(),
    nif: nif || null,
    country: v.country.trim().toUpperCase(),
    timezone: v.timezone,
    defaultVatRate: Number(v.defaultVatRate),
    labourRateCents: Math.round(Number(v.labourRateEuros) * 100),
    quoteValidDays: Number(v.quoteValidDays),
    deliveryChecklistRequired: v.deliveryChecklistRequired,
    version,
  };
}
