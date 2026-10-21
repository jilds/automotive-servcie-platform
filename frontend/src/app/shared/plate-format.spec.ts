import { FormControl } from '@angular/forms';

import {
  currentMonth,
  formatPlate,
  MONTH_ERROR,
  normalizePlate,
  plateMessage,
  plateValidator,
  plateVariant,
  PT_PLATE_ERROR,
  ptPlateEra,
  registrationControls,
  registrationMonthValidator,
} from './plate-format';

describe('Portuguese plate validation', () => {
  it.each([
    ['AA-12-BC', 'PT_2020'],
    ['12-AB-34', 'PT_2005'],
    ['12-34-AB', 'PT_1992'],
    ['AB-12-34', 'PT_1937'],
  ] as const)('%s fits the %s layout', (plate, era) => {
    expect(ptPlateEra(plate)).toBe(era);
  });

  it.each(['aa-12-bc', 'aa 12 bc', 'AA12BC', ' aa-12 bc ', 'Aa-12-bC'])('accepts any case, spaces or hyphens: %j', (plate) => {
    expect(ptPlateEra(plate)).toBe('PT_2020');
  });

  it.each(['', 'ABC123', '1A-2B-3C', 'AA-12-B', 'AA-12-BCD', '12-AB-3', 'A1-23-BC', '12-A3-4B', 'AB-CD-EF', '12-34-56'])(
    'rejects %j',
    (plate) => {
      expect(ptPlateEra(plate)).toBeNull();
    },
  );

  it('normalises to upper case without separators, as the API stores it', () => {
    expect(normalizePlate(' aa-12 bc ')).toBe('AA12BC');
    expect(normalizePlate('12.ab/34')).toBe('12AB34');
  });

  it('validates only Portuguese plates and lets empty values through', () => {
    let country = 'PT';
    const control = new FormControl('ABC123', { nonNullable: true, validators: plateValidator(() => country) });
    expect(control.errors).toEqual({ [PT_PLATE_ERROR]: true });
    control.setValue('12 ab 34');
    expect(control.errors).toBeNull();
    control.setValue('');
    expect(control.errors).toBeNull();
    country = 'ES';
    control.setValue('1234 BCD');
    expect(control.errors).toBeNull();
  });

  it('does not re-check an unchanged stored plate, but does once the plate or the country changes', () => {
    const controls = registrationControls('PT', { stored: () => ({ plate: 'XY1234Z', country: 'PT' }) });
    controls.plate.setValue('xy-1234 z');
    expect(controls.plate.errors).toBeNull();
    controls.plate.setValue('XY1234ZZ');
    expect(controls.plate.hasError(PT_PLATE_ERROR)).toBe(true);
    controls.plate.setValue('XY1234Z');
    controls.registrationCountry.setValue('ES');
    controls.registrationCountry.setValue('PT');
    expect(controls.plate.errors).toBeNull();
  });

  it('re-checks the plate when the registration country changes', () => {
    const controls = registrationControls('DE');
    controls.plate.setValue('B AB 1234');
    expect(controls.plate.valid).toBe(true);
    controls.registrationCountry.setValue('PT');
    expect(controls.plate.hasError(PT_PLATE_ERROR)).toBe(true);
  });

  it('requires a plate in vehicle forms but not in searches', () => {
    expect(registrationControls().plate.hasError('required')).toBe(true);
    expect(registrationControls('PT', { plateRequired: false }).plate.valid).toBe(true);
  });

  it('shows the layout error once six characters are typed or the field is left, and server marks first', () => {
    const { plate } = registrationControls();
    plate.setValue('AA1');
    plate.markAsDirty();
    expect(plateMessage(plate)).toBeNull();
    plate.markAsTouched();
    expect(plateMessage(plate)).toBe('error.invalid-plate');
    plate.markAsUntouched();
    plate.setValue('AA1234Z');
    expect(plateMessage(plate)).toBe('error.invalid-plate');
    plate.setValue('AA-12-BC');
    plate.setErrors({ server: 'duplicate-plate' });
    expect(plateMessage(plate)).toBe('error.duplicate-plate');
  });
});

describe('formatPlate', () => {
  it.each([
    ['aa-12-bc', 'AA-12-BC'],
    ['12 ab 34', '12-AB-34'],
    ['1234ab', '12-34-AB'],
    ['ab1234', 'AB-12-34'],
  ])('formats the Portuguese plate %j as %j', (input, display) => {
    expect(formatPlate(input, 'PT')).toBe(display);
    expect(formatPlate(input, 'pt')).toBe(display);
  });

  it('leaves invalid Portuguese and foreign plates as typed, upper case and trimmed', () => {
    expect(formatPlate(' abc123 ', 'PT')).toBe('ABC123');
    expect(formatPlate(' b-ab 1234 ', 'DE')).toBe('B-AB 1234');
    expect(formatPlate('12ab34', null)).toBe('12AB34');
  });
});

describe('registration month', () => {
  const now = () => new Date(2026, 9, 21);
  const control = new FormControl('', { nonNullable: true, validators: registrationMonthValidator(now) });

  it.each(['', '1937-01', '2012-04', '2026-10'])('accepts %j', (month) => {
    control.setValue(month);
    expect(control.errors).toBeNull();
  });

  it.each(['1936-12', '2026-11', '2030-01', '2012-13', '2012-4', 'abc'])('rejects %j', (month) => {
    control.setValue(month);
    expect(control.errors).toEqual({ [MONTH_ERROR]: true });
  });

  it('formats the current month', () => {
    expect(currentMonth(new Date(2026, 0, 5))).toBe('2026-01');
  });
});

describe('plateVariant', () => {
  it('draws 2020+ plates with the EU band and no date strip, even with a known month', () => {
    expect(plateVariant('PT_2020', '2021-03')).toEqual({ kind: 'eu', dateStrip: null });
  });

  it('adds the year/month strip to 2005–2020 plates when the month is known', () => {
    expect(plateVariant('PT_2005', '2012-04')).toEqual({ kind: 'eu', dateStrip: { year: '12', month: '04' } });
    expect(plateVariant('PT_2005', null)).toEqual({ kind: 'eu', dateStrip: null });
  });

  it('shows the strip on 1992–2005 plates only from January 1998', () => {
    expect(plateVariant('PT_1992', '1997-12')).toEqual({ kind: 'eu', dateStrip: null });
    expect(plateVariant('PT_1992', '1998-01')).toEqual({ kind: 'eu', dateStrip: { year: '98', month: '01' } });
    expect(plateVariant('PT_1992', '2003-07')).toEqual({ kind: 'eu', dateStrip: { year: '03', month: '07' } });
    expect(plateVariant('PT_1992', null)).toEqual({ kind: 'eu', dateStrip: null });
  });

  it('draws 1937–1992 plates black, without band or strip', () => {
    expect(plateVariant('PT_1937', '1980-05')).toEqual({ kind: 'classic', dateStrip: null });
  });

  it('draws other countries and unknown layouts as a neutral plate', () => {
    expect(plateVariant(null, '2012-04')).toEqual({ kind: 'neutral', dateStrip: null });
    expect(plateVariant(undefined, undefined)).toEqual({ kind: 'neutral', dateStrip: null });
  });
});
