import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { problemCode } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { LanguageSelect } from '../shared/language-select';
import { TenantThemeService } from '../shared/theme.service';
import { ThemeToggle } from '../shared/theme-toggle';
import { ActivationPreview, IdentityApi } from './identity-api.service';

/** Same rule as the server (`Password`): 12 to 72 characters. */
export const MIN_PASSWORD = 12;
export const MAX_PASSWORD = 72;

/** Group validator: `password` and `confirm` must be equal. */
export const passwordsMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null =>
  group.get('password')?.value === group.get('confirm')?.value ? null : { mismatch: true };

export function activationForm() {
  return new FormGroup(
    {
      name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
      password: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(MIN_PASSWORD), Validators.maxLength(MAX_PASSWORD)],
      }),
      confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: passwordsMatch },
  );
}

/** `/activate?token=…`: a new user (or re-invited owner) chooses a password and is signed in. */
@Component({
  selector: 'app-activate',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, DatePipe, LanguageSelect, ThemeToggle, Alert, Icon],
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
        <div class="card shadow-lg">
          <div class="card-body vstack gap-3 p-4">
            <h1 class="mb-0">{{ 'activate.title' | t }}</h1>
            @if (linkError()) {
              <app-alert class="mb-0">{{ linkError() }}</app-alert>
              <a routerLink="/login" class="btn btn-outline-secondary">{{ 'activate.backToLogin' | t }}</a>
            } @else if (!preview()) {
              <div class="placeholder-glow" aria-busy="true"><span class="placeholder col-8"></span></div>
            } @else {
              <p class="text-body-secondary mb-0">{{ 'activate.subtitle' | t: { workshop: preview()!.workshopName } }}</p>
              <form [formGroup]="form" (ngSubmit)="submit()" class="vstack gap-3" novalidate>
                <div>
                  <label class="form-label" for="act-email">{{ 'common.email' | t }}</label>
                  <input id="act-email" class="form-control" type="email" [value]="preview()!.email" readonly autocomplete="username" />
                </div>
                <div>
                  <label class="form-label" for="act-name">{{ 'activate.name' | t }}</label>
                  <input id="act-name" class="form-control" formControlName="name" autocomplete="name"
                    [class.is-invalid]="form.controls.name.invalid && form.controls.name.touched" />
                </div>
                <div>
                  <label class="form-label" for="act-password">{{ 'activate.password' | t }}</label>
                  <input id="act-password" class="form-control" type="password" formControlName="password" autocomplete="new-password"
                    [class.is-invalid]="form.controls.password.invalid && form.controls.password.touched" />
                  @if (form.controls.password.invalid && form.controls.password.touched) {
                    <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ 'activate.tooShort' | t }}</div>
                  } @else {
                    <div class="form-text">{{ 'activate.passwordHelp' | t }}</div>
                  }
                </div>
                <div>
                  <label class="form-label" for="act-confirm">{{ 'activate.confirm' | t }}</label>
                  <input id="act-confirm" class="form-control" type="password" formControlName="confirm" autocomplete="new-password"
                    [class.is-invalid]="mismatch()" />
                  @if (mismatch()) {
                    <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ 'activate.mismatch' | t }}</div>
                  }
                </div>
                <p class="form-text mb-0">{{ 'activate.expiresAt' | t: { date: (preview()!.expiresAt | date: 'dd/MM/yyyy HH:mm') } }}</p>
                @if (error()) {
                  <app-alert class="mb-0">{{ error() }}</app-alert>
                }
                <button type="submit" class="btn btn-primary btn-lg w-100" [disabled]="form.invalid || busy()" [attr.aria-busy]="busy()">
                  @if (busy()) {
                    <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                  }
                  {{ 'activate.submit' | t }}
                </button>
              </form>
            }
          </div>
        </div>
      </div>
    </main>
  `,
})
export class Activate {
  private readonly api = inject(IdentityApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);

  /** From `?token=`. */
  readonly token = input<string>();

  protected readonly form = activationForm();
  protected readonly preview = signal<ActivationPreview | null>(null);
  protected readonly linkError = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    inject(TenantThemeService).clear();
    effect(() => this.load(this.token()));
  }

  protected mismatch(): boolean {
    return this.form.hasError('mismatch') && this.form.controls.confirm.touched;
  }

  submit(): void {
    const token = this.token();
    this.form.markAllAsTouched();
    if (!token || this.form.invalid) return;
    const { name, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.activate(token, name.trim(), password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homeUrl()),
      error: (e) => {
        this.busy.set(false);
        if (isLinkProblem(e)) this.linkError.set(this.i18n.error(e));
        else this.error.set(this.i18n.error(e));
      },
    });
  }

  private load(token: string | undefined): void {
    this.preview.set(null);
    this.linkError.set(null);
    if (!token) {
      this.linkError.set(this.i18n.t('activate.missingToken'));
      return;
    }
    this.api.activation(token).subscribe({
      next: (p) => {
        this.preview.set(p);
        this.form.controls.name.setValue(p.name);
      },
      error: (e) => this.linkError.set(this.i18n.error(e)),
    });
  }
}

function isLinkProblem(e: unknown): boolean {
  const code = problemCode(e);
  return code === 'activation-invalid' || code === 'activation-expired' || code === 'workshop-suspended';
}
