import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AccountRole, Role, SessionUser, TokenResponse } from '../api/models';

/** A signed-in workshop user: always a workshop and a workshop role. */
export type WorkshopUser = SessionUser & { tenantId: string; role: Role };

const STORAGE_KEY = 'asp.session';

interface Session {
  token: string;
  expiresAt: number;
  user: SessionUser;
}

/** Why the user was signed out, shown on the login page (`/login?reason=…`). */
export type SignOutReason = 'workshop-suspended';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  /** Whoever is signed in, platform admin included. */
  readonly account = computed(() => this.session()?.user ?? null);
  /** The signed-in workshop user; null for a platform admin, who belongs to no workshop. */
  readonly user = computed<WorkshopUser | null>(() => {
    const account = this.account();
    return account && account.role !== 'PLATFORM_ADMIN' && account.tenantId ? (account as WorkshopUser) : null;
  });
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isPlatformAdmin = computed(() => this.account()?.role === 'PLATFORM_ADMIN');
  readonly canSeePrices = computed(() => this.account()?.role !== 'MECHANIC');

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: AccountRole[]): boolean {
    const role = this.account()?.role;
    return role !== undefined && roles.includes(role);
  }

  /** The workshop role, or null for a platform admin or nobody. */
  workshopRole(): Role | null {
    return this.user()?.role ?? null;
  }

  /** Where a signed-in account starts: the console for platform admins, the board for workshop users. */
  homeUrl(): string {
    return this.isPlatformAdmin() ? '/admin' : '/board';
  }

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(tap((t) => this.store(t)));
  }

  /** Opens an activation link: sets name and password and signs in. */
  activate(token: string, name: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/activate', { token, name, password }).pipe(tap((t) => this.store(t)));
  }

  logout(reason?: SignOutReason): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable */
    }
    this.router.navigate(['/login'], reason ? { queryParams: { reason } } : {});
  }

  private store(t: TokenResponse): void {
    const session: Session = { token: t.accessToken, expiresAt: Date.now() + t.expiresIn * 1000, user: t.user };
    this.session.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      /* storage unavailable: session lasts until reload */
    }
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as Session;
      return session.expiresAt > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
