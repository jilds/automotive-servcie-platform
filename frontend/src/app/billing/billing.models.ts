/** Invoicing and payments (module `billing`), see `docs/api/release-4.md`, "Invoicing and payments". Money in cents. */

export type InvoiceKind = 'INVOICE' | 'CREDIT_NOTE';
export type InvoiceStatus = 'PENDING' | 'ISSUED' | 'FAILED' | 'VOIDED' | 'DISCARDED';
export type PaymentStatusOfInvoice = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'CREDITED';
export type PaymentMethod = 'CASH' | 'CARD' | 'MULTIBANCO' | 'MBWAY' | 'TRANSFER';
export type PaymentState = 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED' | 'CANCELLED';
export type SandboxOutcome = 'PAID' | 'FAILED' | 'EXPIRED';

/** Methods recorded as paid at once, without a provider. */
export const IMMEDIATE_METHODS: readonly PaymentMethod[] = ['CASH', 'CARD', 'TRANSFER'];
export const REASON_MAX = 500;
export const NOTE_MAX = 200;
export const INVOICE_STATUSES: readonly InvoiceStatus[] = ['PENDING', 'ISSUED', 'FAILED', 'VOIDED', 'DISCARDED'];
export const PAYMENT_STATUSES: readonly PaymentStatusOfInvoice[] = ['UNPAID', 'PARTIALLY_PAID', 'PAID', 'CREDITED'];

export interface InvoiceAddress {
  street: string;
  detail: string | null;
  postalCode: string;
  locality: string;
  country: string;
}

export interface InvoiceCustomer {
  name: string;
  nif: string | null;
  finalConsumer: boolean;
  address: InvoiceAddress | null;
}

export interface InvoiceLine {
  id: string;
  position: number;
  type: 'LABOR' | 'PART';
  description: string;
  quantity: number;
  unitPriceCents: number;
  discountPercent: number;
  vatRate: number;
  netCents: number;
  vatCents: number;
  totalCents: number;
}

export interface VatSummary {
  vatRate: number;
  netCents: number;
  vatCents: number;
}

export interface Payment {
  id: string;
  invoiceId: string;
  method: PaymentMethod;
  status: PaymentState;
  amountCents: number;
  provider: string | null;
  multibanco: { entity: string; reference: string } | null;
  mbway: { phoneMasked: string } | null;
  expiresAt: string | null;
  paidAt: string | null;
  failureCode: string | null;
  note: string | null;
  createdAt: string;
  version: number;
}

export interface InvoiceFailure {
  code: string;
  message: string;
  attempts: number;
  lastAttemptAt: string;
}

export interface Invoice {
  id: string;
  kind: InvoiceKind;
  status: InvoiceStatus;
  workOrderId: string;
  workOrderNumber: number;
  creditOfId: string | null;
  creditOfNumber: string | null;
  number: string | null;
  series: string | null;
  sequence: number | null;
  atcud: string | null;
  qrPayload: string | null;
  hashFragment: string | null;
  issuedAt: string | null;
  provider: string;
  sandbox: boolean;
  issuer: { name: string; nif: string | null; country: string };
  customer: InvoiceCustomer;
  plate: string | null;
  lines: InvoiceLine[];
  vatSummary: VatSummary[];
  netCents: number;
  vatCents: number;
  totalCents: number;
  paidCents: number;
  pendingCents: number;
  balanceCents: number;
  overpaidCents: number;
  refundDueCents: number;
  paymentStatus: PaymentStatusOfInvoice | null;
  payments: Payment[];
  creditNote: { id: string; number: string | null; status: 'PENDING' | 'ISSUED' | 'FAILED' } | null;
  reason: string | null;
  closedAt: string | null;
  failure: InvoiceFailure | null;
  createdAt: string;
  version: number;
}

export interface InvoiceSummary {
  id: string;
  kind: InvoiceKind;
  status: InvoiceStatus;
  number: string | null;
  workOrderId: string;
  workOrderNumber: number;
  customerName: string;
  customerNif: string | null;
  plate: string | null;
  issuedAt: string | null;
  createdAt: string;
  totalCents: number;
  balanceCents: number;
  /** Paid money to give back on a credited or voided invoice; 0 otherwise. */
  refundDueCents: number;
  paymentStatus: PaymentStatusOfInvoice | null;
  sandbox: boolean;
}

export interface InvoicePage {
  items: InvoiceSummary[];
  page: number;
  size: number;
  total: number;
}

export interface InvoiceQuery {
  status?: InvoiceStatus[];
  kind?: InvoiceKind | '';
  paymentStatus?: PaymentStatusOfInvoice | '';
  from?: string;
  to?: string;
  q?: string;
  page?: number;
  size?: number;
}

export interface IssueInvoiceRequest {
  requestId: string;
  finalConsumer?: boolean;
  customer?: {
    name: string;
    nif: string | null;
    /** `country` is left out unless the user picked one (the server uses the workshop's). */
    address: (Omit<InvoiceAddress, 'country'> & { country?: string }) | null;
  };
}

export interface CreatePaymentRequest {
  method: PaymentMethod;
  amountCents?: number;
  phone?: string;
  note?: string;
}

export interface BillingSettings {
  invoicing: { provider: string; configured: boolean; sandbox: boolean };
  payments: { provider: string; configured: boolean; sandbox: boolean };
  finalConsumerLimitCents: number;
  currency: string;
  methods: PaymentMethod[];
  multibancoValidDays: number;
  mbwayValidMinutes: number;
}

/** What an issue or credit note call tells besides the body: 201 new, 200 replay of an issued one, 202 stored. */
export type IssueOutcome = 'issued' | 'replay' | 'stored';

export interface IssueResult {
  outcome: IssueOutcome;
  invoice: Invoice;
}
