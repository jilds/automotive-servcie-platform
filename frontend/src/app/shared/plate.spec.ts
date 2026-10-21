import { TestBed } from '@angular/core/testing';

import { PlateEra } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { Plate } from './plate';

function render(inputs: { plate: string | null; era?: PlateEra | null; country?: string | null; registrationDate?: string | null; size?: 'sm' | 'md' | 'lg' }) {
  TestBed.inject(I18nService).use('en');
  const fixture = TestBed.createComponent(Plate);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  fixture.detectChanges();
  const host = fixture.nativeElement as HTMLElement;
  return {
    host,
    number: host.querySelector('.plate-number')?.textContent?.trim(),
    band: host.querySelector('.plate-band'),
    stars: host.querySelectorAll('.plate-stars circle').length,
    date: [...host.querySelectorAll('.plate-date span')].map((s) => s.textContent),
    code: host.querySelector('.plate-code')?.textContent?.trim() ?? null,
  };
}

describe('Plate', () => {
  it('draws a 2020 plate white with the EU band, twelve stars and P, without a date strip', () => {
    const p = render({ plate: 'AA-12-BC', era: 'PT_2020', country: 'PT', registrationDate: '2021-03' });
    expect(p.host.classList).toContain('plate');
    expect(p.host.dataset['kind']).toBe('eu');
    expect(p.band?.textContent?.trim()).toBe('P');
    expect(p.stars).toBe(12);
    expect(p.number).toBe('AA-12-BC');
    expect(p.date).toEqual([]);
  });

  it('adds the year over month strip to a 2005 plate', () => {
    const p = render({ plate: '12-AB-34', era: 'PT_2005', country: 'PT', registrationDate: '2012-04' });
    expect(p.host.dataset['kind']).toBe('eu');
    expect(p.date).toEqual(['12', '04']);
  });

  it('shows the strip on a 1992 plate only from 1998', () => {
    expect(render({ plate: '12-34-AB', era: 'PT_1992', country: 'PT', registrationDate: '1997-06' }).date).toEqual([]);
    TestBed.resetTestingModule();
    expect(render({ plate: '12-34-AB', era: 'PT_1992', country: 'PT', registrationDate: '1999-11' }).date).toEqual(['99', '11']);
  });

  it('draws a 1937 plate as a black classic without band or strip', () => {
    const p = render({ plate: 'AB-12-34', era: 'PT_1937', country: 'PT', registrationDate: '1975-02' });
    expect(p.host.dataset['kind']).toBe('classic');
    expect(p.band).toBeNull();
    expect(p.date).toEqual([]);
  });

  it('draws a foreign plate neutral with its country code', () => {
    const p = render({ plate: 'B-AB 1234', era: null, country: 'DE' });
    expect(p.host.dataset['kind']).toBe('neutral');
    expect(p.band).toBeNull();
    expect(p.code).toBe('DE');
    expect(p.host.getAttribute('aria-label')).toBe('Plate B-AB 1234 (DE)');
  });

  it('is an image labelled with the plate, and formats a stored Portuguese plate', () => {
    const p = render({ plate: '12AB34', era: 'PT_2005', country: 'PT', size: 'sm' });
    expect(p.host.getAttribute('role')).toBe('img');
    expect(p.host.getAttribute('aria-label')).toBe('Plate 12-AB-34');
    expect(p.host.dataset['size']).toBe('sm');
    expect(p.number).toBe('12-AB-34');
  });

  it('translates the label', () => {
    TestBed.inject(I18nService).use('pt-PT');
    const fixture = TestBed.createComponent(Plate);
    fixture.componentRef.setInput('plate', '12-AB-34');
    fixture.componentRef.setInput('country', 'PT');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).getAttribute('aria-label')).toBe('Matrícula 12-AB-34');
  });

  it('shows a dash when the vehicle is gone', () => {
    expect(render({ plate: null }).number).toBe('—');
  });
});
