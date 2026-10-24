import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { computed, effect, EnvironmentProviders, inject, Injectable, provideAppInitializer, signal } from '@angular/core';
import { ResolveFn } from '@angular/router';

import { WorkOrderStatus } from '../api/models';
import {
  CORE_TEXTS,
  Dictionary,
  FEATURE_TEXTS,
  LoadedTexts,
  PRELOADED_TEXTS,
  TextFeature,
  TextPart,
} from './feature-texts';
import type { MessageKey, PluralKey } from './messages.en';
import { ptPT } from './messages.pt-PT';

export type Language = 'pt-PT' | 'en' | 'pt-BR';

/** Each language by its own name; `short` is the code on the compact phone switcher (the same in every language). */
export const LANGUAGES: readonly { code: Language; label: string; short: string }[] = [
  { code: 'pt-PT', label: 'Português (PT)', short: 'PT' },
  { code: 'en', label: 'English', short: 'EN' },
  { code: 'pt-BR', label: 'Português (BR)', short: 'BR' },
];

export const DEFAULT_LANGUAGE: Language = 'pt-PT';

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

/**
 * Holds the chosen UI language and translates message keys. The choice is remembered in this browser.
 *
 * Only the Portuguese (Portugal) core texts are in the start-up bundle. Other languages and the feature texts load
 * when needed: features with their routes (`textsFor`), a stored language before the app starts (`provideTexts`).
 * A language switch first loads the core and every feature in use in the new language and only then changes the
 * language, so a key never shows on screen.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  /** The language chosen in this browser, which may still be loading at start-up. */
  private readonly stored = readStoredLanguage();
  /** Loaded dictionaries, per language and part (the core texts and each feature). */
  private readonly loaded = signal<LoadedTexts>(withDefault(inject(PRELOADED_TEXTS)));
  readonly lang = signal<Language>(this.loaded()[this.stored]?.core ? this.stored : DEFAULT_LANGUAGE);
  private readonly dictionaries = computed(() => {
    const loaded = this.loaded();
    const merge = (lang: Language): Dictionary => Object.assign({}, ...Object.values(loaded[lang] ?? {}));
    return { 'pt-PT': merge('pt-PT'), en: merge('en'), 'pt-BR': merge('pt-BR') } satisfies Record<Language, Dictionary>;
  });
  /** The parts some page asked for; a language switch loads them in the new language. */
  private readonly inUse = new Set<TextPart>(['core']);
  private readonly pending = new Map<string, Promise<void>>();
  private switchCount = 0;

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

  /**
   * Switches the language once the texts of the features in use are loaded in it; at once when they already are.
   * When several switches overlap, the last one wins. If a dictionary cannot be loaded the language stays as it was.
   */
  use(lang: Language): Promise<void> {
    const switchCount = ++this.switchCount;
    const missing = (): TextPart[] => [...this.inUse].filter((part) => !this.loaded()[lang]?.[part]);
    if (missing().length === 0) {
      this.lang.set(lang);
      return Promise.resolve();
    }
    // Again until nothing is missing: a page opened during the switch may have asked for more features.
    const loadAll = (): Promise<void> => (missing().length ? this.fetch(lang, missing()).then(loadAll) : Promise.resolve());
    return loadAll().then(
      () => {
        if (switchCount === this.switchCount) this.lang.set(lang);
      },
      (err: unknown) => console.error('Could not load the texts for', lang, err),
    );
  }

  /** Loads the texts of these features in the UI language, and keeps them in step with later language switches. */
  load(...features: TextFeature[]): Promise<void> {
    features.forEach((feature) => this.inUse.add(feature));
    return this.fetch(this.lang(), features);
  }

  /** Switches to the language stored in this browser, when it is not the one loaded at start-up. */
  restoreStoredLanguage(): Promise<void> {
    return this.stored === this.lang() ? Promise.resolve() : this.use(this.stored);
  }

  /** True when the key has a text in the UI language now. */
  has(key: string): boolean {
    return key in this.dictionaries()[this.lang()];
  }

  private fetch(lang: Language, parts: TextPart[]): Promise<void> {
    return Promise.all(
      parts
        .filter((part) => !this.loaded()[lang]?.[part])
        .map((part) => {
          const id = `${lang}:${part}`;
          let loading = this.pending.get(id);
          if (!loading) {
            loading = (part === 'core' ? CORE_TEXTS[lang] : FEATURE_TEXTS[part][lang])()
              .then((dictionary) =>
                this.loaded.update((all) => ({ ...all, [lang]: { ...all[lang], [part]: dictionary } })),
              )
              .finally(() => this.pending.delete(id));
            this.pending.set(id, loading);
          }
          return loading;
        }),
    ).then(() => undefined);
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
    const all = this.dictionaries();
    const dictionary = all[lang];
    const other = `${key}.other`;
    if (typeof count === 'number' && other in dictionary) {
      return dictionary[`${key}.${pluralRules(lang).select(count)}`] ?? dictionary[other] ?? key;
    }
    return dictionary[key] ?? all[DEFAULT_LANGUAGE][key] ?? key;
  }

  /** Turns an API error (RFC 9457 problem detail with a `code`) into a translated message. */
  error(err: unknown): string {
    if (!(err instanceof HttpErrorResponse)) return this.t('error.unknown');
    if (err.status === 0) return this.t('error.unreachable');
    // A known code wins, also on 403 (for example `workshop-suspended` at login).
    const key = `error.${err.error?.code}`;
    if (this.has(key)) return this.t(key as MessageKey);
    if (err.status === 403) return this.t('error.forbidden');
    return err.error?.detail ?? this.t('error.failed', { status: err.status });
  }
}

/** The default language's core texts are always there; preloaded dictionaries (tests) come on top. */
function withDefault(preloaded: LoadedTexts): LoadedTexts {
  return { ...preloaded, [DEFAULT_LANGUAGE]: { core: ptPT, ...preloaded[DEFAULT_LANGUAGE] } };
}

function readStoredLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && LANGUAGES.some((l) => l.code === stored)) return stored as Language;
  } catch {
    // Fall through to the default.
  }
  return DEFAULT_LANGUAGE;
}

/**
 * Route resolver that loads the texts of these features before the route shows (on a parent route it covers the
 * children). A failed load does not block the page: it shows the texts that are there and logs the error.
 */
export function textsFor(...features: TextFeature[]): ResolveFn<boolean> {
  return () =>
    inject(I18nService)
      .load(...features)
      .then(
        () => true,
        (err: unknown) => {
          console.error('Could not load the texts of', features, err);
          return false;
        },
      );
}

/** Loads the language stored in this browser before the app starts, so the first screen is already in it. */
export function provideTexts(): EnvironmentProviders {
  return provideAppInitializer(() => inject(I18nService).restoreStoredLanguage());
}

/** Message key for a work order status label. */
export function statusKey(status: WorkOrderStatus): `status.${WorkOrderStatus}` {
  return `status.${status}`;
}
