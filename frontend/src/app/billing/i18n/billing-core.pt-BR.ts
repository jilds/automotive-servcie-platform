import { BillingCoreKey } from './billing-core.en';

/** Brazilian Portuguese invoicing texts shown outside the billing routes: the menu item and the error codes. */
export const billingCorePtBR: Record<BillingCoreKey, string> = {
  'nav.invoices': 'Faturas',
  'error.invoice-not-found': 'Fatura não encontrada.',
  'error.payment-not-found': 'Pagamento não encontrado.',
  'error.customer-nif-required': 'Acima do limite de consumidor final, a fatura exige o NIF do cliente.',
  'error.customer-address-required': 'Acima do limite de consumidor final, a fatura exige o endereço do cliente.',
  'error.invalid-phone': 'Informe um número de celular português, por exemplo 912 345 678.',
  'error.already-invoiced': 'Esta ordem de serviço já tem uma fatura em emissão, emitida ou com falha. Abra-a em vez de criar outra.',
  'error.work-order-not-invoiceable': 'Só é possível faturar uma ordem de serviço entregue e com pelo menos uma linha.',
  'error.invoice-not-issued': 'Isto só funciona em uma fatura emitida e sem nota de crédito.',
  'error.invoice-not-retryable': 'Só é possível tentar de novo um documento em emissão ou com falha.',
  'error.invoice-not-discardable': 'Só é possível descartar um documento com falha.',
  'error.invoice-not-voidable': 'Só é possível anular uma fatura emitida e sem nota de crédito.',
  'error.invoice-has-payments': 'Esta fatura tem um pagamento. Emita uma nota de crédito em vez de anulá-la.',
  'error.credit-note-exists': 'Esta fatura já tem uma nota de crédito.',
  'error.payment-exceeds-balance': 'O valor é maior do que o que falta pagar.',
  'error.payment-not-pending': 'Este pagamento não está mais aguardando. O estado mais recente é mostrado.',
  'error.payment-failed': 'O provedor de pagamentos recusou o pedido. Tente de novo ou escolha outro método.',
  'error.provider-not-configured': 'Não há provedor configurado para isto. Fale com o administrador da plataforma.',
  'error.provider-rejected': 'O provedor de faturamento recusou o pedido. Nada foi alterado.',
  'error.request-id-reused': 'Este pedido já foi usado para outro documento. Um novo foi preparado; clique de novo no botão.',
  'error.customer-name-too-long': 'O nome pode ter no máximo 120 caracteres.',
  'error.provider-unavailable': 'O provedor não respondeu. Nada foi alterado; tente de novo em instantes.',
};
