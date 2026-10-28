import type { LineType, PlateEra, QuoteStatus } from '../core/api/models';

export type { QuoteStatus } from '../core/api/models';

/** Who recorded the decision: the customer on the link, or the front desk / owner for the customer. */
export type DecisionChannel = 'CUSTOMER' | 'STAFF';

export type QuoteDecision = 'APPROVED' | 'REJECTED';

/** One frozen line of a quote (a copy of the work order line when the quote was sent). */
export interface QuoteLine {
  position: number;
  type: LineType;
  description: string;
  quantity: number;
  unitPriceCents: number;
  discountPercent: number;
  vatRate: number;
  netCents: number;
  vatCents: number;
  serviceItemId: string | null;
  productId: string | null;
}

/** Sums per VAT rate, ascending by rate. */
export interface VatByRate {
  vatRate: number;
  netCents: number;
  vatCents: number;
}

/** `GET /work-orders/{id}/quotes` item and the answer of a decision (owner, front desk). */
export interface Quote {
  id: string;
  number: number;
  workOrderId: string;
  /** `EXPIRED` already for a SENT quote past `validUntil`. */
  status: QuoteStatus;
  sentAt: string;
  sentByName: string | null;
  validUntil: string;
  netCents: number;
  vatCents: number;
  totalCents: number;
  lines: QuoteLine[];
  vatByRate: VatByRate[];
  decidedAt: string | null;
  decidedChannel: DecisionChannel | null;
  /** The name the customer typed (channel CUSTOMER). */
  decidedName: string | null;
  /** The staff member who recorded it (channel STAFF). */
  decidedByName: string | null;
  /** The staff note, or the customer's rejection reason. */
  decidedNote: string | null;
  version: number;
}

/** `POST /work-orders/{id}/quotes`: the raw token exists only here (and in the first mail). */
export interface SentQuote {
  quote: Quote;
  statusToken: string;
  /** The vehicle's customer has an e-mail, so a mail goes out when mail is on. */
  customerHasEmail: boolean;
}

export interface DecisionRequest {
  decision: QuoteDecision;
  note: string;
  version: number;
}

/** A line on the public page: no ids. */
export interface PublicQuoteLine {
  type: LineType;
  description: string;
  quantity: number;
  unitPriceCents: number;
  discountPercent: number;
  vatRate: number;
  netCents: number;
}

export interface PublicQuoteVehicle {
  plateDisplay: string | null;
  plateEra: PlateEra | null;
  registrationCountry: string | null;
  label: string | null;
}

/** `GET /api/v1/public/{slug}/quotes/{token}`: no customer data, no ids; times shown in `timeZone`. */
export interface PublicQuote {
  number: number;
  workOrderNumber: number;
  status: QuoteStatus;
  sentAt: string;
  validUntil: string;
  timeZone: string | null;
  workshopName: string;
  /** Null when the vehicle is not found. */
  vehicle: PublicQuoteVehicle | null;
  lines: PublicQuoteLine[];
  vatByRate: VatByRate[];
  netCents: number;
  vatCents: number;
  totalCents: number;
  decidedAt: string | null;
  decidedChannel: DecisionChannel | null;
  /** Status SENT and not past `validUntil`. */
  canDecide: boolean;
}

export interface PublicApproveRequest {
  name: string;
  accepted: boolean;
}

export interface PublicRejectRequest {
  reason: string | null;
}
