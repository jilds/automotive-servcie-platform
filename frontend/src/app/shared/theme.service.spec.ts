import { TestBed } from '@angular/core/testing';

import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
    document.documentElement.removeAttribute('data-bs-theme');
  });

  it('follows the system theme by default', () => {
    const theme = TestBed.inject(ThemeService);
    TestBed.tick();
    expect(theme.mode()).toBe('system');
    // The test DOM reports no dark preference, so "system" resolves to light.
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('light');
  });

  it('applies and remembers an explicit choice', () => {
    const theme = TestBed.inject(ThemeService);
    theme.use('dark');
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
    expect(localStorage.getItem('asp.theme')).toBe('dark');

    theme.use('system');
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('light');
    expect(localStorage.getItem('asp.theme')).toBe('system');
  });

  it('restores the stored choice and ignores unknown values', () => {
    localStorage.setItem('asp.theme', 'light');
    expect(TestBed.inject(ThemeService).mode()).toBe('light');

    TestBed.resetTestingModule();
    localStorage.setItem('asp.theme', 'neon');
    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });
});
