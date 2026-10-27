import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { orderActions } from './shop-order-dialog';
import { ShopOrdersPage, tabOf } from './shop-orders-page';
import { ShopOrder, ShopOrderSummary } from './shop.models';

const summary = (id: string, number: number, status: ShopOrderSummary['status']): ShopOrderSummary => ({
  id, number, status, placedAt: '2030-11-04T10:00:00Z', holdUntil: '2030-11-07T10:00:00Z', name: `Cliente ${number}`, lineCount: 1, totalCents: 2952, version: 0,
});

const placed: ShopOrder = {
  id: 'o7', number: 7, status: 'PLACED', placedAt: '2030-11-04T10:00:00Z', readyAt: null, holdUntil: '2030-11-07T10:00:00Z',
  name: 'Ana Silva', phone: '912345678', email: 'ana@example.pt', nif: '123456789', marketingConsent: false,
  lines: [{ productId: 'p1', sku: 'FLT-OL-01', name: 'Filtro de óleo', unit: 'un', quantity: 2, unitPriceCents: 1200, vatRate: 23, unitPriceWithVatCents: 1476, lineTotalCents: 2952 }],
  totalCents: 2952, paymentMethod: null, collectedByName: null, collectedAt: null, cancelReason: null, cancelledBy: null, version: 1,
};

const LIST = [summary('o9', 9, 'COLLECTED'), summary('o8', 8, 'READY'), summary('o7', 7, 'PLACED'), summary('o6', 6, 'PLACED'), summary('o5', 5, 'EXPIRED')];

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

describe('shop order rules', () => {
  it('puts each status in its tab and offers only the allowed moves', () => {
    expect(tabOf('PLACED')).toBe('prepare');
    expect(tabOf('READY')).toBe('ready');
    expect(['COLLECTED', 'CANCELLED', 'EXPIRED'].map((s) => tabOf(s as ShopOrder['status']))).toEqual(['done', 'done', 'done']);
    expect(orderActions({ status: 'PLACED' })).toEqual({ ready: true, collect: true, cancel: true });
    expect(orderActions({ status: 'READY' })).toEqual({ ready: false, collect: true, cancel: true });
    expect(orderActions({ status: 'COLLECTED' })).toEqual({ ready: false, collect: false, cancel: false });
  });
});

describe('ShopOrdersPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');
  const button = (f: ComponentFixture<unknown>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent!.trim() === label) as HTMLButtonElement;

  function flushList(list: ShopOrderSummary[] = LIST) {
    http.expectOne((r) => r.url === '/api/v1/shop-orders' && r.params.keys().length === 0).flush(list);
  }

  function openPage() {
    const f = TestBed.createComponent(ShopOrdersPage);
    f.detectChanges();
    flushList();
    f.detectChanges();
    return f;
  }

  function openOrder(f: ComponentFixture<ShopOrdersPage>, o: ShopOrder = placed) {
    (f.nativeElement.querySelector(`button[aria-label="Open order no. ${o.number}"]`) as HTMLButtonElement).click();
    http.expectOne(`/api/v1/shop-orders/${o.id}`).flush(o);
    f.detectChanges();
  }

  it('splits the orders into to prepare (oldest first), ready and done', () => {
    const f = openPage();
    const rows = () => [...f.nativeElement.querySelectorAll('#so-panel .list-group-item-action')].map((b) => (b as HTMLElement).getAttribute('aria-label'));
    expect(rows()).toEqual(['Open order no. 6', 'Open order no. 7']);
    f.componentInstance.tab.set('ready');
    f.detectChanges();
    expect(rows()).toEqual(['Open order no. 8']);
    f.componentInstance.tab.set('done');
    f.detectChanges();
    expect(rows()).toEqual(['Open order no. 9', 'Open order no. 5']);
    expect(text(f)).toContain('Collected');
    expect(text(f)).toContain('Expired');
  });

  it('marks an order ready with its version and reloads the lists', () => {
    const f = openPage();
    openOrder(f);
    expect(text(f)).toContain('Filtro de óleo');
    expect(text(f)).toContain('€29.52');
    button(f, 'Mark ready').click();
    const req = http.expectOne('/api/v1/shop-orders/o7/ready');
    expect(req.request.body).toEqual({ version: 1 });
    req.flush({ ...placed, status: 'READY', version: 2 });
    flushList();
    f.detectChanges();
    expect(text(f)).toContain('Order no. 7 is ready for collection.');
  });

  it('collects only with a payment method, and sends it with the version', () => {
    const f = openPage();
    openOrder(f);
    button(f, 'Collect and pay').click();
    f.detectChanges();
    button(f, 'Confirm collection').click();
    f.detectChanges();
    http.expectNone('/api/v1/shop-orders/o7/collect');
    expect(text(f)).toContain('Choose how the customer paid.');

    (f.nativeElement.querySelector('#sod-method-MBWAY') as HTMLInputElement).click();
    f.detectChanges();
    button(f, 'Confirm collection').click();
    const req = http.expectOne('/api/v1/shop-orders/o7/collect');
    expect(req.request.body).toEqual({ paymentMethod: 'MBWAY', version: 1 });
    req.flush({ ...placed, status: 'COLLECTED', paymentMethod: 'MBWAY', version: 2 });
    flushList();
    f.detectChanges();
    expect(text(f)).toContain('Order no. 7 was collected.');
  });

  it('cancels only with a reason, and shows the server verdict under it', () => {
    const f = openPage();
    openOrder(f);
    button(f, 'Cancel order').click();
    f.detectChanges();
    button(f, 'Cancel the order').click();
    f.detectChanges();
    http.expectNone('/api/v1/shop-orders/o7/cancel');
    expect(text(f)).toContain('Say why the order is cancelled.');

    const reason = f.nativeElement.querySelector('#sod-reason') as HTMLTextAreaElement;
    reason.value = 'x';
    reason.dispatchEvent(new Event('input'));
    button(f, 'Cancel the order').click();
    http.expectOne('/api/v1/shop-orders/o7/cancel').flush({ code: 'cancel-reason-required', errors: { reason: 'blank' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(reason.classList).toContain('is-invalid');

    reason.value = 'Cliente desistiu por telefone';
    reason.dispatchEvent(new Event('input'));
    button(f, 'Cancel the order').click();
    const req = http.expectOne('/api/v1/shop-orders/o7/cancel');
    expect(req.request.body).toEqual({ reason: 'Cliente desistiu por telefone', version: 1 });
    req.flush({ ...placed, status: 'CANCELLED', cancelledBy: 'STAFF', cancelReason: 'Cliente desistiu por telefone', version: 2 });
    flushList();
    f.detectChanges();
    expect(text(f)).toContain('Order no. 7 was cancelled.');
  });

  it('reloads the order and the lists on stale-data and shows why', () => {
    const f = openPage();
    openOrder(f);
    button(f, 'Mark ready').click();
    http.expectOne('/api/v1/shop-orders/o7/ready').flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/shop-orders/o7').flush({ ...placed, status: 'READY', version: 2 });
    flushList();
    f.detectChanges();
    expect(text(f)).toContain('Someone else changed this record');
    expect(text(f)).toContain('Ready for collection');
    expect(button(f, 'Mark ready')).toBeUndefined();
  });

  it('shows the payment of a collected order and offers no actions', () => {
    const f = openPage();
    f.componentInstance.tab.set('done');
    f.detectChanges();
    openOrder(f, { ...placed, id: 'o9', number: 9, status: 'COLLECTED', paymentMethod: 'CARD', collectedByName: 'Rita', collectedAt: '2030-11-05T15:00:00Z', version: 3 });
    expect(text(f)).toContain('Collected by Rita');
    expect(text(f)).toContain('paid by Card');
    expect(button(f, 'Collect and pay')).toBeUndefined();
  });
});
