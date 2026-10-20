import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { LanguageSelect } from '../shared/language-select';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LanguageSelect],
  template: `
    <main class="auth">
      <div class="row"><span class="spacer"></span><app-language-select /></div>
      <h1>{{ 'app.title' | t }}</h1>
      <p class="muted">{{ 'login.subtitle' | t }}</p>
      <form [formGroup]="form" (ngSubmit)="submit()" class="card stack">
        <label>{{ 'common.email' | t }} <input type="email" formControlName="email" autocomplete="username" /></label>
        <label>{{ 'common.password' | t }} <input type="password" formControlName="password" autocomplete="current-password" /></label>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        <button type="submit" [disabled]="form.invalid || busy()">{{ 'login.submit' | t }}</button>
      </form>
      <p class="muted">{{ 'login.newWorkshop' | t }} <a routerLink="/signup">{{ 'login.createAccount' | t }}</a></p>
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
