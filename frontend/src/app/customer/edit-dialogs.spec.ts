import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { Customer, Vehicle } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { CustomerDetail } from './customer-detail';
import { CustomerEditDialog } from './customer-edit-dialog';
import { VehicleEditDialog } from './vehicle-edit-dialog';
import { OwnerChange, VehicleOwnerDialog } from './vehicle-owner-dialog';

// jsdom has no modal dialogs; the components only need showModal/close to toggle `open`.
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

function setup() {
  try {
    localStorage.clear();
  } catch {
    // ignore
  }
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(I18nService).use('en');
  return TestBed.inject(HttpTestingController);
}

function signInAs(role: 'OWNER' | 'FRONT_DESK' | 'MECHANIC'): void {
  const user = { id: 'u1', tenantId: 't1', name: 'U', email: 'u@x.pt', role };
  localStorage.setItem('asp.session', JSON.stringify({ token: 'tok', expiresAt: Date.now() + 60_000, user }));
}

const ana: Customer = {
  id: 'c1',
  name: 'Ana Silva',
  nif: '123456789',
  phone: '912345678',
  email: 'ana@example.pt',
  address: { street: 'Rua Augusta 10', detail: null, postalCode: '1100-053', locality: 'Lisboa', country: 'PT' },
  marketingConsent: true,
};

const clio: Vehicle = {
  id: 'v1',
  customerId: 'c1',
  plate: '11AA11',
  plateDisplay: '11-AA-11',
  plateEra: 'PT_2005',
  registrationCountry: 'PT',
  registrationDate: null,
  vin: null,
  make: 'Renault',
  model: 'Clio',
  year: 2020,
  fuelType: 'Diesel',
  mileage: 100000,
};

describe('CustomerEditDialog', () => {
  let http: HttpTestingController;
  let dialog: CustomerEditDialog;

  beforeEach(() => {
    http = setup();
    const fixture = TestBed.createComponent(CustomerEditDialog);
    fixture.detectChanges();
    dialog = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  it('opens prefilled with the current values', () => {
    dialog.open(ana);
    expect(dialog.form.getRawValue()).toEqual({
      name: 'Ana Silva',
      nif: '123456789',
      phone: '912345678',
      email: 'ana@example.pt',
      address: { street: 'Rua Augusta 10', detail: '', postalCode: '1100-053', locality: 'Lisboa', country: 'PT' },
      marketingConsent: true,
    });
    const { id: _id, ...request } = ana;
    expect(dialog.request()).toEqual(request);
  });

  it('sends blank optional fields and an empty address as null', () => {
    dialog.open({ ...ana, address: null });
    dialog.form.patchValue({ nif: '', phone: '  ', email: '', marketingConsent: false });
    expect(dialog.request()).toEqual({ name: 'Ana Silva', nif: null, phone: null, email: null, address: null, marketingConsent: false });
  });

  it('mirrors the backend rules and does not call the API while invalid', () => {
    dialog.open(ana);
    dialog.form.patchValue({ name: '', nif: '12345', email: 'not-an-email' });
    dialog.form.controls.address.patchValue({ locality: '' });
    expect(dialog.form.controls.name.hasError('required')).toBe(true);
    expect(dialog.form.controls.nif.hasError('pattern')).toBe(true);
    expect(dialog.form.controls.email.hasError('email')).toBe(true);
    expect(dialog.form.controls.address.hasError('incomplete')).toBe(true);
    dialog.save();
    http.expectNone('/api/v1/customers/c1');
  });

  it('saves with PUT and emits the saved customer', () => {
    const saved = vi.fn();
    dialog.saved.subscribe(saved);
    dialog.open(ana);
    dialog.form.patchValue({ name: 'Ana M. Silva' });
    dialog.save();
    const req = http.expectOne('/api/v1/customers/c1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.name).toBe('Ana M. Silva');
    req.flush({ ...ana, name: 'Ana M. Silva' });
    expect(saved).toHaveBeenCalledWith({ ...ana, name: 'Ana M. Silva' });
  });

  it('shows server validation errors on the fields, including the nested address', () => {
    dialog.open(ana);
    dialog.save();
    http
      .expectOne('/api/v1/customers/c1')
      .flush({ code: 'validation', errors: { 'address.postalCode': 'bad' } }, { status: 400, statusText: 'Bad Request' });
    expect(dialog.form.get('address.postalCode')?.hasError('server')).toBe(true);
  });
});

describe('VehicleEditDialog', () => {
  let http: HttpTestingController;
  let dialog: VehicleEditDialog;

  beforeEach(() => {
    http = setup();
    const fixture = TestBed.createComponent(VehicleEditDialog);
    fixture.detectChanges();
    dialog = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  it('opens prefilled and keeps the recorded mileage when it is cleared', () => {
    dialog.open(clio);
    expect(dialog.form.getRawValue()).toEqual({
      registrationCountry: 'PT',
      plate: '11-AA-11',
      registrationDate: '',
      vin: '',
      make: 'Renault',
      model: 'Clio',
      year: 2020,
      fuelType: 'Diesel',
      mileage: 100000,
    });
    dialog.form.patchValue({ mileage: null, make: ' ' });
    expect(dialog.request()).toEqual({
      plate: '11-AA-11',
      vin: null,
      make: null,
      model: 'Clio',
      year: 2020,
      fuelType: 'Diesel',
      mileage: null,
      registrationCountry: 'PT',
      registrationDate: null,
    });
  });

  it('rejects a short VIN, a year out of range and a mileage below the recorded one', () => {
    dialog.open(clio);
    dialog.form.patchValue({ vin: 'VF1', year: 1900, mileage: 99999 });
    expect(dialog.form.controls.vin.hasError('minlength')).toBe(true);
    expect(dialog.form.controls.year.hasError('min')).toBe(true);
    expect(dialog.form.controls.mileage.hasError('min')).toBe(true);
    dialog.save();
    http.expectNone('/api/v1/vehicles/v1');
  });

  it('shows a duplicate plate on the plate field', () => {
    dialog.open(clio);
    dialog.save();
    http.expectOne('/api/v1/vehicles/v1').flush({ code: 'duplicate-plate' }, { status: 409, statusText: 'Conflict' });
    expect(dialog.form.controls.plate.errors).toEqual({ server: 'duplicate-plate' });
    expect(dialog.form.controls.mileage.errors).toBeNull();
  });

  it('shows a mileage decrease on the mileage field', () => {
    dialog.open({ ...clio, mileage: null });
    dialog.form.patchValue({ mileage: 5 });
    dialog.save();
    http.expectOne('/api/v1/vehicles/v1').flush({ code: 'mileage-decrease' }, { status: 409, statusText: 'Conflict' });
    expect(dialog.form.controls.mileage.errors).toEqual({ server: 'mileage-decrease' });
  });
});

describe('VehicleEditDialog plate rules', () => {
  let http: HttpTestingController;

  beforeEach(() => (http = setup()));
  afterEach(() => http.verify());

  function open(vehicle: Vehicle) {
    const fixture = TestBed.createComponent(VehicleEditDialog);
    fixture.detectChanges();
    fixture.componentInstance.open(vehicle);
    fixture.detectChanges();
    return { fixture, dialog: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('maps an invalid-plate problem to the plate field with the layouts message', () => {
    const { fixture, dialog, el } = open(clio);
    dialog.form.patchValue({ plate: 'AA-11-AA' });
    dialog.save();
    http
      .expectOne('/api/v1/vehicles/v1')
      .flush(
        { code: 'invalid-plate', country: 'PT', errors: { plate: 'Plate AA11AA is not a valid PT plate' } },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(dialog.form.controls.plate.errors).toEqual({ server: 'invalid-plate' });
    expect(el.querySelector('#ve-plate')?.classList).toContain('is-invalid');
    expect(el.querySelector('#ve-plate-help')?.textContent).toContain('AA-00-AA (from 2020), 00-AA-00 (2005–2020), 00-00-AA (1992–2005) or AA-00-00 (1937–1992)');
    expect(el.querySelector('app-alert')).toBeNull();
  });

  it('rejects a plate that fits no Portuguese layout without calling the API', () => {
    const { fixture, dialog, el } = open(clio);
    dialog.form.patchValue({ plate: 'ABC123' });
    dialog.save();
    fixture.detectChanges();
    expect(dialog.form.controls.plate.hasError('ptPlate')).toBe(true);
    expect(el.querySelector('#ve-plate-help')?.textContent).toContain('Not a valid Portuguese plate');
    http.expectNone('/api/v1/vehicles/v1');
  });

  it('accepts free text for another country and sends the country and month', () => {
    const { dialog } = open(clio);
    dialog.form.patchValue({ registrationCountry: 'DE', plate: 'B-AB 1234', registrationDate: '2019-05' });
    expect(dialog.form.valid).toBe(true);
    dialog.save();
    const req = http.expectOne('/api/v1/vehicles/v1');
    expect(req.request.body).toEqual(expect.objectContaining({ plate: 'B-AB 1234', registrationCountry: 'DE', registrationDate: '2019-05' }));
    req.flush(clio);
  });

  it('keeps an old plate that fits no layout as long as it is not changed', () => {
    const { dialog } = open({ ...clio, plate: 'XY1234Z', plateDisplay: 'XY1234Z', plateEra: null });
    expect(dialog.form.controls.plate.valid).toBe(true);
    dialog.form.patchValue({ plate: 'XY1234ZZ' });
    expect(dialog.form.controls.plate.hasError('ptPlate')).toBe(true);
  });

  it('formats a Portuguese plate on blur', () => {
    const { fixture, dialog, el } = open(clio);
    const input = el.querySelector<HTMLInputElement>('#ve-plate')!;
    input.value = 'aa 12bc';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(dialog.form.controls.plate.value).toBe('AA-12-BC');
  });

  it('rejects a registration month in the future', () => {
    const { dialog } = open(clio);
    dialog.form.patchValue({ registrationDate: '2999-01' });
    expect(dialog.form.controls.registrationDate.hasError('registrationMonth')).toBe(true);
  });
});

describe('CustomerDetail add vehicle', () => {
  it('defaults to the workshop country, sends it and maps invalid-plate to the plate field', () => {
    const http = setup();
    signInAs('OWNER');
    const fixture = TestBed.createComponent(CustomerDetail);
    fixture.componentRef.setInput('id', 'c1');
    fixture.detectChanges();
    http.expectOne('/api/v1/customers/c1').flush(ana);
    http.expectOne('/api/v1/customers/c1/vehicles').flush([]);
    http.expectOne('/api/v1/workshop/settings').flush({ country: 'es' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector<HTMLSelectElement>('#va-country')?.value).toBe('ES');

    const page = fixture.componentInstance as unknown as { form: FormGroup; addVehicle(): void };
    const form = page.form;
    form.patchValue({ registrationCountry: 'PT', plate: '12-ab-34', registrationDate: '2012-04' });
    form.controls['registrationCountry'].markAsDirty();
    page.addVehicle();
    const req = http.expectOne('/api/v1/vehicles');
    expect(req.request.body).toEqual(expect.objectContaining({ customerId: 'c1', plate: '12-ab-34', registrationCountry: 'PT', registrationDate: '2012-04' }));
    req.flush({ code: 'invalid-plate', errors: { plate: 'bad' } }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(form.controls['plate'].errors).toEqual({ server: 'invalid-plate' });
    expect(el.querySelector('#va-plate')?.classList).toContain('is-invalid');
    expect(el.querySelector('app-alert')).toBeNull();
    http.verify();
  });
});

describe('VehicleOwnerDialog', () => {
  let http: HttpTestingController;
  let ref: ComponentRef<VehicleOwnerDialog>;
  const bruno: Customer = { ...ana, id: 'c2', name: 'Bruno Costa' };

  beforeEach(() => {
    http = setup();
    const fixture = TestBed.createComponent(VehicleOwnerDialog);
    fixture.detectChanges();
    ref = fixture.componentRef;
  });

  afterEach(() => http.verify());

  it('lists other customers only, then transfers to the picked one', () => {
    const dialog = ref.instance;
    const done = vi.fn<(c: OwnerChange) => void>();
    dialog.transferred.subscribe(done);
    dialog.open(clio, ana);
    http.expectOne('/api/v1/customers').flush([ana, bruno]);
    expect(dialog['results']()).toEqual([bruno]);

    dialog.pick(bruno);
    dialog.confirm();
    const req = http.expectOne('/api/v1/vehicles/v1/owner');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ customerId: 'c2' });
    req.flush({ ...clio, customerId: 'c2' });
    expect(done).toHaveBeenCalledWith({ vehicle: { ...clio, customerId: 'c2' }, from: ana, to: bruno });
  });

  it('loads the current owner when it is not given', () => {
    ref.instance.open(clio);
    http.expectOne('/api/v1/customers/c1').flush(ana);
    http.expectOne('/api/v1/customers').flush([]);
    expect(ref.instance['from']()).toEqual(ana);
  });
});

describe('CustomerDetail edit actions', () => {
  it.each([
    ['OWNER', true],
    ['FRONT_DESK', true],
    ['MECHANIC', false],
  ] as const)('%s sees edit actions: %s', (role, visible) => {
    const http = setup();
    signInAs(role);
    const fixture = TestBed.createComponent(CustomerDetail);
    fixture.componentRef.setInput('id', 'c1');
    fixture.detectChanges();
    http.expectOne('/api/v1/customers/c1').flush(ana);
    http.expectOne('/api/v1/customers/c1/vehicles').flush([clio]);
    // Owners and the front desk read the workshop country (the default registration country); mechanics may not.
    http.match('/api/v1/workshop/settings').forEach((r) => r.flush({ country: 'PT' }));
    fixture.detectChanges();
    // Only buttons count: the closed dialogs' titles are always in the DOM.
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].map((b) => b.textContent?.trim());
    expect(buttons.includes('Edit customer')).toBe(visible);
    expect(buttons.includes('Change owner')).toBe(visible);
    expect(buttons.includes('Edit')).toBe(visible);
    http.verify();
  });
});
