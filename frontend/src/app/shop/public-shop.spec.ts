import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { I18nService } from '../core/i18n/i18n.service';
import { cartKey } from './cart';
import { PublicShop } from './public-shop';
import { checkoutOutcome, statusLink } from './shop-forms';
import { ShopOrderStatusPage } from './shop-order-status';
import { PublicProduct, PublicShopOrder } from './shop.models';

const SLUG = 'oficina-azul';
const BASE = `/api/v1/public/${SLUG}/shop`;
const PRODUCTS: PublicProduct[] = [
  { id: 'p1', sku: 'FLT-OL-01', name: 'Filtro de óleo', brand: 'Bosch', unit: 'un', unitPriceWithVatCents: 1476, availability: 'IN_STOCK' },
  { id: 'p2', sku: 'LMP-H7', name: 'Lâmpada H7', brand: null, unit: 'un', unitPriceWithVatCents: 500, availability: 'LAST_UNITS' },
  { id: 'p3', sku: 'ESC-01', name: 'Escovas', brand: 'Valeo', unit: 'par', unitPriceWithVatCents: 2000, availability: 'OUT' },
];
const BRANDING = { slug: SLUG, name: 'Oficina Azul', displayName: null, primaryColor: null, primaryTextColor: null, accentColor: null, logoUrl: null, privacyUrl: null, version: 1 };

const problem = (status: number, body: object) => new HttpErrorResponse({ status, error: body });

function storedCart(): unknown {
  return JSON.parse(localStorage.getItem(cartKey(SLUG)) ?? 'null');
}

describe('checkoutOutcome', () => {
  it('sorts the order errors of the contract', () => {
    expect(checkoutOutcome(problem(409, { code: 'insufficient-stock', productId: 'p1' }))).toEqual({ kind: 'line', productId: 'p1', problem: 'insufficient-stock' });
    expect(checkoutOutcome(problem(409, { code: 'product-not-listed', productId: 'p9' }))).toEqual({ kind: 'line', productId: 'p9', problem: 'product-not-listed' });
    expect(checkoutOutcome(problem(409, { code: 'product-not-listed' }))).toEqual({ kind: 'line', productId: null, problem: 'product-not-listed' });
    expect(checkoutOutcome(problem(400, { code: 'privacy-consent-required' }))).toEqual({ kind: 'fields', cart: false });
    expect(checkoutOutcome(problem(400, { code: 'invalid-nif', errors: { nif: 'x' } }))).toEqual({ kind: 'fields', cart: false });
    expect(checkoutOutcome(problem(400, { code: 'validation', errors: { name: 'x' } }))).toEqual({ kind: 'fields', cart: false });
    expect(checkoutOutcome(problem(400, { code: 'validation', errors: { lines: 'x' } }))).toEqual({ kind: 'fields', cart: true });
    expect(checkoutOutcome(problem(429, { code: 'too-many-requests' }))).toEqual({ kind: 'message', code: 'too-many-requests' });
    expect(checkoutOutcome(problem(409, { code: 'try-again' }))).toEqual({ kind: 'message', code: 'try-again' });
    expect(checkoutOutcome(problem(404, { code: 'add-on-not-available' }))).toEqual({ kind: 'page', state: 'unavailable' });
    expect(checkoutOutcome(problem(404, { code: 'workshop-not-found' }))).toEqual({ kind: 'page', state: 'not-found' });
  });

  it('builds the status link', () => {
    expect(statusLink('https://app.example.pt/', SLUG, 'tok/en')).toBe('https://app.example.pt/w/oficina-azul/shop/tok%2Fen');
  });
});

describe('PublicShop', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
    localStorage.clear();
    document.documentElement.removeAttribute('data-tenant-theme');
  });

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');
  const button = (f: ComponentFixture<unknown>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent!.trim() === label) as HTMLButtonElement;

  function open(products: PublicProduct[] = PRODUCTS) {
    const f = TestBed.createComponent(PublicShop);
    f.componentRef.setInput('slug', SLUG);
    f.detectChanges();
    http.expectOne(`/api/v1/branding/${SLUG}`).flush(BRANDING);
    http.expectOne((r) => r.url === `${BASE}/products` && !r.params.has('q')).flush(products);
    f.detectChanges();
    return f;
  }

  function addButton(f: ComponentFixture<unknown>, name: string): HTMLButtonElement {
    return f.nativeElement.querySelector(`button[aria-label="Add ${name} to the cart"]`) as HTMLButtonElement;
  }

  function toCheckout(f: ComponentFixture<PublicShop>) {
    addButton(f, 'Filtro de óleo').click();
    addButton(f, 'Filtro de óleo').click();
    f.detectChanges();
    button(f, 'Go to checkout').click();
    f.detectChanges();
    f.componentInstance['form'].patchValue({ name: 'Ana Silva', phone: '912345678', nif: '123456789', privacyAccepted: true });
    f.detectChanges();
  }

  it('lists the products with price incl. VAT and availability; an out-of-stock one cannot be added', () => {
    const f = open();
    expect(text(f)).toContain('Filtro de óleo');
    expect(text(f)).toContain('€14.76');
    expect(text(f)).toContain('In stock');
    expect(text(f)).toContain('Last units');
    expect(text(f)).toContain('Out of stock');
    expect(addButton(f, 'Escovas').disabled).toBe(true);
    expect(addButton(f, 'Filtro de óleo').disabled).toBe(false);
  });

  it('keeps the cart in localStorage for this workshop and shows the total', () => {
    const f = open();
    addButton(f, 'Filtro de óleo').click();
    addButton(f, 'Lâmpada H7').click();
    addButton(f, 'Filtro de óleo').click();
    f.detectChanges();
    expect(storedCart()).toEqual([
      { productId: 'p1', quantity: 2 },
      { productId: 'p2', quantity: 1 },
    ]);
    expect(text(f)).toContain('3 items');
    expect(text(f)).toContain('€34.52');
  });

  it('restores the stored cart and drops what is no longer offered, saying so', () => {
    localStorage.setItem(cartKey(SLUG), JSON.stringify([{ productId: 'p1', quantity: 3 }, { productId: 'gone', quantity: 1 }]));
    const f = open();
    expect(f.componentInstance.cart()).toEqual([{ productId: 'p1', quantity: 3 }]);
    expect(storedCart()).toEqual([{ productId: 'p1', quantity: 3 }]);
    expect(text(f)).toContain('A product in your cart is no longer sold online and was removed.');
  });

  it('caps the quantity at 20 and says so', () => {
    const f = open();
    addButton(f, 'Filtro de óleo').click();
    f.detectChanges();
    f.componentInstance.changeQuantity('p1', 40);
    f.detectChanges();
    expect(f.componentInstance.cart()).toEqual([{ productId: 'p1', quantity: 20 }]);
    expect(text(f)).toContain('At most 20 units of a product per order.');
  });

  it('still works when the browser refuses storage', () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError');
    });
    try {
      const f = open();
      addButton(f, 'Filtro de óleo').click();
      f.detectChanges();
      expect(f.componentInstance.cart()).toEqual([{ productId: 'p1', quantity: 1 }]);
      expect(text(f)).toContain('€14.76');
    } finally {
      spy.mockRestore();
    }
  });

  it('searches after a pause in typing', () => {
    vi.useFakeTimers();
    const f = open();
    f.componentInstance.search('fil');
    f.componentInstance.search('filtro');
    vi.advanceTimersByTime(299);
    http.expectNone(`${BASE}/products`);
    vi.advanceTimersByTime(1);
    http.expectOne((r) => r.url === `${BASE}/products` && r.params.get('q') === 'filtro').flush([PRODUCTS[0]]);
    f.detectChanges();
    expect(text(f)).not.toContain('Lâmpada H7');

    f.componentInstance.search('nada');
    vi.advanceTimersByTime(300);
    http.expectOne((r) => r.params.get('q') === 'nada').flush([]);
    f.detectChanges();
    expect(text(f)).toContain('No products match “nada”.');
  });

  it('places the order with the cart, the details and the page language, then shows the receipt', () => {
    const f = open();
    toCheckout(f);
    expect(text(f)).toContain('Nothing is charged now.');
    f.componentInstance.submit();
    const req = http.expectOne(`${BASE}/orders`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.body).toEqual({
      lines: [{ productId: 'p1', quantity: 2 }],
      name: 'Ana Silva',
      phone: '912345678',
      email: null,
      nif: '123456789',
      privacyAccepted: true,
      marketingConsent: false,
      website: '',
      language: 'en',
    });
    req.flush({ number: 7, statusToken: 'tok-123', totalCents: 2952, holdUntil: '2030-11-07T18:00:00Z', timeZone: 'Europe/Lisbon' });
    f.detectChanges();
    expect(text(f)).toContain('Order placed');
    expect(text(f)).toContain('7');
    expect(text(f)).toContain('€29.52');
    expect((f.nativeElement.querySelector('#sh-link') as HTMLInputElement).value).toContain('/w/oficina-azul/shop/tok-123');
    expect(storedCart()).toBeNull();
  });

  it('needs the privacy consent and a phone or e-mail before sending', () => {
    const f = open();
    toCheckout(f);
    f.componentInstance['form'].patchValue({ phone: '', privacyAccepted: false });
    f.componentInstance.submit();
    f.detectChanges();
    http.expectNone(`${BASE}/orders`);
    expect(text(f)).toContain('Accept the privacy notice to place the order.');
    expect(f.nativeElement.querySelector('#sh-phone').classList).toContain('is-invalid');
  });

  it('marks the cart line the server refused and reads the products again (insufficient-stock)', () => {
    const f = open();
    toCheckout(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'insufficient-stock', productId: 'p1' }, { status: 409, statusText: 'Conflict' });
    http.expectOne((r) => r.url === `${BASE}/products` && !r.params.has('q')).flush(PRODUCTS);
    f.detectChanges();
    const line = f.nativeElement.querySelector('.cart-line[data-product="p1"]') as HTMLElement;
    expect(line.classList).toContain('is-problem');
    expect(line.textContent).toContain('Not enough in stock for this quantity.');
    expect(text(f)).toContain('Check the products marked in your cart');
    // Back on the products with the cart; checkout waits until the line is fixed.
    button(f, 'Go to checkout').click();
    f.detectChanges();
    expect(f.nativeElement.querySelector('#sh-checkout')).toBeNull();
    f.componentInstance.changeQuantity('p1', 1);
    f.detectChanges();
    expect(f.nativeElement.querySelector('.cart-line.is-problem')).toBeNull();
  });

  it('removes a product no longer listed after the reload (product-not-listed)', () => {
    const f = open();
    toCheckout(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'product-not-listed', productId: 'p1' }, { status: 409, statusText: 'Conflict' });
    http.expectOne((r) => r.url === `${BASE}/products`).flush(PRODUCTS.slice(1));
    f.detectChanges();
    expect(f.componentInstance.cart()).toEqual([]);
    expect(text(f)).toContain('no longer sold online and was removed');
  });

  it('shows the server field errors on the form (validation errors map, invalid-nif, privacy)', () => {
    const f = open();
    toCheckout(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'validation', errors: { name: 'invalid', email: 'invalid' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('#sh-name').classList).toContain('is-invalid');
    expect(text(f)).toContain('Some fields are invalid.');

    // The marks go away once the fields are edited.
    f.componentInstance['form'].patchValue({ name: 'Ana Silva', email: 'ana@example.pt' });
    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'invalid-nif', errors: { nif: 'invalid' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('#sh-nif-help').textContent).toContain('Not a valid Portuguese NIF.');
  });

  it('says the cart is wrong when the server rejects its lines', () => {
    const f = open();
    toCheckout(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'validation', errors: { lines: 'size' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(text(f)).toContain('Check the cart: 1 to 20 units of each product');
  });

  it('asks to wait on too-many-requests and to retry on try-again', () => {
    const f = open();
    toCheckout(f);
    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'too-many-requests' }, { status: 429, statusText: 'Too Many Requests' });
    f.detectChanges();
    expect(text(f)).toContain('Too many attempts. Wait a few minutes and try again.');
    expect(f.nativeElement.querySelector('#sh-checkout')).not.toBeNull();

    f.componentInstance.submit();
    http.expectOne(`${BASE}/orders`).flush({ code: 'try-again' }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(text(f)).toContain('Someone else is changing this right now.');
  });

  it('says when the workshop does not sell online', () => {
    const f = TestBed.createComponent(PublicShop);
    f.componentRef.setInput('slug', SLUG);
    f.detectChanges();
    http.expectOne(`/api/v1/branding/${SLUG}`).flush(BRANDING);
    http.expectOne(`${BASE}/products`).flush({ code: 'add-on-not-available' }, { status: 404, statusText: 'Not Found' });
    f.detectChanges();
    expect(text(f)).toContain('The online shop is not available');
  });
});

describe('ShopOrderStatusPage', () => {
  let http: HttpTestingController;
  const order: PublicShopOrder = {
    number: 7, status: 'PLACED', placedAt: '2030-11-04T10:00:00Z', holdUntil: '2030-11-07T10:00:00Z', timeZone: 'America/Sao_Paulo',
    workshopName: 'Oficina Azul', pickupNote: 'Levante ao balcão, das 9h às 18h.',
    lines: [{ name: 'Filtro de óleo', unit: 'un', quantity: 2, unitPriceWithVatCents: 1476, lineTotalCents: 2952 }],
    totalCents: 2952, canCancel: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.documentElement.removeAttribute('data-tenant-theme');
  });

  function open(o: PublicShopOrder | null, error?: object) {
    const f = TestBed.createComponent(ShopOrderStatusPage);
    f.componentRef.setInput('slug', SLUG);
    f.componentRef.setInput('token', 'tok-123');
    f.detectChanges();
    http.expectOne(`/api/v1/branding/${SLUG}`).flush(BRANDING);
    const req = http.expectOne(`${BASE}/orders/tok-123`);
    if (o) req.flush(o);
    else req.flush(error ?? null, { status: 404, statusText: 'Not Found' });
    f.detectChanges();
    return f;
  }

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  it('shows the status, lines, total, pickup note and the hold time in the workshop time zone', () => {
    const f = open(order);
    expect(text(f)).toContain('Order no. 7');
    expect(text(f)).toContain('Being prepared');
    expect(text(f)).toContain('Levante ao balcão');
    expect(text(f)).toContain('€29.52');
    // 10:00 UTC is 07:00 in São Paulo.
    expect(text(f)).toContain('07:00');
  });

  it('cancels after a confirmation and shows the stored answer', () => {
    const f = open(order);
    ([...f.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent!.trim() === 'Cancel order') as HTMLButtonElement).click();
    f.detectChanges();
    const yes = [...f.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent!.trim() === 'Yes, cancel') as HTMLButtonElement;
    yes.click();
    const req = http.expectOne(`${BASE}/orders/tok-123/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...order, status: 'CANCELLED', canCancel: false });
    f.detectChanges();
    expect(text(f)).toContain('The order was cancelled.');
    expect(text(f)).not.toContain('Kept until');
  });

  it('says when the link leads to no order', () => {
    const f = open(null, { code: 'shop-order-not-found' });
    expect(text(f)).toContain('Order not found');
  });
});
