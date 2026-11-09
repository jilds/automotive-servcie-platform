/**
 * The commercial offer and the price list are product decisions that are still open (plan 11). Nothing on the
 * site may promise a trial, "no lock-in" or free migration until the owner sets the values here: while a value is
 * null the pages show a marked TODO placeholder instead, and the production build check (scripts/dist-check.mjs)
 * refuses to ship it.
 */
export interface Offer {
  /** Days of free use, or null while undecided. */
  trialDays: number | null;
  /** True when the contract has no lock-in, null while undecided. */
  noLockIn: boolean | null;
  /** True when data migration is free of charge, null while undecided. */
  migrationIncluded: boolean | null;
}

export const offer: Offer = { trialDays: null, noLockIn: null, migrationIncluded: null };

/** True once the price list exists: then the pricing page shows the monthly / annual switch and the annual prices. */
export const pricesKnown = false;
