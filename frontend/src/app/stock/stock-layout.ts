import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Toasts } from '../backoffice/toasts';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from '../shared/icon';

/** Stock frame (owner and front desk): section tabs, the active section and a toast region shared by the sections. */
@Component({
  selector: 'app-stock-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, Icon],
  providers: [Toasts],
  template: `
    <nav class="nav nav-underline flex-nowrap overflow-x-auto border-bottom mb-4" [attr.aria-label]="'stock.sections' | t">
      <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/stock" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page">
        <app-icon name="inbox" [size]="16" />{{ 'stock.products' | t }}
      </a>
      <a class="nav-link d-inline-flex align-items-center gap-2 text-nowrap" routerLink="/stock/purchase-orders" routerLinkActive="active" ariaCurrentWhenActive="page">
        <app-icon name="check-in" [size]="16" />{{ 'stock.purchaseOrders' | t }}
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
export class StockLayout {
  protected readonly toasts = inject(Toasts);
}
