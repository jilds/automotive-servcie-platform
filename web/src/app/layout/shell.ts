import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <a routerLink="/board" class="brand">ASP</a>
      <nav>
        <a routerLink="/board" routerLinkActive="active">{{ auth.hasRole('MECHANIC') ? 'My jobs' : 'Board' }}</a>
        @if (!auth.hasRole('MECHANIC')) {
          <a routerLink="/check-in" routerLinkActive="active">Check in</a>
          <a routerLink="/customers" routerLinkActive="active">Customers</a>
        }
      </nav>
      <span class="spacer"></span>
      <span class="muted user">{{ auth.user()?.name }}</span>
      <button class="link" (click)="auth.logout()">Sign out</button>
    </header>
    <main class="content">
      <router-outlet />
    </main>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
}
