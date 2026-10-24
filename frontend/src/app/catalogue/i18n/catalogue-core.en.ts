/** English error codes of the catalogue (also shown on the work order page). Eager: spread into `messages.en.ts`. */
export const catalogueCoreEn = {
  'error.service-not-found': 'That service is not in the catalogue.',
  'error.package-not-found': 'That package is not in the catalogue.',
  'error.service-inactive': 'That service is no longer offered.',
  'error.package-inactive': 'That package is no longer offered.',
  'error.package-empty': 'A package needs at least one service.',
} as const;

export type CatalogueCoreKey = keyof typeof catalogueCoreEn;
