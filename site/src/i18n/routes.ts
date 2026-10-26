// Every page of the site, with its path per language. pt-PT lives at the root, EN under /en/, pt-BR under /br/.
// Paths have no leading slash and end with "/" (directory output); "" is the pt-PT home page.
export const langs = ['pt-PT', 'en', 'pt-BR'] as const;
export type Lang = (typeof langs)[number];
export const defaultLang: Lang = 'pt-PT';

export const pageIds = ['home', 'auto', 'moto', 'features', 'proposal', 'privacy', 'terms', 'cookies'] as const;
export type PageId = (typeof pageIds)[number];

const prefixes: Record<Lang, string> = { 'pt-PT': '', en: 'en/', 'pt-BR': 'br/' };

const slugs: Record<PageId, Record<Lang, string>> = {
  home: { 'pt-PT': '', en: '', 'pt-BR': '' },
  auto: { 'pt-PT': 'oficinas-auto', en: 'car-workshops', 'pt-BR': 'oficinas-carros' },
  moto: { 'pt-PT': 'oficinas-motas', en: 'motorcycle-workshops', 'pt-BR': 'oficinas-motos' },
  features: { 'pt-PT': 'funcionalidades', en: 'features', 'pt-BR': 'funcionalidades' },
  proposal: { 'pt-PT': 'pedir-proposta', en: 'request-a-proposal', 'pt-BR': 'pedir-proposta' },
  privacy: { 'pt-PT': 'privacidade', en: 'privacy', 'pt-BR': 'privacidade' },
  terms: { 'pt-PT': 'termos', en: 'terms', 'pt-BR': 'termos' },
  cookies: { 'pt-PT': 'cookies', en: 'cookies', 'pt-BR': 'cookies' },
};

/** Path without the leading slash, for example "en/features/". */
export function routePath(page: PageId, lang: Lang): string {
  const slug = slugs[page][lang];
  return prefixes[lang] + (slug ? `${slug}/` : '');
}

/** Site-relative URL, for example "/en/features/". */
export function href(page: PageId, lang: Lang): string {
  return `/${routePath(page, lang)}`;
}

export interface Route {
  page: PageId;
  lang: Lang;
  path: string;
}

export const routes: Route[] = pageIds.flatMap((page) => langs.map((lang) => ({ page, lang, path: routePath(page, lang) })));

/** The page and language of a site-relative path, used by the sitemap to add the alternates. */
export function routeOf(pathname: string): Route | undefined {
  const path = pathname.replace(/^\/+/, '');
  return routes.find((r) => r.path === path);
}

/** BCP 47 language for <html lang>, hreflang and og:locale. */
export const ogLocale: Record<Lang, string> = { 'pt-PT': 'pt_PT', en: 'en_GB', 'pt-BR': 'pt_BR' };
