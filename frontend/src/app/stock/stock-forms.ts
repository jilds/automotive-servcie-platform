import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import { maxDecimals, notBlank } from '../backoffice/settings-form';
import { Product, ProductRequest, PurchaseOrder, PurchaseOrderRequest } from './stock.models';

/** Portugal's standard VAT rate, suggested for new products. */
export const DEFAULT_VAT = 23;
/** Unit of a new product: units. */
export const DEFAULT_UNIT = 'un';

/** Same limits as the backend's `ProductRequest`. Money is edited in euros and sent in cents. */
export function productForm() {
  return new FormGroup({
    sku: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(40)] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(120)] }),
    brand: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
    unit: new FormControl(DEFAULT_UNIT, { nonNullable: true, validators: [Validators.maxLength(10)] }),
    bin: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
    costEuros: new FormControl<number | null>(0, [Validators.required, Validators.min(0), maxDecimals(2)]),
    salePriceEuros: new FormControl<number | null>(0, [Validators.required, Validators.min(0), maxDecimals(2)]),
    vatRate: new FormControl<number | null>(DEFAULT_VAT, [Validators.required, Validators.min(0), Validators.max(100), maxDecimals(2)]),
    minStock: new FormControl<number | null>(0, [Validators.min(0), maxDecimals(3)]),
    active: new FormControl(true, { nonNullable: true }),
    listedOnline: new FormControl(false, { nonNullable: true }),
  });
}

export type ProductForm = ReturnType<typeof productForm>;

/** API field to form control, for server validation errors. */
export const PRODUCT_FIELDS: Record<string, string> = { costCents: 'costEuros', salePriceCents: 'salePriceEuros' };

export function productFormValue(p: Product | null): ProductForm['value'] {
  return {
    sku: p?.sku ?? '',
    name: p?.name ?? '',
    brand: p?.brand ?? '',
    unit: p?.unit ?? DEFAULT_UNIT,
    bin: p?.bin ?? '',
    costEuros: (p?.costCents ?? 0) / 100,
    salePriceEuros: (p?.salePriceCents ?? 0) / 100,
    vatRate: p?.vatRate ?? DEFAULT_VAT,
    minStock: p?.minStock ?? 0,
    active: p?.active ?? true,
    listedOnline: p?.listedOnline ?? false,
  };
}

/** The request body; `version` only when editing. Blank optional texts become null, a blank unit "un". */
export function toProductRequest(form: ProductForm, version?: number): ProductRequest {
  const v = form.getRawValue();
  return {
    sku: v.sku.trim(),
    name: v.name.trim(),
    brand: v.brand.trim() || null,
    unit: v.unit.trim() || DEFAULT_UNIT,
    costCents: toCents(v.costEuros),
    salePriceCents: toCents(v.salePriceEuros),
    vatRate: Number(v.vatRate),
    minStock: Number(v.minStock ?? 0),
    bin: v.bin.trim() || null,
    active: v.active,
    listedOnline: v.listedOnline,
    ...(version === undefined ? {} : { version }),
  };
}

export type AdjustDirection = 'add' | 'remove';

/** Add or remove a quantity (3 decimals at most) with a reason, as the backend's `AdjustmentRequest` expects. */
export function adjustForm() {
  return new FormGroup({
    direction: new FormControl<AdjustDirection>('add', { nonNullable: true }),
    quantity: new FormControl<number | null>(null, [Validators.required, Validators.min(0.001), maxDecimals(3)]),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(500)] }),
  });
}

export type AdjustForm = ReturnType<typeof adjustForm>;

/** The quantity to send: positive to add, negative to remove, rounded to 3 decimals. */
export function signedQuantity(direction: AdjustDirection, quantity: number | null): number {
  const q = roundQuantity(Math.abs(Number(quantity ?? 0)));
  return direction === 'remove' ? -q : q;
}

/** What would be on hand after the adjustment; null while the quantity is not a number. */
export function onHandAfter(onHand: number | null, direction: AdjustDirection, quantity: number | null): number | null {
  if (onHand == null || quantity == null || Number.isNaN(Number(quantity))) return null;
  return roundQuantity(onHand + signedQuantity(direction, quantity));
}

export type PurchaseLineForm = FormGroup<{
  productId: FormControl<string>;
  quantity: FormControl<number | null>;
  unitCostEuros: FormControl<number | null>;
}>;

export function purchaseLine(productId = '', quantity: number | null = 1, unitCostEuros: number | null = 0): PurchaseLineForm {
  return new FormGroup({
    productId: new FormControl(productId, { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl<number | null>(quantity, [Validators.required, Validators.min(0.001), maxDecimals(3)]),
    unitCostEuros: new FormControl<number | null>(unitCostEuros, [Validators.required, Validators.min(0), maxDecimals(2)]),
  });
}

/** Same limits as the backend's `PurchaseOrderRequest`; at least one line. */
export function purchaseOrderForm() {
  return new FormGroup({
    supplier: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(120)] }),
    notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
    lines: new FormArray<PurchaseLineForm>([purchaseLine()], { validators: [Validators.minLength(1)] }),
  });
}

export type PurchaseOrderForm = ReturnType<typeof purchaseOrderForm>;

export function fillPurchaseOrderForm(form: PurchaseOrderForm, po: PurchaseOrder | null): void {
  form.controls.lines.clear();
  const lines = po?.lines.length
    ? po.lines.map((l) => purchaseLine(l.productId, l.quantity, l.unitCostCents / 100))
    : [purchaseLine()];
  lines.forEach((line) => form.controls.lines.push(line));
  form.reset({ supplier: po?.supplier ?? '', notes: po?.notes ?? '', lines: lines.map((l) => l.getRawValue()) });
}

export function toPurchaseOrderRequest(form: PurchaseOrderForm, version?: number): PurchaseOrderRequest {
  const v = form.getRawValue();
  return {
    supplier: v.supplier.trim(),
    notes: v.notes.trim() || null,
    lines: v.lines
      .filter((l) => l.productId)
      .map((l) => ({ productId: l.productId, quantity: roundQuantity(Number(l.quantity)), unitCostCents: toCents(l.unitCostEuros) })),
    ...(version === undefined ? {} : { version }),
  };
}

/** Quantity times unit cost, rounded half up to the cent, as the backend totals a line. */
export function lineTotalCents(quantity: number | null, unitCostEuros: number | null): number {
  const q = Number(quantity ?? 0);
  const cents = toCents(unitCostEuros);
  return Number.isFinite(q) ? Math.round(roundQuantity(q) * cents) : 0;
}

/** Sum of the line totals, in cents. */
export function purchaseTotalCents(lines: { quantity: number | null; unitCostEuros: number | null }[]): number {
  return lines.reduce((sum, l) => sum + lineTotalCents(l.quantity, l.unitCostEuros), 0);
}

export function toCents(euros: number | null | undefined): number {
  return Math.round(Number(euros ?? 0) * 100);
}

function roundQuantity(value: number): number {
  return Math.round(value * 1000) / 1000;
}
