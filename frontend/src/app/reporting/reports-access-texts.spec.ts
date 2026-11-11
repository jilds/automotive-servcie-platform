import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, provideRouter, Route, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { routes } from '../app.routes';
import { AuthService } from '../core/auth/auth.service';
import { allTexts } from '../core/i18n/all-texts';
import { PRELOADED_TEXTS } from '../core/i18n/feature-texts';
import { I18nService, LANGUAGES, textsFor } from '../core/i18n/i18n.service';
import { ptPT } from '../core/i18n/messages.pt-PT';
import { BACKOFFICE_ROUTES } from '../backoffice/backoffice.routes';
import { reportsEn } from './i18n/reports.en';
import { reportsCoreEn } from './i18n/reports-core.en';
import { REPORT_ROUTES } from './reporting.routes';

function find(list: Route[], path: string): Route | undefined {
  for (const r of list) {
    if (r.path === path) return r;
    const inner = find(r.children ?? [], path);
    if (inner) return inner;
  }
  return undefined;
}

describe('report routes and the owner role', () => {
  const guardOf = () => {
    const backoffice = find(routes, 'backoffice')!;
    expect(backoffice.canActivate).toHaveLength(1);
    return backoffice.canActivate![0] as CanActivateFn;
  };
  const run = (role: string | null) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { hasRole: (...roles: string[]) => role !== null && roles.includes(role) } }],
    });
    return TestBed.runInInjectionContext(() => guardOf()({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  };

  it('puts the reports under the backoffice, which only the owner may open', () => {
    const reports = BACKOFFICE_ROUTES[0].children!.find((r) => r.path === 'reports');
    expect(reports?.loadChildren).toBeDefined();
    expect(find(routes, 'reports')).toBeUndefined();
    expect(run('OWNER')).toBe(true);
  });

  it.each(['FRONT_DESK', 'MECHANIC', 'PLATFORM_ADMIN', null])('sends %s away from the reports to the board', (role) => {
    const result = run(role);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/board');
  });

  it('has the cost vs quote tab at the index and productivity and KPIs as sub-routes, all lazy', () => {
    const shell = REPORT_ROUTES[0];
    expect(shell.children?.map((r) => r.path)).toEqual(['', 'productivity', 'kpis']);
    expect(shell.children?.every((r) => typeof r.loadComponent === 'function')).toBe(true);
    expect(shell.resolve?.['texts']).toBeDefined();
  });
});

describe('report texts', () => {
  const placeholders = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
  const languages = LANGUAGES.map((l) => l.code);

  it('has every report key in all three languages, none empty', () => {
    const keys = [...Object.keys(reportsEn), ...Object.keys(reportsCoreEn), 'backoffice.tab.reports', 'backoffice.settings.labourCost', 'backoffice.settings.labourCostHelp',
      'backoffice.settings.labourCostInvalid', 'backoffice.settings.labourCostClear', 'backoffice.settings.labourCostClearLabel', 'backoffice.settings.retentionMonths',
      'backoffice.settings.retentionMonthsHelp', 'backoffice.settings.retentionMonthsInvalid'];
    for (const lang of languages) {
      const texts = allTexts(lang);
      for (const key of keys) expect(texts[key]?.trim(), `${lang} ${key}`).toBeTruthy();
    }
  });

  it('translates the two error codes of the contract and the three KPI features', () => {
    for (const lang of languages) {
      const texts = allTexts(lang);
      for (const key of ['error.report-range-too-large', 'error.report-too-many-rows', 'reports.availableWith.plan-12-bays', 'reports.availableWith.satisfaction', 'reports.availableWith.complaints']) {
        expect(texts[key], `${lang} ${key}`).toBeTruthy();
      }
    }
  });

  it('keeps the same placeholders in every language', () => {
    const en = allTexts('en');
    for (const lang of languages) {
      const texts = allTexts(lang);
      for (const key of [...Object.keys(reportsEn), 'backoffice.settings.retentionMonths']) expect(placeholders(texts[key]), `${lang} ${key}`).toEqual(placeholders(en[key]));
    }
  });

  it('does not use plural keys (`count` is reserved for them)', () => {
    for (const key of Object.keys(reportsEn)) expect(key.endsWith('.other') || key.endsWith('.one'), key).toBe(false);
  });

  it('respects the spelling of each Portuguese: reparação and Faturas in Portugal, serviço and retornos in Brazil', () => {
    const pt = allTexts('pt-PT');
    const br = allTexts('pt-BR');
    expect(pt['reports.subtitle']).toContain('reparação');
    expect(br['reports.subtitle']).toContain('serviço');
    expect(pt['reports.tab.costVsQuote']).toBe('Custo vs orçamento');
    expect(br['reports.tab.costVsQuote']).toBe('Custo x orçamento');
    expect(pt['reports.kpi.comebacks.title']).toBe('Regressos');
    expect(br['reports.kpi.comebacks.title']).toBe('Retornos');
    expect(pt['reports.filter.pageSize']).toBe(br['reports.filter.pageSize']);
    expect(pt['reports.cvq.col.hours']).toContain('registadas');
    expect(br['reports.cvq.col.hours']).toContain('apontadas');
    expect(pt['reports.period.invalid']).toContain('Indique');
    expect(br['reports.period.invalid']).toContain('Informe');
  });

  it('names VAT as IVA in both Portuguese languages', () => {
    expect(allTexts('pt-PT')['reports.kpi.value.net']).toBe('sem IVA');
    expect(allTexts('pt-BR')['reports.kpi.value.net']).toBe('sem IVA');
  });
});

describe('report texts load lazily', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [{ provide: PRELOADED_TEXTS, useValue: {} }] });
  });
  afterEach(() => localStorage.clear());

  it('keeps only the error codes in the start-up texts; the rest comes with the report routes', async () => {
    const i18n = TestBed.inject(I18nService);
    expect(i18n.has('error.report-range-too-large')).toBe(true);
    expect(i18n.t('error.report-too-many-rows')).toBe(ptPT['error.report-too-many-rows']);
    expect(i18n.has('reports.title')).toBe(false);

    const done = await TestBed.runInInjectionContext(() => textsFor('reports')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    expect(done).toBe(true);
    expect(i18n.t('reports.title')).toBe('Relatórios');
    expect(i18n.t('reports.tab.costVsQuote')).toBe('Custo vs orçamento');
  });

  it('loads the report texts in the new language on a language switch', async () => {
    const i18n = TestBed.inject(I18nService);
    await i18n.load('reports');
    await i18n.use('en');
    expect(i18n.t('reports.tab.productivity')).toBe('Productivity');
    expect(i18n.t('error.report-range-too-large')).toBe(reportsCoreEn['error.report-range-too-large']);
  });
});
