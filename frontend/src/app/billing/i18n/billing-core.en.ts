/**
 * English invoicing texts shown outside the billing routes: the menu item and the error codes (the sign-in page,
 * the work order and the dialogs show them). Eager: spread into `messages.en.ts`, so keep it small.
 */
export const billingCoreEn = {
  'nav.invoices': 'Invoices',
  'error.invoice-not-found': 'Invoice not found.',
  'error.payment-not-found': 'Payment not found.',
  'error.customer-nif-required': 'Above the final consumer limit the invoice needs the customer\'s NIF.',
  'error.customer-address-required': 'Above the final consumer limit the invoice needs the customer\'s address.',
  'error.invalid-phone': 'Enter a Portuguese mobile number, for example 912 345 678.',
  'error.invalid-instalment-count': 'The number of instalments must be between 2 and 6.',
  'error.invalid-instalment-reference': 'The terminal receipt number must have 1 to 60 characters.',
  'error.instalment-required': 'Instalment details are required for this payment method and not allowed for the others.',
  'error.already-invoiced': 'This work order already has an invoice that is being issued, issued or failed. Open it instead of creating another.',
  'error.work-order-not-invoiceable': 'Only a delivered work order with at least one line can be invoiced.',
  'error.invoice-not-issued': 'This only works on an issued invoice that has no credit note.',
  'error.invoice-not-retryable': 'Only a document that is being issued or failed can be tried again.',
  'error.invoice-not-discardable': 'Only a failed document can be discarded.',
  'error.invoice-not-voidable': 'Only an issued invoice without a credit note can be voided.',
  'error.invoice-has-payments': 'This invoice has a payment. Issue a credit note instead of voiding it.',
  'error.credit-note-exists': 'This invoice already has a credit note.',
  'error.payment-exceeds-balance': 'The amount is more than what is left to pay.',
  'error.payment-not-pending': 'This payment is no longer waiting. The latest state is shown.',
  'error.payment-failed': 'The payment provider refused the request. Try again or choose another method.',
  'error.provider-not-configured': 'No provider is set up for this. Ask the platform administrator.',
  'error.provider-rejected': 'The invoicing provider refused the request. Nothing was changed.',
  'error.request-id-reused': 'This request was already used for another document. A new one was prepared; press the button again.',
  'error.customer-name-too-long': 'The name can have at most 120 characters.',
  'error.provider-unavailable': 'The provider did not answer. Nothing was changed; try again in a moment.',
} as const;

export type BillingCoreKey = keyof typeof billingCoreEn;
