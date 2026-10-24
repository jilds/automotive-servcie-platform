import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { I18nService, Language, LANGUAGES } from './i18n.service';
import { allTexts } from './all-texts';
import { MessageKey, PluralKey } from './messages.en';

const DICTIONARIES: Record<Language, Partial<Record<string, string>>> = {
  en: allTexts('en'),
  'pt-PT': allTexts('pt-PT'),
  'pt-BR': allTexts('pt-BR'),
};
const PLURAL_KEYS = Object.keys(DICTIONARIES.en)
  .filter((key) => key.endsWith('.other'))
  .map((key) => key.slice(0, -'.other'.length) as PluralKey);

describe('I18nService plural messages', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
  });

  const cases: [Language, PluralKey, number, string][] = [
    ['en', 'history.lines', 0, '0 lines'],
    ['en', 'history.lines', 1, '1 line'],
    ['en', 'history.lines', 2, '2 lines'],
    // CLDR: European Portuguese uses "one" for 1 only, so 0 takes the plural.
    ['pt-PT', 'history.lines', 0, '0 linhas'],
    ['pt-PT', 'history.lines', 1, '1 linha'],
    ['pt-PT', 'history.lines', 2, '2 linhas'],
    // CLDR: Brazilian Portuguese uses "one" for 0 and 1.
    ['pt-BR', 'history.lines', 0, '0 item'],
    ['pt-BR', 'history.lines', 1, '1 item'],
    ['pt-BR', 'history.lines', 2, '2 itens'],
    ['en', 'backoffice.dashboard.openOrders', 1, '1 open'],
    ['pt-PT', 'backoffice.dashboard.openOrders', 0, '0 abertas'],
    ['pt-PT', 'backoffice.dashboard.openOrders', 1, '1 aberta'],
    ['pt-PT', 'backoffice.dashboard.openOrders', 2, '2 abertas'],
    ['pt-BR', 'backoffice.dashboard.openOrders', 0, '0 aberta'],
    ['pt-BR', 'backoffice.dashboard.openOrders', 1, '1 aberta'],
    ['pt-BR', 'backoffice.dashboard.openOrders', 2, '2 abertas'],
  ];

  it.each(cases)('%s %s with count %i', (lang, key, count, expected) => {
    i18n.use(lang);
    expect(i18n.t(key, { count })).toBe(expected);
  });

  it('follows Intl.PluralRules, including the Portuguese "many" form', () => {
    i18n.use('pt-PT');
    expect(new Intl.PluralRules('pt-PT').select(1_000_000)).toBe('many');
    expect(i18n.t('history.lines', { count: 1_000_000 })).toBe('1000000 de linhas');
  });

  it('has every plural form Intl.PluralRules can pick, in every language', () => {
    expect(PLURAL_KEYS).toEqual(expect.arrayContaining(['history.lines', 'backoffice.dashboard.openOrders']));
    for (const { code } of LANGUAGES) {
      const forms = new Intl.PluralRules(code).resolvedOptions().pluralCategories;
      for (const key of PLURAL_KEYS) {
        for (const form of forms) {
          expect(DICTIONARIES[code][`${key}.${form}`], `${code} ${key}.${form}`).toBeTruthy();
        }
      }
    }
  });

  it('leaves messages without a count alone', () => {
    i18n.use('en');
    expect(i18n.t('checkIn.lastMileage', { km: '5 km' })).toBe('last mileage 5 km');
  });
});

describe('I18nService problem codes', () => {
  const problem = (status: number, error: unknown) => new HttpErrorResponse({ status, error });
  const codes: [string, MessageKey][] = [
    ['last-owner', 'error.last-owner'],
    ['own-account-change', 'error.own-account-change'],
    ['user-not-found', 'error.user-not-found'],
    ['invalid-mechanic', 'error.invalid-mechanic'],
    ['try-again', 'error.try-again'],
  ];

  it.each(codes)('translates %s in every language', (code, key) => {
    const i18n = TestBed.inject(I18nService);
    for (const { code: lang } of LANGUAGES) {
      i18n.use(lang);
      const text = i18n.error(problem(409, { code, detail: 'server text' }));
      expect(text).toBe(DICTIONARIES[lang][key]);
      expect(text).not.toBe('server text');
    }
  });

  it('falls back to the server detail for an unknown code', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.use('en');
    expect(i18n.error(problem(409, { code: 'email-taken' }))).toBe('That email is already registered.');
    expect(i18n.error(problem(500, { code: 'brand-new', detail: 'Server detail' }))).toBe('Server detail');
  });
});
