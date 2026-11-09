import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AccountRole, WorkOrder, WorkOrderStatus } from '../core/api/models';
import { allTexts } from '../core/i18n/all-texts';
import { I18nService } from '../core/i18n/i18n.service';
import { ChecklistPanel } from './checklist-panel';
import { Checklist, ChecklistItem, Photo, TimeEntry, TimeSummary } from './floor.models';
import { PhotoCache } from './photo-cache';
import { PhotoPanel } from './photo-panel';
import { RunningTimerBanner } from './running-timer';
import { TimePanel } from './time-panel';
import { flushFloor } from './floor-test-utils';
import { WorkOrderDetail } from './work-order-detail';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

const LINE = { id: 'l1', position: 1, type: 'LABOR' as const, description: 'Mudança de óleo', quantity: 1, unitPriceCents: null, discountPercent: null, vatRate: null, netCents: null };

const order = (over: Partial<WorkOrder> = {}): WorkOrder => ({
  id: 'w1', number: 12, vehicleId: 'v1', plate: 'AA00AA', plateDisplay: 'AA-00-AA', plateEra: 'PT_2020',
  registrationCountry: 'PT', registrationDate: null, status: 'IN_PROGRESS', allowedNext: [], mileageIn: 1000,
  fuelLevel: 4, complaint: null, damageNotes: null, assignedMechanicId: 'm1', statusChangedAt: '2026-11-09T08:00:00Z',
  lines: [LINE], netCents: null, vatCents: null, totalCents: null, readOnly: false,
  ...over,
});

function setup(role: AccountRole, userId: string) {
  try {
    localStorage.clear();
    localStorage.setItem('asp.session', JSON.stringify({ token: 't', expiresAt: Date.now() + 60_000, user: { id: userId, tenantId: 't1', name: 'U', email: 'u@x.pt', role } }));
  } catch {
    // ignore
  }
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(I18nService).use('en');
  return TestBed.inject(HttpTestingController);
}

const html = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
const text = (f: ComponentFixture<unknown>) => (html(f).textContent ?? '').replace(/\s+/g, ' ');
const q = <T extends Element = HTMLElement>(f: ComponentFixture<unknown>, selector: string) => html(f).querySelector(selector) as T | null;
const click = (f: ComponentFixture<unknown>, selector: string) => {
  const e = q(f, selector);
  if (!e) throw new Error(`no ${selector}`);
  e.click();
  f.detectChanges();
};

const entry = (over: Partial<TimeEntry> = {}): TimeEntry => ({
  id: 'e1', workOrderId: 'w1', lineId: null, mechanicId: 'm1', mechanicName: 'Rui', startedAt: '2026-11-09T08:30:00Z',
  stoppedAt: null, seconds: 1260, running: true, autoStopped: false, editedByName: null, editedAt: null, editReason: null, version: 0,
  ...over,
});

function summary(over: Partial<TimeSummary> = {}): TimeSummary {
  return { now: new Date().toISOString(), totalSeconds: 0, unassignedSeconds: 0, byLine: [], running: null, entries: [], ...over };
}

describe('TimePanel', () => {
  function open(role: AccountRole, userId: string, w: WorkOrder, s: TimeSummary) {
    const http = setup(role, userId);
    const f = TestBed.createComponent(TimePanel);
    f.componentRef.setInput('workOrder', w);
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/time').flush(s);
    f.detectChanges();
    return { http, f };
  }

  /** A running entry that started 21 minutes before the server's now. */
  function running(): TimeSummary {
    const now = new Date();
    const started = new Date(now.getTime() - 21 * 60_000).toISOString();
    return summary({ now: now.toISOString(), totalSeconds: 1260, running: entry({ startedAt: started }), entries: [entry({ startedAt: started })] });
  }

  it('shows the running clock from the server time, and the totals', () => {
    const { f, http } = open('MECHANIC', 'm1', order(), running());
    expect(q(f, '[data-test="clock"]')!.textContent!.trim()).toBe('0:21:00');
    expect(q(f, '[data-test="total"]')!.textContent!.trim()).toBe('0:21:00');
    expect(q(f, '[data-test="stop"]')).not.toBeNull();
    expect(q(f, '[data-test="start"]')).toBeNull();
    http.verify();
  });

  it('uses the server clock even when the device clock is wrong', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    try {
      vi.setSystemTime(new Date('2026-11-09T12:00:00Z'));
      const { f } = open('MECHANIC', 'm1', order(), summary({ now: '2026-11-09T08:51:00Z', running: entry(), totalSeconds: 1260, entries: [entry()] }));
      expect(q(f, '[data-test="clock"]')!.textContent!.trim()).toBe('0:21:00');
    } finally {
      vi.useRealTimers();
    }
  });

  it('stops the timer, then reads the time and the banner timer again', () => {
    const { f, http } = open('MECHANIC', 'm1', order(), running());
    click(f, '[data-test="stop"]');
    http.expectOne('/api/v1/work-orders/w1/time/stop').flush(entry({ running: false, stoppedAt: '2026-11-09T08:51:00Z' }));
    http.expectOne('/api/v1/work-orders/w1/time').flush(summary());
    http.expectOne('/api/v1/time/running').flush({ entry: null, workOrderNumber: null, plateDisplay: null });
    f.detectChanges();
    expect(q(f, '[data-test="start"]')).not.toBeNull();
    http.verify();
  });

  it('starts a timer on the chosen line', () => {
    const { f, http } = open('MECHANIC', 'm1', order(), summary());
    const select = q<HTMLSelectElement>(f, '[data-test="line"]')!;
    select.value = 'l1';
    select.dispatchEvent(new Event('change'));
    click(f, '[data-test="start"]');
    const req = http.expectOne('/api/v1/work-orders/w1/time/start');
    expect(req.request.body).toEqual({ lineId: 'l1' });
    req.flush(entry({ lineId: 'l1' }), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/time').flush(summary());
    http.expectOne('/api/v1/time/running').flush({ entry: null, workOrderNumber: null, plateDisplay: null });
  });

  it('shows a translated error and reads the state again when a start is refused', () => {
    const { f, http } = open('MECHANIC', 'm1', order(), summary());
    click(f, '[data-test="start"]');
    http.expectOne('/api/v1/work-orders/w1/time/start').flush({ code: 'time-tracking-closed' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/work-orders/w1/time').flush(summary());
    http.expectOne('/api/v1/time/running').flush({ entry: null, workOrderNumber: null, plateDisplay: null });
    f.detectChanges();
    expect(text(f)).toContain('a timer cannot start');
  });

  it('gives another mechanic no buttons, only the reading', () => {
    const { f, http } = open('MECHANIC', 'm2', order(), running());
    expect(q(f, '[data-test="stop"]')).toBeNull();
    expect(q(f, '[data-test="start"]')).toBeNull();
    expect(q(f, '[data-test="correct"]')).toBeNull();
    expect(q(f, '[data-test="add-missed"]')).toBeNull();
    expect(q(f, '[data-test="block"]')!.textContent).toContain('assigned to someone else');
    http.verify();
  });

  it('gives the owner start for the assigned mechanic, and a hint when nobody is assigned', () => {
    const a = open('OWNER', 'o1', order(), summary());
    expect(q(a.f, '[data-test="start"]')).not.toBeNull();
    TestBed.resetTestingModule();
    const b = open('OWNER', 'o1', order({ assignedMechanicId: null }), summary());
    expect(q(b.f, '[data-test="start"]')).toBeNull();
    expect(q(b.f, '[data-test="block"]')!.textContent).toContain('Give the job to a mechanic');
  });

  it('keeps the front desk off the timer but lets it correct and add time', () => {
    const { f } = open('FRONT_DESK', 'f1', order(), running());
    expect(q(f, '[data-test="stop"]')).toBeNull();
    expect(q(f, '[data-test="correct"]')).not.toBeNull();
    expect(q(f, '[data-test="add-missed"]')).not.toBeNull();
    expect(q(f, '[data-test="block"]')!.textContent).toContain('front desk does not run timers');
  });

  it('closes starting when the order is ready, but the front office still corrects', () => {
    const { f } = open('OWNER', 'o1', order({ status: 'READY' }), summary({ entries: [entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z' })] }));
    expect(q(f, '[data-test="start"]')).toBeNull();
    expect(q(f, '[data-test="block"]')!.textContent).toContain('ready or closed');
    expect(q(f, '[data-test="correct"]')).not.toBeNull();
  });

  it('lists per-line time and what was corrected', () => {
    const edited = entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z', seconds: 1200, lineId: 'l1', editedByName: 'Rita', editedAt: '2026-11-09T09:00:00Z', editReason: 'Forgot to stop' });
    const { f } = open('MECHANIC', 'm1', order(), summary({ totalSeconds: 1500, unassignedSeconds: 300, byLine: [{ lineId: 'l1', seconds: 1200 }], entries: [edited] }));
    expect(q(f, '[data-test="per-line"]')!.textContent).toContain('Mudança de óleo');
    expect(q(f, '[data-test="per-line"]')!.textContent).toContain('0:20:00');
    expect(q(f, '[data-test="totals"]')!.textContent).toContain('0:05:00');
    expect(q(f, '[data-test="edited"]')!.textContent).toContain('Forgot to stop');
  });

  it('corrects an entry only with a reason, and sends the stored version', () => {
    const e = entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z', seconds: 1200, version: 4 });
    const { f, http } = open('FRONT_DESK', 'f1', order(), summary({ entries: [e] }));
    click(f, '[data-test="correct"]');
    const form = q<HTMLFormElement>(f, '[data-test="time-form"]')!;
    form.dispatchEvent(new Event('submit'));
    f.detectChanges();
    http.expectNone('/api/v1/work-orders/w1/time-entries/e1');
    expect(text(f)).toContain('Say why the time changes.');

    const reason = q<HTMLTextAreaElement>(f, '#te-reason')!;
    reason.value = '  forgot to stop  ';
    reason.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/v1/work-orders/w1/time-entries/e1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ startedAt: e.startedAt, stoppedAt: e.stoppedAt, lineId: null, reason: 'forgot to stop', version: 4 });
    req.flush({ ...e, editReason: 'forgot to stop' });
    http.expectOne('/api/v1/work-orders/w1/time').flush(summary({ entries: [e] }));
  });

  it('says Added for a missed entry (never corrected) and Corrected after a correction', () => {
    const base = { running: false, stoppedAt: '2026-11-09T08:50:00Z', editedByName: 'Rita', editedAt: '2026-11-09T09:00:00Z', editReason: 'No tablet' };
    const a = open('FRONT_DESK', 'f1', order(), summary({ entries: [entry({ ...base, version: 0 })] }));
    expect(q(a.f, '[data-test="edited"]')!.textContent).toContain('Added by Rita');
    TestBed.resetTestingModule();
    const b = open('FRONT_DESK', 'f1', order(), summary({ entries: [entry({ ...base, version: 1 })] }));
    expect(q(b.f, '[data-test="edited"]')!.textContent).toContain('Corrected by Rita');
  });

  it('shows the earlier values of a corrected entry when the history opens', () => {
    const e = entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z', seconds: 1200, lineId: 'l1', editedByName: 'Rita', editedAt: '2026-11-09T09:00:00Z', editReason: 'Forgot to stop', version: 1 });
    const { f, http } = open('FRONT_DESK', 'f1', order(), summary({ entries: [e] }));
    const details = q<HTMLDetailsElement>(f, '[data-test="history"]')!;
    details.open = true;
    details.dispatchEvent(new Event('toggle'));
    http.expectOne('/api/v1/work-orders/w1/time-entries/e1/changes').flush([
      { id: 'c1', oldStartedAt: '2026-11-09T08:00:00Z', oldStoppedAt: null, oldLineId: null, changedByName: 'Rita', changedAt: '2026-11-09T09:00:00Z', reason: 'Forgot to stop' },
    ]);
    f.detectChanges();
    const row = q(f, '[data-test="history-row"]')!.textContent!;
    expect(row).toContain('Before:');
    expect(row).toContain('…');
    expect(row).toContain('Changed by Rita');
    details.dispatchEvent(new Event('toggle'));
    http.expectNone('/api/v1/work-orders/w1/time-entries/e1/changes');
  });

  it('offers no history to a mechanic', () => {
    const e = entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z', editReason: 'x', editedByName: 'R', editedAt: '2026-11-09T09:00:00Z' });
    expect(q(open('MECHANIC', 'm1', order(), summary({ entries: [e] })).f, '[data-test="history"]')).toBeNull();
  });

  it('shows an overlap as a general error on the dialog', () => {
    const e = entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z', seconds: 1200 });
    const { f, http } = open('OWNER', 'o1', order(), summary({ entries: [e] }));
    click(f, '[data-test="correct"]');
    const reason = q<HTMLTextAreaElement>(f, '#te-reason')!;
    reason.value = 'x';
    reason.dispatchEvent(new Event('input'));
    q(f, '[data-test="time-form"]')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/work-orders/w1/time-entries/e1').flush({ code: 'time-entry-overlap' }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(q(f, '[data-test="time-form"] app-alert')!.textContent).toContain('overlaps another entry');
  });

  it('refuses a stop before the start without calling the server', () => {
    const e = entry({ running: false, stoppedAt: '2026-11-09T08:50:00Z', seconds: 1200 });
    const { f, http } = open('OWNER', 'o1', order(), summary({ entries: [e] }));
    click(f, '[data-test="correct"]');
    const stop = q<HTMLInputElement>(f, '#te-stop')!;
    stop.value = '2020-01-01T00:00';
    stop.dispatchEvent(new Event('input'));
    const reason = q<HTMLTextAreaElement>(f, '#te-reason')!;
    reason.value = 'x';
    reason.dispatchEvent(new Event('input'));
    q(f, '[data-test="time-form"]')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
    http.expectNone('/api/v1/work-orders/w1/time-entries/e1');
    expect(text(f)).toContain('cannot be in the future');
  });

  it('adds a missed entry for a mechanic with the reason', () => {
    const { f, http } = open('OWNER', 'o1', order(), summary());
    click(f, '[data-test="add-missed"]');
    http.expectOne('/api/v1/mechanics').flush([{ id: 'm1', name: 'Rui' }]);
    f.detectChanges();
    const set = (selector: string, value: string) => {
      const el = q<HTMLInputElement | HTMLTextAreaElement>(f, selector)!;
      el.value = value;
      el.dispatchEvent(new Event('input'));
    };
    set('#te-start', '2026-11-08T08:00');
    set('#te-stop', '2026-11-08T09:30');
    set('#te-reason', 'Worked without the tablet');
    q(f, '[data-test="time-form"]')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/v1/work-orders/w1/time-entries');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ mechanicId: 'm1', lineId: null, reason: 'Worked without the tablet' });
    expect(Date.parse(req.request.body.stoppedAt) - Date.parse(req.request.body.startedAt)).toBe(90 * 60_000);
    req.flush(entry({ running: false }), { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/work-orders/w1/time').flush(summary());
  });
});

describe('RunningTimerBanner', () => {
  function banner(role: AccountRole) {
    const http = setup(role, 'm1');
    const f = TestBed.createComponent(RunningTimerBanner);
    f.detectChanges();
    return { f, http };
  }

  it('links a mechanic to the order whose timer runs', () => {
    const { f, http } = banner('MECHANIC');
    http.expectOne('/api/v1/time/running').flush({ entry: entry({ seconds: 90 }), workOrderNumber: 12, plateDisplay: 'AA-00-AA' });
    f.detectChanges();
    const a = q<HTMLAnchorElement>(f, '[data-test="banner"]')!;
    expect(a.getAttribute('href')).toBe('/work-orders/w1');
    expect(a.textContent).toContain('Timer running on #12 AA-00-AA');
    expect(q(f, '[data-test="banner-clock"]')!.textContent!.trim()).toBe('0:01:30');
  });

  it('shows nothing while no timer runs', () => {
    const { f, http } = banner('MECHANIC');
    http.expectOne('/api/v1/time/running').flush({ entry: null, workOrderNumber: null, plateDisplay: null });
    f.detectChanges();
    expect(q(f, '[data-test="banner"]')).toBeNull();
  });

  it('is not asked for by the owner or the front desk', () => {
    const { http } = banner('OWNER');
    http.expectNone('/api/v1/time/running');
    http.verify();
  });
});

describe('PhotoCache', () => {
  const create = vi.fn();
  const revoke = vi.fn();
  beforeEach(() => {
    create.mockReset().mockImplementation(() => `blob:${create.mock.calls.length}`);
    revoke.mockReset();
    URL.createObjectURL = create;
    URL.revokeObjectURL = revoke;
  });

  it('fetches a photo once with the token path, and serves it from memory afterwards', async () => {
    const http = setup('MECHANIC', 'm1');
    const cache = TestBed.runInInjectionContext(() => new PhotoCache());
    const first = cache.load('w1', 'p1');
    const req = http.expectOne('/api/v1/work-orders/w1/photos/p1/content');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.headers.has('If-None-Match')).toBe(false);
    req.flush(new Blob(['x']), { headers: { ETag: '"abc"' } });
    expect(await first).toBe('blob:1');
    expect(await cache.load('w1', 'p1')).toBe('blob:1');
    http.expectNone('/api/v1/work-orders/w1/photos/p1/content');
  });

  it('asks again with the ETag and keeps the same URL on a 304', async () => {
    const http = setup('MECHANIC', 'm1');
    const cache = TestBed.runInInjectionContext(() => new PhotoCache());
    const first = cache.load('w1', 'p1');
    http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['x']), { headers: { ETag: '"abc"' } });
    await first;
    const again = cache.load('w1', 'p1', true);
    const req = http.expectOne('/api/v1/work-orders/w1/photos/p1/content');
    expect(req.request.headers.get('If-None-Match')).toBe('"abc"');
    req.flush(null, { status: 304, statusText: 'Not Modified' });
    expect(await again).toBe('blob:1');
    expect(create).toHaveBeenCalledTimes(1);
    expect(revoke).not.toHaveBeenCalled();
  });

  it('swaps in new bytes on a 200 and revokes the old URL', async () => {
    const http = setup('MECHANIC', 'm1');
    const cache = TestBed.runInInjectionContext(() => new PhotoCache());
    const first = cache.load('w1', 'p1');
    http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['x']), { headers: { ETag: '"a"' } });
    await first;
    const again = cache.load('w1', 'p1', true);
    http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['y']), { headers: { ETag: '"b"' } });
    expect(await again).toBe('blob:2');
    expect(revoke).toHaveBeenCalledWith('blob:1');
  });

  it('passes an error through when nothing is held, and revokes everything on dispose', async () => {
    const http = setup('MECHANIC', 'm1');
    const cache = TestBed.runInInjectionContext(() => new PhotoCache());
    const failed = cache.load('w1', 'p9');
    http.expectOne('/api/v1/work-orders/w1/photos/p9/content').flush(new Blob(['{}']), { status: 404, statusText: 'Not Found' });
    await expect(failed).rejects.toBeInstanceOf(HttpErrorResponse);
    const ok = cache.load('w1', 'p1');
    http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['x']));
    await ok;
    cache.dispose();
    expect(revoke).toHaveBeenCalledWith('blob:1');
  });
});

describe('PhotoPanel', () => {
  const create = vi.fn();
  const revoke = vi.fn();
  beforeEach(() => {
    create.mockReset().mockImplementation(() => `blob:${create.mock.calls.length}`);
    revoke.mockReset();
    URL.createObjectURL = create;
    URL.revokeObjectURL = revoke;
  });

  const photo = (id: string, over: Partial<Photo> = {}): Photo => ({
    id, workOrderId: 'w1', stage: 'CHECK_IN', caption: null, contentType: 'image/jpeg', sizeBytes: 1000, uploadedByName: 'Rita',
    uploadedById: 'f1', createdAt: '2026-11-09T08:00:00Z', version: 0, ...over,
  });

  function open(role: AccountRole, userId: string, photos: Photo[], w = order()) {
    const http = setup(role, userId);
    const f = TestBed.createComponent(PhotoPanel);
    f.componentRef.setInput('workOrder', w);
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/photos').flush(photos);
    f.detectChanges();
    return { http, f };
  }

  it('shows the bytes through HttpClient as object URLs, in a grid per stage, and revokes them on destroy', async () => {
    const { http, f } = open('FRONT_DESK', 'f1', [photo('p1', { caption: 'Pára-choques' }), photo('p2', { stage: 'WORK' })]);
    const a = http.expectOne('/api/v1/work-orders/w1/photos/p1/content');
    const b = http.expectOne('/api/v1/work-orders/w1/photos/p2/content');
    a.flush(new Blob(['a']), { headers: { ETag: '"a"' } });
    b.flush(new Blob(['b']), { headers: { ETag: '"b"' } });
    await vi.waitFor(() => {
      f.detectChanges();
      expect([...html(f).querySelectorAll('img')].map((i) => i.getAttribute('src'))).toEqual(['blob:1', 'blob:2']);
    });
    expect(html(f).querySelector('img')!.getAttribute('loading')).toBe('lazy');
    expect(q(f, '[data-stage="CHECK_IN"]')).not.toBeNull();
    expect(q(f, '[data-stage="WORK"]')).not.toBeNull();
    expect(q(f, '[data-stage="DELIVERY"]')).toBeNull();
    f.destroy();
    expect(revoke).toHaveBeenCalledWith('blob:1');
    expect(revoke).toHaveBeenCalledWith('blob:2');
  });

  it('shows the 30-photo and 5 MB limits as hints, and the camera input', () => {
    const { f } = open('FRONT_DESK', 'f1', []);
    expect(q(f, '[data-test="hint"]')!.textContent).toContain('up to 5 MB');
    expect(q(f, '[data-test="hint"]')!.textContent).toContain('at most 30 photos');
    expect(q(f, '[data-test="count"]')!.textContent!.trim()).toBe('0/30');
    const camera = q<HTMLInputElement>(f, '[data-test="camera-input"]')!;
    expect(camera.getAttribute('accept')).toBe('image/*');
    expect(camera.getAttribute('capture')).toBe('environment');
    expect(q(f, '[data-test="empty"]')).not.toBeNull();
  });

  it('disables the upload at 30 photos', () => {
    const many = Array.from({ length: 30 }, (_, n) => photo(`p${n}`));
    const { f, http } = open('OWNER', 'o1', many);
    http.match((r) => r.url.endsWith('/content'));
    expect(q<HTMLButtonElement>(f, '[data-test="camera"]')!.disabled).toBe(true);
    expect(q<HTMLButtonElement>(f, '[data-test="choose"]')!.disabled).toBe(true);
    expect(q(f, '[data-test="full"]')).not.toBeNull();
  });

  it('hides Documents from a mechanic and shows its group to the front office', () => {
    const docs = photo('d1', { stage: 'DOCUMENTS' });
    const staff = open('FRONT_DESK', 'f1', [docs]);
    staff.http.match((r) => r.url.endsWith('/content'));
    expect(q(staff.f, '[data-stage="DOCUMENTS"]')).not.toBeNull();
    expect([...html(staff.f).querySelectorAll('#ph-stage option')].map((o) => o.getAttribute('value'))).toContain('DOCUMENTS');
    TestBed.resetTestingModule();
    const mech = open('MECHANIC', 'm1', []);
    expect([...html(mech.f).querySelectorAll('#ph-stage option')].map((o) => o.getAttribute('value'))).toEqual(['CHECK_IN', 'WORK', 'DELIVERY']);
  });

  it('keeps the large photo inside the dialog whatever its size', async () => {
    const { f, http } = open('FRONT_DESK', 'f1', [photo('p1')]);
    http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['x']));
    await vi.waitFor(() => {
      f.detectChanges();
      expect(html(f).querySelector('app-photo-thumb button')).not.toBeNull();
    });
    click(f, 'app-photo-thumb button');
    http.match((r) => r.url.endsWith('/content'));
    await vi.waitFor(() => {
      f.detectChanges();
      expect(html(f).querySelector('.photo-large')).not.toBeNull();
    });
    const css = [...document.querySelectorAll('style')].map((s) => s.textContent).join('');
    expect(css).toMatch(/\.photo-large[^{]*\{[^}]*max-width:\s*100%/);
    expect(css).toMatch(/\.photo-large[^{]*\{[^}]*object-fit:\s*contain/);
    expect(css).toMatch(/\.photo-large[^{]*\{[^}]*height:\s*auto/);
  });

  it('hints that vehicle documents go in Documents while the stage is Check-in', () => {
    const { f } = open('FRONT_DESK', 'f1', []);
    expect(q(f, '[data-test="documents-hint"]')).toBeNull();
    const sel = q<HTMLSelectElement>(f, '#ph-stage')!;
    sel.value = 'CHECK_IN';
    sel.dispatchEvent(new Event('change'));
    f.detectChanges();
    expect(q(f, '[data-test="documents-hint"]')!.textContent).toContain('go in Documents');
  });

  it('deletes only with a reason, sent in the body, and shows the field error from the server', async () => {
    const { f, http } = open('FRONT_DESK', 'f1', [photo('p1')]);
    http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['x']));
    await vi.waitFor(() => {
      f.detectChanges();
      expect(html(f).querySelector('app-photo-thumb button')).not.toBeNull();
    });
    click(f, 'app-photo-thumb button');
    http.match((r) => r.url.endsWith('/content'));
    click(f, '[data-test="delete"]');
    click(f, '[data-test="delete-confirm"]');
    http.expectNone((r) => r.method === 'DELETE');
    expect(q(f, '[data-test="delete-reason-error"]')!.textContent).toContain('Say why the photo is deleted.');
    const reason = q<HTMLTextAreaElement>(f, '#pe-reason')!;
    reason.value = '  wrong car ';
    reason.dispatchEvent(new Event('input'));
    click(f, '[data-test="delete-confirm"]');
    const req = http.expectOne((r) => r.method === 'DELETE');
    expect(req.request.url).toBe('/api/v1/work-orders/w1/photos/p1');
    expect(req.request.body).toEqual({ reason: 'wrong car' });
    req.flush({ code: 'reason-required', errors: { reason: 'required' } }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(q(f, '[data-test="delete-reason-error"]')).not.toBeNull();
    click(f, '[data-test="delete-confirm"]');
    http.expectOne((r) => r.method === 'DELETE').flush(null, { status: 204, statusText: 'No Content' });
    f.detectChanges();
    expect(q(f, '[data-test="count"]')!.textContent!.trim()).toBe('0/30');
  });

  it('offers no delete to the front desk once the order is delivered, but does to the owner', async () => {
    for (const [role, id, canDelete] of [['FRONT_DESK', 'f1', false], ['OWNER', 'o1', true]] as const) {
      TestBed.resetTestingModule();
      const { f, http } = open(role, id, [photo('p1')], order({ status: 'DELIVERED' }));
      http.expectOne('/api/v1/work-orders/w1/photos/p1/content').flush(new Blob(['x']));
      await vi.waitFor(() => {
        f.detectChanges();
        expect(html(f).querySelector('app-photo-thumb button')).not.toBeNull();
      });
      click(f, 'app-photo-thumb button');
      http.match((r) => r.url.endsWith('/content'));
      expect(q(f, '[data-test="delete"]') !== null).toBe(canDelete);
    }
  });

  it('gives a mechanic who is not assigned no upload', () => {
    const { f } = open('MECHANIC', 'm2', []);
    expect(q(f, '[data-test="upload"]')).toBeNull();
    expect(q(f, '[data-test="view-only"]')).not.toBeNull();
  });

  it('says so when the picture cannot be read (no decoder in the test browser)', async () => {
    const { f } = open('MECHANIC', 'm1', []);
    const input = q<HTMLInputElement>(f, '[data-test="file-input"]')!;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'a.heic', { type: 'image/heic' })], configurable: true });
    input.dispatchEvent(new Event('change'));
    await f.whenStable();
    f.detectChanges();
    expect(text(f)).toContain('cannot be read');
  });
});

describe('ChecklistPanel', () => {
  const item = (id: string, over: Partial<ChecklistItem> = {}): ChecklistItem => ({
    id, position: 1, label: `Item ${id}`, required: true, result: null, note: null, answeredByName: null, answeredAt: null, ...over,
  });
  const checklist = (kind: Checklist['kind'], items: ChecklistItem[]): Checklist => {
    const req = items.filter((i) => i.required);
    const answered = req.filter((i) => i.result).length;
    return { kind, startedAt: '2026-11-09T08:00:00Z', startedByName: 'Rita', requiredTotal: req.length, requiredAnswered: answered, complete: answered === req.length, items };
  };

  function open(role: AccountRole, userId: string, started: Checklist[], opts: { required?: boolean; status?: WorkOrderStatus; refused?: boolean } = {}) {
    const http = setup(role, userId);
    const f = TestBed.createComponent(ChecklistPanel);
    f.componentRef.setInput('workOrder', order({ status: opts.status ?? 'IN_PROGRESS' }));
    f.componentRef.setInput('gateRefused', opts.refused ?? false);
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/checklists').flush({ deliveryChecklistRequired: opts.required ?? false, checklists: started });
    f.detectChanges();
    return { http, f };
  }

  it('starts a checklist, then answers an item and shows the progress from the answer', () => {
    const { f, http } = open('MECHANIC', 'm1', []);
    click(f, '[data-test="start-CHECK_IN"]');
    http.expectOne('/api/v1/work-orders/w1/checklists/CHECK_IN').flush(checklist('CHECK_IN', [item('a'), item('b'), item('c', { required: false })]), { status: 201, statusText: 'Created' });
    f.detectChanges();
    expect(q(f, '[data-kind="CHECK_IN"] [data-test="progress"]')!.textContent!.trim()).toBe('0 of 2 required');
    click(f, '[data-item="a"] [data-result="OK"]');
    const req = http.expectOne('/api/v1/work-orders/w1/checklists/CHECK_IN/items/a');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ result: 'OK', note: null });
    req.flush(checklist('CHECK_IN', [item('a', { result: 'OK', answeredByName: 'Rui', answeredAt: '2026-11-09T08:10:00Z' }), item('b'), item('c', { required: false })]));
    f.detectChanges();
    expect(q(f, '[data-kind="CHECK_IN"] [data-test="progress"]')!.textContent!.trim()).toBe('1 of 2 required');
    expect(q(f, '[data-item="a"] [data-result="OK"]')!.getAttribute('aria-pressed')).toBe('true');
    expect(q(f, '[data-item="a"]')!.textContent).toContain('Rui');
  });

  it('sends the note typed before the answer with it, and a changed note again with the same result', () => {
    const { f, http } = open('FRONT_DESK', 'f1', [checklist('CHECK_IN', [item('a')])]);
    const note = q<HTMLInputElement>(f, '#note-a')!;
    note.value = 'scratch on the left door';
    note.dispatchEvent(new Event('input'));
    click(f, '[data-item="a"] [data-result="ATTENTION"]');
    const first = http.expectOne('/api/v1/work-orders/w1/checklists/CHECK_IN/items/a');
    expect(first.request.body).toEqual({ result: 'ATTENTION', note: 'scratch on the left door' });
    first.flush(checklist('CHECK_IN', [item('a', { result: 'ATTENTION', note: 'scratch on the left door' })]));
    f.detectChanges();
    const edit = q<HTMLInputElement>(f, '#note-a')!;
    edit.value = 'scratch on the right door';
    edit.dispatchEvent(new Event('input'));
    edit.dispatchEvent(new Event('blur'));
    const second = http.expectOne('/api/v1/work-orders/w1/checklists/CHECK_IN/items/a');
    expect(second.request.body).toEqual({ result: 'ATTENTION', note: 'scratch on the right door' });
    second.flush(checklist('CHECK_IN', [item('a', { result: 'ATTENTION', note: 'scratch on the right door' })]));
  });

  it('does not drop quick taps on other items while one is saving, and locks only the saving item', () => {
    const { f, http } = open('MECHANIC', 'm1', [checklist('CHECK_IN', [item('a'), item('b')])]);
    click(f, '[data-item="a"] [data-result="OK"]');
    expect(q<HTMLButtonElement>(f, '[data-item="a"] [data-result="ATTENTION"]')!.disabled).toBe(true);
    expect(q<HTMLButtonElement>(f, '[data-item="b"] [data-result="OK"]')!.disabled).toBe(false);
    click(f, '[data-item="b"] [data-result="N_A"]');
    const ra = http.expectOne('/api/v1/work-orders/w1/checklists/CHECK_IN/items/a');
    const rb = http.expectOne('/api/v1/work-orders/w1/checklists/CHECK_IN/items/b');
    // The answers come back in the opposite order, each without the other's result.
    rb.flush(checklist('CHECK_IN', [item('a'), item('b', { result: 'N_A' })]));
    ra.flush(checklist('CHECK_IN', [item('a', { result: 'OK' }), item('b')]));
    f.detectChanges();
    expect(q(f, '[data-item="a"] [data-result="OK"]')!.getAttribute('aria-pressed')).toBe('true');
    expect(q(f, '[data-item="b"] [data-result="N_A"]')!.getAttribute('aria-pressed')).toBe('true');
    expect(q(f, '[data-test="progress"]')!.textContent!.trim()).toBe('2 of 2 required');
  });

  it('lets a mechanic who is not assigned read, with no start and no answer buttons', () => {
    const { f, http } = open('MECHANIC', 'm2', [checklist('CHECK_IN', [item('a', { result: 'OK', note: 'fine' })])]);
    expect(q(f, '[data-test="start-DELIVERY"]')).toBeNull();
    expect(q<HTMLButtonElement>(f, '[data-item="a"] [data-result="OK"]')!.disabled).toBe(true);
    expect(q(f, '#note-a')).toBeNull();
    expect(text(f)).toContain('fine');
    expect(text(f)).toContain('Not started.');
    http.verify();
  });

  it('locks a delivered order', () => {
    const { f } = open('OWNER', 'o1', [checklist('DELIVERY', [item('a')])], { status: 'DELIVERED' });
    expect(q<HTMLButtonElement>(f, '[data-item="a"] [data-result="OK"]')!.disabled).toBe(true);
    expect(q(f, '[data-test="start-CHECK_IN"]')).toBeNull();
  });

  it('explains the delivery requirement, and a refused move to Delivered', () => {
    const a = open('OWNER', 'o1', [checklist('DELIVERY', [item('a')])], { required: true, status: 'READY' });
    expect(q(a.f, '[data-test="gate"]')!.textContent).toContain('requires the delivery checklist');
    a.f.componentRef.setInput('gateRefused', true);
    a.f.detectChanges();
    expect(q(a.f, '[data-test="gate"]')!.textContent).toContain('cannot be delivered yet');
    TestBed.resetTestingModule();
    const b = open('OWNER', 'o1', [checklist('DELIVERY', [item('a', { result: 'N_A' })])], { required: true, status: 'READY' });
    expect(q(b.f, '[data-test="gate"]')).toBeNull();
  });
});

describe('WorkOrderDetail and the delivery gate', () => {
  beforeEach(() => {
    HTMLElement.prototype.scrollIntoView = vi.fn();
  });

  it('shows the translated refusal, offers the delivery checklist and reads the checklists again', () => {
    const http = setup('OWNER', 'o1');
    const f = TestBed.createComponent(WorkOrderDetail);
    f.componentRef.setInput('id', 'w1');
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1').flush(order({ status: 'READY', allowedNext: ['DELIVERED'] }));
    f.detectChanges();
    http.expectOne('/api/v1/work-orders/w1/quotes').flush([]);
    http.expectOne('/api/v1/work-orders/w1/mechanic').flush({ mechanicId: 'm1', mechanicName: 'Rui', changes: [] });
    flushFloor(http);
    f.detectChanges();
    expect(q(f, '[data-test="open-checklist"]')).toBeNull();

    const move = [...html(f).querySelectorAll<HTMLButtonElement>('header .btn')].find((b) => b.textContent!.includes('Delivered'))!;
    move.click();
    http.expectOne('/api/v1/work-orders/w1/status').flush({ code: 'checklist-incomplete' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/work-orders/w1/checklists').flush({ deliveryChecklistRequired: true, checklists: [] });
    http.expectOne('/api/v1/work-orders/w1').flush(order({ status: 'READY', allowedNext: ['DELIVERED'] }));
    f.detectChanges();
    expect(text(f)).toContain('cannot be delivered yet: the required items');
    const open = q(f, '[data-test="open-checklist"]')!;
    open.click();
    expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalled();
  });
});

describe('error texts of the workshop floor', () => {
  const CODES = [
    'timer-not-running', 'time-tracking-closed', 'no-mechanic-assigned', 'photo-limit-reached', 'checklist-incomplete', 'time-entry-not-found',
    'photo-not-found', 'checklist-not-found', 'checklist-item-not-found', 'invalid-time-range', 'photo-too-large', 'photo-type-not-allowed',
    'photo-storage-full', 'time-entry-overlap', 'upload-too-large',
  ];

  it.each(['pt-PT', 'en', 'pt-BR'] as const)('has all 15 floor codes in %s', (lang) => {
    const texts = allTexts(lang);
    for (const code of CODES) expect(texts[`error.${code}`], `${lang} error.${code}`).toBeTruthy();
  });

  it('has every floor key in the three languages with the same placeholders', () => {
    const en = allTexts('en');
    const keys = Object.keys(en).filter((k) => k.startsWith('floor.') || k.startsWith('backoffice.checklists.'));
    const holders = (s: string) => (s.match(/\{\w+\}/g) ?? []).sort().join(',');
    for (const lang of ['pt-PT', 'pt-BR'] as const) {
      const t = allTexts(lang);
      for (const k of keys) {
        expect(t[k], `${lang} ${k}`).toBeTruthy();
        expect(holders(t[k]), `${lang} ${k}`).toBe(holders(en[k]));
      }
    }
  });
});
