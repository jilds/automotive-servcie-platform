import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { effect, inject, Injectable, signal } from '@angular/core';

import { WorkOrderStatus } from '../api/models';
import { en, MessageKey } from './messages.en';
import { ptBR } from './messages.pt-BR';
import { ptPT } from './messages.pt-PT';

export type Language = 'pt-PT' | 'en' | 'pt-BR';

export const LANGUAGES: readonly { code: Language; label: string }[] = [
  { code: 'pt-PT', label: 'Português (PT)' },
  { code: 'en', label: 'English' },
  { code: 'pt-BR', label: 'Português (BR)' },
];

export const DEFAULT_LANGUAGE: Language = 'pt-PT';

const DICTIONARIES: Record<Language, Record<MessageKey, string>> = { 'pt-PT': ptPT, en, 'pt-BR': ptBR };
const STORAGE_KEY = 'asp.lang';

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

  /** Translates a key, filling {placeholders} from params. */
  t(key: MessageKey, params?: Record<string, string | number | null | undefined>): string {
    const text = DICTIONARIES[this.lang()][key] ?? en[key] ?? key;
    return params ? text.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? '')) : text;
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
