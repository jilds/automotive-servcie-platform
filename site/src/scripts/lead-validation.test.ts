import { describe, expect, it } from 'vitest';

import {
  fromServerErrors,
  isOneLineNoLink,
  retryAfterMinutes,
  toPayload,
  validate,
  type LeadInput,
  type LeadPayload,
} from './lead-validation';

function input(overrides: Partial<LeadInput> = {}): LeadInput {
  return {
    name: 'Ana Silva',
    workshopName: 'Oficina Azul',
    phone: '912345678',
    email: '',
    district: 'Lisboa',
    vehicleType: 'BOTH',
    message: '',
    privacyAccepted: true,
    marketingConsent: false,
    website: '',
    ...overrides,
  };
}

const payload = (overrides: Partial<LeadInput> = {}): LeadPayload => toPayload(input(overrides));

describe('isOneLineNoLink', () => {
  it.each(['Oficina Azul', 'Auto São João, Lda.', 'A&B (Norte) - 2', ''])('accepts %j', (value) => {
    expect(isOneLineNoLink(value)).toBe(true);
  });

  it.each(['https://x.example', 'WWW.x.pt', 'a<b', 'a>b', 'a\nb', 'a\u0007b', 'a\u0085b', 'a\u2028b'])(
    'refuses %j',
    (value) => {
      expect(isOneLineNoLink(value)).toBe(false);
    },
  );
});

describe('validate', () => {
  it('accepts a complete request', () => {
    expect(validate(payload())).toEqual({});
  });

  it('trims every text field and keeps the honeypot as entered', () => {
    const p = toPayload(input({ name: '  Ana  ', email: ' a@b.pt ', website: ' x ' }));
    expect(p.name).toBe('Ana');
    expect(p.email).toBe('a@b.pt');
    expect(p.website).toBe(' x ');
  });

  it('needs a name and a workshop name, one line without links', () => {
    expect(validate(payload({ name: '', workshopName: 'www.spam.pt' }))).toEqual({
      name: 'required',
      workshopName: 'name',
    });
    expect(validate(payload({ name: 'a'.repeat(121) })).name).toBe('name');
    expect(validate(payload({ name: 'a'.repeat(120) })).name).toBeUndefined();
  });

  it('needs a phone or an e-mail', () => {
    expect(validate(payload({ phone: '', email: '' }))).toEqual({ phone: 'contact', email: 'contact' });
    expect(validate(payload({ phone: '', email: 'ana@oficina.pt' }))).toEqual({});
  });

  it('checks the phone characters and length', () => {
    expect(validate(payload({ phone: '+351 (21) 123-45' })).phone).toBeUndefined();
    expect(validate(payload({ phone: '12a' })).phone).toBe('phone');
    expect(validate(payload({ phone: '1'.repeat(31) })).phone).toBe('phone');
  });

  it('checks the e-mail shape', () => {
    expect(validate(payload({ email: 'ana@' })).email).toBe('email');
  });

  it('limits the district and the message', () => {
    expect(validate(payload({ district: 'd'.repeat(61) })).district).toBe('district');
    expect(validate(payload({ district: 'Porto://x' })).district).toBe('district');
    expect(validate(payload({ message: 'm'.repeat(2000) })).message).toBeUndefined();
    expect(validate(payload({ message: 'm'.repeat(2001) })).message).toBe('message');
  });

  it('needs a known vehicle type and the privacy consent', () => {
    expect(validate(payload({ vehicleType: 'TRUCK', privacyAccepted: false }))).toEqual({
      vehicleType: 'vehicleType',
      privacyAccepted: 'privacy',
    });
  });
});

describe('fromServerErrors', () => {
  it('maps a missing contact to the contact rule on both fields', () => {
    expect(fromServerErrors({ phone: 'x', email: 'x' }, payload({ phone: '', email: '' }))).toEqual({
      phone: 'contact',
      email: 'contact',
    });
  });

  it('maps known fields to local rules and drops unknown keys', () => {
    expect(
      fromServerErrors({ name: 'x', workshopName: 'x', 'address.x': 'x', privacyAccepted: 'x' }, payload({ name: '' })),
    ).toEqual({ name: 'required', workshopName: 'name', privacyAccepted: 'privacy' });
  });
});

describe('retryAfterMinutes', () => {
  const now = Date.parse('2026-10-26T10:00:00Z');

  it('rounds seconds up to whole minutes, at least one', () => {
    expect(retryAfterMinutes('61', now)).toBe(2);
    expect(retryAfterMinutes('60', now)).toBe(1);
    expect(retryAfterMinutes('0', now)).toBe(1);
  });

  it('reads an HTTP date', () => {
    expect(retryAfterMinutes('Mon, 26 Oct 2026 10:30:00 GMT', now)).toBe(30);
  });

  it('gives undefined when missing or unreadable', () => {
    expect(retryAfterMinutes(null, now)).toBeUndefined();
    expect(retryAfterMinutes(' ', now)).toBeUndefined();
    expect(retryAfterMinutes('soon', now)).toBeUndefined();
  });
});
