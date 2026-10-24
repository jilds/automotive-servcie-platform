import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { ActivationLink, AddOnsRequest, AdminWorkshop,CreateWorkshopRequest, ProvisionedWorkshop, SubscriptionRequest } from './admin.models';

const API = '/api/v1/admin/workshops';

/** The platform admin console's calls; all of them need the PLATFORM_ADMIN role. Themes go through `IdentityApi`. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  workshops() {
    return this.http.get<AdminWorkshop[]>(API);
  }
  workshop(id: string) {
    return this.http.get<AdminWorkshop>(`${API}/${id}`);
  }
  create(body: CreateWorkshopRequest) {
    return this.http.post<ProvisionedWorkshop>(API, body);
  }
  changeSubscription(id: string, body: SubscriptionRequest) {
    return this.http.put<AdminWorkshop>(`${API}/${id}/subscription`, body);
  }
  changeAddOns(id: string, body: AddOnsRequest) {
    return this.http.put<AdminWorkshop>(`${API}/${id}/add-ons`, body);
  }
  reissueOwnerLink(id: string) {
    return this.http.post<ActivationLink>(`${API}/${id}/owner-activation-link`, null);
  }
}
