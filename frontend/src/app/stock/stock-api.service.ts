import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { WorkOrder } from '../core/api/models';
import { Movement, Product, ProductRequest, PurchaseOrder, PurchaseOrderRequest, PurchaseOrderStatus } from './stock.models';

const API = '/api/v1/stock';

/** Stock: products, adjustments, movements, purchase orders, and parts from stock on a work order. */
@Injectable({ providedIn: 'root' })
export class StockApi {
  private readonly http = inject(HttpClient);

  products(q?: string, options: { activeOnly?: boolean; lowOnly?: boolean } = {}) {
    let params = new HttpParams()
      .set('activeOnly', String(options.activeOnly ?? true))
      .set('lowOnly', String(options.lowOnly ?? false));
    if (q?.trim()) params = params.set('q', q.trim());
    return this.http.get<Product[]>(`${API}/products`, { params });
  }
  product(id: string) {
    return this.http.get<Product>(`${API}/products/${id}`);
  }
  createProduct(body: ProductRequest) {
    return this.http.post<Product>(`${API}/products`, body);
  }
  updateProduct(id: string, body: ProductRequest) {
    return this.http.put<Product>(`${API}/products/${id}`, body);
  }
  /** Positive quantity adds, negative takes away; the reason is required. */
  adjust(id: string, quantity: number, reason: string) {
    return this.http.post<Product>(`${API}/products/${id}/adjustments`, { quantity, reason });
  }
  movements(id: string) {
    return this.http.get<Movement[]>(`${API}/products/${id}/movements`);
  }
  lowStock() {
    return this.http.get<Product[]>(`${API}/low-stock`);
  }

  purchaseOrders(status?: PurchaseOrderStatus) {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<PurchaseOrder[]>(`${API}/purchase-orders`, { params });
  }
  purchaseOrder(id: string) {
    return this.http.get<PurchaseOrder>(`${API}/purchase-orders/${id}`);
  }
  createPurchaseOrder(body: PurchaseOrderRequest) {
    return this.http.post<PurchaseOrder>(`${API}/purchase-orders`, body);
  }
  updatePurchaseOrder(id: string, body: PurchaseOrderRequest) {
    return this.http.put<PurchaseOrder>(`${API}/purchase-orders/${id}`, body);
  }
  markOrdered(id: string) {
    return this.http.post<PurchaseOrder>(`${API}/purchase-orders/${id}/order`, {});
  }
  receive(id: string) {
    return this.http.post<PurchaseOrder>(`${API}/purchase-orders/${id}/receive`, {});
  }

  /** Adds a part from stock to a work order at its sale price and reserves it. */
  addToWorkOrder(workOrderId: string, productId: string, quantity: number) {
    return this.http.post<WorkOrder>(`/api/v1/work-orders/${workOrderId}/lines/from-stock`, { productId, quantity });
  }
}
