import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { BackofficeApi } from './backoffice-api.service';
import { Dashboard as DashboardData, TeamMember, WorkshopSettings } from './backoffice.models';
import { Dashboard } from './dashboard';
import { bars, monthDate, percentOf, statusBars, workloadBars } from './dashboard.model';
import { Settings } from './settings';
import { settingsForm, timeZones, toFormValue, toSettings } from './settings-form';
import { Team } from './team';
import { Toasts } from './toasts';

describe('BackofficeApi', () => {
  let api: BackofficeApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(BackofficeApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the dashboard and updates team members through the release-2 endpoints', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/dashboard').flush({});

    api.updateUser('user-1', { role: 'FRONT_DESK', active: false, version: 3 }).subscribe();
    const request = http.expectOne('/api/v1/users/user-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ role: 'FRONT_DESK', active: false, version: 3 });
    request.flush({});
  });
});

describe('dashboard view model', () => {
  it('scales bars from the largest value and handles zero values safely', () => {
    expect(percentOf(5, 10)).toBe(50);
    expect(percentOf(0, 0)).toBe(0);
    expect(bars(['a', 'b'], (value) => (value === 'a' ? 2 : 4)).map((bar) => bar.percent)).toEqual([50, 100]);
    expect(statusBars({ DIAGNOSTICS: 2 }).map((bar) => bar.value)).toEqual([2, 0, 0, 0, 0]);
    expect(monthDate('2026-10')?.getMonth()).toBe(9);
    expect(monthDate('not-a-month')).toBeNull();
  });
});

describe('workshop settings form', () => {
  it('mirrors API constraints and converts the labour rate to cents', () => {
    const form = settingsForm();
    form.patchValue({ name: '  ', nif: '123', defaultVatRate: 23.456, labourRateEuros: -1 });
    expect(form.invalid).toBe(true);

    form.patchValue({ name: ' Oficina Central ', nif: '', country: 'pt', timezone: 'Europe/Lisbon', defaultVatRate: 23.5, labourRateEuros: 45.25 });
    expect(form.valid).toBe(true);
    expect(toSettings(form, 4)).toMatchObject({ name: 'Oficina Central', nif: null, country: 'PT', labourRateCents: 4525, version: 4 });
  });
});

describe('Team', () => {
  const currentUser: TeamMember = {
    id: 'owner-1', tenantId: 'tenant-1', name: 'Owner', email: 'owner@example.test', role: 'OWNER', active: true, version: 0,
  };

  it('does not render role or activation controls for the signed-in owner', async () => {
    await TestBed.configureTestingModule({
      imports: [Team],
      providers: [
        { provide: AuthService, useValue: { user: () => currentUser } },
        { provide: BackofficeApi, useValue: { users: () => of([currentUser]) } },
        { provide: Toasts, useValue: { show: () => undefined } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(Team);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('tr[data-user="owner-1"]') as HTMLTableRowElement;
    expect(row).toBeTruthy();
    expect(row.querySelector('select')).toBeNull();
    expect(row.querySelector('.btn-link')).toBeNull();
  });
});

describe('BackofficeApi (team and settings)', () => {
  let api: BackofficeApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(BackofficeApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists and invites users', () => {
    api.users().subscribe();
    expect(http.expectOne('/api/v1/users').request.method).toBe('GET');

    const body = { name: 'Rui', email: 'rui@oficina.pt', role: 'MECHANIC' as const };
    api.inviteUser(body).subscribe();
    const invite = http.expectOne('/api/v1/users');
    expect(invite.request.method).toBe('POST');
    expect(invite.request.body).toEqual(body);
    invite.flush({});
  });

  it('reads and saves the workshop settings', () => {
    api.settings().subscribe();
    expect(http.expectOne('/api/v1/workshop/settings').request.method).toBe('GET');

    const settings = { name: 'Oficina', nif: null, country: 'PT', timezone: 'Europe/Lisbon', defaultVatRate: 23, labourRateCents: 4500, version: 0 };
    api.saveSettings(settings).subscribe();
    const put = http.expectOne('/api/v1/workshop/settings');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual(settings);
    put.flush(settings);
  });
});

describe('backoffice errors', () => {
  it('translates the team problem codes through the shared error keys', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.use('en');
    const problem = (code: string) => new HttpErrorResponse({ status: 409, error: { code, detail: 'x' } });
    expect(i18n.error(problem('last-owner'))).toBe('The workshop must keep at least one active owner.');
    expect(i18n.error(problem('own-account-change'))).toBe('You cannot change your own role or deactivate yourself.');
    expect(i18n.error(problem('user-not-found'))).toBe('That user no longer exists.');
  });
});

describe('Dashboard loading', () => {
  const dashboard = (carsInShop: number): DashboardData => ({
    month: '2026-10',
    openByStatus: {} as DashboardData['openByStatus'],
    carsInShop,
    deliveredThisMonth: 0,
    revenueThisMonth: { netCents: 0, vatCents: 0, grossCents: 0 },
    mechanicWorkload: [],
  });

  function setup() {
    const answers: Subject<DashboardData>[] = [];
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        {
          provide: BackofficeApi,
          useValue: {
            dashboard: () => {
              const answer = new Subject<DashboardData>();
              answers.push(answer);
              return answer;
            },
          },
        },
      ],
    });
    TestBed.inject(I18nService).use('en');
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const stat = () => (fixture.nativeElement.querySelector('.stat-value') as HTMLElement | null)?.textContent?.trim();
    return { fixture, answers, stat };
  }

  it('never lets an older answer overwrite a newer one', () => {
    const { fixture, answers, stat } = setup();
    fixture.componentInstance.load();
    expect(answers).toHaveLength(2);
    expect(answers[0].observed).toBe(false);

    answers[1].next(dashboard(5));
    answers[0].next(dashboard(1));
    fixture.detectChanges();
    expect(stat()).toBe('5');
  });

  it('shows an error and still loads on the next refresh', () => {
    const { fixture, answers, stat } = setup();
    answers[0].error(new HttpErrorResponse({ status: 409, error: { code: 'try-again' } }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Someone else is changing this right now.');

    fixture.componentInstance.load();
    answers[1].next(dashboard(3));
    fixture.detectChanges();
    expect(stat()).toBe('3');
    expect(fixture.nativeElement.textContent).not.toContain('Someone else is changing this right now.');
  });

  it('stops listening when the view is destroyed', () => {
    const { fixture, answers } = setup();
    fixture.destroy();
    expect(answers[0].observed).toBe(false);
  });
});

describe('Settings server errors', () => {
  const settings: WorkshopSettings = { name: 'Oficina', nif: null, country: 'PT', timezone: 'Europe/Lisbon', defaultVatRate: 23, labourRateCents: 4500, version: 0 };

  it('shows API validation errors on the matching fields, in the UI language', () => {
    TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        {
          provide: BackofficeApi,
          useValue: {
            settings: () => of(settings),
            saveSettings: () =>
              throwError(() => new HttpErrorResponse({ status: 400, error: { code: 'validation', errors: { labourRateCents: 'must not be negative', nif: 'bad' } } })),
          },
        },
        { provide: Toasts, useValue: { show: () => undefined } },
      ],
    });
    TestBed.inject(I18nService).use('en');
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();

    fixture.componentInstance['save']();
    fixture.detectChanges();

    const feedback = [...fixture.nativeElement.querySelectorAll('.invalid-feedback')].map((e: Element) => e.textContent?.trim());
    expect(feedback).toEqual(['The NIF must have exactly 9 digits.', 'Enter an amount of 0 or more with at most 2 decimals.']);
    expect(fixture.nativeElement.querySelector('#ws-labour').classList).toContain('is-invalid');
    expect(fixture.nativeElement.textContent).toContain('Some fields are invalid.');
  });
});

describe('settings form helpers', () => {
  it('checks decimals, blank names and the VAT range', () => {
    const form = settingsForm();
    form.patchValue({ name: 'Oficina', defaultVatRate: 100.01 });
    expect(form.controls.defaultVatRate.hasError('max')).toBe(true);
    form.patchValue({ defaultVatRate: 6.5, labourRateEuros: 12.345 });
    expect(form.controls.defaultVatRate.valid).toBe(true);
    expect(form.controls.labourRateEuros.hasError('decimals')).toBe(true);
    form.patchValue({ labourRateEuros: 0.1 + 0.2 });
    expect(form.controls.labourRateEuros.valid).toBe(true);
    expect(toSettings(form, 0).labourRateCents).toBe(30);
  });

  it('round-trips the API shape (cents to euros and back)', () => {
    const form = settingsForm();
    form.reset(toFormValue({ name: 'Oficina', nif: '509999999', country: 'PT', timezone: 'Atlantic/Azores', defaultVatRate: 16, labourRateCents: 4550, version: 2 }));
    expect(form.controls.labourRateEuros.value).toBe(45.5);
    expect(toSettings(form, 2)).toEqual({ name: 'Oficina', nif: '509999999', country: 'PT', timezone: 'Atlantic/Azores', defaultVatRate: 16, labourRateCents: 4550, version: 2 });
  });

  it('falls back to a fixed time zone list and always keeps the stored zone', () => {
    expect(timeZones('Europe/Lisbon', [])).toContain('Atlantic/Madeira');
    expect(timeZones('Etc/GMT+1', ['Europe/Lisbon'])).toEqual(['Etc/GMT+1', 'Europe/Lisbon']);
  });
});

describe('dashboard view model (workload)', () => {
  it('gives the busiest mechanic the full bar and zeros nothing', () => {
    const result = workloadBars({
      mechanicWorkload: [
        { mechanicId: 'a', name: 'Rui', openOrders: 3 },
        { mechanicId: 'b', name: 'Zé', openOrders: 1 },
        { mechanicId: 'c', name: 'Ana', openOrders: 0 },
      ],
    });
    expect(result.map((b) => b.percent)).toEqual([100, 33.3, 0]);
    expect(workloadBars({ mechanicWorkload: [{ mechanicId: 'a', name: 'Rui', openOrders: 0 }] })[0].percent).toBe(0);
  });
});

describe('Team (other users)', () => {
  it('renders role and deactivate controls for the other members', async () => {
    const owner: TeamMember = { id: 'owner-1', tenantId: 't', name: 'Owner', email: 'o@example.test', role: 'OWNER', active: true, version: 0 };
    const mechanic: TeamMember = { id: 'mech-1', tenantId: 't', name: 'Rui', email: 'r@example.test', role: 'MECHANIC', active: true, version: 0 };
    await TestBed.configureTestingModule({
      imports: [Team],
      providers: [
        { provide: AuthService, useValue: { user: () => owner } },
        { provide: BackofficeApi, useValue: { users: () => of([owner, mechanic]) } },
        { provide: Toasts, useValue: { show: () => undefined } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(Team);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('tr[data-user="mech-1"]') as HTMLTableRowElement;
    expect((row.querySelector('select') as HTMLSelectElement).value).toBe('MECHANIC');
    expect(row.querySelector('.btn-link.text-danger-emphasis')).toBeTruthy();
  });
});
