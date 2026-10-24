import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { MechanicAssignment, WorkOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { MechanicCard, reasonRequired } from './mechanic-card';

const order: WorkOrder = {
  id: 'w1', number: 7, vehicleId: 'v1', plate: '11AA11', plateDisplay: '11-AA-11', plateEra: 'PT_2005',
  registrationCountry: 'PT', registrationDate: null, status: 'IN_PROGRESS', allowedNext: [], mileageIn: 1000,
  fuelLevel: 4, complaint: null, damageNotes: null, assignedMechanicId: 'rui', statusChangedAt: '2026-10-22T09:00:00Z',
  lines: [], netCents: 0, vatCents: 0, totalCents: 0, readOnly: false,
};

const assigned: MechanicAssignment = {
  mechanicId: 'rui',
  mechanicName: 'Rui',
  changes: [
    { fromMechanicId: null, fromName: null, toMechanicId: 'rui', toName: 'Rui', reason: null, changedById: 'd', changedByName: 'Sara', changedAt: '2026-10-22T08:00:00Z' },
  ],
};

describe('reasonRequired', () => {
  it('asks for a reason only when an assigned mechanic is replaced or removed', () => {
    expect(reasonRequired(null, 'rui')).toBe(false);
    expect(reasonRequired('rui', 'rui')).toBe(false);
    expect(reasonRequired('rui', 'ze')).toBe(true);
    expect(reasonRequired('rui', null)).toBe(true);
  });
});

describe('MechanicCard', () => {
  let http: HttpTestingController;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  function setup(w: WorkOrder = order) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(MechanicCard);
    fixture.componentRef.setInput('workOrder', w);
    fixture.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/mechanic').flush(assigned);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('shows the current mechanic and the history', () => {
    const fixture = setup();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Rui');
    expect(text).toContain('Sara');
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Change mechanic');
  });

  it('needs a reason to replace the mechanic, then sends it and emits the saved order', () => {
    const fixture = setup();
    const card = fixture.componentInstance;
    const changed = vi.fn();
    card.changed.subscribe(changed);
    card.open();
    http.expectOne('/api/v1/mechanics').flush([{ id: 'rui', name: 'Rui' }, { id: 'ze', name: 'Zé' }]);

    card.form.controls.mechanicId.setValue('ze');
    card.save();
    expect(card.form.controls.reason.hasError('required')).toBe(true);
    http.expectNone('/api/v1/work-orders/w1/mechanic');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#mc-reason-help').textContent).toContain('Say why the mechanic changes.');

    card.form.controls.reason.setValue('  Rui is off sick  ');
    card.save();
    const put = http.expectOne({ method: 'PUT', url: '/api/v1/work-orders/w1/mechanic' });
    expect(put.request.body).toEqual({ mechanicId: 'ze', reason: 'Rui is off sick' });
    put.flush({ ...order, assignedMechanicId: 'ze' });
    expect(changed).toHaveBeenCalledWith({ ...order, assignedMechanicId: 'ze' });
  });

  it('shows the server reason-required error on the reason field', () => {
    const fixture = setup();
    const card = fixture.componentInstance;
    card.open();
    http.expectOne('/api/v1/mechanics').flush([]);
    card.form.controls.mechanicId.setValue(null);
    card.form.controls.reason.setValue('x');
    card.save();
    http.expectOne({ method: 'PUT', url: '/api/v1/work-orders/w1/mechanic' })
      .flush({ code: 'reason-required', errors: { reason: 'Say why' } }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#mc-reason').classList).toContain('is-invalid');
  });

  it('offers no change on a delivered work order', () => {
    const fixture = setup({ ...order, status: 'DELIVERED' });
    expect(fixture.nativeElement.querySelector('section button')).toBeNull();
  });
});
