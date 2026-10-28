import { Routes } from '@angular/router';

import { adminGuard, authGuard, roleGuard } from './core/auth/guards';
import { textsFor } from './core/i18n/i18n.service';
import { Login } from './identity/login';
import { Shell } from './shared/shell';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'w/:slug', component: Login },
  // Public booking pages of a workshop (add-on `booking`, ADR 0004): no sign-in, lazy.
  {
    path: 'w/:slug/book',
    resolve: { texts: textsFor('booking') },
    loadComponent: () => import('./booking/public-booking').then((m) => m.PublicBooking),
  },
  {
    path: 'w/:slug/booking/:token',
    resolve: { texts: textsFor('booking') },
    loadComponent: () => import('./booking/booking-status').then((m) => m.BookingStatusPage),
  },
  // Public shop pages of a workshop (add-on `shop`): no sign-in, lazy.
  {
    path: 'w/:slug/shop',
    resolve: { texts: textsFor('shop') },
    loadComponent: () => import('./shop/public-shop').then((m) => m.PublicShop),
  },
  {
    path: 'w/:slug/shop/:token',
    resolve: { texts: textsFor('shop') },
    loadComponent: () => import('./shop/shop-order-status').then((m) => m.ShopOrderStatusPage),
  },
  // Public quote page (core feature, no add-on): the customer approves or rejects on a private link; lazy.
  {
    path: 'w/:slug/quote/:token',
    resolve: { texts: textsFor('quote') },
    loadComponent: () => import('./workshop/public-quote').then((m) => m.PublicQuotePage),
  },
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
        // ?booking=<id> shows the booking's number and services.
        resolve: { texts: textsFor('booking') },
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
        resolve: { texts: textsFor('catalogue', 'quote') },
        loadComponent: () => import('./workshop/work-order-detail').then((m) => m.WorkOrderDetail),
      },
      {
        path: 'stock',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        resolve: { texts: textsFor('stock') },
        loadChildren: () => import('./stock/stock.routes').then((m) => m.STOCK_ROUTES),
      },
      {
        path: 'bookings',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        resolve: { texts: textsFor('booking') },
        loadComponent: () => import('./booking/bookings-page').then((m) => m.BookingsPage),
      },
      {
        path: 'shop-orders',
        canActivate: [roleGuard('OWNER', 'FRONT_DESK')],
        resolve: { texts: textsFor('shop') },
        loadComponent: () => import('./shop/shop-orders-page').then((m) => m.ShopOrdersPage),
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
