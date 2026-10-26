// @ts-check
import sitemap from '@astrojs/sitemap';
import { defineConfig } from 'astro/config';
import { loadEnv } from 'vite';

import { href, routeOf } from './src/i18n/routes.ts';

const env = loadEnv(process.env.NODE_ENV ?? 'production', process.cwd(), 'PUBLIC_');
const site = (env.PUBLIC_SITE_URL || 'https://asp.example').replace(/\/+$/, '');

// A build without the app origin posts the form to the site itself (405) and links canonical URLs to the placeholder.
// Only CI (a build check, never deployed) may build without them; the runbook passes both.
if (process.argv.includes('build') && !process.env.CI) {
  const missing = ['PUBLIC_SITE_URL', 'PUBLIC_API_ORIGIN'].filter((name) => !env[name]);
  if (missing.length > 0) {
    throw new Error(`Set ${missing.join(' and ')} for a site build (site/README.md); CI=true skips this check.`);
  }
}

export default defineConfig({
  site,
  output: 'static',
  trailingSlash: 'ignore',
  build: {
    format: 'directory',
    // The site is served with a Content-Security-Policy (script-src 'self'): no inline scripts or styles.
    inlineStylesheets: 'never',
  },
  vite: {
    build: { assetsInlineLimit: 0 },
  },
  devToolbar: { enabled: false },
  integrations: [
    sitemap({
      filter: (page) => routeOf(new URL(page).pathname) !== undefined,
      serialize(item) {
        const route = routeOf(new URL(item.url).pathname);
        if (route) {
          item.links = /** @type {const} */ (['pt-PT', 'en', 'pt-BR']).map((lang) => ({
            lang,
            url: site + href(route.page, lang),
          }));
        }
        return item;
      },
    }),
  ],
});
