import { StockCoreKey } from './stock-core.en';

/** Portuguese (Brazil) stock texts shown outside the stock pages, and the error codes. */
export const stockCorePtBR: Record<StockCoreKey, string> = {
  'nav.stock': 'Estoque',
  'stock.picker.title': 'Adicionar peça do estoque',
  'stock.picker.choose': 'Escolha uma peça',
  'stock.picker.option': '{name} ({sku}) · {available} disponível · {price}',
  'stock.picker.none': '{name} ({sku}) · sem estoque disponível',
  'stock.picker.quantity': 'Quantidade',
  'stock.picker.add': 'Adicionar peça',
  'stock.picker.empty': 'Ainda não há peças em estoque. O proprietário as cadastra em Estoque.',
  'stock.picker.hint': 'A peça fica reservada para este serviço e sai do estoque quando o carro é entregue.',
  'stock.picker.quantityInvalid': 'Informe uma quantidade acima de 0 com no máximo 2 casas decimais.',
  'stock.dashboard.lowStock': 'Estoque baixo',
  'stock.dashboard.lowStockHint': 'Produtos abaixo do estoque mínimo',
  'stock.dashboard.open': 'Ver os produtos',
  'error.product-not-found': 'Esse produto não está no estoque.',
  'error.product-inactive': 'Esse produto não é mais vendido.',
  'error.duplicate-sku': 'Já existe outro produto com esse código.',
  'error.insufficient-stock': 'Não há estoque suficiente deste produto.',
  'error.adjustment-reason-required': 'Informe o motivo da alteração do estoque.',
  'error.purchase-order-not-found': 'Pedido de compra não encontrado.',
  'error.purchase-order-locked': 'Este pedido de compra já foi enviado e não pode ser alterado.',
  'error.illegal-purchase-order-status': 'O pedido de compra não pode passar para esse status.',
};
