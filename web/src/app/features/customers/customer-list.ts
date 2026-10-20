import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { Customer } from '../../core/models';
import { errorMessage } from '../../shared/errors';

@Component({
  selector: 'app-customer-list',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="row">
      <h1>Customers</h1>
      <span class="spacer"></span>
      <button (click)="showForm.set(!showForm())">{{ showForm() ? 'Close' : 'New customer' }}</button>
    </div>

    @if (showForm()) {
      <form [formGroup]="form" (ngSubmit)="create()" class="card grid">
        <label>Name <input formControlName="name" /></label>
        <label>NIF <input formControlName="nif" inputmode="numeric" maxlength="9" /></label>
        <label>Phone <input formControlName="phone" type="tel" /></label>
        <label>Email <input formControlName="email" type="email" /></label>
        <label class="wide">Address <input formControlName="address" /></label>
        <label class="check"><input type="checkbox" formControlName="marketingConsent" /> Agrees to marketing messages</label>
        <button type="submit" [disabled]="form.invalid">Save customer</button>
      </form>
    }

    <input class="search" placeholder="Search by name, NIF or phone" (input)="search($any($event.target).value)" />
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    <table class="table">
      <thead><tr><th>Name</th><th>NIF</th><th>Phone</th><th>Email</th></tr></thead>
      <tbody>
        @for (c of customers(); track c.id) {
          <tr>
            <td><a [routerLink]="['/customers', c.id]">{{ c.name }}</a></td>
            <td>{{ c.nif }}</td>
            <td>{{ c.phone }}</td>
            <td>{{ c.email }}</td>
          </tr>
        } @empty {
          <tr><td colspan="4" class="muted">No customers yet</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class CustomerList implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly showForm = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    nif: ['', Validators.pattern(/^\d{9}$/)],
    phone: [''],
    email: ['', Validators.email],
    address: [''],
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
        error: (e) => this.error.set(errorMessage(e)),
      });
    }, 250);
  }

  create(): void {
    const v = this.form.getRawValue();
    this.api.createCustomer({ ...v, nif: v.nif || null, email: v.email || null }).subscribe({
      next: (c) => {
        this.customers.update((list) => [c, ...list]);
        this.form.reset();
        this.showForm.set(false);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
