import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const NINE_DIGITS = /^\d{9}$/;
/** 1, 2, 3: people; 5: companies; 6: public bodies; 8: sole traders (old numbers). */
const FIRST_DIGITS = new Set(['1', '2', '3', '5', '6', '8']);
/** 45: non-resident people; 70-79: estates, condominiums, funds; 90-99: irregular entities and others. */
const FIRST_TWO_DIGITS = new Set(['45', '70', '71', '72', '74', '75', '77', '79', '90', '91', '98', '99']);

/**
 * The Portuguese NIF rule, the same as the backend's `PortugueseNif`: 9 digits, a known first digit (or first two)
 * and a mod-11 check digit. For example 123456789 and 509999999 are valid.
 */
export function isValidPortugueseNif(nif: string): boolean {
  if (!NINE_DIGITS.test(nif)) return false;
  if (!FIRST_DIGITS.has(nif[0]) && !FIRST_TWO_DIGITS.has(nif.slice(0, 2))) return false;
  let sum = 0;
  for (let i = 0; i < 8; i++) sum += Number(nif[i]) * (9 - i);
  const remainder = sum % 11;
  const check = remainder < 2 ? 0 : 11 - remainder;
  return check === Number(nif[8]);
}

/**
 * Validates a NIF control as 9 digits (`pattern`) and, when `country()` is PT, the check digit (`nif`).
 * Other countries' tax numbers only need the 9 digits, like the API.
 */
export function nifValidator(country: () => string | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = String(control.value ?? '').trim();
    if (!value) return null;
    if (!NINE_DIGITS.test(value)) return { pattern: true };
    return (country() ?? 'PT').toUpperCase() === 'PT' && !isValidPortugueseNif(value) ? { nif: true } : null;
  };
}
