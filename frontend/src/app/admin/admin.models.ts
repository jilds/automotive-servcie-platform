export const PLANS = ['STARTER', 'PRO', 'ENTERPRISE'] as const;
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
  createdAt: string;
  ownerEmail: string | null;
  users: number;
  /** Optimistic locking: sent back with subscription and theme changes. */
  version: number;
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
