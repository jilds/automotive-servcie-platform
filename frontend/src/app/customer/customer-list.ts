import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { Customer } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { addressForm, COUNTRIES, toAddress } from './address';

@Component({
  selector: 'app-customer-list',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="row">
      <h1>{{ 'customers.title' | t }}</h1>
      <span class="spacer"></span>
      <button (click)="showForm.set(!showForm())">{{ (showForm() ? 'common.close' : 'customers.new') | t }}</button>
    </div>

    @if (showForm()) {
      <form [formGroup]="form" (ngSubmit)="create()" class="card grid">
        <label>{{ 'common.name' | t }} <input formControlName="name" /></label>
        <label>{{ 'common.nif' | t }} <input formControlName="nif" inputmode="numeric" maxlength="9" /></label>
        <label>{{ 'common.phone' | t }} <input formControlName="phone" type="tel" /></label>
        <label>{{ 'common.email' | t }} <input formControlName="email" type="email" /></label>
        <fieldset class="address wide" formGroupName="address">
          <legend>{{ 'address.title' | t }}</legend>
          <label class="wide">{{ 'address.street' | t }} <input formControlName="street" autocomplete="address-line1" /></label>
          <label>{{ 'address.detail' | t }} <input formControlName="detail" autocomplete="address-line2" /></label>
          <label>{{ 'address.postalCode' | t }}
            <input formControlName="postalCode" autocomplete="postal-code" [placeholder]="'address.postalCodeHint' | t" maxlength="10" />
          </label>
          <label>{{ 'address.locality' | t }} <input formControlName="locality" autocomplete="address-level2" /></label>
          <label>{{ 'address.country' | t }}
            <select formControlName="country" autocomplete="country">
              @for (c of countries(); track c.code) {
                <option [value]="c.code">{{ c.name }}</option>
              }
            </select>
          </label>
          @if (form.controls.address.hasError('postalCode')) {
            <span class="field-error wide">{{ 'address.postalCodeInvalid' | t }}</span>
          }
          @if (form.controls.address.hasError('incomplete') && form.controls.address.dirty) {
            <span class="field-error wide">{{ 'address.incomplete' | t }}</span>
          }
        </fieldset>
        <label class="check"><input type="checkbox" formControlName="marketingConsent" /> {{ 'customers.marketingConsent' | t }}</label>
        <button type="submit" [disabled]="form.invalid">{{ 'customers.save' | t }}</button>
      </form>
    }

    <input class="search" [placeholder]="'customers.search' | t" (input)="search($any($event.target).value)" />
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    <table class="table">
      <thead>
        <tr><th>{{ 'common.name' | t }}</th><th>{{ 'common.nif' | t }}</th><th>{{ 'common.phone' | t }}</th><th>{{ 'common.email' | t }}</th><th>{{ 'address.locality' | t }}</th></tr>
      </thead>
      <tbody>
        @for (c of customers(); track c.id) {
          <tr>
            <td><a [routerLink]="['/customers', c.id]">{{ c.name }}</a></td>
            <td>{{ c.nif }}</td>
            <td>{{ c.phone }}</td>
            <td>{{ c.email }}</td>
            <td>{{ c.address?.locality }}</td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="muted">{{ 'customers.empty' | t }}</td></tr>
        }
      </tbody>
    </table>
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
