import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';

import { builtHost, findTodos, shouldCheck } from './dist-check.mjs';

const dirs = [];
function dist(files) {
  const dir = mkdtempSync(join(tmpdir(), 'asp-dist-'));
  dirs.push(dir);
  for (const [name, content] of Object.entries(files)) {
    const path = join(dir, name);
    mkdirSync(join(path, '..'), { recursive: true });
    writeFileSync(path, content);
  }
  return dir;
}
afterEach(() => dirs.splice(0).forEach((d) => rmSync(d, { recursive: true, force: true })));

describe('dist-check', () => {
  it('finds pages with a todo marker, in any folder', () => {
    const dir = dist({
      'index.html': '<p class="x">ok</p>',
      'precos/index.html': '<span class="todo">TODO</span>',
      'en/a/index.html': '<span class="b todo c">TODO</span>',
    });
    expect(findTodos(dir).sort()).toEqual([join('en', 'a', 'index.html'), join('precos', 'index.html')].sort());
  });

  it('does not mistake other classes for the marker', () => {
    expect(findTodos(dist({ 'index.html': '<p class="todolist">x</p><p class="todo-x">y</p>' }))).toEqual([]);
  });

  it('reads the built host and skips CI and placeholder builds', () => {
    const real = dist({ 'robots.txt': 'Sitemap: https://oficina.pt/sitemap-index.xml\n' });
    const dummy = dist({ 'robots.txt': 'Sitemap: https://asp.example/sitemap-index.xml\n' });
    expect(builtHost(real)).toBe('oficina.pt');
    expect(shouldCheck({}, real)).toBe(true);
    expect(shouldCheck({ CI: 'true' }, real)).toBe(false);
    expect(shouldCheck({}, dummy)).toBe(false);
  });
});
