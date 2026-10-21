import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl } from '@angular/forms';

import { en, MessageKey } from '../i18n/messages.en';

/** RFC 9457 problem detail as the API sends it (see docs/api/release-2.md, "Errors"). */
export interface Problem {
  status: number;
  type?: string;
  title?: string;
  detail?: string;
  instance?: string;
  code?: string;
  /** Validation problems only: field name (nested with dots, e.g. `address.postalCode`) to message. */
  errors?: Record<string, string>;
}

/** Error key put on a form control that the server rejected; its value is the problem code. */
export const SERVER_ERROR = 'server';

/** The problem detail inside an HTTP error, or null for anything that is not an HTTP error. */
export function problemOf(err: unknown): Problem | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const body = err.error && typeof err.error === 'object' ? (err.error as Omit<Problem, 'status'>) : {};
  return { ...body, status: err.status };
}

/** The problem `code`, such as `duplicate-plate`, or null. */
export function problemCode(err: unknown): string | null {
  return problemOf(err)?.code ?? null;
}

/**
 * Marks the form controls a problem is about, so the message shows next to the right field, and returns how many
 * controls were marked. Validation problems name their fields in `errors`; `codeFields` maps other codes to a
 * field, for example `{ 'duplicate-plate': 'plate' }`. `fieldPaths` renames API fields whose control has another
 * name, for example `{ labourRateCents: 'labourRateEuros' }`. The mark goes away as soon as the user edits the field.
 */
export function applyProblem(
  form: AbstractControl,
  err: unknown,
  codeFields: Record<string, string> = {},
  fieldPaths: Record<string, string> = {},
): number {
  const problem = problemOf(err);
  if (!problem?.code) return 0;
  const marks: [path: string, code: string][] =
    problem.code === 'validation'
      ? Object.keys(problem.errors ?? {}).map((path) => [fieldPaths[path] ?? path, 'validation'])
      : codeFields[problem.code]
        ? [[codeFields[problem.code], problem.code]]
        : [];
  let marked = 0;
  for (const [path, code] of marks) {
    const control = form.get(path);
    if (!control) continue;
    control.setErrors({ ...control.errors, [SERVER_ERROR]: code });
    control.markAsTouched();
    marked++;
  }
  return marked;
}

/** True when the problem is fully explained by field marks, so no separate alert is needed. */
export function shownOnFields(err: unknown, marked: number): boolean {
  return marked > 0 && problemCode(err) !== 'validation';
}

/**
 * The message key for a control's error, once the user has touched it: the server's verdict first, then the first
 * client validator listed in `client` (validator name to key). Null when there is nothing to show.
 */
export function fieldMessage(control: AbstractControl | null, client: Partial<Record<string, MessageKey>> = {}): MessageKey | null {
  const errors = control?.errors;
  if (!errors || !(control.touched || control.dirty)) return null;
  const server = errors[SERVER_ERROR] as string | undefined;
  if (server) {
    const key = `error.${server}`;
    return server !== 'validation' && key in en ? (key as MessageKey) : 'error.field';
  }
  for (const name of Object.keys(errors)) {
    const key = client[name];
    if (key) return key;
  }
  return null;
}
