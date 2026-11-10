import type { MessageKey } from '../core/i18n/messages.en';
import { isValidPortugueseNif } from '../shared/nif';
import { InvoiceAddress, IssueInvoiceRequest } from './billing.models';

/** Whose data the invoice carries: the vehicle owner's (copied by the server), another customer's, or a final consumer. */
export type CustomerMode = 'owner' | 'another' | 'final';

export const NAME_MAX = 120;
const POSTAL_PT = /^\d{4}-\d{3}$/;

export interface CustomerFields {
  name: string;
  nif: string;
  street: string;
  detail: string;
  postalCode: string;
  locality: string;
  country: string;
}

export const EMPTY_CUSTOMER: CustomerFields = { name: '', nif: '', street: '', detail: '', postalCode: '', locality: '', country: '' };

const blank = (s: string) => s.trim() === '';

/** True when the person typed any part of an address (then street, postal code and locality are all required). */
export function hasAddress(v: CustomerFields): boolean {
  return !blank(v.street) || !blank(v.detail) || !blank(v.postalCode) || !blank(v.locality);
}

/** Field to message key for what is wrong in the "another customer" form; empty when it can be sent. */
export function customerErrors(v: CustomerFields): Partial<Record<keyof CustomerFields, MessageKey>> {
  const errors: Partial<Record<keyof CustomerFields, MessageKey>> = {};
  if (blank(v.name)) errors.name = 'billing.issue.nameRequired';
  else if (v.name.trim().length > NAME_MAX) errors.name = 'billing.issue.nameTooLong';
  const nif = v.nif.replace(/\s+/g, '');
  // Without a country the workshop's applies on the server; the Portuguese rules are checked here only for an explicit PT.
  const pt = v.country.trim().toUpperCase() === 'PT';
  if (nif && !/^\d{9}$/.test(nif)) errors.nif = 'billing.issue.nifDigits';
  else if (nif && pt && !isValidPortugueseNif(nif)) errors.nif = 'billing.issue.nifInvalid';
  if (hasAddress(v)) {
    if (blank(v.street)) errors.street = 'billing.issue.required';
    if (blank(v.locality)) errors.locality = 'billing.issue.required';
    if (blank(v.postalCode)) errors.postalCode = 'billing.issue.required';
    else if (pt && !POSTAL_PT.test(v.postalCode.trim())) errors.postalCode = 'billing.issue.postalInvalid';
    if (!blank(v.country) && !/^[A-Za-z]{2}$/.test(v.country.trim())) errors.country = 'billing.issue.countryInvalid';
  }
  return errors;
}

/** The address of the form, or null when none was typed. */
export function addressOf(v: CustomerFields): Omit<InvoiceAddress, 'country'> & { country?: string } | null {
  if (!hasAddress(v)) return null;
  return {
    street: v.street.trim(),
    detail: v.detail.trim() || null,
    postalCode: v.postalCode.trim(),
    locality: v.locality.trim(),
    // No country unless the user picked one: the server resolves it with the workshop's country.
    ...(blank(v.country) ? {} : { country: v.country.trim().toUpperCase() }),
  };
}

/**
 * The POST body. The request id is made once per opening of the dialog and passed in, so a retry after a timeout
 * sends the same id and the server answers with the existing document instead of invoicing twice.
 */
export function toIssueRequest(requestId: string, mode: CustomerMode, v: CustomerFields): IssueInvoiceRequest {
  if (mode === 'final') return { requestId, finalConsumer: true };
  if (mode === 'owner') return { requestId };
  return {
    requestId,
    finalConsumer: false,
    customer: { name: v.name.trim(), nif: v.nif.replace(/\s+/g, '') || null, address: addressOf(v) },
  };
}

/** Server field names of the issue call mapped to the form's fields. */
export const ISSUE_FIELD_PATHS: Record<string, keyof CustomerFields> = {
  'customer.name': 'name',
  'customer.nif': 'nif',
  'customer.address': 'street',
  'customer.address.street': 'street',
  'customer.address.detail': 'detail',
  'customer.address.postalCode': 'postalCode',
  'customer.address.locality': 'locality',
  'customer.address.country': 'country',
};

/** Codes that mean the customer's data is not enough for the amount: the form asks for it, with a link to the customer. */
export const DATA_CODES: ReadonlySet<string> = new Set(['customer-nif-required', 'customer-address-required']);

/** The form fields a failed issue call is about (empty when the problem is not about a field). */
export function serverFields(code: string | null, errors: Record<string, string> | undefined): Partial<Record<keyof CustomerFields, string>> {
  const marks: Partial<Record<keyof CustomerFields, string>> = {};
  if (code === 'customer-nif-required' || code === 'invalid-nif') marks.nif = code;
  else if (code === 'customer-name-too-long') marks.name = code;
  else if (code === 'customer-address-required') marks.street = code;
  if (code === 'validation') {
    for (const path of Object.keys(errors ?? {})) {
      const field = ISSUE_FIELD_PATHS[path];
      if (field) marks[field] = 'validation';
    }
  }
  return marks;
}
