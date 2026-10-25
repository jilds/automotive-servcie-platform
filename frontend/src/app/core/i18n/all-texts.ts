import { adminEn } from '../../admin/i18n/admin.en';
import { adminPtBR } from '../../admin/i18n/admin.pt-BR';
import { adminPtPT } from '../../admin/i18n/admin.pt-PT';
import { backofficeEn } from '../../backoffice/i18n/backoffice.en';
import { backofficePtBR } from '../../backoffice/i18n/backoffice.pt-BR';
import { backofficePtPT } from '../../backoffice/i18n/backoffice.pt-PT';
import { bookingEn } from '../../booking/i18n/booking.en';
import { bookingPtBR } from '../../booking/i18n/booking.pt-BR';
import { bookingPtPT } from '../../booking/i18n/booking.pt-PT';
import { catalogueEn } from '../../catalogue/i18n/catalogue.en';
import { cataloguePtBR } from '../../catalogue/i18n/catalogue.pt-BR';
import { cataloguePtPT } from '../../catalogue/i18n/catalogue.pt-PT';
import { identityEn } from '../../identity/i18n/identity.en';
import { identityPtBR } from '../../identity/i18n/identity.pt-BR';
import { identityPtPT } from '../../identity/i18n/identity.pt-PT';
import { stockEn } from '../../stock/i18n/stock.en';
import { stockPtBR } from '../../stock/i18n/stock.pt-BR';
import { stockPtPT } from '../../stock/i18n/stock.pt-PT';
import type { Language } from './i18n.service';
import type { LoadedTexts, TextPart } from './feature-texts';
import { en } from './messages.en';
import { ptBR } from './messages.pt-BR';
import { ptPT } from './messages.pt-PT';

/**
 * Every dictionary, statically imported. For tests only (preloaded through `src/test-providers.ts`, and used by the
 * completeness checks); the app loads them lazily through `feature-texts.ts`.
 */
export const ALL_TEXTS = {
  en: { core: en, admin: adminEn, backoffice: backofficeEn, booking: bookingEn, catalogue: catalogueEn, identity: identityEn, stock: stockEn },
  'pt-PT': { core: ptPT, admin: adminPtPT, backoffice: backofficePtPT, booking: bookingPtPT, catalogue: cataloguePtPT, identity: identityPtPT, stock: stockPtPT },
  'pt-BR': { core: ptBR, admin: adminPtBR, backoffice: backofficePtBR, booking: bookingPtBR, catalogue: cataloguePtBR, identity: identityPtBR, stock: stockPtBR },
} satisfies Record<Language, Record<TextPart, object>> & LoadedTexts;

/** All texts of a language in one dictionary, as the app has them once every feature is loaded. */
export function allTexts(lang: Language): Readonly<Record<string, string>> {
  return Object.assign({}, ...Object.values(ALL_TEXTS[lang]));
}
