import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { problemOf } from '../core/api/problem';
import type { Language } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { isValidPortugueseNif } from '../shared/nif';
import { Cart, orderLines } from './cart';
import { PublicOrderRequest, ShopOrderStatus } from './shop.models';

export const NAME_MAX = 120;
export const PHONE_MAX = 30;
export const EMAIL_MAX = 254;

/** At least one way to reach the customer: a phone number or an e-mail address. */
export const phoneOrEmail: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const v = group.value as { phone?: string; email?: string };
  return v.phone?.trim() || v.email?.trim() ? null : { phoneOrEmail: true };
};

/** The name goes into an e-mail: one line, no links, no tags, no control characters (the server's rule). */
// eslint-disable-next-line no-control-regex
const NOT_PLAIN = /:\/\/|www\.|[<>]|[\u0000-\u001f\u007f-\u009f\u2028\u2029]/i;
export const plainName: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  NOT_PLAIN.test(String(control.value ?? '')) ? { plainName: true } : null;

/**
 * The NIF check of the public order: the Portuguese rule only when the workshop's country is known to be PT (the
 * public branding does not say it, so the page usually sends the NIF and shows the server's `invalid-nif`).
 */
export function shopNifValidator(country: () => string | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = String(control.value ?? '').trim();
    if (!value || country()?.toUpperCase() !== 'PT') return null;
    return isValidPortugueseNif(value) ? null : { nif: true };
  };
}

/** The customer's details at checkout. `website` is the hidden field people leave empty. */
export function checkoutForm(country: () => string | null = () => null) {
  return new FormGroup(
    {
      name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(NAME_MAX), plainName] }),
      phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(PHONE_MAX), Validators.pattern(/^[\d\s+()-]*$/)] }),
      email: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(EMAIL_MAX)] }),
      nif: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(20), shopNifValidator(country)] }),
      privacyAccepted: new FormControl(false, { nonNullable: true, validators: Validators.requiredTrue }),
      marketingConsent: new FormControl(false, { nonNullable: true }),
      website: new FormControl('', { nonNullable: true }),
    },
    { validators: phoneOrEmail },
  );
}

export type CheckoutForm = ReturnType<typeof checkoutForm>;

/** The POST body: texts trimmed, blank optional fields as null, the NIF without spaces, the page language. */
export function toOrderRequest(cart: Cart, form: CheckoutForm, language: Language): PublicOrderRequest {
  const v = form.getRawValue();
  return {
    lines: orderLines(cart),
    name: v.name.trim(),
    phone: v.phone.trim() || null,
    email: v.email.trim() || null,
    nif: v.nif.replace(/\s+/g, '') || null,
    privacyAccepted: v.privacyAccepted,
    marketingConsent: v.marketingConsent,
    website: v.website,
    language,
  };
}

/** Why a public page cannot work: unknown or closed workshop, shop add-on off, too many requests. */
export type PageState = 'ok' | 'not-found' | 'unavailable' | 'limited';

/** The page state a failed public request leads to; null when the page stays and shows the message. */
export function pageStateOf(err: unknown): PageState | null {
  const problem = problemOf(err);
  if (problem?.status === 429) return 'limited';
  if (problem?.status !== 404) return null;
  if (problem.code === 'add-on-not-available') return 'unavailable';
  return problem.code === 'workshop-not-found' || !problem.code ? 'not-found' : null;
}

/** A cart line the server refused: the product is no longer offered, or not enough is available. */
export type LineProblem = 'product-not-listed' | 'insufficient-stock';

/** What the checkout does with a failed order. */
export type CheckoutOutcome =
  | { kind: 'page'; state: Exclude<PageState, 'ok'> }
  | { kind: 'line'; productId: string | null; problem: LineProblem }
  /** Shown on the form fields (validation `errors`, `privacy-consent-required`, `invalid-nif`); `lines` = the cart. */
  | { kind: 'fields'; cart: boolean }
  /** A message above the form: too many requests, try again, or anything else. */
  | { kind: 'message'; code: string | null };

/** Sorts a failed `POST /orders` into what the page should do with it. */
export function checkoutOutcome(err: unknown): CheckoutOutcome {
  const problem = problemOf(err);
  const code = problem?.code ?? null;
  if (code === 'product-not-listed' || code === 'insufficient-stock') {
    const productId = (problem as { productId?: unknown } | null)?.productId;
    return { kind: 'line', productId: typeof productId === 'string' ? productId : null, problem: code };
  }
  if (problem?.status === 429) return { kind: 'message', code: 'too-many-requests' };
  const state = pageStateOf(err);
  if (state === 'not-found' || state === 'unavailable') return { kind: 'page', state };
  if (code === 'privacy-consent-required' || code === 'invalid-nif') return { kind: 'fields', cart: false };
  if (code === 'validation') return { kind: 'fields', cart: Object.keys(problem?.errors ?? {}).some((f) => f === 'lines' || f.startsWith('lines')) };
  return { kind: 'message', code };
}

/** The full address of an order's status page. */
export function statusLink(origin: string, slug: string, token: string): string {
  return `${origin.replace(/\/$/, '')}/w/${encodeURIComponent(slug)}/shop/${encodeURIComponent(token)}`;
}

/** Badge colours of an order status (Bootstrap tint pairs that follow the colour mode). */
export const STATUS_BADGE: Record<ShopOrderStatus, string> = {
  PLACED: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
  READY: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  COLLECTED: 'bg-info-subtle text-info-emphasis border border-info-subtle',
  CANCELLED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
  EXPIRED: 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle',
};

export function statusText(status: ShopOrderStatus): MessageKey {
  return `shop.status.${status}`;
}

/** True while the order still waits for the customer (stock reserved). */
export function isOpen(status: ShopOrderStatus): boolean {
  return status === 'PLACED' || status === 'READY';
}

/** Formats an instant in the UI language, in a time zone (IANA id) or else the browser's. */
export function formatInstant(instant: string, lang: string, options: Intl.DateTimeFormatOptions, timeZone?: string | null): string {
  let zone = timeZone || undefined;
  try {
    if (zone) new Intl.DateTimeFormat(lang, { timeZone: zone });
  } catch {
    zone = undefined; // An unknown zone id: the browser's zone.
  }
  return new Intl.DateTimeFormat(lang, { ...options, timeZone: zone }).format(new Date(instant));
}

export const DATE_TIME: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' };
export const LONG_DATE_TIME: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' };
