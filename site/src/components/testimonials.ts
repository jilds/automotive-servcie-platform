import type { Lang } from '../i18n/routes';

/**
 * A customer's own words, used with their permission. Only real, approved quotes go here (plan 11): the
 * Testimonials component renders nothing while this list is empty. They are customer-supplied texts, so they are
 * data in the language they were given, not dictionary keys.
 */
export interface Testimonial {
  quote: Record<Lang, string>;
  author: string;
  role: Record<Lang, string>;
  workshop: string;
}

export const testimonials: readonly Testimonial[] = [];

/** Workshops that agreed to be named. `src` is a file in `public/`. Empty until a customer agrees. */
export interface CustomerLogo {
  name: string;
  src: string;
}

export const customerLogos: readonly CustomerLogo[] = [];
