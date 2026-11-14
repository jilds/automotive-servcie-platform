import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { I18nService } from '../core/i18n/i18n.service';
import { NotesPanel } from './notes-panel';

describe('NotesPanel', () => {
  let http: HttpTestingController;

  function open(kind: 'vehicle' | 'customer') {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18nService).use('en');
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(NotesPanel);
    fixture.componentRef.setInput('kind', kind);
    fixture.componentRef.setInput('subjectId', 'x1');
    fixture.detectChanges();
    return fixture;
  }

  const note = (id: string, body: string) => ({ id, body, authorId: 'u1', authorName: 'Ana', createdAt: '2026-11-11T10:00:00Z' });
  afterEach(() => http.verify());

  it('lists vehicle observations with their author and adds a new one on top', () => {
    const fixture = open('vehicle');
    http.expectOne('/api/v1/vehicles/x1/observations').flush([note('n1', 'Pneus gastos')]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Pneus gastos');
    expect(el.textContent).toContain('Ana');

    const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '  Ruído na suspensão ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    const req = http.expectOne('/api/v1/vehicles/x1/observations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Ruído na suspensão' });
    req.flush(note('n2', 'Ruído na suspensão'));
    fixture.detectChanges();
    const items = el.querySelectorAll('[data-test="notes"] li');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Ruído na suspensão');
  });

  it('uses the customer notes endpoint for a customer', () => {
    const fixture = open('customer');
    http.expectOne('/api/v1/customers/x1/notes').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nothing recorded yet.');
  });

  it('does not send an empty note', () => {
    const fixture = open('vehicle');
    http.expectOne('/api/v1/vehicles/x1/observations').flush([]);
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
  });
});
