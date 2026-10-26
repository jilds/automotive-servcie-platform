import type { APIRoute } from 'astro';

// robots.txt with the sitemap address taken from PUBLIC_SITE_URL (astro.config.mjs `site`).
export const GET: APIRoute = ({ site }) => {
  const sitemap = new URL('/sitemap-index.xml', site ?? 'https://asp.example').href;
  return new Response(`User-agent: *\nAllow: /\n\nSitemap: ${sitemap}\n`, {
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
  });
};
