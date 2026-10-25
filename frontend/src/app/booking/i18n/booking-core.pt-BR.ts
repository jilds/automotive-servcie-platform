import { BookingCoreKey } from './booking-core.en';

/** Portuguese (Brazil) booking texts shown outside the booking routes, and the error codes. */
export const bookingCorePtBR: Record<BookingCoreKey, string> = {
  'nav.bookings': 'Agendamentos',
  'error.slot-unavailable': 'Esse horário não está mais livre. Escolha outro.',
  'error.booking-not-found': 'Agendamento não encontrado.',
  'error.booking-not-confirmed': 'Só é possível fazer check-in de um agendamento confirmado.',
  'error.booking-cancel-too-late': 'Não é mais possível cancelar online. Entre em contato com a oficina.',
  'error.booking-status': 'O agendamento não pode mais mudar dessa forma.',
  'error.invalid-opening-hours': 'Revise o horário: cada período fecha depois de abrir e não se sobrepõe a outro.',
  'error.decline-reason-required': 'Informe o motivo da recusa.',
};
