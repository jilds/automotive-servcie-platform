import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../shared/errors';

@Component({
  selector: 'app-signup',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <main class="auth">
      <h1>Create your workshop</h1>
      <form [formGroup]="form" (ngSubmit)="submit()" class="card stack">
        <label>Workshop name <input formControlName="workshopName" /></label>
        <label>NIF <input formControlName="nif" inputmode="numeric" maxlength="9" /></label>
        <label>Your name <input formControlName="ownerName" autocomplete="name" /></label>
        <label>Email <input type="email" formControlName="email" autocomplete="email" /></label>
        <label>Password (8+ characters) <input type="password" formControlName="password" autocomplete="new-password" /></label>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        <button type="submit" [disabled]="form.invalid || busy()">Create account</button>
      </form>
      <p class="muted">Already registered? <a routerLink="/login">Sign in</a></p>
    </main>
  `,
})
export class Signup {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    workshopName: ['', Validators.required],
    nif: ['', Validators.pattern(/^\d{9}$/)],
    ownerName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit(): void {
    this.busy.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.auth.signup({ ...v, nif: v.nif || undefined }).subscribe({
      next: () => this.router.navigate(['/board']),
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
