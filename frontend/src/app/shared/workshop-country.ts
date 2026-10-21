import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';

import { AuthService } from '../core/auth/auth.service';

/**
 * The workshop's country, the default registration country of new vehicles. `load()` reads it once from
 * `GET /workshop/settings` when the user's role may (owner, front desk); otherwise, or on an error, it stays `PT`.
 */
@Injectable({ providedIn: 'root' })
export class WorkshopCountry {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly value = signal('PT');
  /** Tenant the country was loaded for, so signing in to another workshop reloads it. */
  private loadedFor: string | null = null;

  /** The country code: `PT` until the settings have loaded. */
  readonly country = this.value.asReadonly();

  load(): void {
    const tenant = this.auth.user()?.tenantId ?? null;
    if (!tenant || tenant === this.loadedFor || !this.auth.hasRole('OWNER', 'FRONT_DESK')) return;
    this.loadedFor = tenant;
    this.value.set('PT');
    this.http.get<{ country: string | null }>('/api/v1/workshop/settings').subscribe({
      next: (s) => {
        if (s.country) this.value.set(s.country.toUpperCase());
      },
      error: () => {},
    });
  }
}
