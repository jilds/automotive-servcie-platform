import { allTexts } from '../core/i18n/all-texts';
import { LANGUAGES } from '../core/i18n/i18n.service';
import { billingEn } from './i18n/billing.en';
import { billingCoreEn } from './i18n/billing-core.en';

/** Every error code the invoicing contract lists as user-facing (`docs/api/release-4.md`, "Error codes added"). */
const ERROR_CODES = [
  'invoice-not-found', 'payment-not-found', 'customer-nif-required', 'customer-address-required', 'invalid-phone', 'already-invoiced',
  'work-order-not-invoiceable', 'invoice-not-issued', 'invoice-not-retryable', 'invoice-not-discardable', 'invoice-not-voidable',
  'invoice-has-payments', 'credit-note-exists', 'payment-exceeds-balance', 'payment-not-pending', 'payment-failed',
  'provider-not-configured', 'provider-rejected', 'provider-unavailable', 'request-id-reused', 'try-again', 'customer-name-too-long',
];

const placeholders = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

describe('billing texts', () => {
  const languages = LANGUAGES.map((l) => l.code);

  it('has an error text in every language for every code of the contract', () => {
    for (const lang of languages) {
      const texts = allTexts(lang);
      for (const code of ERROR_CODES) expect(texts[`error.${code}`], `${lang} error.${code}`).toBeTruthy();
    }
  });

  it('has the menu item and every billing key in all three languages, none empty', () => {
    const keys = [...Object.keys(billingEn), ...Object.keys(billingCoreEn)];
    for (const lang of languages) {
      const texts = allTexts(lang);
      for (const key of keys) expect(texts[key]?.trim(), `${lang} ${key}`).toBeTruthy();
    }
  });

  it('keeps the same placeholders in every language', () => {
    const en = allTexts('en');
    for (const lang of languages) {
      const texts = allTexts(lang);
      for (const key of Object.keys(billingEn)) expect(placeholders(texts[key]), `${lang} ${key}`).toEqual(placeholders(en[key]));
    }
  });

  it('uses the Portuguese terms IVA and NIF, and the Brazilian spelling where it differs', () => {
    expect(allTexts('pt-PT')['billing.lines.vat']).toBe('IVA');
    expect(allTexts('pt-BR')['billing.lines.vat']).toBe('IVA');
    expect(allTexts('pt-PT')['billing.detail.noNif']).toContain('NIF');
    expect(allTexts('pt-PT')['billing.filter.searchPlaceholder']).toContain('matrícula');
    expect(allTexts('pt-BR')['billing.filter.searchPlaceholder']).toContain('placa');
    expect(allTexts('pt-PT')['billing.payments.phone']).toContain('Telemóvel');
    expect(allTexts('pt-BR')['billing.payments.phone']).toContain('Celular');
    expect(allTexts('pt-PT')['billing.action.pdf']).toBe('Descarregar PDF');
    expect(allTexts('pt-BR')['billing.action.pdf']).toBe('Baixar PDF');
  });

  it('makes the menu item and the sandbox banner say what the contract asks', () => {
    expect(allTexts('pt-PT')['nav.invoices']).toBe('Faturas');
    expect(allTexts('en')['billing.sandbox.banner']).toBe('Sandbox: this is not a fiscal document.');
  });
});
