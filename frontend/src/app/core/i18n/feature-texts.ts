import { InjectionToken } from '@angular/core';

import type { Language } from './i18n.service';
import { ptPT } from './messages.pt-PT';

/** A dictionary as looked up at run time: by string, so plural forms (`.one`, `.many`, ...) resolve. */
export type Dictionary = Readonly<Partial<Record<string, string>>>;

/**
 * Feature dictionaries that are not in the start-up bundle. Each language of each feature is its own lazy chunk,
 * loaded by `textsFor(...)` on the routes that show the feature and again for a new language on a switch.
 *
 * To add a feature: put `<feature>.{en,pt-PT,pt-BR}.ts` in `<feature>/i18n/`, add its key type to `MessageKey` in
 * `messages.en.ts` (as `import type`), add a loader here, add the dictionaries to `all-texts.ts` (tests) and put
 * `textsFor('<feature>')` on its routes. Keys shown outside those routes (error codes, texts of the sign-in page) go
 * in an eager `<feature>-core.<lang>.ts` spread into `messages.<lang>.ts` instead.
 */
export const FEATURE_TEXTS = {
  admin: {
    en: () => import('../../admin/i18n/admin.en').then((m) => m.adminEn),
    'pt-PT': () => import('../../admin/i18n/admin.pt-PT').then((m) => m.adminPtPT),
    'pt-BR': () => import('../../admin/i18n/admin.pt-BR').then((m) => m.adminPtBR),
  },
  backoffice: {
    en: () => import('../../backoffice/i18n/backoffice.en').then((m) => m.backofficeEn),
    'pt-PT': () => import('../../backoffice/i18n/backoffice.pt-PT').then((m) => m.backofficePtPT),
    'pt-BR': () => import('../../backoffice/i18n/backoffice.pt-BR').then((m) => m.backofficePtBR),
  },
  booking: {
    en: () => import('../../booking/i18n/booking.en').then((m) => m.bookingEn),
    'pt-PT': () => import('../../booking/i18n/booking.pt-PT').then((m) => m.bookingPtPT),
    'pt-BR': () => import('../../booking/i18n/booking.pt-BR').then((m) => m.bookingPtBR),
  },
  catalogue: {
    en: () => import('../../catalogue/i18n/catalogue.en').then((m) => m.catalogueEn),
    'pt-PT': () => import('../../catalogue/i18n/catalogue.pt-PT').then((m) => m.cataloguePtPT),
    'pt-BR': () => import('../../catalogue/i18n/catalogue.pt-BR').then((m) => m.cataloguePtBR),
  },
  identity: {
    en: () => import('../../identity/i18n/identity.en').then((m) => m.identityEn),
    'pt-PT': () => import('../../identity/i18n/identity.pt-PT').then((m) => m.identityPtPT),
    'pt-BR': () => import('../../identity/i18n/identity.pt-BR').then((m) => m.identityPtBR),
  },
  shop: {
    en: () => import('../../shop/i18n/shop.en').then((m) => m.shopEn),
    'pt-PT': () => import('../../shop/i18n/shop.pt-PT').then((m) => m.shopPtPT),
    'pt-BR': () => import('../../shop/i18n/shop.pt-BR').then((m) => m.shopPtBR),
  },
  stock: {
    en: () => import('../../stock/i18n/stock.en').then((m) => m.stockEn),
    'pt-PT': () => import('../../stock/i18n/stock.pt-PT').then((m) => m.stockPtPT),
    'pt-BR': () => import('../../stock/i18n/stock.pt-BR').then((m) => m.stockPtBR),
  },
} satisfies Record<string, Record<Language, () => Promise<Dictionary>>>;

export type TextFeature = keyof typeof FEATURE_TEXTS;

/**
 * The core texts (shell, board, work orders, error codes, ...). Portuguese (Portugal) is the default and ships in the
 * start-up bundle; English and Brazilian Portuguese load when chosen, before the app starts for a stored choice.
 */
export const CORE_TEXTS: Record<Language, () => Promise<Dictionary>> = {
  'pt-PT': () => Promise.resolve(ptPT),
  en: () => import('./messages.en').then((m) => m.en),
  'pt-BR': () => import('./messages.pt-BR').then((m) => m.ptBR),
};

/** The core texts and every feature: what a language needs loaded. */
export type TextPart = TextFeature | 'core';

export type LoadedTexts = Partial<Record<Language, Partial<Record<TextPart, Dictionary>>>>;

/** Dictionaries already there when the service starts. Empty in the app; the unit tests preload all of them. */
export const PRELOADED_TEXTS = new InjectionToken<LoadedTexts>('PRELOADED_TEXTS', { factory: () => ({}) });
