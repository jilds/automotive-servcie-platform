import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { BackofficeApi } from '../backoffice/backoffice-api.service';
import { BackofficeLayout } from '../backoffice/backoffice-layout';
import { Dashboard } from '../backoffice/dashboard';
import { Branding, Role } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { Shell } from '../shared/shell';
import { TenantThemeService } from '../shared/theme.service';

const branding = (shop: boolean): Branding => ({
  slug: 'oficina-azul', name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null, accentColor: null,
  logoUrl: null, privacyUrl: null, addOns: { booking: false, shop }, version: 1,
});

const DASHBOARD = {
  month: '2030-11', openByStatus: {}, carsInShop: 0, deliveredThisMonth: 0,
  revenueThisMonth: { netCents: 0, vatCents: 0, grossCents: 0 }, mechanicWorkload: [],
};

/** The online shop shows up in the staff screens only when the signed-in branding has `addOns.shop`. */
describe('online shop menu, tab and tile', () => {
  let http: HttpTestingController;

  function setup(role: Role = 'FRONT_DESK') {
    const user = signal({ id: 'u1', name: 'Rita', email: 'rita@example.pt', role, tenantId: 't1' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { user, hasRole: (...roles: string[]) => roles.includes(role), logout: () => {} } },
        { provide: BackofficeApi, useValue: { dashboard: () => of(DASHBOARD) } },
      ],
    });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    http.verify();
    document.documentElement.removeAttribute('data-tenant-theme');
  });

  function shell(shop: boolean, role: Role = 'FRONT_DESK') {
    setup(role);
    const f = TestBed.createComponent(Shell);
    f.detectChanges();
    http.expectOne('/api/v1/branding').flush(branding(shop));
    f.detectChanges();
    return f.nativeElement.querySelector('a[href="/shop-orders"]') as HTMLAnchorElement | null;
  }

  it('shows the Online orders menu item when the add-on is on', () => {
    expect(shell(true)?.textContent).toContain('Online orders');
  });

  it('hides it when the add-on is off', () => {
    expect(shell(false)).toBeNull();
  });

  it('never shows it to a mechanic', () => {
    expect(shell(true, 'MECHANIC')).toBeNull();
  });

  it('shows the backoffice shop tab only with the add-on', () => {
    setup('OWNER');
    TestBed.inject(TenantThemeService).branding.set(branding(false));
    const f = TestBed.createComponent(BackofficeLayout);
    f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/backoffice/shop"]')).toBeNull();
    TestBed.inject(TenantThemeService).branding.set(branding(true));
    f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/backoffice/shop"]')?.textContent).toContain('Online shop');
  });

  function dashboard(shop: boolean) {
    setup('OWNER');
    TestBed.inject(TenantThemeService).branding.set(branding(shop));
    const f = TestBed.createComponent(Dashboard);
    f.detectChanges();
    http.expectOne('/api/v1/stock/low-stock').flush([]);
    return f;
  }

  it('shows the "orders to prepare" tile with the pending count when the add-on is on', () => {
    const f = dashboard(true);
    http.expectOne('/api/v1/shop-orders/pending-count').flush({ count: 2 });
    f.detectChanges();
    const tile = f.nativeElement.querySelector('a[href="/shop-orders"]') as HTMLAnchorElement;
    expect(tile.textContent).toContain('Orders to prepare: 2');
  });

  it('hides the tile when nothing waits', () => {
    const f = dashboard(true);
    http.expectOne('/api/v1/shop-orders/pending-count').flush({ count: 0 });
    f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/shop-orders"]')).toBeNull();
  });

  it('does not ask for the count when the add-on is off', () => {
    const f = dashboard(false);
    http.expectNone('/api/v1/shop-orders/pending-count');
    f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/shop-orders"]')).toBeNull();
  });
});
