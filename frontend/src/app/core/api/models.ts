export type Role = 'OWNER' | 'FRONT_DESK' | 'MECHANIC';

/** Portuguese plate layout the backend recognised; null for other countries or a plate that fits no layout. */
export type PlateEra = 'PT_2020' | 'PT_2005' | 'PT_1992' | 'PT_1937';

/** Plate fields the API adds to work orders and board cards (Release 2, §7); null when the vehicle is gone. */
export interface PlateInfo {
  /** The plate as printed, e.g. `12-AB-34`. */
  plateDisplay: string | null;
  plateEra: PlateEra | null;
  /** ISO 3166-1 alpha-2, upper case. */
  registrationCountry: string | null;
  /** Month of first registration, `YYYY-MM`. */
  registrationDate: string | null;
}

export type WorkOrderStatus =
  | 'DIAGNOSTICS'
  | 'AWAITING_APPROVAL'
  | 'AWAITING_PARTS'
  | 'IN_PROGRESS'
  | 'READY'
  | 'DELIVERED'
  | 'CANCELLED';

/** Status of a quote; a SENT quote past its `validUntil` already reads EXPIRED. */
export type QuoteStatus = 'SENT' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED' | 'EXPIRED';

/** The latest quote of a work order, without amounts (mechanics get it too). */
export interface WorkOrderQuote {
  id: string;
  number: number;
  status: QuoteStatus;
  validUntil: string;
  decidedAt: string | null;
  decidedChannel: 'CUSTOMER' | 'STAFF' | null;
}

export interface User {
  id: string;
  tenantId: string;
  name: string;
  email: string;
  role: Role;
}

/** Who is signed in: a workshop user, or a platform admin (no workshop, role `PLATFORM_ADMIN`). */
export type AccountRole = Role | 'PLATFORM_ADMIN';

export interface SessionUser {
  id: string;
  /** Null for a platform admin. */
  tenantId: string | null;
  name: string;
  email: string;
  role: AccountRole;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: SessionUser;
}

/** A workshop's theme (`GET /branding`, `GET /branding/{slug}`). Unset colours fall back to the ASP theme. */
export interface Branding {
  slug: string;
  /** Display name, or the workshop name when none is set. */
  name: string;
  displayName: string | null;
  /** `#rrggbb`. */
  primaryColor: string | null;
  /** `#ffffff` or `#000000`, whichever reaches 4.5:1 on the primary colour. */
  primaryTextColor: string | null;
  accentColor: string | null;
  logoUrl: string | null;
  /** The platform's privacy policy on the landing site (`app.site.privacy-url`), linked from the public booking page; null when unset. */
  privacyUrl: string | null;
  /** The add-ons switched on; only for the signed-in workshop and the platform admin, not on the public `/branding/{slug}`. */
  addOns?: AddOns;
  /** Workshop version (optimistic locking); sent back with the next theme change. */
  version: number;
}

/** Add-ons the platform admin switches on per workshop (ADR 0004). */
export interface AddOns {
  booking: boolean;
  shop: boolean;
}

export interface Address {
  street: string;
  detail: string | null;
  postalCode: string;
  locality: string;
  country: string;
}

export interface Customer {
  id: string;
  name: string;
  nif: string | null;
  phone: string | null;
  email: string | null;
  address: Address | null;
  marketingConsent: boolean;
  /** Optimistic locking: sent back with `PUT /customers/{id}`; a newer save answers 409 `stale-data`. */
  version: number;
}

export interface Vehicle {
  id: string;
  customerId: string;
  plate: string;
  plateDisplay: string;
  plateEra: PlateEra | null;
  registrationCountry: string;
  registrationDate: string | null;
  vin: string | null;
  make: string | null;
  model: string | null;
  year: number | null;
  fuelType: string | null;
  mileage: number | null;
  /** Optimistic locking: sent back with `PUT /vehicles/{id}` and `PUT /vehicles/{id}/owner`. */
  version: number;
}

/** Body of `POST /customers` and `PUT /customers/{id}`; PUT replaces every field and needs `version`. */
export interface CustomerRequest {
  name: string;
  nif: string | null;
  phone: string | null;
  email: string | null;
  address: Address | null;
  marketingConsent: boolean;
  /** Required on PUT: the version the form was opened with. */
  version?: number;
}

/** Body of `PUT /vehicles/{id}`. A null mileage keeps the recorded reading; a null registration date clears it. */
export interface VehicleUpdateRequest {
  plate: string;
  vin: string | null;
  make: string | null;
  model: string | null;
  year: number | null;
  fuelType: string | null;
  mileage: number | null;
  registrationCountry: string;
  /** `YYYY-MM`, 1937-01 up to the current month. */
  registrationDate: string | null;
  /** The version the form was opened with (optimistic locking). */
  version: number;
}

/** Body of `POST /vehicles`. A null registration country means the workshop's country. */
export interface VehicleCreateRequest extends Omit<VehicleUpdateRequest, 'registrationCountry' | 'version'> {
  customerId: string;
  registrationCountry: string | null;
}

/** Body of `PUT /work-orders/{id}/check-in`; every field is replaced (null clears it). */
export interface CheckInUpdateRequest {
  mileageIn: number | null;
  fuelLevel: number | null;
  complaint: string | null;
  damageNotes: string | null;
}

export type LineType = 'LABOR' | 'PART';

export interface WorkOrderLine {
  id: string;
  position: number;
  type: LineType;
  description: string;
  quantity: number;
  unitPriceCents: number | null;
  discountPercent: number | null;
  vatRate: number | null;
  netCents: number | null;
  /** The catalogue service the line was added from; null for a line typed by hand. */
  serviceItemId?: string | null;
  /** The stock product a part line reserves; null for a line that does not come from stock. */
  productId?: string | null;
}

export interface WorkOrder extends PlateInfo {
  id: string;
  number: number;
  vehicleId: string;
  plate: string | null;
  status: WorkOrderStatus;
  allowedNext: WorkOrderStatus[];
  mileageIn: number | null;
  fuelLevel: number | null;
  complaint: string | null;
  damageNotes: string | null;
  assignedMechanicId: string | null;
  statusChangedAt: string;
  lines: WorkOrderLine[];
  netCents: number | null;
  vatCents: number | null;
  totalCents: number | null;
  /** True for a mechanic viewing a job that is not theirs (from the vehicle history): no actions. */
  readOnly: boolean;
  /** The latest quote, or null when none was sent. */
  quote?: WorkOrderQuote | null;
}

export interface BoardCard extends PlateInfo {
  id: string;
  number: number;
  plate: string | null;
  vehicle: string;
  assignedMechanicId: string | null;
  statusChangedAt: string;
  /** Status of the latest quote, or null when none was sent. */
  quoteStatus?: QuoteStatus | null;
}

export interface Board {
  columns: Partial<Record<WorkOrderStatus, BoardCard[]>>;
}

/** An active mechanic a work order can be given to (`GET /mechanics`). */
export interface Mechanic {
  id: string;
  name: string;
}

/** One change of a work order's mechanic. Names are null only when the user no longer exists. */
export interface MechanicChange {
  fromMechanicId: string | null;
  fromName: string | null;
  toMechanicId: string | null;
  toName: string | null;
  /** Required when a mechanic was replaced or removed; may be null for a first assignment. */
  reason: string | null;
  changedById: string;
  changedByName: string | null;
  changedAt: string;
}

/** `GET /work-orders/{id}/mechanic`: who works on it now and every change, oldest first. */
export interface MechanicAssignment {
  mechanicId: string | null;
  mechanicName: string | null;
  changes: MechanicChange[];
}
