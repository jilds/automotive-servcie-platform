import { Component, effect, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Branding } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { LanguageSelect } from '../shared/language-select';
import { TenantThemeService } from '../shared/theme.service';
import { ThemeToggle } from '../shared/theme-toggle';
import { IdentityApi, PlatformContact } from './identity-api.service';

/**
 * Sign in for everyone: workshop users go to the board, platform admins to the console. On `/w/{slug}` the page
 * wears the workshop's theme, logo and name. Workshops cannot sign up here: the contact block says how to get ASP.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, TranslatePipe, LanguageSelect, ThemeToggle, Alert, Icon],
  template: `
    <main class="auth-page px-3 pb-5">
      <div class="auth-inner mx-auto">
        <div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4" data-bs-theme="dark">
          <span class="app-brand">
            @if (branding()?.logoUrl; as logo) {
              <img class="app-brand-logo" [src]="logo" alt="" />
            } @else {
              <span class="app-brand-mark"><app-icon name="wrench" [size]="20" /></span>
            }
            <span class="app-brand-name">{{ branding()?.name ?? 'ASP' }} <small>{{ (branding() ? 'login.poweredBy' : 'app.tagline') | t }}</small></span>
          </span>
          <div class="d-flex align-items-center gap-2"><app-language-select /><app-theme-toggle /></div>
        </div>
        <form [formGroup]="form" (ngSubmit)="submit()" class="card shadow-lg">
          <div class="card-body vstack gap-3 p-4">
            <div>
              <!-- The product name is the page title in every language (CLAUDE.md §6), also under a workshop's brand. -->
              <h1 class="h3 mb-2">{{ 'app.title' | t }}</h1>
              <p class="text-body-secondary mb-0">
                @if (branding(); as b) {
                  {{ 'login.subtitleBranded' | t: { name: b.name } }}
                } @else {
                  {{ 'login.subtitle' | t }}
                }
              </p>
            </div>
            @if (reason() && !error()) {
              <app-alert tone="warning" class="mb-0">{{ 'error.workshop-suspended' | t }}</app-alert>
            }
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
        @if (contact(); as c) {
          <section class="text-center text-body-secondary small mt-4" aria-labelledby="login-contact">
            <h2 id="login-contact" class="h6 text-body mb-1">{{ 'login.contactTitle' | t }}</h2>
            <p class="mb-1">{{ 'login.contactBody' | t }}</p>
            <p class="d-flex flex-wrap justify-content-center gap-3 mb-0">
              @if (c.email) {
                <a [href]="'mailto:' + c.email">{{ c.email }}</a>
              }
              @if (c.phone) {
                <a [href]="'tel:' + c.phone.replace(' ', '')">{{ c.phone }}</a>
              }
            </p>
          </section>
        }
      </div>
    </main>
  `,
})
export class Login implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly api = inject(IdentityApi);
  private readonly theme = inject(TenantThemeService);

  /** From `/w/:slug`: the workshop whose branded login this is. */
  readonly slug = input<string>();
  /** From `?reason=`: why the user was signed out. */
  readonly reason = input<string>();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly branding = signal<Branding | null>(null);
  protected readonly contact = signal<PlatformContact | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    effect(() => this.loadBranding(this.slug()));
  }

  ngOnInit(): void {
    this.api.contact().subscribe({
      next: (c) => this.contact.set(c.email || c.phone ? c : null),
      error: () => this.contact.set(null),
    });
  }

  submit(): void {
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homeUrl()),
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }

  private loadBranding(slug: string | undefined): void {
    this.branding.set(null);
    this.theme.clear();
    if (!slug) return;
    this.api.brandingBySlug(slug).subscribe({
      next: (b) => {
        this.branding.set(b);
        this.theme.apply(b);
      },
      // An unknown address shows the plain ASP sign-in.
      error: () => this.branding.set(null),
    });
  }
}
