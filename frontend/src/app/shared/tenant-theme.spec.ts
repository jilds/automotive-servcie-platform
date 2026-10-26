import { TestBed } from '@angular/core/testing';

import { Branding } from '../core/api/models';
import { applyPalette, brandPalette, contrast, ensureContrast, TenantThemeService, textOn } from './theme.service';

const BRANDING: Branding = {
  slug: 'oficina',
  name: 'Oficina Azul',
  displayName: 'Oficina Azul',
  primaryColor: '#0b3d91',
  primaryTextColor: '#ffffff',
  accentColor: '#ffcc00',
  logoUrl: null, privacyUrl: null,
  version: 0,
};

describe('workshop theme palette', () => {
  it('computes WCAG contrast and picks readable button text', () => {
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(textOn('#0b3d91')).toBe('#ffffff');
    expect(textOn('#ffd43b')).toBe('#000000');
    expect(textOn('#777777')).toBe('#000000');
  });

  it('returns null when nothing is customised, so the ASP default applies', () => {
    expect(brandPalette({ primaryColor: null, accentColor: null })).toBeNull();
    expect(brandPalette({ primaryColor: 'not-a-colour', accentColor: null })).toBeNull();
  });

  it('derives links and tints that stay readable in light and dark mode', () => {
    // A light yellow is unreadable as link text on white; the palette darkens it for light mode.
    const p = brandPalette({ primaryColor: '#ffd43b', accentColor: null })!;
    expect(p['--asp-brand-primary']).toBe('#ffd43b');
    expect(p['--asp-brand-primary-rgb']).toBe('255, 212, 59');
    expect(p['--asp-brand-on-primary']).toBe('#000000');
    for (const bg of ['#ffffff', '#f3f4f6']) expect(contrast(p['--asp-brand-link'], bg)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(p['--asp-brand-emphasis'], p['--asp-brand-subtle-bg'])).toBeGreaterThanOrEqual(4.5);
    // A dark blue is unreadable on the dark page; the palette lightens it for dark mode.
    const dark = brandPalette({ primaryColor: '#0b3d91', accentColor: '#0b3d91' })!;
    for (const bg of ['#17191c', '#1f2226']) expect(contrast(dark['--asp-brand-link-dark'], bg)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(dark['--asp-brand-accent-chrome'], '#1c1f23')).toBeGreaterThanOrEqual(3);
    // Without an accent, the accent follows the primary colour.
    expect(p['--asp-brand-accent']).toBe('#ffd43b');
  });

  it('leaves a colour alone when it already reads well', () => {
    expect(ensureContrast('#000000', ['#ffffff'], 4.5, '#000000')).toBe('#000000');
  });
});

describe('TenantThemeService', () => {
  afterEach(() => TestBed.inject(TenantThemeService).clear());

  it('applies the theme to <html> and removes it again', () => {
    const theme = TestBed.inject(TenantThemeService);
    const root = document.documentElement;

    theme.apply(BRANDING);

    expect(root.hasAttribute('data-tenant-theme')).toBe(true);
    expect(root.style.getPropertyValue('--asp-brand-primary')).toBe('#0b3d91');
    expect(root.style.getPropertyValue('--asp-brand-accent')).toBe('#ffcc00');
    expect(theme.branding()?.name).toBe('Oficina Azul');

    theme.apply({ ...BRANDING, primaryColor: null, accentColor: null });
    expect(root.hasAttribute('data-tenant-theme')).toBe(false);
    expect(root.style.getPropertyValue('--asp-brand-primary')).toBe('');
    // The name and logo still show; only the colours fall back.
    expect(theme.branding()?.name).toBe('Oficina Azul');

    theme.apply(BRANDING);
    theme.clear();
    expect(root.hasAttribute('data-tenant-theme')).toBe(false);
    expect(theme.branding()).toBeNull();
  });

  it('scopes a palette to one element for the editor preview', () => {
    const preview = document.createElement('div');
    applyPalette(preview, brandPalette(BRANDING));
    expect(preview.getAttribute('data-tenant-theme')).toBe('');
    expect(document.documentElement.hasAttribute('data-tenant-theme')).toBe(false);
  });
});
