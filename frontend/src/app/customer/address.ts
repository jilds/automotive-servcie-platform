import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';

import { Address } from '../core/api/models';

/** Countries offered in the address form; the backend accepts any two-letter code. */
export const COUNTRIES = ['PT', 'ES', 'FR', 'DE', 'GB', 'CH', 'LU', 'BR', 'AO', 'CV', 'MZ'] as const;

const PT_POSTAL_CODE = /^\d{4}-\d{3}$/;

export type AddressForm = FormGroup<{
  street: FormControl<string>;
  detail: FormControl<string>;
  postalCode: FormControl<string>;
  locality: FormControl<string>;
  country: FormControl<string>;
}>;

export function addressForm(): AddressForm {
  return new FormGroup(
    {
      street: new FormControl('', { nonNullable: true }),
      detail: new FormControl('', { nonNullable: true }),
      postalCode: new FormControl('', { nonNullable: true }),
      locality: new FormControl('', { nonNullable: true }),
      country: new FormControl('PT', { nonNullable: true }),
    },
    { validators: addressValidator },
  );
}

/**
 * The address is optional, but once any of street, postal code or locality is filled the other two are needed.
 * Portuguese postal codes must look like 1000-001.
 */
export const addressValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const v = (group as AddressForm).getRawValue();
  const street = v.street.trim();
  const postalCode = v.postalCode.trim();
  const locality = v.locality.trim();
  const errors: ValidationErrors = {};
  if ((street || postalCode || locality || v.detail.trim()) && !(street && postalCode && locality)) errors['incomplete'] = true;
  if (postalCode && v.country === 'PT' && !PT_POSTAL_CODE.test(postalCode)) errors['postalCode'] = true;
  return Object.keys(errors).length ? errors : null;
};

/** The address to send, or null when the form was left empty. */
export function toAddress(form: AddressForm): Address | null {
  const v = form.getRawValue();
  if (!v.street.trim()) return null;
  return {
    street: v.street.trim(),
    detail: v.detail.trim() || null,
    postalCode: v.postalCode.trim(),
    locality: v.locality.trim(),
    country: v.country,
  };
}

/** One-line address for display, for example "Rua A 1, 2º Esq, 1000-001 Lisboa". */
export function formatAddress(a: Address, countryName: (code: string) => string): string {
  const parts = [a.street, a.detail, `${a.postalCode} ${a.locality}`];
  if (a.country !== 'PT') parts.push(countryName(a.country));
  return parts.filter(Boolean).join(', ');
}
