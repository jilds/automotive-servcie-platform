import { Routes } from '@angular/router';

import { StockLayout } from './stock-layout';

/** Child routes of `/stock` (lazy-loaded, owner and front desk; the role guard sits on the parent route). */
export const STOCK_ROUTES: Routes = [
  {
    path: '',
    component: StockLayout,
    children: [
      { path: '', loadComponent: () => import('./products-page').then((m) => m.ProductsPage) },
      { path: 'purchase-orders', loadComponent: () => import('./purchase-orders-page').then((m) => m.PurchaseOrdersPage) },
    ],
  },
];
