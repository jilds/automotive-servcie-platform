// Production build gate: a site that still shows a TODO placeholder (prices, offer, legal entity) must not ship.
// Runs after `astro build`. It is skipped for CI builds (CI=true) and for builds of the placeholder site URL
// (https://asp.example, the dev default), which are never deployed.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PLACEHOLDER_HOST = 'asp.example';
const TODO = /class="(?:[^"]*\s)?todo(?:\s[^"]*)?"/;

/** Relative paths of the HTML files under `dir` that contain a TODO marker. */
export function findTodos(dir, base = dir) {
  const found = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) found.push(...findTodos(path, base));
    else if (entry.name.endsWith('.html') && TODO.test(readFileSync(path, 'utf8'))) found.push(path.slice(base.length + 1));
  }
  return found;
}

/** The site URL the build was made for, read from the robots.txt sitemap line. */
export function builtHost(dir) {
  const robots = join(dir, 'robots.txt');
  if (!existsSync(robots)) return undefined;
  const match = /^Sitemap:\s*https?:\/\/([^/\s]+)/im.exec(readFileSync(robots, 'utf8'));
  return match?.[1];
}

export function shouldCheck(env, dir) {
  if (env.CI === 'true') return false;
  return builtHost(dir) !== PLACEHOLDER_HOST;
}

function main() {
  const dir = fileURLToPath(new URL('../dist', import.meta.url));
  if (!shouldCheck(process.env, dir)) {
    console.log('dist-check: skipped (CI build or placeholder site URL).');
    return;
  }
  const todos = findTodos(dir);
  if (todos.length > 0) {
    console.error(`dist-check: ${todos.length} page(s) still show a TODO placeholder:\n  ${todos.join('\n  ')}`);
    console.error('Fill the prices, the offer (src/offer.ts) and the legal entity, or build with CI=true for a check build.');
    process.exit(1);
  }
  console.log('dist-check: no TODO placeholders in dist.');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
