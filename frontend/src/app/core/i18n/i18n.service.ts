import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { effect, inject, Injectable, signal } from '@angular/core';

import { WorkOrderStatus } from '../api/models';
import { en, MessageKey, PluralKey } from './messages.en';
import { ptBR } from './messages.pt-BR';
import { ptPT } from './messages.pt-PT';

export type Language = 'pt-PT' | 'en' | 'pt-BR';

export const LANGUAGES: readonly { code: Language; label: string }[] = [
  { code: 'pt-PT', label: 'Português (PT)' },
  { code: 'en', label: 'English' },
  { code: 'pt-BR', label: 'Português (BR)' },
];

export const DEFAULT_LANGUAGE: Language = 'pt-PT';

/** Looked up by string so plural forms (`.one`, `.many`, ...) resolve; each dictionary file is typed in full. */
const DICTIONARIES: Record<Language, Readonly<Partial<Record<string, string>>>> = { 'pt-PT': ptPT, en, 'pt-BR': ptBR };
const STORAGE_KEY = 'asp.lang';
const PLURAL_RULES = new Map<Language, Intl.PluralRules>();

function pluralRules(lang: Language): Intl.PluralRules {
  let rules = PLURAL_RULES.get(lang);
  if (!rules) {
    rules = new Intl.PluralRules(lang);
    PLURAL_RULES.set(lang, rules);
  }
  return rules;
}

/** Holds the chosen UI language and translates message keys. The choice is remembered in this browser. */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  readonly lang = signal<Language>(readStoredLanguage());

  constructor() {
    effect(() => {
      const lang = this.lang();
      this.document.documentElement.lang = lang;
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        // Storage can be blocked (private window); the choice then lasts for this visit only.
      }
    });
  }

  use(lang: Language): void {
    this.lang.set(lang);
  }

  /**
   * Translates a key, filling {placeholders} from params. A plural key (`history.lines`) with a numeric `count`
   * uses the form `Intl.PluralRules` picks for the language, for example `history.lines.one` for 1.
   */
  t(key: MessageKey | PluralKey, params?: Record<string, string | number | null | undefined>): string {
    const text = this.text(key, params?.['count']);
    return params ? text.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? '')) : text;
  }

  private text(key: string, count: unknown): string {
    const lang = this.lang();
    const dictionary = DICTIONARIES[lang];
    const other = `${key}.other`;
    if (typeof count === 'number' && other in en) {
      return dictionary[`${key}.${pluralRules(lang).select(count)}`] ?? dictionary[other] ?? en[other as MessageKey];
    }
    return dictionary[key] ?? en[key as MessageKey] ?? key;
  }

  /** Turns an API error (RFC 9457 problem detail with a `code`) into a translated message. */
  error(err: unknown): string {
    if (!(err instanceof HttpErrorResponse)) return this.t('error.unknown');
    if (err.status === 0) return this.t('error.unreachable');
    if (err.status === 403) return this.t('error.forbidden');
    const key = `error.${err.error?.code}`;
    if (key in en) return this.t(key as MessageKey);
    return err.error?.detail ?? this.t('error.failed', { status: err.status });
  }
}

function readStoredLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && stored in DICTIONARIES) return stored as Language;
  } catch {
    // Fall through to the default.
  }
  return DEFAULT_LANGUAGE;
}

/** Message key for a work order status label. */
export function statusKey(status: WorkOrderStatus): `status.${WorkOrderStatus}` {
  return `status.${status}`;
}
