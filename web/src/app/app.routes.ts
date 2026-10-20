import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/guards';
import { Login } from './features/auth/login';
import { Signup } from './features/auth/signup';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'signup', component: Signup },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'board' },
      { path: 'board', loadComponent: () => import('./features/board/board').then((m) => m.Board) },
      {
        path: 'check-in',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        loadComponent: () => import('./features/work-orders/check-in').then((m) => m.CheckIn),
      },
      {
        path: 'customers',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        loadComponent: () => import('./features/customers/customer-list').then((m) => m.CustomerList),
      },
      {
        path: 'customers/:id',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        loadComponent: () => import('./features/customers/customer-detail').then((m) => m.CustomerDetail),
      },
      { path: 'vehicles/:id', loadComponent: () => import('./features/work-orders/vehicle-history').then((m) => m.VehicleHistory) },
      { path: 'work-orders/:id', loadComponent: () => import('./features/work-orders/work-order-detail').then((m) => m.WorkOrderDetail) },
    ],
  },
  { path: '**', redirectTo: '' },
];
