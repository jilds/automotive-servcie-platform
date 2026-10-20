import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { LanguageSelect } from './language-select';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSelect],
  template: `
    <header class="topbar">
      <a routerLink="/board" class="brand">ASP</a>
      <nav>
        <a routerLink="/board" routerLinkActive="active">{{ (auth.hasRole('MECHANIC') ? 'nav.myJobs' : 'nav.board') | t }}</a>
        @if (!auth.hasRole('MECHANIC')) {
          <a routerLink="/check-in" routerLinkActive="active">{{ 'nav.checkIn' | t }}</a>
          <a routerLink="/customers" routerLinkActive="active">{{ 'nav.customers' | t }}</a>
        }
      </nav>
      <span class="spacer"></span>
      <app-language-select />
      <span class="muted user">{{ auth.user()?.name }}</span>
      <button class="link" (click)="auth.logout()">{{ 'nav.signOut' | t }}</button>
    </header>
    <main class="content">
      <router-outlet />
    </main>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
}
