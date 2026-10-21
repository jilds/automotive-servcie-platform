import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Customer } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { addressForm, COUNTRIES, toAddress } from './address';

@Component({
  selector: 'app-customer-list',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, Alert, Icon],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <h1 class="me-auto mb-0">{{ 'customers.title' | t }}</h1>
      <button type="button" class="btn" [class.btn-primary]="!showForm()" [class.btn-outline-secondary]="showForm()" [attr.aria-expanded]="showForm()" (click)="showForm.set(!showForm())">
        <app-icon [name]="showForm() ? 'x' : 'plus'" [size]="16" />{{ (showForm() ? 'common.close' : 'customers.new') | t }}
      </button>
    </header>

    @if (showForm()) {
      <form [formGroup]="form" (ngSubmit)="create()" class="card shadow-sm mb-4">
        <div class="card-body">
          <div class="row g-3">
          <div class="col-sm-6 col-lg-3">
            <label class="form-label" for="cn-name">{{ 'common.name' | t }}</label>
            <input id="cn-name" class="form-control" formControlName="name" [class.is-invalid]="form.controls.name.invalid && form.controls.name.touched" />
          </div>
          <div class="col-sm-6 col-lg-3">
            <label class="form-label" for="cn-nif">{{ 'common.nif' | t }}</label>
            <input id="cn-nif" class="form-control" formControlName="nif" inputmode="numeric" maxlength="9" [class.is-invalid]="form.controls.nif.invalid && form.controls.nif.touched" />
          </div>
          <div class="col-sm-6 col-lg-3">
            <label class="form-label" for="cn-phone">{{ 'common.phone' | t }}</label>
            <input id="cn-phone" class="form-control" formControlName="phone" type="tel" />
          </div>
          <div class="col-sm-6 col-lg-3">
            <label class="form-label" for="cn-email">{{ 'common.email' | t }}</label>
            <input id="cn-email" class="form-control" formControlName="email" type="email" [class.is-invalid]="form.controls.email.invalid && form.controls.email.touched" />
          </div>
          <div class="col-12">
            <fieldset class="border rounded-3 p-3" formGroupName="address">
              <legend class="float-none w-auto px-2 mb-0 small fw-semibold text-body-secondary">{{ 'address.title' | t }}</legend>
              <div class="row g-3">
                <div class="col-12">
                  <label class="form-label" for="cn-street">{{ 'address.street' | t }}</label>
                  <input id="cn-street" class="form-control" formControlName="street" autocomplete="address-line1" />
                </div>
                <div class="col-sm-6 col-lg-3">
                  <label class="form-label" for="cn-detail">{{ 'address.detail' | t }}</label>
                  <input id="cn-detail" class="form-control" formControlName="detail" autocomplete="address-line2" />
                </div>
                <div class="col-sm-6 col-lg-3">
                  <label class="form-label" for="cn-postal">{{ 'address.postalCode' | t }}</label>
                  <input id="cn-postal" class="form-control" formControlName="postalCode" autocomplete="postal-code" [placeholder]="'address.postalCodeHint' | t" maxlength="10"
                    [class.is-invalid]="form.controls.address.hasError('postalCode')" />
                </div>
                <div class="col-sm-6 col-lg-3">
                  <label class="form-label" for="cn-locality">{{ 'address.locality' | t }}</label>
                  <input id="cn-locality" class="form-control" formControlName="locality" autocomplete="address-level2" />
                </div>
                <div class="col-sm-6 col-lg-3">
                  <label class="form-label" for="cn-country">{{ 'address.country' | t }}</label>
                  <select id="cn-country" class="form-select" formControlName="country" autocomplete="country">
                    @for (c of countries(); track c.code) {
                      <option [value]="c.code">{{ c.name }}</option>
                    }
                  </select>
                </div>
                @if (form.controls.address.hasError('postalCode')) {
                  <div class="col-12 invalid-feedback d-block mt-1">{{ 'address.postalCodeInvalid' | t }}</div>
                }
                @if (form.controls.address.hasError('incomplete') && form.controls.address.dirty) {
                  <div class="col-12 invalid-feedback d-block mt-1">{{ 'address.incomplete' | t }}</div>
                }
              </div>
            </fieldset>
          </div>
          <div class="col-12">
            <div class="form-check">
              <input id="cn-consent" class="form-check-input" type="checkbox" formControlName="marketingConsent" />
              <label class="form-check-label" for="cn-consent">{{ 'customers.marketingConsent' | t }}</label>
            </div>
          </div>
          <div class="col-12 d-flex justify-content-end">
            <button type="submit" class="btn btn-primary" [disabled]="form.invalid">{{ 'customers.save' | t }}</button>
          </div>
          </div>
        </div>
      </form>
    }

    <div class="row mb-3">
      <div class="col-md-8 col-lg-5">
        <div class="input-group">
          <span class="input-group-text"><app-icon name="search" [size]="18" /></span>
          <input type="search" class="form-control" [attr.aria-label]="'customers.search' | t" [placeholder]="'customers.search' | t" (input)="search($any($event.target).value)" />
        </div>
      </div>
    </div>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    <div class="card table-stack mb-4">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr><th>{{ 'common.name' | t }}</th><th>{{ 'common.nif' | t }}</th><th>{{ 'common.phone' | t }}</th><th>{{ 'common.email' | t }}</th><th>{{ 'address.locality' | t }}</th></tr>
          </thead>
          <tbody>
            @for (c of customers(); track c.id) {
              <tr>
                <td class="cell-title"><a [routerLink]="['/customers', c.id]">{{ c.name }}</a></td>
                <td [attr.data-label]="'common.nif' | t">{{ c.nif }}</td>
                <td [attr.data-label]="'common.phone' | t">{{ c.phone }}</td>
                <td [attr.data-label]="'common.email' | t">{{ c.email }}</td>
                <td [attr.data-label]="'address.locality' | t">{{ c.address?.locality }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="cell-full">
                  <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                    <app-icon name="users" [size]="36" class="text-body-tertiary" />
                    <p class="fw-semibold text-body mb-0">{{ 'customers.empty' | t }}</p>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class CustomerList implements OnInit {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly showForm = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly countries = computed(() => {
    const names = new Intl.DisplayNames([this.i18n.lang()], { type: 'region' });
    return COUNTRIES.map((code) => ({ code, name: names.of(code) ?? code }));
  });
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    nif: ['', Validators.pattern(/^\d{9}$/)],
    phone: [''],
    email: ['', Validators.email],
    address: addressForm(),
    marketingConsent: [false],
  });
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.search('');
  }

  search(q: string): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.api.customers(q).subscribe({
        next: (list) => this.customers.set(list),
        error: (e) => this.error.set(this.i18n.error(e)),
      });
    }, 250);
  }

  create(): void {
    const v = this.form.getRawValue();
    this.error.set(null);
    this.api
      .createCustomer({
        name: v.name,
        nif: v.nif || null,
        phone: v.phone || null,
        email: v.email || null,
        address: toAddress(this.form.controls.address),
        marketingConsent: v.marketingConsent,
      })
      .subscribe({
        next: (c) => {
          this.customers.update((list) => [c, ...list]);
          this.form.reset();
          this.showForm.set(false);
        },
        error: (e) => this.error.set(this.i18n.error(e)),
      });
  }
}
