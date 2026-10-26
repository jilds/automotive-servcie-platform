import { en, type MessageKey } from './en';
import { ptBR } from './pt-BR';
import { ptPT } from './pt-PT';
import type { Lang } from './routes';

export type { MessageKey };

const dictionaries: Record<Lang, Record<MessageKey, string>> = { 'pt-PT': ptPT, en, 'pt-BR': ptBR };

/** Text of a key in a language; `{name}` placeholders are filled from params. */
export function t(lang: Lang, key: MessageKey, params: Record<string, string | number> = {}): string {
  return dictionaries[lang][key].replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

/** A translator bound to one language, for use in components. */
export function useT(lang: Lang): (key: MessageKey, params?: Record<string, string | number>) => string {
  return (key, params) => t(lang, key, params);
}
