import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { LanguageSelect } from '../shared/language-select';
import { ThemeToggle } from '../shared/theme-toggle';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LanguageSelect, ThemeToggle, Alert, Icon],
  template: `
    <main class="auth-page px-3 pb-5">
      <div class="auth-inner mx-auto">
        <div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4" data-bs-theme="dark">
          <span class="app-brand">
            <span class="app-brand-mark"><app-icon name="wrench" [size]="20" /></span>
            <span class="app-brand-name">ASP <small>{{ 'app.tagline' | t }}</small></span>
          </span>
          <div class="d-flex align-items-center gap-2"><app-language-select /><app-theme-toggle /></div>
        </div>
        <form [formGroup]="form" (ngSubmit)="submit()" class="card shadow-lg">
          <div class="card-body vstack gap-3 p-4">
            <div>
              <h1 class="mb-2">{{ 'login.submit' | t }}</h1>
              <p class="text-body-secondary mb-0">{{ 'login.subtitle' | t }}</p>
            </div>
            <div>
              <label class="form-label" for="login-email">{{ 'common.email' | t }}</label>
              <input id="login-email" class="form-control" type="email" formControlName="email" autocomplete="username"
                [class.is-invalid]="form.controls.email.invalid && form.controls.email.touched" />
            </div>
            <div>
              <label class="form-label" for="login-password">{{ 'common.password' | t }}</label>
              <input id="login-password" class="form-control" type="password" formControlName="password" autocomplete="current-password"
                [class.is-invalid]="form.controls.password.invalid && form.controls.password.touched" />
            </div>
            @if (error()) {
              <app-alert class="mb-0">{{ error() }}</app-alert>
            }
            <button type="submit" class="btn btn-primary btn-lg w-100" [disabled]="form.invalid || busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'login.submit' | t }}
            </button>
          </div>
        </form>
        <p class="text-center text-body-secondary small mt-4">{{ 'login.newWorkshop' | t }} <a routerLink="/signup">{{ 'login.createAccount' | t }}</a></p>
      </div>
    </main>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigate(['/board']),
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }
}
