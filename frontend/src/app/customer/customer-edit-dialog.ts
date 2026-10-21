import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { Customer, CustomerRequest } from '../core/api/models';
import { applyProblem, fieldMessage, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { addressForm, COUNTRIES, toAddress } from './address';

/** Modal form that edits a customer's contact details (PUT /customers/{id}). Open it with `open(customer)`. */
@Component({
  selector: 'app-customer-edit-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="customer-edit-title">
      <div class="modal-dialog">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="customer-edit-title">{{ 'customers.edit' | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            <div class="row g-3">
            @if (error()) {
              <app-alert class="col-12 mb-0">{{ error() }}</app-alert>
            }
            <div class="col-12">
              <label class="form-label" for="ce-name">{{ 'common.name' | t }}</label>
              <input id="ce-name" class="form-control" formControlName="name" autocomplete="name" [class.is-invalid]="!!message('name')" [attr.aria-invalid]="!!message('name')" />
              @if (message('name'); as m) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              }
            </div>
            <div class="col-sm-6">
              <label class="form-label" for="ce-nif">{{ 'common.nif' | t }}</label>
              <input id="ce-nif" class="form-control" formControlName="nif" inputmode="numeric" maxlength="9" [class.is-invalid]="!!message('nif')" [attr.aria-invalid]="!!message('nif')" />
              @if (message('nif'); as m) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              }
            </div>
            <div class="col-sm-6">
              <label class="form-label" for="ce-phone">{{ 'common.phone' | t }}</label>
              <input id="ce-phone" class="form-control" formControlName="phone" type="tel" autocomplete="tel" [class.is-invalid]="!!message('phone')" [attr.aria-invalid]="!!message('phone')" />
              @if (message('phone'); as m) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              }
            </div>
            <div class="col-12">
              <label class="form-label" for="ce-email">{{ 'common.email' | t }}</label>
              <input id="ce-email" class="form-control" formControlName="email" type="email" autocomplete="email" [class.is-invalid]="!!message('email')" [attr.aria-invalid]="!!message('email')" />
              @if (message('email'); as m) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
              }
            </div>
            <div class="col-12">
              <fieldset class="border rounded-3 p-3" formGroupName="address">
                <legend class="float-none w-auto px-2 mb-0 small fw-semibold text-body-secondary">{{ 'address.title' | t }}</legend>
                <div class="row g-3">
                  <div class="col-12">
                    <label class="form-label" for="ce-street">{{ 'address.street' | t }}</label>
                    <input id="ce-street" class="form-control" formControlName="street" autocomplete="address-line1" />
                  </div>
                  <div class="col-sm-6">
                    <label class="form-label" for="ce-detail">{{ 'address.detail' | t }}</label>
                    <input id="ce-detail" class="form-control" formControlName="detail" autocomplete="address-line2" />
                  </div>
                  <div class="col-sm-6">
                    <label class="form-label" for="ce-postal">{{ 'address.postalCode' | t }}</label>
                    <input id="ce-postal" class="form-control" formControlName="postalCode" autocomplete="postal-code" [placeholder]="'address.postalCodeHint' | t" maxlength="10"
                      [class.is-invalid]="form.controls.address.hasError('postalCode')" />
                  </div>
                  <div class="col-sm-6">
                    <label class="form-label" for="ce-locality">{{ 'address.locality' | t }}</label>
                    <input id="ce-locality" class="form-control" formControlName="locality" autocomplete="address-level2" />
                  </div>
                  <div class="col-sm-6">
                    <label class="form-label" for="ce-country">{{ 'address.country' | t }}</label>
                    <select id="ce-country" class="form-select" formControlName="country" autocomplete="country">
                      @for (c of countries(); track c.code) {
                        <option [value]="c.code">{{ c.name }}</option>
                      }
                    </select>
                  </div>
                  @if (form.controls.address.hasError('postalCode')) {
                    <div class="col-12 invalid-feedback d-block mt-1"><app-icon name="alert-circle" [size]="14" /> {{ 'address.postalCodeInvalid' | t }}</div>
                  }
                  @if (form.controls.address.hasError('incomplete') && form.controls.address.dirty) {
                    <div class="col-12 invalid-feedback d-block mt-1"><app-icon name="alert-circle" [size]="14" /> {{ 'address.incomplete' | t }}</div>
                  }
                  @for (m of addressServerErrors(); track m) {
                    <div class="col-12 invalid-feedback d-block mt-1"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                  }
                </div>
              </fieldset>
            </div>
            <div class="col-12">
              <div class="form-check">
                <input id="ce-consent" class="form-check-input" type="checkbox" formControlName="marketingConsent" />
                <label class="form-check-label" for="ce-consent">{{ 'customers.marketingConsent' | t }}</label>
              </div>
            </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'common.save' | t }}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class CustomerEditDialog {
  /** Emits the customer as saved by the server. */
  readonly saved = output<Customer>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private customerId: string | null = null;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly countries = computed(() => {
    const names = new Intl.DisplayNames([this.i18n.lang()], { type: 'region' });
    return COUNTRIES.map((code) => ({ code, name: names.of(code) ?? code }));
  });
  /** Mirrors the backend CustomerRequest rules. */
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    nif: ['', Validators.pattern(/^\d{9}$/)],
    phone: [''],
    email: ['', Validators.email],
    address: addressForm(),
    marketingConsent: [false],
  });

  open(customer: Customer): void {
    this.customerId = customer.id;
    this.error.set(null);
    this.busy.set(false);
    const a = customer.address;
    this.form.reset({
      name: customer.name,
      nif: customer.nif ?? '',
      phone: customer.phone ?? '',
      email: customer.email ?? '',
      address: {
        street: a?.street ?? '',
        detail: a?.detail ?? '',
        postalCode: a?.postalCode ?? '',
        locality: a?.locality ?? '',
        country: a?.country ?? 'PT',
      },
      marketingConsent: customer.marketingConsent,
    });
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  /** The PUT body: blank optional fields become null, an empty address is sent as null. */
  request(): CustomerRequest {
    const v = this.form.getRawValue();
    return {
      name: v.name.trim(),
      nif: v.nif.trim() || null,
      phone: v.phone.trim() || null,
      email: v.email.trim() || null,
      address: toAddress(this.form.controls.address),
      marketingConsent: v.marketingConsent,
    };
  }

  save(): void {
    if (!this.customerId) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.form.controls.address.markAsDirty();
      this.error.set(this.i18n.t('error.validation'));
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.updateCustomer(this.customerId, this.request()).subscribe({
      next: (customer) => {
        this.busy.set(false);
        this.saved.emit(customer);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        const marked = applyProblem(this.form, e);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected message(field: 'name' | 'nif' | 'phone' | 'email') {
    return fieldMessage(this.form.controls[field], {
      required: 'common.required',
      pattern: 'customers.nifInvalid',
      email: 'customers.emailInvalid',
    });
  }

  /** Server verdicts on address parts (`address.postalCode` and so on), shown under the address. */
  protected addressServerErrors() {
    const group = this.form.controls.address.controls;
    return [...new Set(Object.values(group).map((c) => fieldMessage(c)))].filter((m) => m !== null);
  }
}
