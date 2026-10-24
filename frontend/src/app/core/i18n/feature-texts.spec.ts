import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { PRELOADED_TEXTS } from './feature-texts';
import { I18nService, textsFor } from './i18n.service';

/** The app as it starts: only the Portuguese (Portugal) core texts, everything else loads on demand. */
describe('Lazy texts', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [{ provide: PRELOADED_TEXTS, useValue: {} }] });
  });

  afterEach(() => localStorage.clear());

  it('starts with the Portuguese core texts only', () => {
    const i18n = TestBed.inject(I18nService);
    expect(i18n.lang()).toBe('pt-PT');
    expect(i18n.t('nav.board')).toBe('Quadro');
    expect(i18n.t('error.service-inactive')).toBe('Esse serviço já não se faz.');
    expect(i18n.has('catalogue.title')).toBe(false);
  });

  it('loads a feature with its route', async () => {
    const i18n = TestBed.inject(I18nService);
    const resolve = textsFor('catalogue');

    const done = await TestBed.runInInjectionContext(() =>
      resolve({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(done).toBe(true);
    expect(i18n.t('catalogue.title')).toBe('Serviços e pacotes');
  });

  it('switches the language only once the core and the features in use are loaded in it', async () => {
    const i18n = TestBed.inject(I18nService);
    await i18n.load('catalogue');

    const switching = i18n.use('en');
    expect(i18n.lang()).toBe('pt-PT');
    expect(i18n.t('catalogue.title')).toBe('Serviços e pacotes');
    await switching;

    expect(i18n.lang()).toBe('en');
    expect(i18n.t('nav.board')).toBe('Board');
    expect(i18n.t('catalogue.title')).toBe('Services and packages');
  });

  it('loads a feature asked for during a switch in the new language too', async () => {
    const i18n = TestBed.inject(I18nService);
    const switching = i18n.use('pt-BR');
    const loading = i18n.load('backoffice');
    await Promise.all([switching, loading]);

    expect(i18n.lang()).toBe('pt-BR');
    expect(i18n.has('backoffice.tab.team')).toBe(true);
    expect(i18n.t('backoffice.tab.team')).not.toBe('backoffice.tab.team');
  });

  it('lets the last of overlapping switches win', async () => {
    const i18n = TestBed.inject(I18nService);
    const toEnglish = i18n.use('en');
    const back = i18n.use('pt-PT');
    await Promise.all([toEnglish, back]);
    expect(i18n.lang()).toBe('pt-PT');
  });

  it('restores the language stored in this browser before the app starts', async () => {
    localStorage.setItem('asp.lang', 'pt-BR');
    const i18n = TestBed.inject(I18nService);
    expect(i18n.lang()).toBe('pt-PT');

    await i18n.restoreStoredLanguage();

    expect(i18n.lang()).toBe('pt-BR');
    expect(i18n.t('nav.checkIn')).toBe('Recepção');
  });
});
