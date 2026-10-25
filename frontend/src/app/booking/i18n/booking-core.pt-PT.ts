import { BookingCoreKey } from './booking-core.en';

/** Portuguese (Portugal) booking texts shown outside the booking routes, and the error codes. */
export const bookingCorePtPT: Record<BookingCoreKey, string> = {
  'nav.bookings': 'Marcações',
  'error.slot-unavailable': 'Essa hora já não está livre. Escolha outra.',
  'error.booking-not-found': 'Marcação não encontrada.',
  'error.booking-not-confirmed': 'Só se dá entrada a uma marcação confirmada.',
  'error.booking-cancel-too-late': 'Já não é possível cancelar online. Contacte a oficina.',
  'error.booking-status': 'A marcação já não pode mudar desta forma.',
  'error.invalid-opening-hours': 'Reveja o horário: cada período fecha depois de abrir e não se sobrepõe a outro.',
  'error.decline-reason-required': 'Indique o motivo da recusa.',
};
