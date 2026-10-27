import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Toasts } from '../backoffice/toasts';
import { I18nService } from '../core/i18n/i18n.service';
import { ShopSettingsPage, shopSettingsForm, toShopSettings } from './shop-settings';

describe('shop settings form', () => {
  it('takes whole hold days from 1 to 14 and a note up to 500 characters', () => {
    const form = shopSettingsForm();
    for (const [days, valid] of [[1, true], [14, true], [0, false], [15, false], [2.5, false], [null, false]] as const) {
      form.controls.holdDays.setValue(days);
      expect(form.controls.holdDays.valid).toBe(valid);
    }
    form.controls.pickupNote.setValue('x'.repeat(501));
    expect(form.controls.pickupNote.valid).toBe(false);
  });

  it('sends a blank note as null', () => {
    const form = shopSettingsForm();
    form.setValue({ holdDays: 5, pickupNote: '   ' });
    expect(toShopSettings(form, 2)).toEqual({ holdDays: 5, pickupNote: null, version: 2 });
  });
});

describe('ShopSettingsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), Toasts] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!;

  function open() {
    const f = TestBed.createComponent(ShopSettingsPage);
    f.detectChanges();
    http.expectOne('/api/v1/shop/settings').flush({ holdDays: 3, pickupNote: null, version: 0 });
    f.detectChanges();
    return f;
  }

  function type(f: ComponentFixture<unknown>, selector: string, value: string) {
    const input = f.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    f.detectChanges();
  }

  it('creates the settings with version 0 and keeps the returned version', () => {
    const f = open();
    type(f, '#ss-hold', '7');
    type(f, '#ss-note', 'Levante ao balcão, das 9h às 18h.');
    f.componentInstance.save();
    const put = http.expectOne('/api/v1/shop/settings');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ holdDays: 7, pickupNote: 'Levante ao balcão, das 9h às 18h.', version: 0 });
    put.flush({ holdDays: 7, pickupNote: 'Levante ao balcão, das 9h às 18h.', version: 1 });
    f.detectChanges();
    expect(TestBed.inject(Toasts).items().map((t) => t.text)).toContain('Shop settings saved.');

    type(f, '#ss-hold', '8');
    f.componentInstance.save();
    expect(http.expectOne('/api/v1/shop/settings').request.body.version).toBe(1);
  });

  it('does not send hold days outside 1 to 14', () => {
    const f = open();
    type(f, '#ss-hold', '30');
    f.componentInstance.save();
    f.detectChanges();
    http.expectNone('/api/v1/shop/settings');
    expect(text(f)).toContain('Enter a whole number from 1 to 14.');
  });

  it('reloads the stored settings on stale-data and says why', () => {
    const f = open();
    type(f, '#ss-hold', '5');
    f.componentInstance.save();
    http.expectOne('/api/v1/shop/settings').flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/shop/settings').flush({ holdDays: 10, pickupNote: 'Outra nota', version: 4 });
    f.detectChanges();
    expect(text(f)).toContain('Someone else changed this record');
    expect((f.nativeElement.querySelector('#ss-hold') as HTMLInputElement).value).toBe('10');
    expect(f.componentInstance.confirmLeave()).toBe(true);
  });
});
