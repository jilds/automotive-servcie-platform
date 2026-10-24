import { Routes } from '@angular/router';

import { textsFor } from '../core/i18n/i18n.service';
import { AdminLayout } from './admin-layout';

/** Child routes of `/admin` (lazy-loaded, PLATFORM_ADMIN only; the guard sits on the parent route). */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayout,
    // The console reuses backoffice buttons and the identity pieces (branding, activation links).
    resolve: { texts: textsFor('admin', 'backoffice', 'identity') },
    children: [
      { path: '', loadComponent: () => import('./workshop-list').then((m) => m.WorkshopList) },
      { path: 'workshops/:id', loadComponent: () => import('./workshop-detail').then((m) => m.WorkshopDetail) },
    ],
  },
];
