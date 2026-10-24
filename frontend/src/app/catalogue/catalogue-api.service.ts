import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { WorkOrder } from '../core/api/models';
import { CatalogueService, CataloguePackage, PackageRequest, ServiceRequest } from './catalogue.models';

const API = '/api/v1/catalogue';

/** The service catalogue: services, packages, and adding them to a work order. */
@Injectable({ providedIn: 'root' })
export class CatalogueApi {
  private readonly http = inject(HttpClient);

  services(q?: string, activeOnly = true) {
    return this.http.get<CatalogueService[]>(`${API}/services`, { params: params(q, activeOnly) });
  }
  service(id: string) {
    return this.http.get<CatalogueService>(`${API}/services/${id}`);
  }
  createService(body: ServiceRequest) {
    return this.http.post<CatalogueService>(`${API}/services`, body);
  }
  updateService(id: string, body: ServiceRequest) {
    return this.http.put<CatalogueService>(`${API}/services/${id}`, body);
  }

  packages(q?: string, activeOnly = true) {
    return this.http.get<CataloguePackage[]>(`${API}/packages`, { params: params(q, activeOnly) });
  }
  servicePackage(id: string) {
    return this.http.get<CataloguePackage>(`${API}/packages/${id}`);
  }
  createPackage(body: PackageRequest) {
    return this.http.post<CataloguePackage>(`${API}/packages`, body);
  }
  updatePackage(id: string, body: PackageRequest) {
    return this.http.put<CataloguePackage>(`${API}/packages/${id}`, body);
  }

  /**
   * Adds a service (one line) or a package (one line per service and per product) to a work order at the catalogue
   * price; a package's products reserve stock (409 `insufficient-stock`, nothing added, when one is short).
   */
  addToWorkOrder(workOrderId: string, choice: { serviceId: string } | { packageId: string }) {
    return this.http.post<WorkOrder>(`/api/v1/work-orders/${workOrderId}/lines/from-catalogue`, choice);
  }
}

function params(q: string | undefined, activeOnly: boolean): HttpParams {
  let p = new HttpParams().set('activeOnly', String(activeOnly));
  if (q?.trim()) p = p.set('q', q.trim());
  return p;
}
