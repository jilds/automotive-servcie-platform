import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { MoneyPipe } from './shared/money.pipe';

describe('App', () => {
  it('creates the app shell', async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });
});

describe('MoneyPipe', () => {
  it('formats cents as euros', () => {
    const text = new MoneyPipe().transform(10148).replace(/\s/g, ' ');
    expect(text).toContain('101,48');
    expect(text).toContain('€');
  });

  it('renders nothing for hidden prices', () => {
    expect(new MoneyPipe().transform(null)).toBe('');
  });
});
