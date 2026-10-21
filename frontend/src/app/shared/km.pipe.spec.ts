import { formatKm, KmPipe } from './km.pipe';

/** Intl uses no-break spaces as group and unit separators; compare them as plain spaces. */
const plain = (text: string) => text.replace(/[\u00a0\u202f]/g, ' ');

describe('km pipe', () => {
  const pipe = new KmPipe();

  it('groups digits the way each language does', () => {
    expect(plain(pipe.transform(100100, 'pt-PT'))).toBe('100 100 km');
    expect(plain(pipe.transform(100100, 'en'))).toBe('100,100 km');
    expect(plain(pipe.transform(100100, 'pt-BR'))).toBe('100.100 km');
  });

  it('shows small and zero readings', () => {
    expect(plain(formatKm(0, 'pt-PT'))).toBe('0 km');
    expect(plain(formatKm(950, 'en'))).toBe('950 km');
  });

  it('shows a dash when there is no reading', () => {
    expect(pipe.transform(null, 'pt-PT')).toBe('—');
    expect(pipe.transform(undefined, 'en')).toBe('—');
  });
});
