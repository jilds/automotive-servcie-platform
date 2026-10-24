import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { Activate, activationForm } from './activate';
import { absoluteLink } from './copy-link';

describe('activation form', () => {
  it('needs a name, 12 to 72 characters and the same password twice', () => {
    const form = activationForm();
    form.setValue({ name: 'Ana', password: 'short', confirm: 'short' });
    expect(form.controls.password.hasError('minlength')).toBe(true);

    form.setValue({ name: 'Ana', password: 'long-enough-1', confirm: 'long-enough-2' });
    expect(form.hasError('mismatch')).toBe(true);
    expect(form.valid).toBe(false);

    form.setValue({ name: 'Ana', password: 'long-enough-1', confirm: 'long-enough-1' });
    expect(form.valid).toBe(true);

    form.setValue({ name: 'Ana', password: 'x'.repeat(73), confirm: 'x'.repeat(73) });
    expect(form.controls.password.hasError('maxlength')).toBe(true);
  });

  it('builds the full link to copy from the path the API returns', () => {
    expect(absoluteLink('/activate?token=abc', 'https://asp.example/')).toBe('https://asp.example/activate?token=abc');
  });
});

describe('Activate page', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem('asp.session');
    await TestBed.configureTestingModule({
      imports: [Activate],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function open(token: string) {
    const fixture = TestBed.createComponent(Activate);
    fixture.componentRef.setInput('token', token);
    fixture.detectChanges();
    return fixture;
  }

  it('greets the user, activates and signs them in', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = open('tok-1');
    http.expectOne('/api/v1/auth/activation?token=tok-1').flush({
      email: 'ana@oficina.pt', name: 'Ana', workshopName: 'Oficina Azul', expiresAt: '2026-10-24T10:00:00Z',
    });
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.textContent).toContain('Oficina Azul');
    expect((page.querySelector('#act-name') as HTMLInputElement).value).toBe('Ana');

    const submit = page.querySelector('button[type=submit]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    for (const [id, value] of [['#act-password', 'long-enough-1'], ['#act-confirm', 'long-enough-1']]) {
      const input = page.querySelector(id) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
    expect(submit.disabled).toBe(false);
    submit.click();

    const post = http.expectOne('/api/v1/auth/activate');
    expect(post.request.body).toEqual({ token: 'tok-1', name: 'Ana', password: 'long-enough-1' });
    post.flush({
      accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600,
      user: { id: 'u1', tenantId: 't1', name: 'Ana', email: 'ana@oficina.pt', role: 'OWNER' },
    });
    expect(TestBed.inject(AuthService).user()?.role).toBe('OWNER');
    expect(navigate).toHaveBeenCalledWith('/board');
  });

  it('explains an expired link instead of showing the form', () => {
    const fixture = open('old');
    http.expectOne('/api/v1/auth/activation?token=old').flush(
      { code: 'activation-expired', status: 409 },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('form')).toBeNull();
    expect(page.textContent).toContain('expirou');
  });
});

describe('error messages for sign-in problems', () => {
  it('says the workshop is suspended instead of a generic 403', async () => {
    const { HttpErrorResponse } = await import('@angular/common/http');
    const { I18nService } = await import('../core/i18n/i18n.service');
    const i18n = TestBed.inject(I18nService);
    i18n.use('en');
    const suspended = new HttpErrorResponse({ status: 403, error: { code: 'workshop-suspended' } });
    expect(i18n.error(suspended)).toContain('suspended');
    expect(i18n.error(new HttpErrorResponse({ status: 403, error: null }))).toBe(i18n.t('error.forbidden'));
  });
});
