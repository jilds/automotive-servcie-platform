/**
 * English booking texts shown outside the booking routes: the menu entry and the error codes. Eager: spread into
 * `messages.en.ts`, so keep it small (initial bundle budget).
 */
export const bookingCoreEn = {
  'nav.bookings': 'Bookings',
  'error.slot-unavailable': 'That time is no longer free. Choose another one.',
  'error.booking-not-found': 'Booking not found.',
  'error.booking-not-confirmed': 'Only a confirmed booking can be checked in.',
  'error.booking-cancel-too-late': 'It is too late to cancel online. Contact the workshop.',
  'error.booking-status': 'The booking can no longer change this way.',
  'error.invalid-opening-hours': 'Check the opening hours: each range must close after it opens and not overlap another.',
  'error.decline-reason-required': 'Say why the booking is declined.',
} as const;

export type BookingCoreKey = keyof typeof bookingCoreEn;
