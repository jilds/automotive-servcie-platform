import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { I18nService } from './core/i18n/i18n.service';
import { en } from './core/i18n/messages.en';
import { ptBR } from './core/i18n/messages.pt-BR';
import { ptPT } from './core/i18n/messages.pt-PT';
import { addressForm, toAddress } from './customer/address';
import { MoneyPipe } from './shared/money.pipe';

beforeEach(() => {
  try {
    localStorage.clear();
  } catch {
    // ignore
  }
});

describe('App', () => {
  it('creates the app shell', async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });
});

describe('MoneyPipe', () => {
  const pipe = () => TestBed.runInInjectionContext(() => new MoneyPipe());

  it('formats cents as euros', () => {
    const text = pipe().transform(10148).replace(/\s/g, ' ');
    expect(text).toContain('101,48');
    expect(text).toContain('€');
  });

  it('follows the UI language', () => {
    TestBed.inject(I18nService).use('en');
    expect(pipe().transform(10148)).toContain('101.48');
  });

  it('renders nothing for hidden prices', () => {
    expect(pipe().transform(null)).toBe('');
  });
});

describe('I18nService', () => {
  it('defaults to Portuguese (Portugal)', () => {
    const i18n = TestBed.inject(I18nService);
    expect(i18n.lang()).toBe('pt-PT');
    expect(i18n.t('nav.customers')).toBe('Clientes');
  });

  it('switches language and fills placeholders', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.use('en');
    expect(i18n.t('workOrder.moveTo', { status: 'Ready' })).toBe('Move to Ready');
    i18n.use('pt-BR');
    expect(i18n.t('vehicles.plate')).toBe('Placa');
  });

  it('translates API errors by their code and falls back to the detail', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.use('en');
    const problem = (status: number, error: unknown) => new HttpErrorResponse({ status, error });
    expect(i18n.error(problem(409, { code: 'duplicate-plate', detail: 'x' }))).toBe('A vehicle with that plate already exists.');
    expect(i18n.error(problem(500, { code: 'something-new', detail: 'Server detail' }))).toBe('Server detail');
    expect(i18n.error(problem(0, null))).toBe('Cannot reach the server.');
  });

  it('has no empty translations', () => {
    for (const dict of [en, ptPT, ptBR] as Record<string, string>[]) {
      expect(Object.keys(dict).sort()).toEqual(Object.keys(en).sort());
      expect(Object.values(dict).filter((v) => !v.trim())).toEqual([]);
    }
  });
});

describe('address form', () => {
  it('is valid when empty and sends no address', () => {
    const form = addressForm();
    expect(form.valid).toBe(true);
    expect(toAddress(form)).toBeNull();
  });

  it('needs street, postal code and locality together', () => {
    const form = addressForm();
    form.patchValue({ street: 'Rua Augusta 10' });
    expect(form.hasError('incomplete')).toBe(true);
    form.patchValue({ postalCode: '1100-053', locality: 'Lisboa' });
    expect(form.valid).toBe(true);
    expect(toAddress(form)).toEqual({ street: 'Rua Augusta 10', detail: null, postalCode: '1100-053', locality: 'Lisboa', country: 'PT' });
  });

  it('checks the Portuguese postal code format only for Portugal', () => {
    const form = addressForm();
    form.patchValue({ street: 'Calle Mayor 1', postalCode: '28013', locality: 'Madrid' });
    expect(form.hasError('postalCode')).toBe(true);
    form.patchValue({ country: 'ES' });
    expect(form.valid).toBe(true);
  });
});
