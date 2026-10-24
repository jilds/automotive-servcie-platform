/** `FIXED`: the service's own price. `HOURLY`: standard hours times the workshop's labour rate. */
export type ServicePricing = 'FIXED' | 'HOURLY';

/**
 * A standard service (`GET /catalogue/services`). Prices and VAT are null for mechanics. Money in cents: `priceCents`
 * is the stored fixed price, `effectivePriceCents` what the service costs now (for HOURLY at the current labour rate).
 */
export interface CatalogueService {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  standardHours: number;
  pricing: ServicePricing;
  priceCents: number | null;
  effectivePriceCents: number | null;
  vatRate: number | null;
  durationMinutes: number;
  active: boolean;
  listedOnline: boolean;
  version: number;
}

/** Body of `POST /catalogue/services`; PUT also sends `version`. */
export interface ServiceRequest {
  name: string;
  description: string | null;
  category: string | null;
  standardHours: number;
  pricing: ServicePricing;
  /** Kept but not used while the pricing is HOURLY. */
  priceCents: number;
  vatRate: number;
  durationMinutes: number;
  active: boolean;
  listedOnline: boolean;
  version?: number;
}

/** A package item is a catalogue service or a stock product. */
export type PackageItemKind = 'SERVICE' | 'PRODUCT';

export interface PackageItem {
  kind: PackageItemKind;
  /** Set for a SERVICE. */
  serviceId: string | null;
  /** Set for a PRODUCT. */
  productId: string | null;
  name: string;
  /** The product's SKU; null for a service. */
  sku: string | null;
  quantity: number;
  /** The service's effective price, or the product's sale price. */
  unitPriceCents: number;
  /** False when the service is no longer offered or the product no longer sold; the package still holds it. */
  active: boolean;
}

/**
 * A stock product the package dialog offers (a `stock` `Product` fits): sale price before VAT in cents, and what is
 * available now (on hand minus reservations).
 */
export interface PackageProductChoice {
  id: string;
  sku: string;
  name: string;
  unit: string;
  salePriceCents: number | null;
  available: number;
  active: boolean;
}

/** A package item as sent: a service or a product, with how many. */
export type PackageItemRequest = { serviceId: string; quantity: number } | { productId: string; quantity: number };

/** A package of services and stock products (`GET /catalogue/packages`, owner and front desk). */
export interface CataloguePackage {
  id: string;
  name: string;
  description: string | null;
  priceCents: number;
  vatRate: number;
  /** What the services and products cost on their own, to show the saving. */
  listPriceCents: number;
  active: boolean;
  listedOnline: boolean;
  items: PackageItem[];
  version: number;
}

/** Body of `POST /catalogue/packages`; PUT also sends `version`. */
export interface PackageRequest {
  name: string;
  description: string | null;
  priceCents: number;
  vatRate: number;
  active: boolean;
  listedOnline: boolean;
  items: PackageItemRequest[];
  version?: number;
}
