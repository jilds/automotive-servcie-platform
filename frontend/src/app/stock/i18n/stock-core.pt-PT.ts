import { StockCoreKey } from './stock-core.en';

/** Portuguese (Portugal) stock texts shown outside the stock pages, and the error codes. */
export const stockCorePtPT: Record<StockCoreKey, string> = {
  'nav.stock': 'Stock',
  'stock.picker.title': 'Adicionar peça do stock',
  'stock.picker.choose': 'Escolha uma peça',
  'stock.picker.option': '{name} ({sku}) · {available} disponível · {price}',
  'stock.picker.none': '{name} ({sku}) · sem stock disponível',
  'stock.picker.quantity': 'Quantidade',
  'stock.picker.add': 'Adicionar peça',
  'stock.picker.empty': 'Ainda não há peças em stock. O proprietário adiciona-as em Stock.',
  'stock.picker.hint': 'A peça fica reservada para este trabalho e sai do stock quando o carro é entregue.',
  'stock.picker.quantityInvalid': 'Indique uma quantidade acima de 0 com no máximo 2 casas decimais.',
  'stock.dashboard.lowStock': 'Stock baixo',
  'stock.dashboard.lowStockHint': 'Produtos abaixo do stock mínimo',
  'stock.dashboard.open': 'Ver os produtos',
  'error.product-not-found': 'Esse produto não está no stock.',
  'error.product-inactive': 'Esse produto já não se vende.',
  'error.duplicate-sku': 'Já existe outro produto com essa referência.',
  'error.insufficient-stock': 'Não há stock suficiente deste produto.',
  'error.adjustment-reason-required': 'Indique o motivo da alteração do stock.',
  'error.purchase-order-not-found': 'Encomenda ao fornecedor não encontrada.',
  'error.purchase-order-locked': 'Esta encomenda já foi enviada e não pode ser alterada.',
  'error.illegal-purchase-order-status': 'A encomenda não pode passar para esse estado.',
};
