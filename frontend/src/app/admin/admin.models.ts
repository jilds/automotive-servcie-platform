import { AddOns } from '../core/api/models';

export const PLANS =['STARTER', 'PRO', 'ENTERPRISE'] as const;
export type Plan = (typeof PLANS)[number];

export const STATUSES = ['TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'] as const;
export type SubscriptionStatus = (typeof STATUSES)[number];
/** A new workshop starts on trial or active. */
export const INITIAL_STATUSES: readonly SubscriptionStatus[] = ['TRIAL', 'ACTIVE'];

/** A workshop as `GET /admin/workshops` returns it: identity and subscription only, never business data. */
export interface AdminWorkshop {
  id: string;
  name: string;
  nif: string | null;
  country: string;
  timezone: string;
  slug: string;
  displayName: string | null;
  plan: Plan;
  status: SubscriptionStatus;
  /** `YYYY-MM-DD`, or null. */
  subscriptionUntil: string | null;
  /** True once `subscriptionUntil` plus the grace days has passed: locked out like a suspended workshop. */
  expired: boolean;
  createdAt: string;
  ownerEmail: string | null;
  users: number;
  /** Online booking and the online shop, switched on by the platform admin independent of the plan (ADR 0004). */
  addOns: AddOns;
  /** Optimistic locking: sent back with subscription, add-ons and theme changes. */
  version: number;
}

/** Body of `PUT /admin/workshops/{id}/add-ons`. */
export interface AddOnsRequest extends AddOns {
  version: number;
}

/** True when the add-ons on the form differ from the stored ones. */
export function addOnsChanged(stored: AddOns, draft: AddOns): boolean {
  return stored.booking !== draft.booking || stored.shop !== draft.shop;
}

export interface CreateWorkshopRequest {
  name: string;
  nif: string | null;
  country: string;
  timezone: string;
  plan: Plan;
  status: SubscriptionStatus;
  subscriptionUntil: string | null;
  slug: string | null;
  ownerName: string;
  ownerEmail: string;
}

export interface ProvisionedWorkshop {
  workshop: AdminWorkshop;
  activationPath: string;
  activationExpiresAt: string;
}

export interface SubscriptionRequest {
  plan: Plan;
  status: SubscriptionStatus;
  subscriptionUntil: string | null;
  version: number;
}

export interface ActivationLink {
  activationPath: string;
  expiresAt: string;
}

export const STATUS_BADGE: Record<SubscriptionStatus, string> = {
  TRIAL: 'badge bg-info-subtle text-info-emphasis',
  ACTIVE: 'badge bg-success-subtle text-success-emphasis',
  SUSPENDED: 'badge bg-warning-subtle text-warning-emphasis',
  CANCELLED: 'badge bg-secondary-subtle text-secondary-emphasis',
};

export function planKey(plan: Plan): `admin.plan.${Plan}` {
  return `admin.plan.${plan}`;
}

export function statusKey(status: SubscriptionStatus): `admin.status.${SubscriptionStatus}` {
  return `admin.status.${status}`;
}
