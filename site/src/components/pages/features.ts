import type { MessageKey } from '../../i18n';
import type { IconName } from '../Icon.astro';

export interface Feature {
  icon: IconName;
  title: MessageKey;
  text: MessageKey;
}

/** What ASP does today (home page and features page). */
export const available: readonly Feature[] = [
  { icon: 'car', title: 'feature.checkin.title', text: 'feature.checkin.text' },
  { icon: 'board', title: 'feature.board.title', text: 'feature.board.text' },
  { icon: 'list', title: 'feature.catalogue.title', text: 'feature.catalogue.text' },
  { icon: 'box', title: 'feature.stock.title', text: 'feature.stock.text' },
  { icon: 'calendar', title: 'feature.booking.title', text: 'feature.booking.text' },
  { icon: 'palette', title: 'feature.branding.title', text: 'feature.branding.text' },
  { icon: 'globe', title: 'feature.languages.title', text: 'feature.languages.text' },
  { icon: 'shield', title: 'feature.isolation.title', text: 'feature.isolation.text' },
  { icon: 'phone', title: 'feature.pwa.title', text: 'feature.pwa.text' },
];

/** Shown with the "Em breve" label; remove each one as it is released. */
export const comingSoon: readonly Feature[] = [
  { icon: 'receipt', title: 'feature.invoicing.title', text: 'feature.invoicing.text' },
  { icon: 'card', title: 'feature.payments.title', text: 'feature.payments.text' },
  { icon: 'cart', title: 'feature.shop.title', text: 'feature.shop.text' },
];
