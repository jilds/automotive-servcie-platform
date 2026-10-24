import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { Role } from '../api/models';

/** Workshop screens: signed in as a workshop user. A platform admin belongs to no workshop and goes to `/admin`. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
  return !auth.isPlatformAdmin() || router.createUrlTree(['/admin']);
};

/** The platform admin console. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
  return auth.isPlatformAdmin() || router.createUrlTree(['/board']);
};

export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    return auth.hasRole(...roles) || inject(Router).createUrlTree(['/board']);
  };
