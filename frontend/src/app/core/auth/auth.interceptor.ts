import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Adds the access token to API calls and sends the user to login when it expires or is revoked. A token of a
 * suspended workshop (401 with code `workshop-suspended`) lands on the login page with that reason shown.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;
  const authed = token && req.url.startsWith('/api/') ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token) {
        auth.logout(err.error?.code === 'workshop-suspended' ? 'workshop-suspended' : undefined);
      }
      return throwError(() => err);
    }),
  );
};
