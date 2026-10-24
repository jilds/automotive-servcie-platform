/**
 * English stock texts shown outside the stock pages: the menu entry, the work order's part picker, the owner
 * dashboard tile and the error codes. Eager: spread into `messages.en.ts`.
 */
export const stockCoreEn = {
  'nav.stock': 'Stock',
  'stock.picker.title': 'Add a part from stock',
  'stock.picker.choose': 'Choose a part',
  'stock.picker.option': '{name} ({sku}) · {available} available · {price}',
  'stock.picker.none': '{name} ({sku}) · none available',
  'stock.picker.quantity': 'Quantity',
  'stock.picker.add': 'Add part',
  'stock.picker.empty': 'No parts in stock yet. The owner adds them under Stock.',
  'stock.picker.hint': 'The part is reserved for this job and leaves stock when the car is delivered.',
  'stock.picker.quantityInvalid': 'Enter a quantity above 0 with at most 2 decimals.',
  'stock.dashboard.lowStock': 'Low stock',
  'stock.dashboard.lowStockHint': 'Products below their minimum stock',
  'stock.dashboard.open': 'See the products',
  'error.product-not-found': 'That product is not in stock.',
  'error.product-inactive': 'That product is no longer sold.',
  'error.duplicate-sku': 'Another product already has that SKU.',
  'error.insufficient-stock': 'Not enough of this product in stock.',
  'error.adjustment-reason-required': 'Say why the stock changes.',
  'error.purchase-order-not-found': 'Purchase order not found.',
  'error.purchase-order-locked': 'This purchase order was already sent and can no longer be changed.',
  'error.illegal-purchase-order-status': 'The purchase order cannot move to that status.',
} as const;

export type StockCoreKey = keyof typeof stockCoreEn;
