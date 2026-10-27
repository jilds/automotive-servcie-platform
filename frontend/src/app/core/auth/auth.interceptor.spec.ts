import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { authInterceptor, sendsToken } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: 'secret-token', logout: () => {} } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('sends the token to the workshop API', () => {
    client.get('/api/v1/bookings').subscribe();
    expect(http.expectOne('/api/v1/bookings').request.headers.get('Authorization')).toBe('Bearer secret-token');
  });

  it('never sends it to a workshop public page, even when signed in', () => {
    client.post('/api/v1/public/oficina-azul/booking/bookings', {}).subscribe();
    client.get('/api/v1/public/oficina-azul/booking/slots').subscribe();
    for (const req of http.match((r) => r.url.startsWith('/api/v1/public/'))) {
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    }
  });

  it('knows which URLs carry the token', () => {
    expect(sendsToken('/api/v1/branding')).toBe(true);
    // The public theme by slug is anonymous: a stale token there would sign a customer out of a shop page.
    expect(sendsToken('/api/v1/branding/oficina-azul')).toBe(false);
    expect(sendsToken('/api/v1/public/x/booking/services')).toBe(false);
    expect(sendsToken('/assets/logo.png')).toBe(false);
    expect(sendsToken('https://elsewhere.example/api/v1/bookings')).toBe(false);
  });
});
