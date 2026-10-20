import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/auth/guards';
import { Login } from './identity/login';
import { Signup } from './identity/signup';
import { Shell } from './shared/shell';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'signup', component: Signup },
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
      { path: 'work-orders/:id', loadComponent: () => import('./workshop/work-order-detail').then((m) => m.WorkOrderDetail) },
    ],
  },
  { path: '**', redirectTo: '' },
];
