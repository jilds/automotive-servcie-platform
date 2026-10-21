import { Routes } from '@angular/router';

import { BackofficeLayout } from './backoffice-layout';
import { unsavedChangesGuard } from './unsaved-changes.guard';

/** Child routes of `/backoffice` (lazy-loaded, OWNER only; the role guard sits on the parent route). */
export const BACKOFFICE_ROUTES: Routes = [
  {
    path: '',
    component: BackofficeLayout,
    children: [
      { path: '', loadComponent: () => import('./dashboard').then((m) => m.Dashboard) },
      { path: 'team', loadComponent: () => import('./team').then((m) => m.Team) },
      {
        path: 'settings',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./settings').then((m) => m.Settings),
      },
    ],
  },
];
