/**
 * A product with its stock (`GET /stock/products`). Quantities may have 3 decimals; money in cents.
 * For mechanics cost, prices and every stock figure except `available` are null.
 */
export interface Product {
  id: string;
  sku: string;
  name: string;
  brand: string | null;
  unit: string;
  costCents: number | null;
  salePriceCents: number | null;
  vatRate: number | null;
  minStock: number | null;
  bin: string | null;
  onHand: number | null;
  reserved: number | null;
  /** On hand minus what open work orders reserved. */
  available: number;
  /** Less available than the minimum stock; null for mechanics. */
  low: boolean | null;
  active: boolean;
  listedOnline: boolean;
  /** Optimistic locking: sent back with `PUT /stock/products/{id}`. Stock movements do not change it. */
  version: number;
}

/** Body of `POST /stock/products`; PUT also sends `version`. */
export interface ProductRequest {
  sku: string;
  name: string;
  brand: string | null;
  unit: string;
  costCents: number;
  salePriceCents: number;
  vatRate: number;
  minStock: number;
  bin: string | null;
  active: boolean;
  listedOnline: boolean;
  version?: number;
}

export type MovementType = 'RECEIPT' | 'ISSUE' | 'ADJUSTMENT';

/** A ledger row (`GET /stock/products/{id}/movements`), newest first. */
export interface Movement {
  id: string;
  type: MovementType;
  /** Positive for what came in, negative for what went out. */
  quantity: number;
  /** `WORK_ORDER` or `PURCHASE_ORDER`; null for an adjustment. */
  referenceType: string | null;
  referenceId: string | null;
  reason: string | null;
  changedById: string;
  changedByName: string | null;
  changedAt: string;
}

export type PurchaseOrderStatus = 'DRAFT' | 'ORDERED' | 'RECEIVED';

export interface PurchaseOrderLine {
  productId: string;
  sku: string;
  name: string;
  unit: string;
  quantity: number;
  unitCostCents: number;
  totalCents: number;
}

/** A purchase order (`GET /stock/purchase-orders`). Money in cents. */
export interface PurchaseOrder {
  id: string;
  number: number;
  supplier: string;
  notes: string | null;
  status: PurchaseOrderStatus;
  orderedAt: string | null;
  receivedAt: string | null;
  totalCents: number;
  lines: PurchaseOrderLine[];
  version: number;
}

/** Body of `POST /stock/purchase-orders`; PUT (drafts only) also sends `version`. */
export interface PurchaseOrderRequest {
  supplier: string;
  notes: string | null;
  lines: { productId: string; quantity: number; unitCostCents: number }[];
  version?: number;
}
