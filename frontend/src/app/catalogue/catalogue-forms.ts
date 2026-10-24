import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import { maxDecimals, notBlank } from '../backoffice/settings-form';
import {
  CatalogueService,
  CataloguePackage,
  PackageItem,
  PackageItemKind,
  PackageItemRequest,
  PackageProductChoice,
  PackageRequest,
  ServicePricing,
  ServiceRequest,
} from './catalogue.models';

/** Portugal's standard VAT rate, suggested for new services and packages. */
export const DEFAULT_VAT = 23;

/** Same limits as the backend's `ServiceRequest`. Prices are edited in euros and sent in cents. */
export function serviceForm() {
  return new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(120)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
    category: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
    standardHours: new FormControl<number | null>(1, [Validators.required, Validators.min(0), Validators.max(999.99), maxDecimals(2)]),
    pricing: new FormControl<ServicePricing>('FIXED', { nonNullable: true }),
    priceEuros: new FormControl<number | null>(0, [Validators.required, Validators.min(0), maxDecimals(2)]),
    vatRate: new FormControl<number | null>(DEFAULT_VAT, [Validators.required, Validators.min(0), Validators.max(100), maxDecimals(2)]),
    durationMinutes: new FormControl<number | null>(60, [Validators.required, Validators.min(5), Validators.max(1440)]),
    active: new FormControl(true, { nonNullable: true }),
    listedOnline: new FormControl(false, { nonNullable: true }),
  });
}

export type ServiceForm = ReturnType<typeof serviceForm>;

/** API field to form control, for server validation errors. */
export const SERVICE_FIELDS: Record<string, string> = { priceCents: 'priceEuros' };

/** Values of a new service: offered, not online, 1 hour, 60 minutes in the workshop, standard VAT. */
export function newServiceValue(): ServiceForm['value'] {
  return {
    name: '',
    description: '',
    category: '',
    standardHours: 1,
    pricing: 'FIXED',
    priceEuros: 0,
    vatRate: DEFAULT_VAT,
    durationMinutes: 60,
    active: true,
    listedOnline: false,
  };
}

export function serviceFormValue(s: CatalogueService): ServiceForm['value'] {
  return {
    name: s.name,
    description: s.description ?? '',
    category: s.category ?? '',
    standardHours: s.standardHours,
    pricing: s.pricing,
    priceEuros: (s.priceCents ?? 0) / 100,
    vatRate: s.vatRate ?? DEFAULT_VAT,
    durationMinutes: s.durationMinutes,
    active: s.active,
    listedOnline: s.listedOnline,
  };
}

/**
 * A copy of a service as a new record: the same values, the name with the copy suffix (for example "Oil change
 * (copy)"), cut to the 120-character limit.
 */
export function copyServiceValue(s: CatalogueService, copySuffix: string): ServiceForm['value'] {
  return { ...serviceFormValue(s), name: copyName(s.name, copySuffix) };
}

/** `name` plus the copy suffix, within the 120-character name limit. */
export function copyName(name: string, copySuffix: string): string {
  return `${name.slice(0, 120 - copySuffix.length - 1)} ${copySuffix}`;
}

/**
 * Price before VAT of an hourly service in cents: hours times the labour rate, rounded half up like the server
 * (1.25 h at 40.10 €/h is 50.125 €, so 50.13 €). Hours have at most two decimals, so the product in hundredths is exact.
 */
export function hourlyPriceCents(hours: number | null, labourRateCents: number): number {
  const hundredths = Math.round(Number(hours ?? 0) * 100);
  return Math.round((hundredths * labourRateCents) / 100);
}

/** The request body; `version` only when editing. Blank texts become null. */
export function toServiceRequest(form: ServiceForm, version?: number): ServiceRequest {
  const v = form.getRawValue();
  return {
    name: v.name.trim(),
    description: v.description.trim() || null,
    category: v.category.trim() || null,
    standardHours: Number(v.standardHours),
    pricing: v.pricing,
    priceCents: toCents(v.priceEuros),
    vatRate: Number(v.vatRate),
    durationMinutes: Number(v.durationMinutes),
    active: v.active,
    listedOnline: v.listedOnline,
    ...(version === undefined ? {} : { version }),
  };
}

/**
 * A package row's choice: `s:<id>` for a service, `p:<id>` for a stock product, '' for nothing chosen yet. One select
 * offers both, in two groups.
 */
export type PackageItemForm = FormGroup<{ ref: FormControl<string>; quantity: FormControl<number> }>;

export function packageItem(ref = '', quantity = 1): PackageItemForm {
  return new FormGroup({
    ref: new FormControl(ref, { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl(quantity, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(99)] }),
  });
}

/** The row choice of a package item: `s:<serviceId>` or `p:<productId>`. */
export function itemRef(item: Pick<PackageItem, 'serviceId' | 'productId'>): string {
  return item.productId ? `p:${item.productId}` : `s:${item.serviceId ?? ''}`;
}

/** Reads a row choice; null when nothing (or something unknown) is chosen. */
export function parseItemRef(ref: string): { kind: PackageItemKind; id: string } | null {
  const id = ref.slice(2);
  if (!id) return null;
  if (ref.startsWith('s:')) return { kind: 'SERVICE', id };
  if (ref.startsWith('p:')) return { kind: 'PRODUCT', id };
  return null;
}

/** Same limits as the backend's `PackageRequest`; at least one service or product. */
export function packageForm() {
  return new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(120)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
    priceEuros: new FormControl<number | null>(0, [Validators.required, Validators.min(0), maxDecimals(2)]),
    vatRate: new FormControl<number | null>(DEFAULT_VAT, [Validators.required, Validators.min(0), Validators.max(100), maxDecimals(2)]),
    active: new FormControl(true, { nonNullable: true }),
    listedOnline: new FormControl(false, { nonNullable: true }),
    items: new FormArray<PackageItemForm>([packageItem()], { validators: [Validators.minLength(1)] }),
  });
}

export type PackageForm = ReturnType<typeof packageForm>;

export const PACKAGE_FIELDS: Record<string, string> = { priceCents: 'priceEuros' };

/**
 * Fills the form from a package, or empties it for a new one. With `copySuffix` it fills a copy as a new package:
 * the name gets the suffix and services no longer offered or products no longer sold are left out (a new package
 * cannot hold them).
 */
export function fillPackageForm(form: PackageForm, p: CataloguePackage | null, copySuffix?: string): void {
  form.controls.items.clear();
  const held = p?.items.filter((i) => copySuffix === undefined || i.active) ?? [];
  const items = held.length ? held.map((i) => packageItem(itemRef(i), i.quantity)) : [packageItem()];
  items.forEach((item) => form.controls.items.push(item));
  form.reset({
    name: p ? (copySuffix === undefined ? p.name : copyName(p.name, copySuffix)) : '',
    description: p?.description ?? '',
    priceEuros: p ? p.priceCents / 100 : 0,
    vatRate: p?.vatRate ?? DEFAULT_VAT,
    active: p?.active ?? true,
    listedOnline: p?.listedOnline ?? false,
    items: items.map((i) => i.getRawValue()),
  });
}

export function toPackageRequest(form: PackageForm, version?: number): PackageRequest {
  const v = form.getRawValue();
  return {
    name: v.name.trim(),
    description: v.description.trim() || null,
    priceCents: toCents(v.priceEuros),
    vatRate: Number(v.vatRate),
    active: v.active,
    listedOnline: v.listedOnline,
    items: v.items.flatMap((i): PackageItemRequest[] => {
      const chosen = parseItemRef(i.ref);
      if (!chosen) return [];
      const quantity = Number(i.quantity);
      return [chosen.kind === 'SERVICE' ? { serviceId: chosen.id, quantity } : { productId: chosen.id, quantity }];
    }),
    ...(version === undefined ? {} : { version }),
  };
}

/**
 * What the chosen items cost on their own, in cents: services at their effective price, products at their sale price,
 * times the quantity (unknown ones count as 0).
 */
export function listPriceCents(
  items: { ref: string; quantity: number }[],
  services: CatalogueService[],
  products: PackageProductChoice[] = [],
): number {
  const price = new Map<string, number>([
    ...services.map((s): [string, number] => [`s:${s.id}`, s.effectivePriceCents ?? s.priceCents ?? 0]),
    ...products.map((p): [string, number] => [`p:${p.id}`, p.salePriceCents ?? 0]),
  ]);
  return items.reduce((sum, i) => sum + (price.get(i.ref) ?? 0) * Number(i.quantity || 0), 0);
}

function toCents(euros: number | null): number {
  return Math.round(Number(euros) * 100);
}
