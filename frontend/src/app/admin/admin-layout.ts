import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from '../shared/icon';
import { LanguageSelect } from '../shared/language-select';
import { TenantThemeService } from '../shared/theme.service';
import { ThemeToggle } from '../shared/theme-toggle';

/** Frame of the platform admin console: graphite top bar, always the ASP theme (no workshop is signed in). */
@Component({
  selector: 'app-admin-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, TranslatePipe, Icon, LanguageSelect, ThemeToggle],
  styles: `.admin-topbar { min-height: 56px; background: var(--asp-chrome-bg); }`,
  template: `
    <header class="admin-topbar sticky-top d-flex flex-wrap align-items-center gap-2 px-3 py-2" data-bs-theme="dark">
      <a routerLink="/admin" class="app-brand me-auto">
        <span class="app-brand-mark"><app-icon name="wrench" [size]="18" /></span>
        <span class="app-brand-name">ASP <small>{{ 'admin.tagline' | t }}</small></span>
      </a>
      <app-language-select />
      <app-theme-toggle />
      @if (auth.account(); as u) {
        <span class="small text-body-secondary d-none d-sm-inline">{{ u.name }}</span>
      }
      <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'nav.signOut' | t" [title]="'nav.signOut' | t" (click)="auth.logout()">
        <app-icon name="log-out" />
      </button>
    </header>
    <main class="app-content w-100 mx-auto">
      <nav class="mb-4" [attr.aria-label]="'admin.nav.label' | t">
        <ul class="nav nav-underline flex-nowrap overflow-auto">
          <li class="nav-item">
            <a class="nav-link d-flex align-items-center gap-1 text-nowrap" routerLink="/admin"
               [class.active]="!onLeads()" [attr.aria-current]="onLeads() ? null : 'page'">
              <app-icon name="briefcase" [size]="16" />{{ 'admin.nav.workshops' | t }}
            </a>
          </li>
          <li class="nav-item">
            <a class="nav-link d-flex align-items-center gap-1 text-nowrap" routerLink="/admin/leads"
               [class.active]="onLeads()" [attr.aria-current]="onLeads() ? 'page' : null">
              <app-icon name="inbox" [size]="16" />{{ 'admin.nav.leads' | t }}
            </a>
          </li>
        </ul>
      </nav>
      <router-outlet />
    </main>
  `,
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  /** Workshops (the list and each workshop) or demo requests. */
  protected readonly onLeads = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.isLeads()),
    ),
    { initialValue: this.isLeads() },
  );

  private isLeads(): boolean {
    return this.router.url.startsWith('/admin/leads');
  }

  constructor() {
    inject(TenantThemeService).clear();
  }
}
