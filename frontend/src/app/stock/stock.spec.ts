import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Toasts } from '../backoffice/toasts';
import { WorkOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { AdjustDialog } from './adjust-dialog';
import { ProductDialog } from './product-dialog';
import { PurchaseOrderDialog } from './purchase-order-dialog';
import { formatMoney, formatQuantity } from './qty.pipe';
import {
  fillPurchaseOrderForm,
  lineTotalCents,
  onHandAfter,
  productForm,
  productFormValue,
  purchaseOrderForm,
  purchaseTotalCents,
  signedQuantity,
  toProductRequest,
  toPurchaseOrderRequest,
} from './stock-forms';
import { StockPicker } from './stock-picker';
import { Product, PurchaseOrder } from './stock.models';

const filter: Product = {
  id: 'f1', sku: 'OF-712', name: 'Oil filter', brand: 'Mann', unit: 'un', costCents: 450, salePriceCents: 900, vatRate: 23,
  minStock: 2, bin: 'A-03', onHand: 5, reserved: 2, available: 3, low: false, active: true, listedOnline: false, version: 1,
};
const pads: Product = { ...filter, id: 'p1', sku: 'BP-100', name: 'Brake pads', costCents: 2000, available: 0, onHand: 0, reserved: 0 };
const retired: Product = { ...filter, id: 'r1', sku: 'OLD-1', name: 'Old part', active: false };

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

describe('stock forms', () => {
  it('sends a product in cents with blank texts as null, a blank unit as "un", and the version only when editing', () => {
    const form = productForm();
    form.reset({ ...productFormValue(null), sku: ' OF-712 ', name: ' Oil filter ', unit: ' ', costEuros: 4.5, salePriceEuros: 9.99, minStock: 2.5 });
    expect(form.valid).toBe(true);
    expect(toProductRequest(form)).toEqual({
      sku: 'OF-712', name: 'Oil filter', brand: null, unit: 'un', costCents: 450, salePriceCents: 999, vatRate: 23,
      minStock: 2.5, bin: null, active: true, listedOnline: false,
    });
    expect(toProductRequest(form, 4).version).toBe(4);
  });

  it('fills the form from a product and refuses prices with more than two decimals', () => {
    const form = productForm();
    form.reset(productFormValue(filter));
    expect(form.getRawValue()).toMatchObject({ sku: 'OF-712', costEuros: 4.5, salePriceEuros: 9, bin: 'A-03', minStock: 2 });
    form.controls.salePriceEuros.setValue(1.234);
    expect(form.controls.salePriceEuros.hasError('decimals')).toBe(true);
    form.controls.minStock.setValue(1.2345);
    expect(form.controls.minStock.hasError('decimals')).toBe(true);
  });

  it('signs adjustments and shows what will be on hand, rounded to three decimals', () => {
    expect(signedQuantity('add', 2)).toBe(2);
    expect(signedQuantity('remove', 1.5)).toBe(-1.5);
    expect(signedQuantity('remove', -3)).toBe(-3);
    expect(onHandAfter(5, 'remove', 1.25)).toBe(3.75);
    expect(onHandAfter(0.1, 'add', 0.2)).toBe(0.3);
    expect(onHandAfter(1, 'remove', 2)).toBe(-1);
    expect(onHandAfter(null, 'add', 1)).toBeNull();
    expect(onHandAfter(1, 'add', null)).toBeNull();
  });

  it('totals purchase order lines like the server: quantity times unit cost, half up per line', () => {
    expect(lineTotalCents(1.5, 3.33)).toBe(500);
    expect(lineTotalCents(2, 10)).toBe(2000);
    expect(lineTotalCents(null, 10)).toBe(0);
    expect(purchaseTotalCents([{ quantity: 1.5, unitCostEuros: 3.33 }, { quantity: 2, unitCostEuros: 10 }])).toBe(2500);
  });

  it('round-trips a purchase order through the form, leaving out lines without a product', () => {
    const po: PurchaseOrder = {
      id: 'po1', number: 7, supplier: 'Auto Parts Ltd', notes: null, status: 'DRAFT', orderedAt: null, receivedAt: null,
      totalCents: 1800, version: 3,
      lines: [{ productId: 'f1', sku: 'OF-712', name: 'Oil filter', unit: 'un', quantity: 4, unitCostCents: 450, totalCents: 1800 }],
    };
    const form = purchaseOrderForm();
    fillPurchaseOrderForm(form, po);
    form.controls.lines.push(purchaseOrderForm().controls.lines.at(0));
    expect(toPurchaseOrderRequest(form, po.version)).toEqual({
      supplier: 'Auto Parts Ltd', notes: null, lines: [{ productId: 'f1', quantity: 4, unitCostCents: 450 }], version: 3,
    });
  });

  it('formats quantities and money in the UI language', () => {
    expect(formatQuantity(1.5, 'pt-PT')).toBe('1,5');
    expect(formatQuantity(1.5, 'en')).toBe('1.5');
    expect(formatQuantity(-2, 'en', true)).toBe('-2');
    expect(formatQuantity(3, 'en', true)).toBe('+3');
    expect(formatQuantity(null, 'en')).toBe('—');
    expect(formatMoney(900, 'en')).toBe('€9.00');
  });
});

describe('stock components', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), Toasts] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('offers parts with what is available and adds one to the work order', () => {
    const fixture = TestBed.createComponent(StockPicker);
    fixture.componentRef.setInput('workOrderId', 'w1');
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/stock/products').flush([filter, pads]);
    fixture.detectChanges();

    const options = Array.from(fixture.nativeElement.querySelectorAll('option') as NodeListOf<HTMLOptionElement>);
    expect(options[1].textContent).toContain('Oil filter (OF-712) · 3 un available · €9.00');
    expect(options[2].disabled).toBe(true);
    expect(options[2].textContent).toContain('none available');

    const picker = fixture.componentInstance;
    let added: WorkOrder | null = null;
    picker.added.subscribe((w) => (added = w));
    picker['product'].setValue('f1');
    picker['quantity'].setValue(2);
    picker.add();
    const post = http.expectOne('/api/v1/work-orders/w1/lines/from-stock');
    expect(post.request.body).toEqual({ productId: 'f1', quantity: 2 });
    post.flush({ id: 'w1' } as WorkOrder);
    expect(added).toEqual({ id: 'w1' });
    http.expectOne((r) => r.url === '/api/v1/stock/products').flush([{ ...filter, available: 1, reserved: 4 }]);
  });

  it('shows the insufficient-stock error from the server', () => {
    const fixture = TestBed.createComponent(StockPicker);
    fixture.componentRef.setInput('workOrderId', 'w1');
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/stock/products').flush([filter]);
    const picker = fixture.componentInstance;
    picker['product'].setValue('f1');
    picker['quantity'].setValue(9);
    picker.add();
    http.expectOne('/api/v1/work-orders/w1/lines/from-stock')
      .flush({ code: 'insufficient-stock' }, { status: 409, statusText: 'Conflict' });
    expect(picker['error']()).toBe(TestBed.inject(I18nService).t('error.insufficient-stock'));
    http.expectOne((r) => r.url === '/api/v1/stock/products').flush([filter]);
  });

  it('adjusts with a signed quantity and marks a missing reason on the field', () => {
    const fixture = TestBed.createComponent(AdjustDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(filter);
    dialog.form.setValue({ direction: 'remove', quantity: 2, reason: 'Broken' });
    expect(dialog['after']()).toEqual({ text: '3 un', negative: false });
    dialog.save();
    const post = http.expectOne({ method: 'POST', url: '/api/v1/stock/products/f1/adjustments' });
    expect(post.request.body).toEqual({ quantity: -2, reason: 'Broken' });
    post.flush({ code: 'adjustment-reason-required', errors: { reason: 'x' } }, { status: 400, statusText: 'Bad Request' });
    expect(dialog.form.controls.reason.hasError('server')).toBe(true);
    expect(dialog['error']()).toBeNull();
  });

  it('marks a duplicate SKU on the field and reloads the product after a stale save', () => {
    const fixture = TestBed.createComponent(ProductDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(null);
    dialog.form.patchValue({ sku: 'OF-712', name: 'Filter' });
    dialog.save();
    http.expectOne({ method: 'POST', url: '/api/v1/stock/products' })
      .flush({ code: 'duplicate-sku' }, { status: 409, statusText: 'Conflict' });
    expect(dialog.form.controls.sku.getError('server')).toBe('duplicate-sku');

    dialog.open(filter);
    dialog.form.patchValue({ salePriceEuros: 9.5 });
    dialog.save();
    const put = http.expectOne({ method: 'PUT', url: '/api/v1/stock/products/f1' });
    expect(put.request.body).toMatchObject({ salePriceCents: 950, version: 1 });
    put.flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne({ method: 'GET', url: '/api/v1/stock/products/f1' }).flush({ ...filter, salePriceCents: 1000, version: 2 });
    expect(dialog.form.controls.salePriceEuros.value).toBe(10);
    expect(dialog['error']()).toBe(TestBed.inject(I18nService).t('error.stale-data'));
  });

  it('offers only active products for new purchase order lines, suggests their cost and totals the order', () => {
    const fixture = TestBed.createComponent(PurchaseOrderDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(null, [filter, pads, retired]);
    expect(dialog['choices']('').map((p) => p.id)).toEqual(['f1', 'p1']);
    expect(dialog['choices']('r1').map((p) => p.id)).toEqual(['f1', 'p1', 'r1']);

    const line = dialog.form.controls.lines.at(0);
    line.controls.productId.setValue('p1');
    dialog['productChosen'](line);
    expect(line.controls.unitCostEuros.value).toBe(20);
    line.controls.quantity.setValue(3);
    expect(dialog['total']()).toBe(6000);

    dialog.form.controls.supplier.setValue('Auto Parts Ltd');
    dialog.save();
    const post = http.expectOne({ method: 'POST', url: '/api/v1/stock/purchase-orders' });
    expect(post.request.body).toEqual({ supplier: 'Auto Parts Ltd', notes: null, lines: [{ productId: 'p1', quantity: 3, unitCostCents: 2000 }] });
    post.flush({ id: 'po1', number: 1 });
  });
});
