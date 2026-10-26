// The proposal form: client checks, then a JSON POST to the backend, and the translated states
// (sending, thank you, field errors, privacy, too many requests, network or other error).
import {
  type FieldRule,
  LEAD_FIELDS,
  type LeadErrors,
  type LeadField,
  type LeadInput,
  type LeadPayload,
  fromServerErrors,
  retryAfterMinutes,
  toPayload,
  validate,
} from './lead-validation';

type Messages = Record<FieldRule, string> & {
  summary: string;
  submit: string;
  sending: string;
  rateLimited: string;
  rateLimitedLater: string;
  network: string;
  generic: string;
};

interface Problem {
  code?: unknown;
  errors?: unknown;
}

const API_ORIGIN = (import.meta.env.PUBLIC_API_ORIGIN ?? '').replace(/\/+$/, '');
const ENDPOINT = `${API_ORIGIN}/api/v1/public-site/leads`;

function setup(form: HTMLFormElement): void {
  const messages = JSON.parse(form.dataset['messages'] ?? '{}') as Messages;
  const summary = form.querySelector<HTMLElement>('[data-lead-summary]');
  const submit = form.querySelector<HTMLButtonElement>('button[type="submit"]');
  const submitLabel = submit?.querySelector<HTMLElement>('[data-label]') ?? null;
  const thanks = document.querySelector<HTMLElement>('[data-lead-thanks]');
  if (!summary || !submit || !submitLabel || !thanks) return;

  const text = (name: string): string => {
    const field = form.elements.namedItem(name);
    return field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement ? field.value : '';
  };
  const checked = (name: string): boolean => {
    const field = form.elements.namedItem(name);
    return field instanceof HTMLInputElement && field.checked;
  };
  const radio = (name: string): string =>
    form.querySelector<HTMLInputElement>(`input[name="${name}"]:checked`)?.value ?? '';

  const read = (): LeadInput => ({
    name: text('name'),
    workshopName: text('workshopName'),
    phone: text('phone'),
    email: text('email'),
    district: text('district'),
    vehicleType: radio('vehicleType'),
    message: text('message'),
    privacyAccepted: checked('privacyAccepted'),
    marketingConsent: checked('marketingConsent'),
    website: text('website'),
  });

  const control = (field: LeadField): HTMLElement | null =>
    field === 'vehicleType'
      ? form.querySelector<HTMLElement>('input[name="vehicleType"]')
      : form.querySelector<HTMLElement>(`#lead-${field}`);

  const invalidTarget = (field: LeadField): HTMLElement | null =>
    field === 'vehicleType' ? form.querySelector<HTMLElement>('#lead-vehicleType') : control(field);

  const showMessage = (message: string): void => {
    summary.textContent = message;
    summary.focus();
  };

  const clear = (): void => {
    summary.textContent = '';
    for (const field of LEAD_FIELDS) {
      invalidTarget(field)?.removeAttribute('aria-invalid');
      const error = form.querySelector<HTMLElement>(`#lead-${field}-error`);
      if (error) error.textContent = '';
    }
  };

  const showErrors = (errors: LeadErrors): void => {
    let first: LeadField | undefined;
    for (const field of LEAD_FIELDS) {
      const rule = errors[field];
      if (!rule) continue;
      first ??= field;
      invalidTarget(field)?.setAttribute('aria-invalid', 'true');
      const error = form.querySelector<HTMLElement>(`#lead-${field}-error`);
      if (error) error.textContent = messages[rule];
    }
    summary.textContent = messages.summary;
    if (first) control(first)?.focus();
  };

  const busy = (on: boolean): void => {
    submit.disabled = on;
    submitLabel.textContent = on ? messages.sending : messages.submit;
    form.setAttribute('aria-busy', String(on));
  };

  const done = (): void => {
    form.hidden = true;
    thanks.hidden = false;
    thanks.focus();
  };

  const handleProblem = async (response: Response, payload: LeadPayload): Promise<void> => {
    if (response.status === 429) {
      const minutes = retryAfterMinutes(response.headers.get('Retry-After'));
      showMessage(
        minutes === undefined ? messages.rateLimitedLater : messages.rateLimited.replace('{minutes}', String(minutes)),
      );
      return;
    }
    let problem: Problem = {};
    try {
      problem = (await response.json()) as Problem;
    } catch {
      // Not a ProblemDetail body: treated as an unknown error below.
    }
    if (response.status === 400 && problem.code === 'privacy-consent-required') {
      showErrors({ privacyAccepted: 'privacy' });
      return;
    }
    if (response.status === 400 && problem.code === 'validation' && problem.errors && typeof problem.errors === 'object') {
      const errors = fromServerErrors(problem.errors as Record<string, unknown>, payload);
      if (Object.keys(errors).length > 0) {
        showErrors(errors);
        return;
      }
    }
    showMessage(messages.generic);
  };

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (submit.disabled) return;
    clear();
    const payload = toPayload(read());
    const errors = validate(payload);
    if (Object.keys(errors).length > 0) {
      showErrors(errors);
      return;
    }
    busy(true);
    void (async () => {
      try {
        const response = await fetch(ENDPOINT, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/problem+json, application/json' },
          body: JSON.stringify(payload),
          credentials: 'omit',
          referrerPolicy: 'no-referrer',
        });
        if (response.ok) {
          done();
          return;
        }
        await handleProblem(response, payload);
      } catch {
        showMessage(messages.network);
      } finally {
        busy(false);
      }
    })();
  });

  // The button stays disabled until this script runs, so the form never submits without it.
  submit.disabled = false;
}

document.querySelectorAll<HTMLFormElement>('form[data-lead-form]').forEach(setup);
