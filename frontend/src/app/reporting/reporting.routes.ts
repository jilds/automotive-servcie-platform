import { Routes } from '@angular/router';

import { textsFor } from '../core/i18n/i18n.service';
import { ReportsShell } from './reports-shell';

/**
 * Child routes of `/backoffice/reports` (lazy-loaded under the backoffice, whose parent route holds the OWNER guard).
 * The report texts load with the shell; the tabs are lazy too.
 */
export const REPORT_ROUTES: Routes = [
  {
    path: '',
    component: ReportsShell,
    resolve: { texts: textsFor('reports') },
    children: [
      { path: '', loadComponent: () => import('./cost-vs-quote-page').then((m) => m.CostVsQuotePage) },
      { path: 'productivity', loadComponent: () => import('./productivity-page').then((m) => m.ProductivityPage) },
      { path: 'kpis', loadComponent: () => import('./kpis-page').then((m) => m.KpisPage) },
    ],
  },
];
