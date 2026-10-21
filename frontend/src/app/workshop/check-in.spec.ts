import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Vehicle } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { CheckIn } from './check-in';

const vehicle: Vehicle = {
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
  year: 2015,
  fuelType: null,
  mileage: 100100,
};

describe('CheckIn', () => {
  let http: HttpTestingController;

  function open(lang: 'en' | 'pt-PT') {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18nService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CheckIn);
    fixture.detectChanges();
    http.expectOne('/api/v1/users').flush([{ id: 'm1', tenantId: 't', name: 'Rui', email: 'r@example.test', role: 'MECHANIC', active: true }]);
    fixture.componentInstance.find('11-AA-11');
    http.expectOne((r) => r.url === '/api/v1/vehicles').flush(vehicle);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('formats the last mileage in the UI language', () => {
    const fixture = open('pt-PT');
    expect((fixture.nativeElement.textContent as string).replace(/[\u00a0\u202f]/g, ' ')).toContain('últimos 100 100 km');
  });

  it('shows invalid-mechanic on the mechanic select instead of an alert', () => {
    const fixture = open('en');
    fixture.componentInstance['form'].patchValue({ assignedMechanicId: 'm1' });
    fixture.componentInstance.submit(vehicle);
    http.expectOne('/api/v1/work-orders').flush({ code: 'invalid-mechanic' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('#ci-mechanic') as HTMLSelectElement;
    expect(select.classList).toContain('is-invalid');
    expect(fixture.nativeElement.querySelector('#ci-mechanic-help').textContent).toContain('Choose an active mechanic of this workshop.');
    expect(fixture.nativeElement.querySelector('app-alert')).toBeNull();
  });
});
