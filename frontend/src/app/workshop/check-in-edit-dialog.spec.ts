import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { WorkOrder, WorkOrderStatus } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { CheckInEditDialog } from './check-in-edit-dialog';
import { WorkOrderDetail } from './work-order-detail';

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

const order = (status: WorkOrderStatus, fuelLevel: number | null = 5): WorkOrder => ({
  id: 'w1',
  number: 7,
  vehicleId: 'v1',
  plate: '11AA11',
  plateDisplay: '11-AA-11',
  plateEra: 'PT_2005',
  registrationCountry: 'PT',
  registrationDate: null,
  status,
  allowedNext: [],
  mileageIn: 100000,
  fuelLevel,
  complaint: 'Noise when braking',
  damageNotes: null,
  assignedMechanicId: null,
  statusChangedAt: '2026-10-20T09:00:00Z',
  lines: [],
  netCents: 0,
  vatCents: 0,
  totalCents: 0,
  readOnly: false,
});

describe('CheckInEditDialog', () => {
  let http: HttpTestingController;
  let dialog: CheckInEditDialog;

  beforeEach(() => {
    http = setup();
    const fixture = TestBed.createComponent(CheckInEditDialog);
    fixture.detectChanges();
    dialog = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  it('opens prefilled and sends every field, blanks as null', () => {
    dialog.open(order('DIAGNOSTICS'));
    expect(dialog.request()).toEqual({ mileageIn: 100000, fuelLevel: 5, complaint: 'Noise when braking', damageNotes: null });
    dialog.form.patchValue({ complaint: '  ', damageNotes: 'Dent on rear bumper', fuelLevel: 2 });
    expect(dialog.request()).toEqual({ mileageIn: 100000, fuelLevel: 2, complaint: null, damageNotes: 'Dent on rear bumper' });
  });

  it('keeps an unrecorded fuel level empty unless the slider is moved', () => {
    dialog.open(order('DIAGNOSTICS', null));
    expect(dialog.request().fuelLevel).toBeNull();
  });

  it('rejects a negative mileage without calling the API', () => {
    dialog.open(order('DIAGNOSTICS'));
    dialog.form.patchValue({ mileageIn: -1 });
    expect(dialog.form.controls.mileageIn.hasError('min')).toBe(true);
    dialog.save();
    http.expectNone('/api/v1/work-orders/w1/check-in');
  });

  it('saves with PUT and emits the updated order', () => {
    const saved = vi.fn();
    dialog.saved.subscribe(saved);
    dialog.open(order('IN_PROGRESS'));
    dialog.save();
    const req = http.expectOne('/api/v1/work-orders/w1/check-in');
    expect(req.request.method).toBe('PUT');
    req.flush(order('IN_PROGRESS'));
    expect(saved).toHaveBeenCalledOnce();
  });

  it('reports a 409 as a conflict so the page reloads', () => {
    const conflict = vi.fn();
    dialog.conflict.subscribe(conflict);
    dialog.open(order('DIAGNOSTICS'));
    dialog.save();
    http.expectOne('/api/v1/work-orders/w1/check-in').flush({ code: 'work-order-locked' }, { status: 409, statusText: 'Conflict' });
    expect(conflict).toHaveBeenCalledOnce();
  });

  it('marks server validation errors on the field', () => {
    dialog.open(order('DIAGNOSTICS'));
    dialog.save();
    http
      .expectOne('/api/v1/work-orders/w1/check-in')
      .flush({ code: 'validation', errors: { fuelLevel: 'must be at most 8' } }, { status: 400, statusText: 'Bad Request' });
    expect(dialog.form.controls.fuelLevel.hasError('server')).toBe(true);
  });
});

describe('WorkOrderDetail check-in edit action', () => {
  function render(role: 'OWNER' | 'FRONT_DESK' | 'MECHANIC', status: WorkOrderStatus) {
    const http = setup();
    const user = { id: 'u1', tenantId: 't1', name: 'U', email: 'u@x.pt', role };
    localStorage.setItem('asp.session', JSON.stringify({ token: 'tok', expiresAt: Date.now() + 60_000, user }));
    const fixture = TestBed.createComponent(WorkOrderDetail);
    fixture.componentRef.setInput('id', 'w1');
    fixture.detectChanges();
    http.expectOne('/api/v1/work-orders/w1').flush(order(status));
    fixture.detectChanges();
    return { http, fixture, canEdit: editButton(fixture.nativeElement) !== undefined };
  }

  it.each(['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS'] as const)('front desk can edit while %s', (status) => {
    expect(render('FRONT_DESK', status).canEdit).toBe(true);
  });

  it.each(['READY', 'DELIVERED', 'CANCELLED'] as const)('nobody can edit once %s', (status) => {
    expect(render('OWNER', status).canEdit).toBe(false);
  });

  it('mechanics never get the action', () => {
    expect(render('MECHANIC', 'DIAGNOSTICS').canEdit).toBe(false);
  });

  it('reloads the order with a message after a conflict', () => {
    const { http, fixture } = render('OWNER', 'DIAGNOSTICS');
    const button = editButton(fixture.nativeElement);
    button!.click();
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLFormElement>('app-check-in-edit-dialog form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/work-orders/w1/check-in').flush({ code: 'work-order-locked' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/work-orders/w1').flush(order('READY'));
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('It has been reloaded');
    expect(editButton(fixture.nativeElement)).toBeUndefined();
    // The owner's mechanic card loads the assignment for the order, and again after the reload.
    http.match('/api/v1/work-orders/w1/mechanic').forEach((r) => r.flush({ mechanicId: null, mechanicName: null, changes: [] }));
    // ...and the catalogue picker loads the services and packages it offers.
    http.match((r) => r.url.startsWith('/api/v1/catalogue/')).forEach((r) => r.flush([]));
    // ...and the stock picker loads the products it offers.
    http.match((r) => r.url.startsWith('/api/v1/stock/')).forEach((r) => r.flush([]));
    http.verify();
  });
});

/** The page's "Edit check-in" button; the closed dialog's title with the same text does not count. */
function editButton(root: HTMLElement): HTMLButtonElement | undefined {
  return [...root.querySelectorAll('button')].find((b) => !b.closest('dialog') && b.textContent?.trim() === 'Edit check-in');
}
