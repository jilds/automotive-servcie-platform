import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** A workshop's public pages (ADR 0004): no token is ever sent there, even when someone is signed in. */
const PUBLIC_API = '/api/v1/public/';

/** True for API calls that carry the access token: every `/api/` call except the public workshop pages. */
export function sendsToken(url: string): boolean {
  return url.startsWith('/api/') && !url.startsWith(PUBLIC_API);
}

/**
 * Adds the access token to API calls (not to the public workshop pages) and sends the user to login when it expires
 * or is revoked. A token of a suspended workshop (401 with code `workshop-suspended`) lands on the login page with that
 * reason shown.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = sendsToken(req.url) ? auth.token : null;
  const authed = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token) {
        auth.logout(err.error?.code === 'workshop-suspended' ? 'workshop-suspended' : undefined);
      }
      return throwError(() => err);
    }),
  );
};
