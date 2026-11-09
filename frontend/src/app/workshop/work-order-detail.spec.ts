import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { WorkOrder } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { flushFloor } from './floor-test-utils';
import { WorkOrderDetail } from './work-order-detail';

const order: WorkOrder = {
  id: 'w1', number: 7, vehicleId: 'v1', plate: '11AA11', plateDisplay: '11-AA-11', plateEra: 'PT_2005',
  registrationCountry: 'PT', registrationDate: null, status: 'AWAITING_APPROVAL', allowedNext: [], mileageIn: 1000,
  fuelLevel: 4, complaint: null, damageNotes: null, assignedMechanicId: null, statusChangedAt: '2026-10-22T09:00:00Z',
  lines: [], netCents: null, vatCents: null, totalCents: null, readOnly: false,
};

describe('WorkOrderDetail approval hint', () => {
  let http: HttpTestingController;
  let current: ComponentFixture<WorkOrderDetail>;

  function open(w: WorkOrder, lang: 'en' | 'pt-PT' | 'pt-BR' = 'en') {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
    // A role that sees no prices and no front-office panels keeps the page small; the hint does not depend on the role.
    const auth = { user: signal(null), account: signal(null), hasRole: () => false, canSeePrices: () => false };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    TestBed.inject(I18nService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(WorkOrderDetail);
    fixture.componentRef.setInput('id', 'w1');
    fixture.detectChanges();
    http.expectOne('/api/v1/work-orders/w1').flush(w);
    fixture.detectChanges();
    flushFloor(http);
    current = fixture;
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  it('shows the info hint and no move buttons while the answer is pending', () => {
    const el = open({ ...order, approvalPending: true });
    const hint = el.querySelector('app-alert');
    expect(hint?.textContent).toContain("Waiting for the customer's answer to the quote.");
    expect(el.querySelectorAll('header .btn')).toHaveLength(0);
  });

  it('translates the hint', () => {
    expect(open({ ...order, approvalPending: true }, 'pt-PT').querySelector('app-alert')?.textContent).toContain('A aguardar a resposta do cliente');
  });

  it('shows no hint when nothing is pending, and the server-filtered moves as buttons', () => {
    const el = open({ ...order, approvalPending: false, allowedNext: ['IN_PROGRESS', 'CANCELLED'] });
    expect(el.querySelector('app-alert')).toBeNull();
    expect(el.querySelectorAll('header .btn')).toHaveLength(2);
  });

  it('hides the owner-only hint on a read-only job', () => {
    expect(open({ ...order, approvalPending: true, readOnly: true }).textContent).not.toContain("Waiting for the customer's answer");
  });

  it('reloads after a 409 move and keeps the translated error, with the refreshed state', () => {
    const el = open({ ...order, allowedNext: ['IN_PROGRESS'] });
    expect(el.querySelectorAll('header .btn')).toHaveLength(1);
    (el.querySelector('header .btn') as HTMLButtonElement).click();
    http.expectOne('/api/v1/work-orders/w1/status').flush({ code: 'approval-pending' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/work-orders/w1').flush({ ...order, approvalPending: true, allowedNext: [] });
    current.detectChanges();
    expect(el.querySelectorAll('header .btn')).toHaveLength(0);
    expect(el.textContent).toContain('cannot leave Awaiting approval until the customer has answered the quote');
    expect(el.textContent).toContain("Waiting for the customer's answer");
  });

  it('shows no hint when the server omits the field', () => {
    expect(open(order).querySelector('app-alert')).toBeNull();
  });
});
