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
  selector: 'app-signup',
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
            <h1 class="mb-0">{{ 'signup.title' | t }}</h1>
            <div>
              <label class="form-label" for="signup-workshop">{{ 'signup.workshopName' | t }}</label>
              <input id="signup-workshop" class="form-control" formControlName="workshopName" autocomplete="organization"
                [class.is-invalid]="form.controls.workshopName.invalid && form.controls.workshopName.touched" />
            </div>
            <div>
              <label class="form-label" for="signup-nif">{{ 'common.nif' | t }}</label>
              <input id="signup-nif" class="form-control" formControlName="nif" inputmode="numeric" maxlength="9"
                [class.is-invalid]="form.controls.nif.invalid && form.controls.nif.touched" />
            </div>
            <div>
              <label class="form-label" for="signup-owner">{{ 'signup.ownerName' | t }}</label>
              <input id="signup-owner" class="form-control" formControlName="ownerName" autocomplete="name"
                [class.is-invalid]="form.controls.ownerName.invalid && form.controls.ownerName.touched" />
            </div>
            <div>
              <label class="form-label" for="signup-email">{{ 'common.email' | t }}</label>
              <input id="signup-email" class="form-control" type="email" formControlName="email" autocomplete="email"
                [class.is-invalid]="form.controls.email.invalid && form.controls.email.touched" />
            </div>
            <div>
              <label class="form-label" for="signup-password">{{ 'signup.password' | t }}</label>
              <input id="signup-password" class="form-control" type="password" formControlName="password" autocomplete="new-password"
                [class.is-invalid]="form.controls.password.invalid && form.controls.password.touched" />
            </div>
            @if (error()) {
              <app-alert class="mb-0">{{ error() }}</app-alert>
            }
            <button type="submit" class="btn btn-primary btn-lg w-100" [disabled]="form.invalid || busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'signup.submit' | t }}
            </button>
          </div>
        </form>
        <p class="text-center text-body-secondary small mt-4">{{ 'signup.alreadyRegistered' | t }} <a routerLink="/login">{{ 'login.submit' | t }}</a></p>
      </div>
    </main>
  `,
})
export class Signup {
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
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
        this.error.set(this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }
}
