import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { ChecklistTemplate } from '../workshop/floor.models';
import { ChecklistTemplateEditor } from './checklist-template-editor';
import { ChecklistTemplates } from './checklist-templates';
import { Toasts } from './toasts';
import { settingsForm, toFormValue, toSettings } from './settings-form';

const tpl = (over: Partial<ChecklistTemplate> = {}): ChecklistTemplate => ({
  kind: 'DELIVERY', custom: true, version: 3, items: [{ label: 'Keys', required: true }, { label: 'Clean', required: false }], ...over,
});

const html = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
const q = (f: ComponentFixture<unknown>, s: string) => html(f).querySelector(s) as HTMLElement | null;
const all = (f: ComponentFixture<unknown>, s: string) => [...html(f).querySelectorAll<HTMLElement>(s)];
const labels = (f: ComponentFixture<unknown>) => all(f, '[data-test="item"] input[type="text"]').map((i) => (i as HTMLInputElement).value);
const click = (f: ComponentFixture<unknown>, s: string) => {
  q(f, s)!.click();
  f.detectChanges();
};

function editor(t: ChecklistTemplate) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(I18nService).use('en');
  const http = TestBed.inject(HttpTestingController);
  const f = TestBed.createComponent(ChecklistTemplateEditor);
  f.componentRef.setInput('template', t);
  f.detectChanges();
  return { f, http };
}

describe('ChecklistTemplateEditor', () => {
  it('lists the items in order with their required switch', () => {
    const { f } = editor(tpl());
    expect(labels(f)).toEqual(['Keys', 'Clean']);
    expect(all(f, '.form-switch input').map((i) => (i as HTMLInputElement).checked)).toEqual([true, false]);
    expect(q(f, '[data-test="save"]')!.hasAttribute('disabled')).toBe(true);
  });

  it('says when the built-in default is shown', () => {
    expect(q(editor(tpl({ custom: false, version: 0 })).f, '[data-test="default"]')).not.toBeNull();
    TestBed.resetTestingModule();
    expect(q(editor(tpl()).f, '[data-test="default"]')).toBeNull();
  });

  it('adds, moves and removes items, then saves them in order with the stored version', () => {
    const { f, http } = editor(tpl());
    click(f, '[data-test="add"]');
    const fresh = all(f, '[data-test="item"] input[type="text"]')[2] as HTMLInputElement;
    fresh.value = '  Test drive  ';
    fresh.dispatchEvent(new Event('input'));
    f.detectChanges();
    all(f, '[data-test="up"]')[2].click();
    f.detectChanges();
    expect(labels(f)).toEqual(['Keys', '  Test drive  ', 'Clean']);
    all(f, '[data-test="remove"]')[0].click();
    f.detectChanges();
    expect(labels(f)).toEqual(['  Test drive  ', 'Clean']);
    expect(q(f, '[data-test="up"]')!.hasAttribute('disabled')).toBe(true);

    click(f, '[data-test="save"]');
    const req = http.expectOne('/api/v1/checklist-templates/DELIVERY');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ items: [{ label: 'Test drive', required: true }, { label: 'Clean', required: false }], version: 3 });
    const saved = tpl({ version: 4, items: req.request.body.items });
    req.flush(saved);
    f.detectChanges();
    expect(q(f, '[data-test="save"]')!.hasAttribute('disabled')).toBe(true);
  });

  it('changes the required flag of one item', () => {
    const { f, http } = editor(tpl());
    const sw = all(f, '.form-switch input')[1] as HTMLInputElement;
    sw.checked = true;
    sw.dispatchEvent(new Event('change'));
    f.detectChanges();
    click(f, '[data-test="save"]');
    expect(http.expectOne('/api/v1/checklist-templates/DELIVERY').request.body.items[1]).toEqual({ label: 'Clean', required: true });
  });

  it('does not save a blank item or an empty list, and shows why', () => {
    const { f, http } = editor(tpl());
    click(f, '[data-test="add"]');
    click(f, '[data-test="save"]');
    http.expectNone('/api/v1/checklist-templates/DELIVERY');
    expect(html(f).textContent).toContain('Write the item or remove it.');
    all(f, '[data-test="remove"]').forEach((b) => b.click());
    f.detectChanges();
    click(f, '[data-test="save"]');
    expect(q(f, '[data-test="list-problem"]')!.textContent).toContain('Add at least one item.');
    http.expectNone('/api/v1/checklist-templates/DELIVERY');
  });

  it('stops adding at 30 items', () => {
    const items = Array.from({ length: 30 }, (_, n) => ({ label: `Item ${n}`, required: true }));
    const { f } = editor(tpl({ items }));
    expect(q(f, '[data-test="add"]')!.hasAttribute('disabled')).toBe(true);
    expect(html(f).textContent).toContain('30 of 30 items');
  });

  it('shows the stored list after a stale version and saves with the new version next', () => {
    const { f, http } = editor(tpl());
    const first = all(f, '[data-test="item"] input[type="text"]')[0] as HTMLInputElement;
    first.value = 'Mine';
    first.dispatchEvent(new Event('input'));
    f.detectChanges();
    click(f, '[data-test="save"]');
    http.expectOne('/api/v1/checklist-templates/DELIVERY').flush({ code: 'stale-data' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/checklist-templates').flush([tpl({ kind: 'CHECK_IN' }), tpl({ version: 7, items: [{ label: 'Theirs', required: true }] })]);
    f.detectChanges();
    expect(labels(f)).toEqual(['Theirs']);
    expect(html(f).textContent).toContain('Someone else changed this record');
    const edit = all(f, '[data-test="item"] input[type="text"]')[0] as HTMLInputElement;
    edit.value = 'Theirs 2';
    edit.dispatchEvent(new Event('input'));
    f.detectChanges();
    click(f, '[data-test="save"]');
    expect(http.expectOne('/api/v1/checklist-templates/DELIVERY').request.body.version).toBe(7);
  });

  it('discards edits', () => {
    const { f } = editor(tpl());
    click(f, '[data-test="add"]');
    expect(labels(f)).toHaveLength(3);
    click(f, '[data-test="discard"]');
    expect(labels(f)).toEqual(['Keys', 'Clean']);
  });
});

describe('ChecklistTemplates page', () => {
  it('shows both kinds, check-in first, and replaces the saved one', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), Toasts] });
    TestBed.inject(I18nService).use('en');
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(ChecklistTemplates);
    f.detectChanges();
    http.expectOne('/api/v1/checklist-templates').flush([tpl({ kind: 'CHECK_IN', version: 0, custom: false }), tpl()]);
    f.detectChanges();
    expect(all(f, 'h2').map((h) => h.textContent!.trim())).toEqual(['Check-in checklist', 'Delivery checklist']);
  });
});

describe('delivery checklist setting', () => {
  const stored = { name: 'Oficina', nif: null, country: 'PT', timezone: 'Europe/Lisbon', defaultVatRate: 23, labourRateCents: 4500, quoteValidDays: 15, version: 1 };

  it('reads the flag (false when the server omits it) and sends it', () => {
    const form = settingsForm();
    form.reset(toFormValue({ ...stored, deliveryChecklistRequired: true }));
    expect(toSettings(form, 1).deliveryChecklistRequired).toBe(true);
    form.reset(toFormValue(stored));
    expect(form.controls.deliveryChecklistRequired.value).toBe(false);
    expect(toSettings(form, 1).deliveryChecklistRequired).toBe(false);
  });
});
