/** English texts of the sign-in page and the identity error codes. Eager: spread into `messages.en.ts`. */
export const identityCoreEn = {
  'login.subtitleBranded': 'Sign in to {name}',
  'login.poweredBy': 'with ASP',
  'login.contactTitle': 'Want ASP for your workshop?',
  'login.contactBody': 'ASP is sold by subscription. Contact us and we set everything up for you.',
  'error.workshop-suspended': 'This workshop’s subscription is suspended. Contact the ASP team.',
  'error.workshop-not-found': 'Workshop not found.',
  'error.activation-invalid': 'This activation link is no longer valid. Ask for a new one.',
  'error.activation-expired': 'This activation link has expired. Ask for a new one.',
  'error.already-activated': 'This user has already activated their account.',
  'error.slug-taken': 'Another workshop already uses this address.',
  'error.logo-too-large': 'The logo must be at most 512 KB.',
  'error.logo-unsupported': 'The logo must be a PNG, SVG or WEBP image.',
  'error.logo-unsafe': 'This SVG contains scripts, links or other active content. Export it as a plain drawing or use PNG.',
  'error.logo-not-found': 'This workshop has no logo.',
} as const;

export type IdentityCoreKey = keyof typeof identityCoreEn;
