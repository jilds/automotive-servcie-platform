import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AddOns } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon } from './icon';
import { LanguageSelect } from './language-select';
import { IdentityApi } from '../identity/identity-api.service';
import { TenantThemeService } from './theme.service';
import { ThemeToggle } from './theme-toggle';

const NO_ADD_ONS: AddOns = { booking: false, shop: false };

/**
 * Signed-in layout: graphite sidebar on desktop; below 1024px a top bar and the same sidebar as a Bootstrap
 * offcanvas-lg drawer, opened by the menuOpen signal (no Bootstrap JS). The chrome is always dark (data-bs-theme).
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSelect, ThemeToggle, Icon],
  host: { '(document:keydown.escape)': 'menuOpen.set(false)' },
  template: `
    <div class="app-layout">
      <header class="app-topbar sticky-top align-items-center gap-2 px-2" data-bs-theme="dark">
        <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'nav.openMenu' | t" [attr.aria-expanded]="menuOpen()" aria-controls="sidebar" (click)="menuOpen.set(true)">
          <app-icon name="menu" />
        </button>
        <a routerLink="/board" class="app-brand">
          @if (brand()?.logoUrl; as logo) {
            <img class="app-brand-logo" [src]="logo" alt="" />
          } @else {
            <span class="app-brand-mark"><app-icon name="wrench" [size]="18" /></span>
          }
          <span class="app-brand-name text-truncate">{{ brand()?.name ?? 'ASP' }}</span>
        </a>
        <!-- Phones: the sidebar (with its language drop-down) is behind the menu, so the language stays one tap away. -->
        <app-language-select class="ms-auto flex-shrink-0 pe-1" [compact]="true" />
      </header>

      @if (menuOpen()) {
        <div class="offcanvas-backdrop fade show" (click)="menuOpen.set(false)"></div>
      }

      <div class="app-sidebar" data-bs-theme="dark">
        <aside id="sidebar" class="offcanvas-lg offcanvas-start h-100" [class.show]="menuOpen()">
          <div class="offcanvas-body d-flex flex-column gap-3 h-100 p-3">
            <div class="d-flex align-items-center justify-content-between gap-2">
              <a routerLink="/board" class="app-brand px-2 py-1" (click)="menuOpen.set(false)">
                @if (brand()?.logoUrl; as logo) {
                  <img class="app-brand-logo" [src]="logo" alt="" />
                } @else {
                  <span class="app-brand-mark"><app-icon name="wrench" [size]="20" /></span>
                }
                <span class="app-brand-name overflow-hidden"><span class="text-truncate">{{ brand()?.name ?? 'ASP' }}</span> <small>{{ 'app.tagline' | t }}</small></span>
              </a>
              <button type="button" class="btn-close" [attr.aria-label]="'nav.closeMenu' | t" (click)="menuOpen.set(false)"></button>
            </div>

            <nav class="nav nav-pills flex-column gap-1 app-nav" [attr.aria-label]="'nav.main' | t" (click)="menuOpen.set(false)">
              <a class="nav-link" routerLink="/board" routerLinkActive="active" ariaCurrentWhenActive="page">
                <app-icon name="board" />{{ (auth.hasRole('MECHANIC') ? 'nav.myJobs' : 'nav.board') | t }}
              </a>
              @if (!auth.hasRole('MECHANIC')) {
                <a class="nav-link" routerLink="/check-in" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="check-in" />{{ 'nav.checkIn' | t }}</a>
                @if (addOns().booking) {
                  <a class="nav-link" routerLink="/bookings" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="clock" />{{ 'nav.bookings' | t }}</a>
                }
                @if (addOns().shop) {
                  <a class="nav-link" routerLink="/shop-orders" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="bag" />{{ 'nav.shopOrders' | t }}</a>
                }
                <a class="nav-link" routerLink="/customers" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="users" />{{ 'nav.customers' | t }}</a>
                <a class="nav-link" routerLink="/stock" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="inbox" />{{ 'nav.stock' | t }}</a>
              }
              @if (auth.hasRole('OWNER')) {
                <a class="nav-link" routerLink="/backoffice" routerLinkActive="active" ariaCurrentWhenActive="page"><app-icon name="briefcase" />{{ 'nav.backoffice' | t }}</a>
              }
            </nav>

            <div class="vstack gap-3 flex-grow-0 mt-auto pt-3 border-top">
              <div class="d-flex flex-wrap align-items-center gap-2 px-2">
                <app-language-select class="flex-grow-1" />
                <app-theme-toggle />
              </div>
              @if (auth.user(); as u) {
                <div class="d-flex align-items-center gap-3 p-2">
                  <span class="app-avatar"><app-icon name="user" [size]="18" /></span>
                  <span class="d-flex flex-column flex-grow-1 overflow-hidden lh-sm">
                    <span class="fw-semibold small text-truncate">{{ u.name }}</span>
                    <span class="text-body-secondary small">{{ $any('role.' + u.role) | t }}</span>
                  </span>
                  <button type="button" class="btn btn-ghost btn-icon" [attr.aria-label]="'nav.signOut' | t" [title]="'nav.signOut' | t" (click)="auth.logout()">
                    <app-icon name="log-out" />
                  </button>
                </div>
              }
            </div>
          </div>
        </aside>
      </div>

      <main class="app-content w-100 mx-auto">
        <router-outlet />
      </main>
    </div>
  `,
})
export class Shell implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly identity = inject(IdentityApi);
  private readonly theme = inject(TenantThemeService);
  /** The workshop's name, logo and colours; null while loading or when the ASP default applies. */
  protected readonly brand = this.theme.branding;
  /** Drawer state on phones; view-only. */
  protected readonly menuOpen = signal(false);
  /** The add-ons switched on for the workshop; the Bookings and Online orders menu items follow them. */
  protected readonly addOns = signal<AddOns>(NO_ADD_ONS);

  /** Applies the workshop theme for the signed-in user; the ASP default stays when it cannot be loaded. */
  ngOnInit(): void {
    this.identity.currentBranding().subscribe({
      next: (b) => {
        this.theme.apply(b);
        this.addOns.set(b.addOns ?? NO_ADD_ONS);
      },
      error: () => {
        this.theme.clear();
        this.addOns.set(NO_ADD_ONS);
      },
    });
  }
}
