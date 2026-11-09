import { QuoteCoreKey } from './quote-core.en';

/** Portuguese (Portugal) quote texts shown outside the quote routes, and the error codes. */
export const quoteCorePtPT: Record<QuoteCoreKey, string> = {
  'quote.badge.SENT': 'Orçamento enviado',
  'quote.badge.APPROVED': 'Orçamento aprovado',
  'quote.badge.REJECTED': 'Orçamento recusado',
  'quote.badge.SUPERSEDED': 'Orçamento desatualizado',
  'quote.badge.EXPIRED': 'Orçamento expirado',
  'error.quote-not-found': 'Orçamento não encontrado.',
  'error.quote-not-open': 'Este orçamento já foi decidido ou substituído e não pode ser alterado.',
  'error.quote-expired': 'Este orçamento expirou.',
  'error.work-order-not-quotable': 'Só é possível enviar um orçamento em Diagnóstico ou A aguardar aprovação, com pelo menos uma linha.',
  'error.approval-pending': 'Esta ordem não pode sair de A aguardar aprovação antes de o cliente responder ao orçamento. Só o proprietário pode contornar esta regra.',
  'workOrder.approvalPending': 'A aguardar a resposta do cliente ao orçamento. Só o proprietário pode mover esta ordem antes disso.',
  'error.decision-note-required': 'Acrescente uma nota a dizer como o cliente decidiu.',
};
