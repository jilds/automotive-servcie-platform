import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Branding } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TenantThemeService } from '../shared/theme.service';
import { BackofficeLayout } from './backoffice-layout';

const branding = (booking: boolean): Branding => ({
  slug: 'oficina-azul', name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null, accentColor: null,
  logoUrl: null, privacyUrl: null, addOns: { booking, shop: false }, version: 1,
});

describe('BackofficeLayout', () => {
  function render(booking: boolean | null) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    TestBed.inject(TenantThemeService).branding.set(booking === null ? null : branding(booking));
    const f = TestBed.createComponent(BackofficeLayout);
    f.detectChanges();
    return f;
  }

  afterEach(() => document.documentElement.removeAttribute('data-tenant-theme'));

  it('shows the online booking tab when the add-on is on', () => {
    const f = render(true);
    const tab = f.nativeElement.querySelector('a[href="/backoffice/booking"]') as HTMLAnchorElement;
    expect(tab.textContent).toContain('Online booking');
  });

  it('hides it when the add-on is off or the branding is not loaded yet, and follows a later change', () => {
    const f = render(false);
    expect(f.nativeElement.querySelector('a[href="/backoffice/booking"]')).toBeNull();
    TestBed.inject(TenantThemeService).branding.set(branding(true));
    f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/backoffice/booking"]')).not.toBeNull();
  });

  it('renders without a branding at all', () => {
    const f = render(null);
    expect(f.nativeElement.querySelector('a[href="/backoffice/settings"]')).not.toBeNull();
    expect(f.nativeElement.querySelector('a[href="/backoffice/booking"]')).toBeNull();
  });
});
