import { BillingCoreKey } from './billing-core.en';

/** Portuguese (Portugal) invoicing texts shown outside the billing routes: the menu item and the error codes. */
export const billingCorePtPT: Record<BillingCoreKey, string> = {
  'nav.invoices': 'Faturas',
  'error.invoice-not-found': 'Fatura não encontrada.',
  'error.payment-not-found': 'Pagamento não encontrado.',
  'error.customer-nif-required': 'Acima do limite de consumidor final, a fatura exige o NIF do cliente.',
  'error.customer-address-required': 'Acima do limite de consumidor final, a fatura exige a morada do cliente.',
  'error.invalid-phone': 'Indique um número de telemóvel português, por exemplo 912 345 678.',
  'error.already-invoiced': 'Esta ordem de reparação já tem uma fatura em emissão, emitida ou falhada. Abra-a em vez de criar outra.',
  'error.work-order-not-invoiceable': 'Só é possível faturar uma ordem de reparação entregue e com pelo menos uma linha.',
  'error.invoice-not-issued': 'Isto só funciona numa fatura emitida e sem nota de crédito.',
  'error.invoice-not-retryable': 'Só se pode tentar de novo um documento em emissão ou falhado.',
  'error.invoice-not-discardable': 'Só se pode descartar um documento falhado.',
  'error.invoice-not-voidable': 'Só se pode anular uma fatura emitida e sem nota de crédito.',
  'error.invoice-has-payments': 'Esta fatura tem um pagamento. Emita uma nota de crédito em vez de a anular.',
  'error.credit-note-exists': 'Esta fatura já tem uma nota de crédito.',
  'error.payment-exceeds-balance': 'O valor é superior ao que falta pagar.',
  'error.payment-not-pending': 'Este pagamento já não está à espera. Mostra-se o estado mais recente.',
  'error.payment-failed': 'O prestador de pagamentos recusou o pedido. Tente de novo ou escolha outro método.',
  'error.provider-not-configured': 'Não há prestador configurado para isto. Fale com o administrador da plataforma.',
  'error.provider-rejected': 'O prestador de faturação recusou o pedido. Nada foi alterado.',
  'error.request-id-reused': 'Este pedido já foi usado para outro documento. Foi preparado um novo; carregue de novo no botão.',
  'error.customer-name-too-long': 'O nome pode ter no máximo 120 caracteres.',
  'error.provider-unavailable': 'O prestador não respondeu. Nada foi alterado; tente de novo daqui a pouco.',
};
