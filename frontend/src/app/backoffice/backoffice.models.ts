import { Role, User, WorkOrderStatus } from '../core/api/models';

/** The five open work-order statuses, in board order; the dashboard always returns exactly these. */
export const OPEN_STATUSES = ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS', 'READY'] as const satisfies readonly WorkOrderStatus[];
export type OpenStatus = (typeof OPEN_STATUSES)[number];

export const ROLES: readonly Role[] = ['OWNER', 'FRONT_DESK', 'MECHANIC'];

/** A user of the workshop as `GET /users` returns it (Release 2 adds `active`). */
export interface TeamMember extends User {
  active: boolean;
  /** Release 3: true until the member opens their activation link and sets a password. */
  pending?: boolean;
  /** Optimistic locking: sent back with `PUT /users/{id}`. */
  version: number;
}

/** Release 3: no password; the response carries a one-time activation link. */
export interface InviteUserRequest {
  name: string;
  email: string;
  role: Role;
}

/** `POST /users`: the new member plus their activation link, shown only once. */
export interface Invitation extends TeamMember {
  activationPath: string;
  activationExpiresAt: string;
}

export interface ActivationLink {
  activationPath: string;
  expiresAt: string;
}

export interface UpdateUserRequest {
  role: Role;
  active: boolean;
  /** The member's version the owner read; a newer save answers 409 `stale-data`. */
  version: number;
}

export interface WorkshopSettings {
  name: string;
  /** 9 digits, or null. */
  nif: string | null;
  /** ISO 3166-1 alpha-2, upper case. */
  country: string;
  /** IANA time zone id. */
  timezone: string;
  /** Percentage 0..100 with at most 2 decimals. */
  defaultVatRate: number;
  /** Hourly labour rate in cents, ≥ 0. */
  labourRateCents: number;
  /** Days a sent quote stays open, 1..90 (default 15). */
  quoteValidDays: number;
  /** READY to DELIVERED needs the delivery checklist complete. Left out of a PUT (or null) keeps the stored value. */
  deliveryChecklistRequired?: boolean;
  /**
   * What one hour of labour costs the workshop, in cents (for the report margins; not the price). GET always answers it,
   * null = not set. On a PUT left out keeps the stored value; use `clearLabourCost` to unset it.
   */
  labourCostPerHourCents?: number | null;
  /** Customer retention window of the KPI report, 1..60 months (default 12). On a PUT left out keeps the stored value. */
  retentionMonths?: number;
  /** PUT only, never in a response: set the labour cost back to unset. Never sent together with a value. */
  clearLabourCost?: boolean;
  /** Workshop version (shared with the theme); a newer save answers 409 `stale-data`. */
  version: number;
}

export interface MechanicWorkload {
  mechanicId: string;
  name: string;
  openOrders: number;
}

export interface Dashboard {
  /** `YYYY-MM` in the workshop time zone. */
  month: string;
  openByStatus: Record<OpenStatus, number>;
  carsInShop: number;
  deliveredThisMonth: number;
  revenueThisMonth: { netCents: number; vatCents: number; grossCents: number };
  mechanicWorkload: MechanicWorkload[];
}
