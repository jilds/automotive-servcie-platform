import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiService } from './api.service';
import { CheckInUpdateRequest, CustomerRequest, VehicleUpdateRequest } from './models';

describe('ApiService (Release 2 edits)', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('updates a customer with PUT /customers/{id}', () => {
    const body: CustomerRequest = {
      name: 'Ana',
      nif: '123456789',
      phone: null,
      email: 'ana@example.pt',
      address: { street: 'Rua A 1', detail: null, postalCode: '1000-001', locality: 'Lisboa', country: 'PT' },
      marketingConsent: true,
    };
    let saved: unknown;
    api.updateCustomer('c1', body).subscribe((c) => (saved = c));
    const req = http.expectOne('/api/v1/customers/c1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 'c1', ...body });
    expect(saved).toEqual({ id: 'c1', ...body });
  });

  it('updates a vehicle with PUT /vehicles/{id}', () => {
    const body: VehicleUpdateRequest = { plate: '11-AA-11', vin: null, make: 'Renault', model: 'Clio', year: 2020, fuelType: 'Diesel', mileage: null, registrationCountry: 'PT', registrationDate: '2020-01' };
    api.updateVehicle('v1', body).subscribe();
    const req = http.expectOne('/api/v1/vehicles/v1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('transfers a vehicle with PUT /vehicles/{id}/owner', () => {
    api.transferVehicle('v1', 'c2').subscribe();
    const req = http.expectOne('/api/v1/vehicles/v1/owner');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ customerId: 'c2' });
    req.flush({});
  });

  it('updates check-in data with PUT /work-orders/{id}/check-in', () => {
    const body: CheckInUpdateRequest = { mileageIn: 101000, fuelLevel: 5, complaint: 'Noise when braking', damageNotes: null };
    api.updateCheckIn('w1', body).subscribe();
    const req = http.expectOne('/api/v1/work-orders/w1/check-in');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });
});
