import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { Dashboard, InviteUserRequest, TeamMember, UpdateUserRequest, WorkshopSettings } from './backoffice.models';

const API = '/api/v1';

/** HTTP calls of the owner backoffice (dashboard, team, workshop settings). All of them need the OWNER role. */
@Injectable({ providedIn: 'root' })
export class BackofficeApi {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<Dashboard>(`${API}/dashboard`);
  }

  users() {
    return this.http.get<TeamMember[]>(`${API}/users`);
  }
  inviteUser(body: InviteUserRequest) {
    return this.http.post<TeamMember>(`${API}/users`, body);
  }
  updateUser(id: string, body: UpdateUserRequest) {
    return this.http.put<TeamMember>(`${API}/users/${id}`, body);
  }

  settings() {
    return this.http.get<WorkshopSettings>(`${API}/workshop/settings`);
  }
  saveSettings(body: WorkshopSettings) {
    return this.http.put<WorkshopSettings>(`${API}/workshop/settings`, body);
  }
}
