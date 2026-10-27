import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  OrderPlaced,
  PaymentMethod,
  PublicOrderRequest,
  PublicProduct,
  PublicShopOrder,
  SEARCH_MAX,
  ShopOrder,
  ShopOrderStatus,
  ShopOrderSummary,
  ShopSettings,
} from './shop.models';

const API = '/api/v1';

/** The public shop API of one workshop: no token (`sendsToken` skips `/api/v1/public/`), found by its slug. */
function publicBase(slug: string): string {
  return `${API}/public/${encodeURIComponent(slug)}/shop`;
}

/** Online shop: the public page, the front desk's orders and the owner's settings. */
@Injectable({ providedIn: 'root' })
export class ShopApi {
  private readonly http = inject(HttpClient);

  // Public (no token)
  /** Offered products by name, at most 200; `q` (at most 60 characters) matches name, brand or SKU. */
  products(slug: string, q = '') {
    const query = q.trim().slice(0, SEARCH_MAX);
    const params = query ? new HttpParams().set('q', query) : undefined;
    return this.http.get<PublicProduct[]>(`${publicBase(slug)}/products`, { params });
  }
  placeOrder(slug: string, body: PublicOrderRequest) {
    return this.http.post<OrderPlaced>(`${publicBase(slug)}/orders`, body);
  }
  publicOrder(slug: string, token: string) {
    return this.http.get<PublicShopOrder>(`${publicBase(slug)}/orders/${encodeURIComponent(token)}`);
  }
  cancelPublic(slug: string, token: string) {
    return this.http.post<PublicShopOrder>(`${publicBase(slug)}/orders/${encodeURIComponent(token)}/cancel`, {});
  }

  // Workshop (owner, front desk)
  /** Newest first; `from` / `to` are placement days `YYYY-MM-DD` (server default: the last 30 days). */
  orders(filter: { status?: ShopOrderStatus; from?: string; to?: string } = {}) {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) if (value) params = params.set(key, value);
    return this.http.get<ShopOrderSummary[]>(`${API}/shop-orders`, { params });
  }
  order(id: string) {
    return this.http.get<ShopOrder>(`${API}/shop-orders/${encodeURIComponent(id)}`);
  }
  /** Orders still PLACED (the dashboard tile). */
  pendingCount() {
    return this.http.get<{ count: number }>(`${API}/shop-orders/pending-count`);
  }
  markReady(id: string, version: number) {
    return this.http.post<ShopOrder>(`${API}/shop-orders/${encodeURIComponent(id)}/ready`, { version });
  }
  collect(id: string, paymentMethod: PaymentMethod, version: number) {
    return this.http.post<ShopOrder>(`${API}/shop-orders/${encodeURIComponent(id)}/collect`, { paymentMethod, version });
  }
  cancel(id: string, reason: string, version: number) {
    return this.http.post<ShopOrder>(`${API}/shop-orders/${encodeURIComponent(id)}/cancel`, { reason, version });
  }

  settings() {
    return this.http.get<ShopSettings>(`${API}/shop/settings`);
  }
  saveSettings(body: ShopSettings) {
    return this.http.put<ShopSettings>(`${API}/shop/settings`, body);
  }
}
