import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { LanguageSelect } from '../shared/language-select';

@Component({
  selector: 'app-signup',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LanguageSelect],
  template: `
    <main class="auth">
      <div class="row"><span class="spacer"></span><app-language-select /></div>
      <h1>{{ 'signup.title' | t }}</h1>
      <form [formGroup]="form" (ngSubmit)="submit()" class="card stack">
        <label>{{ 'signup.workshopName' | t }} <input formControlName="workshopName" /></label>
        <label>{{ 'common.nif' | t }} <input formControlName="nif" inputmode="numeric" maxlength="9" /></label>
        <label>{{ 'signup.ownerName' | t }} <input formControlName="ownerName" autocomplete="name" /></label>
        <label>{{ 'common.email' | t }} <input type="email" formControlName="email" autocomplete="email" /></label>
        <label>{{ 'signup.password' | t }} <input type="password" formControlName="password" autocomplete="new-password" /></label>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        <button type="submit" [disabled]="form.invalid || busy()">{{ 'signup.submit' | t }}</button>
      </form>
      <p class="muted">{{ 'signup.alreadyRegistered' | t }} <a routerLink="/login">{{ 'login.submit' | t }}</a></p>
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
