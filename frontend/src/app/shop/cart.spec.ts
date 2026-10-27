import { addToCart, cartKey, cartTotalCents, cartUnits, orderLines, pruneCart, readCart, removeFromCart, sanitizeCart, setQuantity, writeCart } from './cart';

const inStock = { id: 'p1', availability: 'IN_STOCK' as const };

/** A storage double: a map, or one that throws like a blocked or full `localStorage`. */
function memoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (k: string) => map.get(k) ?? null,
    key: (i: number) => [...map.keys()][i] ?? null,
    removeItem: (k: string) => void map.delete(k),
    setItem: (k: string, v: string) => void map.set(k, v),
  };
}

const throwing = (): Storage => {
  throw new DOMException('blocked', 'SecurityError');
};

describe('cart', () => {
  it('adds a product as a new line, then one more unit on the same line', () => {
    let { cart, limit } = addToCart([], inStock);
    expect(cart).toEqual([{ productId: 'p1', quantity: 1 }]);
    expect(limit).toBeNull();
    ({ cart } = addToCart(cart, inStock));
    expect(cart).toEqual([{ productId: 'p1', quantity: 2 }]);
  });

  it('never adds a product that is out of stock', () => {
    const change = addToCart([], { id: 'p1', availability: 'OUT' });
    expect(change.cart).toEqual([]);
    expect(change.limit).toBe('out-of-stock');
  });

  it('caps a line at 20 units and says so', () => {
    const full = setQuantity([{ productId: 'p1', quantity: 19 }], 'p1', 25);
    expect(full.cart[0].quantity).toBe(20);
    expect(full.limit).toBe('max-quantity');
    const more = addToCart(full.cart, inStock);
    expect(more.cart[0].quantity).toBe(20);
    expect(more.limit).toBe('max-quantity');
  });

  it('keeps quantities whole and at least 1', () => {
    expect(setQuantity([{ productId: 'p1', quantity: 3 }], 'p1', 0).cart[0].quantity).toBe(1);
    expect(setQuantity([{ productId: 'p1', quantity: 3 }], 'p1', 2.7).cart[0].quantity).toBe(2);
    expect(setQuantity([{ productId: 'p1', quantity: 3 }], 'p1', Number.NaN).cart[0].quantity).toBe(3);
  });

  it('refuses a 21st product', () => {
    const cart = Array.from({ length: 20 }, (_, i) => ({ productId: `p${i}`, quantity: 1 }));
    const change = addToCart(cart, { id: 'new', availability: 'IN_STOCK' });
    expect(change.cart).toHaveLength(20);
    expect(change.limit).toBe('max-lines');
    // More of a product already in the cart is still fine.
    expect(addToCart(cart, { id: 'p3', availability: 'IN_STOCK' }).cart[3].quantity).toBe(2);
  });

  it('removes a line and builds the order lines, units and total', () => {
    const cart = [
      { productId: 'p1', quantity: 2 },
      { productId: 'p2', quantity: 1 },
    ];
    expect(removeFromCart(cart, 'p1')).toEqual([{ productId: 'p2', quantity: 1 }]);
    expect(orderLines(cart)).toEqual(cart);
    expect(cartUnits(cart)).toBe(3);
    const prices = new Map([
      ['p1', { unitPriceWithVatCents: 1476 }],
      ['p2', { unitPriceWithVatCents: 500 }],
    ]);
    expect(cartTotalCents(cart, prices)).toBe(3452);
    expect(cartTotalCents(cart, new Map())).toBe(0);
  });

  it('prunes the products no longer offered and keeps the others, out of stock ones too', () => {
    const cart = [
      { productId: 'p1', quantity: 2 },
      { productId: 'gone', quantity: 1 },
      { productId: 'p3', quantity: 1 },
    ];
    const { cart: kept, removed } = pruneCart(cart, [{ id: 'p1' }, { id: 'p3' }]);
    expect(kept.map((l) => l.productId)).toEqual(['p1', 'p3']);
    expect(removed).toEqual([{ productId: 'gone', quantity: 1 }]);
  });

  it('stores the cart per workshop and reads it back', () => {
    const storage = memoryStorage();
    expect(writeCart('oficina-azul', [{ productId: 'p1', quantity: 2 }], () => storage)).toBe(true);
    expect(readCart('oficina-azul', () => storage)).toEqual([{ productId: 'p1', quantity: 2 }]);
    expect(readCart('outra', () => storage)).toEqual([]);
    expect(storage.getItem(cartKey('oficina-azul'))).toContain('p1');
    // An empty cart removes the entry.
    writeCart('oficina-azul', [], () => storage);
    expect(storage.getItem(cartKey('oficina-azul'))).toBeNull();
  });

  it('keeps working when storage is blocked or holds garbage', () => {
    expect(writeCart('oficina-azul', [{ productId: 'p1', quantity: 1 }], throwing)).toBe(false);
    expect(readCart('oficina-azul', throwing)).toEqual([]);
    expect(readCart('oficina-azul', () => null)).toEqual([]);

    const storage = memoryStorage();
    storage.setItem(cartKey('oficina-azul'), '{not json');
    expect(readCart('oficina-azul', () => storage)).toEqual([]);
  });

  it('cleans a stored cart: bad lines out, duplicates once, quantities capped, at most 20 lines', () => {
    expect(sanitizeCart('nope')).toEqual([]);
    expect(
      sanitizeCart([
        { productId: 'p1', quantity: 50 },
        { productId: 'p1', quantity: 2 },
        { productId: '', quantity: 1 },
        { productId: 'p2', quantity: 0 },
        { productId: 'p3', quantity: '2' },
        null,
        { productId: 'p4', quantity: 1.5 },
      ]),
    ).toEqual([
      { productId: 'p1', quantity: 20 },
      { productId: 'p4', quantity: 1 },
    ]);
    expect(sanitizeCart(Array.from({ length: 30 }, (_, i) => ({ productId: `p${i}`, quantity: 1 })))).toHaveLength(20);
  });
});
