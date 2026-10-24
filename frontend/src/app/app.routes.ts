import { Routes } from '@angular/router';

import { adminGuard, authGuard, roleGuard } from './core/auth/guards';
import { textsFor } from './core/i18n/i18n.service';
import { Login } from './identity/login';
import { Shell } from './shared/shell';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'w/:slug', component: Login },
  {
    path: 'activate',
    resolve: { texts: textsFor('identity') },
    loadComponent: () => import('./identity/activate').then((m) => m.Activate),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadChildren: () => import('./admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'board' },
      { path: 'board', loadComponent: () => import('./workshop/board').then((m) => m.Board) },
      {
        path: 'check-in',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        loadComponent: () => import('./workshop/check-in').then((m) => m.CheckIn),
      },
      {
        path: 'customers',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        loadComponent: () => import('./customer/customer-list').then((m) => m.CustomerList),
      },
      {
        path: 'customers/:id',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        loadComponent: () => import('./customer/customer-detail').then((m) => m.CustomerDetail),
      },
      { path: 'vehicles/:id', loadComponent: () => import('./workshop/vehicle-history').then((m) => m.VehicleHistory) },
      {
        path: 'work-orders/:id',
        resolve: { texts: textsFor('catalogue') },
        loadComponent: () => import('./workshop/work-order-detail').then((m) => m.WorkOrderDetail),
      },
      {
        path: 'stock',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        resolve: { texts: textsFor('stock') },
        loadChildren: () => import('./stock/stock.routes').then((m) => m.STOCK_ROUTES),
      },
      {
        path: 'backoffice',
        canActivate: [roleGuard('OWNER')],
        loadChildren: () => import('./backoffice/backoffice.routes').then((m) => m.BACKOFFICE_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
