import { MAX_LINES, MAX_QUANTITY, OrderLineRequest, PublicProduct } from './shop.models';

/**
 * The public shop's cart: product ids and quantities only, kept in `localStorage` per workshop. Names and prices
 * always come from the product list, so a stored cart never shows an old price. Storage failures (private windows,
 * blocked site data, a full quota) never break the page: the cart then lives in memory only.
 */

export interface CartLine {
  productId: string;
  quantity: number;
}

export type Cart = readonly CartLine[];

/** Why an add or a quantity change did not go through as asked. */
export type CartLimit = 'max-lines' | 'max-quantity' | 'out-of-stock';

export interface CartChange {
  cart: CartLine[];
  /** Set when the change was refused or capped. */
  limit: CartLimit | null;
}

const KEY_PREFIX = 'asp.shop.cart.';

export function cartKey(slug: string): string {
  return KEY_PREFIX + slug;
}

function clampQuantity(n: number): number {
  return Math.min(MAX_QUANTITY, Math.max(1, Math.trunc(n)));
}

/** Keeps only well-formed lines: a product id, a whole quantity 1–20, each product once, at most 20 lines. */
export function sanitizeCart(value: unknown): CartLine[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  const lines: CartLine[] = [];
  for (const item of value) {
    const line = item as Partial<CartLine> | null;
    if (!line || typeof line.productId !== 'string' || !line.productId || seen.has(line.productId)) continue;
    if (typeof line.quantity !== 'number' || !Number.isFinite(line.quantity) || line.quantity < 1) continue;
    seen.add(line.productId);
    lines.push({ productId: line.productId, quantity: clampQuantity(line.quantity) });
    if (lines.length === MAX_LINES) break;
  }
  return lines;
}

function defaultStorage(): Storage | null {
  return typeof localStorage === 'undefined' ? null : localStorage;
}

/** The stored cart of a workshop; empty when there is none, it is unreadable or storage is blocked. */
export function readCart(slug: string, storage: () => Storage | null = defaultStorage): CartLine[] {
  try {
    const raw = storage()?.getItem(cartKey(slug));
    return raw ? sanitizeCart(JSON.parse(raw)) : [];
  } catch {
    return [];
  }
}

/** Stores the cart (removes the entry when empty); false when storage is not available. */
export function writeCart(slug: string, cart: Cart, storage: () => Storage | null = defaultStorage): boolean {
  try {
    const s = storage();
    if (!s) return false;
    if (cart.length) s.setItem(cartKey(slug), JSON.stringify(cart));
    else s.removeItem(cartKey(slug));
    return true;
  } catch {
    return false;
  }
}

/** Adds `quantity` units of a product: a new line, or more on its line, within 20 lines and 20 units a line. */
export function addToCart(cart: Cart, product: Pick<PublicProduct, 'id' | 'availability'>, quantity = 1): CartChange {
  if (product.availability === 'OUT') return { cart: [...cart], limit: 'out-of-stock' };
  const existing = cart.find((l) => l.productId === product.id);
  if (!existing) {
    if (cart.length >= MAX_LINES) return { cart: [...cart], limit: 'max-lines' };
    return { cart: [...cart, { productId: product.id, quantity: clampQuantity(quantity) }], limit: quantity > MAX_QUANTITY ? 'max-quantity' : null };
  }
  if (existing.quantity >= MAX_QUANTITY) return { cart: [...cart], limit: 'max-quantity' };
  const wanted = existing.quantity + quantity;
  return {
    cart: cart.map((l) => (l === existing ? { ...l, quantity: clampQuantity(wanted) } : l)),
    limit: wanted > MAX_QUANTITY ? 'max-quantity' : null,
  };
}

/** Sets a line's quantity, capped to 1–20 (removing a line is `removeFromCart`). */
export function setQuantity(cart: Cart, productId: string, quantity: number): CartChange {
  if (!Number.isFinite(quantity)) return { cart: [...cart], limit: null };
  const capped = clampQuantity(quantity);
  return {
    cart: cart.map((l) => (l.productId === productId ? { ...l, quantity: capped } : l)),
    limit: quantity > MAX_QUANTITY ? 'max-quantity' : null,
  };
}

export function removeFromCart(cart: Cart, productId: string): CartLine[] {
  return cart.filter((l) => l.productId !== productId);
}

/**
 * Drops the lines whose product is no longer offered (missing from the full product list) and returns them, so
 * the page can say so. Products listed as `OUT` stay, marked on the page, so the customer sees what happened.
 */
export function pruneCart(cart: Cart, offered: readonly Pick<PublicProduct, 'id'>[]): { cart: CartLine[]; removed: CartLine[] } {
  const ids = new Set(offered.map((p) => p.id));
  const kept: CartLine[] = [];
  const removed: CartLine[] = [];
  for (const line of cart) (ids.has(line.productId) ? kept : removed).push(line);
  return { cart: kept, removed };
}

/** The order lines of the POST body. */
export function orderLines(cart: Cart): OrderLineRequest[] {
  return cart.map((l) => ({ productId: l.productId, quantity: l.quantity }));
}

/** Total with VAT from the products' current prices; a line without a known product counts 0. */
export function cartTotalCents(cart: Cart, products: ReadonlyMap<string, Pick<PublicProduct, 'unitPriceWithVatCents'>>): number {
  return cart.reduce((sum, l) => sum + l.quantity * (products.get(l.productId)?.unitPriceWithVatCents ?? 0), 0);
}

/** Units in the cart, for the cart's badge. */
export function cartUnits(cart: Cart): number {
  return cart.reduce((sum, l) => sum + l.quantity, 0);
}
