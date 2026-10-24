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
