import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { plainName } from './public-forms';

describe('plainName', () => {
  const check = (value: string) => plainName(new FormControl(value));

  it('accepts a plain name with digits and accents', () => {
    expect(check('Ana Silva 2028')).toBeNull();
    expect(check('João Conceição')).toBeNull();
  });

  it('refuses links, markup, control characters and line separators', () => {
    for (const bad of ['see https://x.pt', 'www.x.pt', '<b>Ana</b>', 'Ana'+'\n'+'Silva', 'Ana'+'\u2028'+'Silva', 'Ana'+'\u0085']) {
      expect(check(bad)).toEqual({ plainName: true });
    }
  });
});
