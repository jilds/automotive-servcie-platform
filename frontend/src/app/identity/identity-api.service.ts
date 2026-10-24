import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { Branding } from '../core/api/models';

const API = '/api/v1';

/** How a workshop reaches the ASP team (`GET /platform/contact`); both null when not configured. */
export interface PlatformContact {
  email: string | null;
  phone: string | null;
}

export interface ActivationPreview {
  email: string;
  name: string;
  workshopName: string;
  expiresAt: string;
}

/** Body of `PUT /workshop/branding`; the platform admin variant adds `slug`. Null falls back to the ASP default. */
export interface BrandingRequest {
  displayName: string | null;
  primaryColor: string | null;
  accentColor: string | null;
  slug?: string | null;
  /** The workshop version the editor read (optimistic locking). */
  version: number;
}

/** Whose theme an editor changes: the signed-in owner's workshop, or one the platform admin chose. */
export type BrandingTarget = { kind: 'own' } | { kind: 'workshop'; id: string };

/** Public identity endpoints and workshop themes. */
@Injectable({ providedIn: 'root' })
export class IdentityApi {
  private readonly http = inject(HttpClient);

  contact() {
    return this.http.get<PlatformContact>(`${API}/platform/contact`);
  }
  activation(token: string) {
    return this.http.get<ActivationPreview>(`${API}/auth/activation`, { params: { token } });
  }

  /** Public theme of a branded login page. */
  brandingBySlug(slug: string) {
    return this.http.get<Branding>(`${API}/branding/${encodeURIComponent(slug)}`);
  }
  /** The signed-in user's workshop theme. */
  currentBranding() {
    return this.http.get<Branding>(`${API}/branding`);
  }

  branding(target: BrandingTarget) {
    return target.kind === 'own' ? this.currentBranding() : this.http.get<Branding>(`${base(target)}`);
  }
  saveBranding(target: BrandingTarget, body: BrandingRequest) {
    return this.http.put<Branding>(base(target), body);
  }
  uploadLogo(target: BrandingTarget, file: Blob) {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<Branding>(`${base(target)}/logo`, form);
  }
  removeLogo(target: BrandingTarget) {
    return this.http.delete<Branding>(`${base(target)}/logo`);
  }
}

function base(target: BrandingTarget): string {
  return target.kind === 'own' ? `${API}/workshop/branding` : `${API}/admin/workshops/${target.id}/branding`;
}
