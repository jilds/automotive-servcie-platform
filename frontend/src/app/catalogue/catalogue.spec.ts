import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { WorkOrder } from '../core/api/models';
import { Toasts } from '../backoffice/toasts';
import { I18nService } from '../core/i18n/i18n.service';
import { CatalogueService, CataloguePackage, PackageProductChoice } from './catalogue.models';
import {
  copyName,
  copyServiceValue,
  fillPackageForm,
  hourlyPriceCents,
  itemRef,
  listPriceCents,
  newServiceValue,
  packageForm,
  packageItem,
  parseItemRef,
  serviceForm,
  toPackageRequest,
  toServiceRequest,
} from './catalogue-forms';
import { groupByCategory } from './catalogue-groups';
import { CataloguePage } from './catalogue-page';
import { PackageDialog } from './package-dialog';
import { CataloguePicker, parseChoice } from './catalogue-picker';
import { ServiceDialog } from './service-dialog';

const oil: CatalogueService = {
  id: 'oil', name: 'Oil change', description: null, category: 'Maintenance', standardHours: 0.75, pricing: 'FIXED',
  priceCents: 4000, effectivePriceCents: 4000, vatRate: 23, durationMinutes: 60, active: true, listedOnline: true, version: 2,
};
const filter: CatalogueService = { ...oil, id: 'filter', name: 'Air filter', priceCents: 2000, effectivePriceCents: 2000, version: 0 };
/** 1.25 h at 40.00 €/h. */
const diagnostics: CatalogueService = {
  ...oil, id: 'diag', name: 'Diagnostics', category: null, standardHours: 1.25, pricing: 'HOURLY', priceCents: 0,
  effectivePriceCents: 5000, version: 4,
};
const pack: CataloguePackage = {
  id: 'pk', name: 'Annual service', description: null, priceCents: 5000, vatRate: 23, listPriceCents: 6000, active: true,
  listedOnline: false, version: 1,
  items: [
    { kind: 'SERVICE', serviceId: 'oil', productId: null, name: 'Oil change', sku: null, quantity: 1, unitPriceCents: 4000, active: true },
    { kind: 'SERVICE', serviceId: 'filter', productId: null, name: 'Air filter', sku: null, quantity: 1, unitPriceCents: 2000, active: true },
  ],
};

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

describe('catalogue forms', () => {
  it('sends a service in cents with blank texts as null, and the version only when editing', () => {
    const form = serviceForm();
    form.reset({ ...newServiceValue(), name: '  Oil change ', priceEuros: 40.5, description: '  ' });
    expect(form.valid).toBe(true);
    expect(toServiceRequest(form)).toEqual({
      name: 'Oil change', description: null, category: null, standardHours: 1, pricing: 'FIXED', priceCents: 4050, vatRate: 23,
      durationMinutes: 60, active: true, listedOnline: false,
    });
    expect(toServiceRequest(form, 3).version).toBe(3);

    form.patchValue({ priceEuros: 1.234, durationMinutes: 2, vatRate: 120 });
    expect(form.controls.priceEuros.hasError('decimals')).toBe(true);
    expect(form.controls.durationMinutes.hasError('min')).toBe(true);
    expect(form.controls.vatRate.hasError('max')).toBe(true);
  });

  it('fills a package, adds up the list price and sends only chosen services', () => {
    const form = packageForm();
    fillPackageForm(form, pack);
    expect(form.controls.items.length).toBe(2);
    expect(form.controls.priceEuros.value).toBe(50);
    expect(listPriceCents(form.controls.items.getRawValue(), [oil, filter])).toBe(6000);

    form.controls.items.at(1).controls.quantity.setValue(2);
    expect(listPriceCents(form.controls.items.getRawValue(), [oil, filter])).toBe(8000);
    expect(toPackageRequest(form, 1)).toEqual({
      name: 'Annual service', description: null, priceCents: 5000, vatRate: 23, active: true, listedOnline: false,
      items: [{ serviceId: 'oil', quantity: 1 }, { serviceId: 'filter', quantity: 2 }], version: 1,
    });

    fillPackageForm(form, null);
    expect(form.controls.items.length).toBe(1);
    expect(form.invalid).toBe(true);
  });

  it('prices an hourly service at the labour rate, rounded half up like the server', () => {
    expect(hourlyPriceCents(1.25, 4010)).toBe(5013);
    expect(hourlyPriceCents(0.75, 4500)).toBe(3375);
    expect(hourlyPriceCents(0.29, 4010)).toBe(1163);
    expect(hourlyPriceCents(null, 4500)).toBe(0);
    // An hourly service counts at its effective price in a package's list price.
    expect(listPriceCents([{ ref: 's:diag', quantity: 2 }, { ref: 's:oil', quantity: 1 }], [oil, diagnostics])).toBe(14000);
  });

  it('copies a service or package as a new record with the copy suffix', () => {
    expect(copyServiceValue(oil, '(cópia)')).toEqual(expect.objectContaining({ name: 'Oil change (cópia)', priceEuros: 40, pricing: 'FIXED' }));
    expect(copyName('x'.repeat(120), '(copy)')).toHaveLength(120);

    const form = packageForm();
    const withOld = { ...pack, items: [...pack.items, { kind: 'SERVICE' as const, serviceId: 'old', productId: null, name: 'Old', sku: null, quantity: 1, unitPriceCents: 0, active: false }] };
    fillPackageForm(form, withOld, '(copy)');
    expect(form.controls.name.value).toBe('Annual service (copy)');
    // A new package cannot hold a service that is no longer offered.
    expect(form.controls.items.getRawValue().map((i) => i.ref)).toEqual(['s:oil', 's:filter']);
  });

  it('groups services by category, A to Z, without a category last', () => {
    const brakes = { ...oil, id: 'b', name: 'Brake pads', category: 'Travões' };
    const tyres = { ...oil, id: 't', name: 'Tyres', category: 'manutenção' };
    const groups = groupByCategory([diagnostics, tyres, oil, brakes, filter], 'pt-PT');
    expect(groups.map((g) => g.category)).toEqual(['Maintenance', 'manutenção', 'Travões', null]);
    expect(groups[0].services.map((s) => s.name)).toEqual(['Air filter', 'Oil change']);
    expect(groupByCategory([{ ...oil, category: 'Manutenção' }, tyres], 'pt-PT')).toHaveLength(1);
  });

  it('reads the picker choice', () => {
    expect(parseChoice('s:oil')).toEqual({ serviceId: 'oil' });
    expect(parseChoice('p:pk')).toEqual({ packageId: 'pk' });
    expect(parseChoice('')).toBeNull();
    expect(parseChoice('x:1')).toBeNull();
  });
});

describe('catalogue components', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), Toasts] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('adds a package to the work order and emits the saved order', () => {
    const fixture = TestBed.createComponent(CataloguePicker);
    fixture.componentRef.setInput('workOrderId', 'w1');
    const added = vi.fn();
    fixture.componentInstance.added.subscribe(added);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/catalogue/services').flush([oil]);
    http.expectOne((r) => r.url === '/api/v1/catalogue/packages').flush([pack]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('option').length).toBe(3);

    fixture.componentInstance.add('p:pk');
    const req = http.expectOne('/api/v1/work-orders/w1/lines/from-catalogue');
    expect(req.request.body).toEqual({ packageId: 'pk' });
    req.flush({ id: 'w1' } as WorkOrder);
    expect(added).toHaveBeenCalledWith({ id: 'w1' });
  });

  it('reloads the service and says why when someone else saved it first', () => {
    const fixture = TestBed.createComponent(ServiceDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(oil);
    dialog.form.patchValue({ priceEuros: 45 });
    dialog.save();
    const put = http.expectOne({ method: 'PUT', url: '/api/v1/catalogue/services/oil' });
    expect(put.request.body.version).toBe(2);
    expect(put.request.body.priceCents).toBe(4500);
    put.flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne({ method: 'GET', url: '/api/v1/catalogue/services/oil' }).flush({ ...oil, priceCents: 4200, version: 3 });

    expect(dialog.form.controls.priceEuros.value).toBe(42);
    expect(dialog['error']()).toBe(TestBed.inject(I18nService).t('error.stale-data'));
  });

  it('switches to hourly pricing: locks the price and shows hours × rate', () => {
    const fixture = TestBed.createComponent(ServiceDialog);
    fixture.componentRef.setInput('labourRateCents', 4010);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(null);
    dialog.form.patchValue({ name: 'Diagnostics', standardHours: 1.25, priceEuros: 12 });
    dialog.form.controls.pricing.setValue('HOURLY');
    fixture.detectChanges();

    expect(dialog.form.controls.priceEuros.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('#sd-hourly-price').textContent).toContain('1.25 h × €40.10/h = €50.13');

    dialog.save();
    const post = http.expectOne({ method: 'POST', url: '/api/v1/catalogue/services' });
    // The stored fixed price goes along unchanged; the server prices the service from the hours.
    expect(post.request.body).toEqual(expect.objectContaining({ pricing: 'HOURLY', standardHours: 1.25, priceCents: 1200 }));
    post.flush(diagnostics);

    dialog.open(oil);
    expect(dialog.form.controls.pricing.value).toBe('FIXED');
    expect(dialog.form.controls.priceEuros.enabled).toBe(true);
    dialog.open(diagnostics);
    expect(dialog.form.controls.priceEuros.disabled).toBe(true);
  });

  it('saves a duplicated service as a new one', () => {
    const fixture = TestBed.createComponent(ServiceDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(oil, true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#service-dialog-title').textContent).toContain('Duplicate service');
    expect(dialog.form.controls.name.value).toBe('Oil change (copy)');

    dialog.save();
    const post = http.expectOne({ method: 'POST', url: '/api/v1/catalogue/services' });
    expect(post.request.body.version).toBeUndefined();
    expect(post.request.body).toEqual(expect.objectContaining({ name: 'Oil change (copy)', priceCents: 4000, category: 'Maintenance' }));
    post.flush({ ...oil, id: 'copy', name: 'Oil change (copy)', version: 0 });
  });

  it('saves a duplicated package as a new one', () => {
    const fixture = TestBed.createComponent(PackageDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(pack, [oil, filter], [], true);
    dialog.save();
    const post = http.expectOne({ method: 'POST', url: '/api/v1/catalogue/packages' });
    expect(post.request.body).toEqual(expect.objectContaining({ name: 'Annual service (copy)', priceCents: 5000 }));
    expect(post.request.body.version).toBeUndefined();
    post.flush({ ...pack, id: 'pk2', name: 'Annual service (copy)', version: 0 });
  });

  it('lists services by category with their effective price', () => {
    const fixture = TestBed.createComponent(CataloguePage);
    fixture.detectChanges();
    http.match((r) => r.url === '/api/v1/catalogue/services').forEach((r) => r.flush([diagnostics, oil, filter]));
    http.expectOne((r) => r.url === '/api/v1/catalogue/packages').flush([]);
    http.expectOne('/api/v1/workshop/settings').flush({ labourRateCents: 4000 });
    http.expectOne((r) => r.url === '/api/v1/stock/products' && r.params.get('activeOnly') === 'false').flush([]);
    fixture.detectChanges();

    const rows = [...fixture.nativeElement.querySelectorAll('tbody tr')].map((r: HTMLElement) => r.textContent!.replace(/\s+/g, ' ').trim());
    expect(rows[0]).toBe('Maintenance');
    expect(rows[1]).toContain('Air filter');
    expect(rows[3]).toBe('No category');
    expect(rows[4]).toContain('Diagnostics');
    expect(rows[4]).toContain('€50.00');
    expect(rows[4]).toContain('By the hour');
  });
});

describe('packages with stock parts', () => {
  const oilLitre: PackageProductChoice = { id: 'p-oil', sku: '5W30', name: 'Oil 5W30', unit: 'l', salePriceCents: 1200, available: 3, active: true };
  const oldPart: PackageProductChoice = { ...oilLitre, id: 'p-old', sku: 'OLD', name: 'Old part', active: false };
  const mixed: CataloguePackage = {
    ...pack,
    listPriceCents: 8800,
    items: [
      pack.items[0],
      { kind: 'PRODUCT', serviceId: null, productId: 'p-oil', name: 'Oil 5W30', sku: '5W30', quantity: 4, unitPriceCents: 1200, active: true },
    ],
  };
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), Toasts] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads and writes the row choice of a service or a product', () => {
    expect(itemRef(mixed.items[0])).toBe('s:oil');
    expect(itemRef(mixed.items[1])).toBe('p:p-oil');
    expect(parseItemRef('p:p-oil')).toEqual({ kind: 'PRODUCT', id: 'p-oil' });
    expect(parseItemRef('s:oil')).toEqual({ kind: 'SERVICE', id: 'oil' });
    expect(parseItemRef('')).toBeNull();
    expect(parseItemRef('x:1')).toBeNull();
  });

  it('adds products at their sale price to the list price and sends each row as a service or a product', () => {
    const form = packageForm();
    fillPackageForm(form, mixed);
    expect(form.controls.items.getRawValue()).toEqual([{ ref: 's:oil', quantity: 1 }, { ref: 'p:p-oil', quantity: 4 }]);
    // 40.00 + 4 x 12.00
    expect(listPriceCents(form.controls.items.getRawValue(), [oil], [oilLitre])).toBe(8800);
    form.controls.items.push(packageItem());
    expect(toPackageRequest(form).items).toEqual([{ serviceId: 'oil', quantity: 1 }, { productId: 'p-oil', quantity: 4 }]);
  });

  it('leaves products no longer sold out of a copy', () => {
    const form = packageForm();
    const withOld = { ...mixed, items: [...mixed.items, { ...mixed.items[1], productId: 'p-old', name: 'Old part', active: false }] };
    fillPackageForm(form, withOld, '(copy)');
    expect(form.controls.items.getRawValue().map((i) => i.ref)).toEqual(['s:oil', 'p:p-oil']);
  });

  it('offers services and products in two groups, shows the stock of a chosen product and saves both kinds', () => {
    const fixture = TestBed.createComponent(PackageDialog);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;
    dialog.open(mixed, [oil, filter], [oilLitre, oldPart]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const groups = [...el.querySelectorAll('select')[0].querySelectorAll('optgroup')].map((g) => g.label);
    expect(groups).toEqual(['Services', 'Stock parts']);
    // A product no longer sold is only offered when the package already holds it.
    const products = [...el.querySelectorAll('select')[0].querySelectorAll('optgroup')[1].querySelectorAll('option')].map((o) => o.textContent!.trim());
    expect(products).toEqual(['Oil 5W30 (5W30)']);
    // 4 litres asked, 3 available: shown as short.
    const hint = el.querySelector('.form-text.text-danger');
    expect(hint?.textContent?.trim()).toBe('Available: 3 l');
    expect(el.textContent).toContain('€88.00');

    dialog.save();
    const req = http.expectOne({ method: 'PUT', url: '/api/v1/catalogue/packages/pk' });
    expect(req.request.body.items).toEqual([{ serviceId: 'oil', quantity: 1 }, { productId: 'p-oil', quantity: 4 }]);
    req.flush(mixed);
  });

  it('marks the parts of a package in the list', () => {
    const fixture = TestBed.createComponent(CataloguePage);
    fixture.detectChanges();
    fixture.componentInstance['tab'].set('packages');
    http.match((r) => r.url === '/api/v1/catalogue/services').forEach((r) => r.flush([oil]));
    http.expectOne((r) => r.url === '/api/v1/catalogue/packages').flush([mixed]);
    http.expectOne('/api/v1/workshop/settings').flush({ labourRateCents: 4000 });
    http.expectOne((r) => r.url === '/api/v1/stock/products').flush([oilLitre]);
    fixture.detectChanges();

    const items = [...fixture.nativeElement.querySelectorAll('tbody li')].map((li: HTMLElement) => li.textContent!.replace(/s+/g, ' ').trim());
    expect(items).toEqual(['Oil change', 'Oil 5W30 × 4 Part']);
  });
});
