import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { PHOTO_PREPARER } from './image-resize';

import { WorkOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { Photo } from './floor.models';
import { PhotoPanel } from './photo-panel';

const prepared: { result: unknown } = { result: null };

const order = { id: 'w1', assignedMechanicId: 'm1', status: 'IN_PROGRESS' } as unknown as WorkOrder;

function open() {
  localStorage.setItem('asp.session', JSON.stringify({ token: 't', expiresAt: Date.now() + 60_000, user: { id: 'm1', tenantId: 't1', name: 'U', email: 'u@x.pt', role: 'MECHANIC' } }));
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: PHOTO_PREPARER, useValue: () => Promise.resolve(prepared.result) }] });
  TestBed.inject(I18nService).use('en');
  const http = TestBed.inject(HttpTestingController);
  const f = TestBed.createComponent(PhotoPanel);
  f.componentRef.setInput('workOrder', order);
  f.detectChanges();
  http.expectOne('/api/v1/work-orders/w1/photos').flush([]);
  f.detectChanges();
  return { f, http };
}

function pick(f: ReturnType<typeof open>['f']) {
  const root = f.nativeElement as HTMLElement;
  (root.querySelector('#ph-stage') as HTMLSelectElement).value = 'DELIVERY';
  root.querySelector('#ph-stage')!.dispatchEvent(new Event('change'));
  const caption = root.querySelector('#ph-caption') as HTMLInputElement;
  caption.value = '  Rear bumper ';
  caption.dispatchEvent(new Event('input'));
  const input = root.querySelector('[data-test="file-input"]') as HTMLInputElement;
  Object.defineProperty(input, 'files', { value: [new File(['raw'], 'IMG 1.heic', { type: 'image/heic' })], configurable: true });
  input.dispatchEvent(new Event('change'));
}

describe('PhotoPanel upload', () => {
  beforeEach(() => {
    prepared.result = { ok: true, blob: new Blob(['jpeg-bytes']), name: 'IMG_1.jpg' };
  });

  it('posts a FormData with file, stage and the trimmed caption', async () => {
    const { f, http } = open();
    pick(f);
    const req = await vi.waitFor(() => http.expectOne('/api/v1/work-orders/w1/photos'));
    const body = req.request.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('stage')).toBe('DELIVERY');
    expect(body.get('caption')).toBe('Rear bumper');
    expect((body.get('file') as File).name).toBe('IMG_1.jpg');
    const saved: Photo = { id: 'p1', workOrderId: 'w1', stage: 'DELIVERY', caption: 'Rear bumper', contentType: 'image/jpeg', sizeBytes: 10, uploadedByName: 'U', uploadedById: 'm1', createdAt: '2026-11-09T08:00:00Z', version: 0 };
    req.flush(saved, { status: 201, statusText: 'Created' });
    await f.whenStable();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('[data-test="count"]')!.textContent!.trim()).toBe('1/30');
  });

  it('shows the translated server error and reads the photos again', async () => {
    const { f, http } = open();
    pick(f);
    const req = await vi.waitFor(() => http.expectOne('/api/v1/work-orders/w1/photos'));
    req.flush({ code: 'photo-limit-reached' }, { status: 409, statusText: 'Conflict' });
    const again = await vi.waitFor(() => http.expectOne('/api/v1/work-orders/w1/photos'));
    again.flush([]);
    await f.whenStable();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).toContain('already has 30 photos');
  });

  it('does not upload a picture that is still over 5 MB after the resize', async () => {
    prepared.result = { ok: false, reason: 'too-large' };
    const { f, http } = open();
    pick(f);
    await f.whenStable();
    f.detectChanges();
    http.expectNone('/api/v1/work-orders/w1/photos');
    expect((f.nativeElement as HTMLElement).textContent).toContain('over 5 MB');
  });
});
