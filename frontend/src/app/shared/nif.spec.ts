import { FormControl } from '@angular/forms';

import { isValidPortugueseNif, nifValidator } from './nif';

describe('Portuguese NIF', () => {
  it('accepts valid NIFs (check digit and first digits), like the backend', () => {
    for (const nif of ['123456789', '509999999', '999999990', '501442600', '286999994', '450000001', '710000006']) {
      expect(isValidPortugueseNif(nif), nif).toBe(true);
    }
  });

  it('rejects a wrong check digit, unknown first digits and other lengths', () => {
    for (const nif of ['123456780', '509999990', '400000006', '700000004', '000000000', '12345678', '1234567890', '12345678a']) {
      expect(isValidPortugueseNif(nif), nif).toBe(false);
    }
  });

  it('checks the check digit only for Portugal; other countries need just 9 digits', () => {
    let country = 'PT';
    const control = new FormControl('123456780', { nonNullable: true, validators: nifValidator(() => country) });
    expect(control.errors).toEqual({ nif: true });

    country = 'ES';
    control.updateValueAndValidity();
    expect(control.errors).toBeNull();

    control.setValue('12345');
    expect(control.errors).toEqual({ pattern: true });
    control.setValue('');
    expect(control.errors).toBeNull();
  });
});
