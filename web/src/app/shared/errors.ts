import { HttpErrorResponse } from '@angular/common/http';

/** Turns an API error (RFC 9457 problem detail) into a short message for the user. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Cannot reach the server.';
    return err.error?.detail ?? err.error?.title ?? `Request failed (${err.status}).`;
  }
  return 'Something went wrong.';
}
