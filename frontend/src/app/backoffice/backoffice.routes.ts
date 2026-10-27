import { Routes } from '@angular/router';

import { textsFor } from '../core/i18n/i18n.service';
import { BackofficeLayout } from './backoffice-layout';
import { unsavedChangesGuard } from './unsaved-changes.guard';

/** Child routes of `/backoffice` (lazy-loaded, OWNER only; the role guard sits on the parent route). */
export const BACKOFFICE_ROUTES: Routes = [
  {
    path: '',
    component: BackofficeLayout,
    // The catalogue page, the identity pieces (branding, activation links) and the booking and shop settings are part of the backoffice.
    resolve: { texts: textsFor('backoffice', 'catalogue', 'identity', 'booking', 'shop') },
    children: [
      { path: '', loadComponent: () => import('./dashboard').then((m) => m.Dashboard) },
      { path: 'team', loadComponent: () => import('./team').then((m) => m.Team) },
      { path: 'services', loadComponent: () => import('../catalogue/catalogue-page').then((m) => m.CataloguePage) },
      {
        path: 'booking',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('../booking/booking-settings').then((m) => m.BookingSettingsPage),
      },
      {
        path: 'shop',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('../shop/shop-settings').then((m) => m.ShopSettingsPage),
      },
      {
        path: 'settings',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./settings').then((m) => m.Settings),
      },
    ],
  },
];
