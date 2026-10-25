import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from '../shared/icon';
import { TenantThemeService } from '../shared/theme.service';
import { Toasts } from './toasts';

/** Owner backoffice frame: section tabs, the active section and a toast region shared by the sections. */
@Component({
  selector: 'app-backoffice-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, Icon],
  providers: [Toasts],
  template: `
    <nav class="nav nav-underline flex-nowrap overflow-x-auto border-bottom mb-4" [attr.aria-label]="'backoffice.sections' | t">
      <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/backoffice" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page">
        <app-icon name="gauge" [size]="16" />{{ 'backoffice.tab.dashboard' | t }}
      </a>
      <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/backoffice/team" routerLinkActive="active" ariaCurrentWhenActive="page">
        <app-icon name="users" [size]="16" />{{ 'backoffice.tab.team' | t }}
      </a>
      <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/backoffice/services" routerLinkActive="active" ariaCurrentWhenActive="page">
        <app-icon name="inbox" [size]="16" />{{ 'catalogue.tab' | t }}
      </a>
      @if (bookingOn()) {
        <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/backoffice/booking" routerLinkActive="active" ariaCurrentWhenActive="page">
          <app-icon name="clock" [size]="16" />{{ 'booking.settings.tab' | t }}
        </a>
      }
      <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/backoffice/settings" routerLinkActive="active" ariaCurrentWhenActive="page">
        <app-icon name="wrench" [size]="16" />{{ 'backoffice.tab.settings' | t }}
      </a>
    </nav>

    <router-outlet />

    <div class="toast-container position-fixed bottom-0 end-0 p-3" aria-live="polite">
      @for (m of toasts.items(); track m.id) {
        <div class="toast show" [class]="'border-' + m.tone + '-subtle'" role="status">
          <div class="d-flex align-items-start gap-2 toast-body">
            <span class="d-inline-flex pt-1" [class]="'text-' + m.tone + '-emphasis'">
              <app-icon [name]="m.tone === 'success' ? 'check-circle' : m.tone === 'info' ? 'info' : m.tone === 'warning' ? 'alert-triangle' : 'alert-circle'" />
            </span>
            <span class="flex-grow-1 pt-1">{{ m.text }}</span>
            <button type="button" class="btn-close flex-shrink-0" [attr.aria-label]="'common.close' | t" (click)="toasts.dismiss(m.id)"></button>
          </div>
        </div>
      }
    </div>
  `,
})
export class BackofficeLayout {
  protected readonly toasts = inject(Toasts);
  /** The online booking add-on is on (from `GET /branding`, loaded by the shell). */
  private readonly theme = inject(TenantThemeService);
  protected readonly bookingOn = computed(() => !!this.theme.branding()?.addOns?.booking);
}
