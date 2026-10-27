import { ShopCoreKey } from './shop-core.en';

/** Portuguese (Portugal) shop texts shown outside the shop routes, and the error codes. */
export const shopCorePtPT: Record<ShopCoreKey, string> = {
  'nav.shopOrders': 'Encomendas online',
  'shop.dashboard.pending': 'Encomendas por preparar',
  'shop.dashboard.pendingHint': 'Encomendas da loja online à espera de preparação',
  'shop.dashboard.open': 'Ver as encomendas',
  'error.shop-order-not-found': 'Encomenda não encontrada.',
  'error.product-not-listed': 'Um produto do carrinho já não está à venda online.',
  'error.shop-order-not-open': 'Esta encomenda já está fechada e não pode ser alterada.',
  'error.invalid-payment-method': 'Escolha a forma de pagamento do cliente.',
  'error.cancel-reason-required': 'Indique o motivo do cancelamento.',
  'error.shop-busy': 'De momento a oficina não aceita mais encomendas online. Tente mais tarde ou contacte a oficina.',
};
