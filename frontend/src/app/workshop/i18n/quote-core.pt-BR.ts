import { QuoteCoreKey } from './quote-core.en';

/** Brazilian Portuguese quote texts shown outside the quote routes, and the error codes. */
export const quoteCorePtBR: Record<QuoteCoreKey, string> = {
  'quote.badge.SENT': 'Orçamento enviado',
  'quote.badge.APPROVED': 'Orçamento aprovado',
  'quote.badge.REJECTED': 'Orçamento recusado',
  'quote.badge.SUPERSEDED': 'Orçamento desatualizado',
  'quote.badge.EXPIRED': 'Orçamento vencido',
  'error.quote-not-found': 'Orçamento não encontrado.',
  'error.quote-not-open': 'Este orçamento já foi decidido ou substituído e não pode mais ser alterado.',
  'error.quote-expired': 'Este orçamento venceu.',
  'error.work-order-not-quotable': 'Só é possível enviar um orçamento em Diagnóstico ou Aguardando aprovação, com pelo menos uma linha.',
  'error.decision-note-required': 'Adicione uma observação dizendo como o cliente decidiu.',
};
