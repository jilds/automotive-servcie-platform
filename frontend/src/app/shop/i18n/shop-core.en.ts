/**
 * English shop texts shown outside the shop routes: the menu entry, the owner dashboard tile and the error codes.
 * Eager: spread into `messages.en.ts`, so keep it small (initial bundle budget).
 */
export const shopCoreEn = {
  'nav.shopOrders': 'Online orders',
  'shop.dashboard.pending': 'Orders to prepare',
  'shop.dashboard.pendingHint': 'Online shop orders waiting to be prepared',
  'shop.dashboard.open': 'See the orders',
  'error.shop-order-not-found': 'Order not found.',
  'error.product-not-listed': 'A product in the cart is no longer sold online.',
  'error.shop-order-not-open': 'This order is already closed and can no longer change.',
  'error.invalid-payment-method': 'Choose how the customer paid.',
  'error.cancel-reason-required': 'Say why the order is cancelled.',
  'error.shop-busy': 'The workshop cannot take more online orders right now. Please try again later or contact the workshop.',
} as const;

export type ShopCoreKey = keyof typeof shopCoreEn;
