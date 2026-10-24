import { FormControl, FormGroup, Validators } from '@angular/forms';

import { notBlank } from '../backoffice/settings-form';
import { nifValidator } from '../shared/nif';
import { CreateWorkshopRequest, Plan, SubscriptionStatus } from './admin.models';

const SLUG = /^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$/;

/** The "new workshop" form; same rules as `CreateWorkshopRequest` on the server, including the PT NIF check digit. */
export function workshopForm() {
  const form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(120)] }),
    nif: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{9}$/)] }),
    country: new FormControl('PT', { nonNullable: true, validators: [Validators.required] }),
    timezone: new FormControl('Europe/Lisbon', { nonNullable: true, validators: [Validators.required] }),
    plan: new FormControl<Plan>('PRO', { nonNullable: true }),
    status: new FormControl<SubscriptionStatus>('TRIAL', { nonNullable: true }),
    subscriptionUntil: new FormControl('', { nonNullable: true }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.pattern(SLUG)] }),
    ownerName: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(120)] }),
    ownerEmail: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });
  form.controls.nif.setValidators(nifValidator(() => form.controls.country.value));
  form.controls.country.valueChanges.subscribe(() => form.controls.nif.updateValueAndValidity({ emitEvent: false }));
  return form;
}

export type WorkshopForm = ReturnType<typeof workshopForm>;

/** Trims text, turns empty optional fields into null. */
export function toCreateRequest(form: WorkshopForm): CreateWorkshopRequest {
  const v = form.getRawValue();
  return {
    name: v.name.trim(),
    nif: v.nif.trim() || null,
    country: v.country,
    timezone: v.timezone,
    plan: v.plan,
    status: v.status,
    subscriptionUntil: v.subscriptionUntil || null,
    slug: v.slug.trim().toLowerCase() || null,
    ownerName: v.ownerName.trim(),
    ownerEmail: v.ownerEmail.trim(),
  };
}
