import { ShopCoreKey } from './shop-core.en';

/** Brazilian Portuguese shop texts shown outside the shop routes, and the error codes. */
export const shopCorePtBR: Record<ShopCoreKey, string> = {
  'nav.shopOrders': 'Pedidos online',
  'shop.dashboard.pending': 'Pedidos a preparar',
  'shop.dashboard.pendingHint': 'Pedidos da loja online aguardando preparação',
  'shop.dashboard.open': 'Ver os pedidos',
  'error.shop-order-not-found': 'Pedido não encontrado.',
  'error.product-not-listed': 'Um produto do carrinho não está mais à venda online.',
  'error.shop-order-not-open': 'Este pedido já foi encerrado e não pode mais ser alterado.',
  'error.invalid-payment-method': 'Escolha a forma de pagamento do cliente.',
  'error.cancel-reason-required': 'Informe o motivo do cancelamento.',
  'error.shop-busy': 'No momento a oficina não está aceitando mais pedidos online. Tente mais tarde ou entre em contato com a oficina.',
};
