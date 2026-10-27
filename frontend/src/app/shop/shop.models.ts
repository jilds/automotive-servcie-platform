import type { Language } from '../core/i18n/i18n.service';

/** Online shop with pay at the counter (add-on `shop`), see `docs/api/release-4.md`, "Online shop". Money in cents. */

export type ShopOrderStatus = 'PLACED' | 'READY' | 'COLLECTED' | 'CANCELLED' | 'EXPIRED';

/** Never a number: `LAST_UNITS` is a few left, `OUT` cannot be ordered. */
export type Availability = 'IN_STOCK' | 'LAST_UNITS' | 'OUT';

export type PaymentMethod = 'CASH' | 'CARD' | 'MULTIBANCO' | 'MBWAY';

export const PAYMENT_METHODS: readonly PaymentMethod[] = ['CASH', 'CARD', 'MULTIBANCO', 'MBWAY'];

/** Who cancelled or expired an order; null unless CANCELLED or EXPIRED. */
export type CancelledBy = 'CUSTOMER' | 'STAFF' | 'EXPIRY';

/** A product the public page offers (`GET /public/{slug}/shop/products`). */
export interface PublicProduct {
  id: string;
  sku: string;
  name: string;
  brand: string | null;
  unit: string;
  unitPriceWithVatCents: number;
  availability: Availability;
}

export interface OrderLineRequest {
  productId: string;
  quantity: number;
}

/** Body of `POST /public/{slug}/shop/orders`. */
export interface PublicOrderRequest {
  lines: OrderLineRequest[];
  name: string;
  phone: string | null;
  email: string | null;
  nif: string | null;
  privacyAccepted: boolean;
  marketingConsent: boolean;
  /** Hidden field: people leave it empty, so it is sent as "". */
  website: string;
  /** The page language, for the e-mails. */
  language: Language;
}

/** `201` answer of the public POST. The token is the only key of the status link. */
export interface OrderPlaced {
  number: number;
  statusToken: string;
  totalCents: number;
  holdUntil: string;
  /** The workshop's IANA zone, in which `holdUntil` is shown. */
  timeZone: string;
}

export interface PublicShopOrderLine {
  name: string;
  unit: string;
  quantity: number;
  unitPriceWithVatCents: number;
  lineTotalCents: number;
}

/** `GET /public/{slug}/shop/orders/{token}`: what the customer sees on the status link (no contact data, no ids). */
export interface PublicShopOrder {
  number: number;
  status: ShopOrderStatus;
  placedAt: string;
  holdUntil: string;
  /** The workshop's IANA zone, for showing the times. */
  timeZone: string;
  workshopName: string;
  pickupNote: string | null;
  lines: PublicShopOrderLine[];
  totalCents: number;
  canCancel: boolean;
}

/** One row of the front desk's list (`GET /shop-orders`). */
export interface ShopOrderSummary {
  id: string;
  number: number;
  status: ShopOrderStatus;
  placedAt: string;
  holdUntil: string;
  name: string;
  lineCount: number;
  totalCents: number;
  version: number;
}

export interface ShopOrderLine {
  productId: string;
  sku: string;
  name: string;
  unit: string;
  quantity: number;
  unitPriceCents: number;
  vatRate: number;
  unitPriceWithVatCents: number;
  lineTotalCents: number;
}

/** An order as the front desk sees it (`GET /shop-orders/{id}`). */
export interface ShopOrder {
  id: string;
  number: number;
  status: ShopOrderStatus;
  placedAt: string;
  readyAt: string | null;
  holdUntil: string;
  name: string;
  phone: string | null;
  email: string | null;
  nif: string | null;
  marketingConsent: boolean;
  lines: ShopOrderLine[];
  totalCents: number;
  paymentMethod: PaymentMethod | null;
  collectedByName: string | null;
  collectedAt: string | null;
  cancelReason: string | null;
  cancelledBy: CancelledBy | null;
  version: number;
}

/** `GET/PUT /shop/settings` (owner writes). */
export interface ShopSettings {
  holdDays: number;
  pickupNote: string | null;
  version: number;
}

/** Limits of the contract. */
export const MAX_QUANTITY = 20;
export const MAX_LINES = 20;
export const HOLD_DAYS_MIN = 1;
export const HOLD_DAYS_MAX = 14;
export const PICKUP_NOTE_MAX = 500;
export const CANCEL_REASON_MAX = 500;
export const SEARCH_MAX = 60;
