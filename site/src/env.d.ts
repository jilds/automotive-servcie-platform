/// <reference types="astro/client" />

interface ImportMetaEnv {
  /** Public URL of the site, used for canonical links, Open Graph and the sitemap. */
  readonly PUBLIC_SITE_URL?: string;
  /** Origin of the ASP backend that receives proposal requests; empty means the same origin as the site. */
  readonly PUBLIC_API_ORIGIN?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
