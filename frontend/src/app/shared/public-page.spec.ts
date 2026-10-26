import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Branding } from '../core/api/models';
import { PublicPage } from './public-page';

@Component({
  imports: [PublicPage],
  template: `@if (shown()) {
    <app-public-page [slug]="slug()"><p id="content">Marcar</p></app-public-page>
  }`,
})
class Host {
  readonly slug = signal('oficina-azul');
  readonly shown = signal(true);
}

const BRANDING: Branding = {
  slug: 'oficina-azul', name: 'Oficina Azul', displayName: null, primaryColor: '#0b3d91', primaryTextColor: '#ffffff',
  accentColor: null, logoUrl: '/api/v1/branding/oficina-azul/logo?v=abc', privacyUrl: null, version: 3,
};

describe('PublicPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.documentElement.removeAttribute('data-tenant-theme');
  });

  function open() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture;
  }

  it('wears the workshop theme, shows its logo and name, and the page content', async () => {
    const fixture = open();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('#content')?.textContent).toBe('Marcar');
    expect(page.querySelector('.app-brand-name')?.textContent?.trim()).toBe('ASP');

    http.expectOne('/api/v1/branding/oficina-azul').flush(BRANDING);
    fixture.detectChanges();

    expect(page.querySelector('.app-brand-name')?.textContent?.trim()).toBe('Oficina Azul');
    expect(page.querySelector('img.app-brand-logo')?.getAttribute('src')).toBe(BRANDING.logoUrl);
    expect(document.documentElement.hasAttribute('data-tenant-theme')).toBe(true);
    expect(page.querySelector('app-language-select')).not.toBeNull();
    expect(page.querySelector('app-theme-toggle')).not.toBeNull();
  });

  it('falls back to the plain ASP look when the workshop cannot be loaded', () => {
    const fixture = open();
    http.expectOne('/api/v1/branding/oficina-azul').flush(
      { code: 'workshop-not-found', status: 404 }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('.app-brand-name')?.textContent?.trim()).toBe('ASP');
    expect(page.querySelector('img.app-brand-logo')).toBeNull();
    expect(document.documentElement.hasAttribute('data-tenant-theme')).toBe(false);
    expect(page.querySelector('#content')).not.toBeNull();
  });

  it('loads the new workshop when the slug changes and clears the theme when it goes', () => {
    const fixture = open();
    http.expectOne('/api/v1/branding/oficina-azul').flush(BRANDING);
    fixture.detectChanges();

    fixture.componentInstance.slug.set('oficina-verde');
    fixture.detectChanges();
    expect(document.documentElement.hasAttribute('data-tenant-theme')).toBe(false);
    http.expectOne('/api/v1/branding/oficina-verde').flush({ ...BRANDING, slug: 'oficina-verde', name: 'Oficina Verde' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.app-brand-name')?.textContent?.trim()).toBe('Oficina Verde');

    fixture.componentInstance.shown.set(false);
    fixture.detectChanges();
    expect(document.documentElement.hasAttribute('data-tenant-theme')).toBe(false);
  });
});
