import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { I18nService } from '../core/i18n/i18n.service';
import { BackofficeApi } from './backoffice-api.service';
import { WorkshopSettings } from './backoffice.models';
import { Settings } from './settings';
import { labourCostChange, RETENTION_MONTHS_DEFAULT, settingsForm, toFormValue, toSettings } from './settings-form';
import { Toasts } from './toasts';

const base: WorkshopSettings = {
  name: 'Oficina', nif: null, country: 'PT', timezone: 'Europe/Lisbon', defaultVatRate: 23, labourRateCents: 4500, quoteValidDays: 15,
  deliveryChecklistRequired: false, labourCostPerHourCents: 2500, retentionMonths: 12, version: 3,
};

describe('report settings in the form', () => {
  it('starts with no labour cost and the default retention window', () => {
    const form = settingsForm();
    expect(form.controls.labourCostEuros.value).toBeNull();
    expect(form.controls.retentionMonths.value).toBe(RETENTION_MONTHS_DEFAULT);
    expect(form.controls.labourCostEuros.valid).toBe(true);
  });

  it('accepts an empty labour cost and 0, and rejects negatives and more than 2 decimals', () => {
    const c = settingsForm().controls.labourCostEuros;
    for (const ok of [null, 0, 25, 12.5, 12.34, 10000]) {
      c.setValue(ok);
      expect(c.valid, `${ok}`).toBe(true);
    }
    for (const bad of [-1, 12.345, 10000.01, 10000.5]) {
      c.setValue(bad);
      expect(c.invalid, `${bad}`).toBe(true);
    }
  });

  it('keeps the retention window to whole months from 1 to 60', () => {
    const c = settingsForm().controls.retentionMonths;
    for (const ok of [1, 12, 60]) {
      c.setValue(ok);
      expect(c.valid, `${ok}`).toBe(true);
    }
    for (const bad of [null, 0, 61, 1.5, -3]) {
      c.setValue(bad);
      expect(c.invalid, `${bad}`).toBe(true);
    }
  });

  it('shows the stored cents as euros, null as an empty field, and an old response as the default window', () => {
    expect(toFormValue(base)).toMatchObject({ labourCostEuros: 25, retentionMonths: 12 });
    expect(toFormValue({ ...base, labourCostPerHourCents: null, retentionMonths: 24 })).toMatchObject({ labourCostEuros: null, retentionMonths: 24 });
    const old: WorkshopSettings = { ...base };
    delete old.labourCostPerHourCents;
    delete old.retentionMonths;
    expect(toFormValue(old)).toMatchObject({ labourCostEuros: null, retentionMonths: 12 });
  });
});

describe('report settings in the PUT body', () => {
  const filled = (euros: number | null, months = 12) => {
    const form = settingsForm();
    form.reset(toFormValue({ ...base, labourCostPerHourCents: null }));
    form.patchValue({ labourCostEuros: euros, retentionMonths: months });
    return form;
  };

  it('sends a typed labour cost as cents and never clearLabourCost with it', () => {
    const body = toSettings(filled(45.25, 24), 3, 2500);
    expect(body.labourCostPerHourCents).toBe(4525);
    expect(body.retentionMonths).toBe(24);
    expect('clearLabourCost' in body).toBe(false);
  });

  it('sends 0 as a real value', () => {
    const body = toSettings(filled(0), 3, 2500);
    expect(body.labourCostPerHourCents).toBe(0);
    expect('clearLabourCost' in body).toBe(false);
  });

  it('sends clearLabourCost (and no value) when the field was emptied and a value was stored', () => {
    const body = toSettings(filled(null), 3, 2500);
    expect(body.clearLabourCost).toBe(true);
    expect('labourCostPerHourCents' in body).toBe(false);
  });

  it('sends neither when the field is empty and nothing was stored', () => {
    const body = toSettings(filled(null), 3, null);
    expect('labourCostPerHourCents' in body).toBe(false);
    expect('clearLabourCost' in body).toBe(false);
    expect(toSettings(filled(null), 3)).not.toHaveProperty('clearLabourCost');
  });

  it('keeps the version and the other fields', () => {
    expect(toSettings(filled(10), 7, null)).toMatchObject({ name: 'Oficina', labourRateCents: 4500, quoteValidDays: 15, version: 7 });
  });

  it('never combines a value with the clear flag, whatever the input', () => {
    for (const euros of [null, 0, 1, 99.99]) {
      for (const saved of [null, 0, 2500]) {
        const change = labourCostChange(euros, saved);
        expect('labourCostPerHourCents' in change && 'clearLabourCost' in change).toBe(false);
      }
    }
  });
});

describe('Settings screen: labour cost and retention window', () => {
  function setup(saveSettings: (body: WorkshopSettings) => unknown = (b) => of({ ...b, labourCostPerHourCents: b.clearLabourCost ? null : b.labourCostPerHourCents, version: b.version + 1 }), loaded = base) {
    const saved: WorkshopSettings[] = [];
    TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        {
          provide: BackofficeApi,
          useValue: {
            settings: () => of(loaded),
            saveSettings: (body: WorkshopSettings) => {
              saved.push(body);
              return saveSettings(body);
            },
          },
        },
        { provide: Toasts, useValue: { show: () => undefined } },
      ],
    });
    TestBed.inject(I18nService).use('en');
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = (id: string) => el.querySelector(id) as HTMLInputElement;
    const type = (id: string, value: string) => {
      input(id).value = value;
      input(id).dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const save = () => {
      (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    return { fixture, el, input, type, save, saved };
  }

  it('shows the stored labour cost in euros and the retention window', () => {
    const { input } = setup();
    expect(input('#ws-labour-cost').value).toBe('25');
    expect(input('#ws-retention').value).toBe('12');
  });

  it('leaves the labour cost empty when none is stored, with the clear button disabled', () => {
    const { input, el } = setup(undefined, { ...base, labourCostPerHourCents: null });
    expect(input('#ws-labour-cost').value).toBe('');
    expect((el.querySelector('[data-test="clear-labour-cost"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('saves a new value in cents with the retention window and the workshop version', () => {
    const { type, save, saved } = setup();
    type('#ws-labour-cost', '30.5');
    type('#ws-retention', '24');
    save();
    expect(saved).toHaveLength(1);
    expect(saved[0]).toMatchObject({ labourCostPerHourCents: 3050, retentionMonths: 24, version: 3 });
    expect('clearLabourCost' in saved[0]).toBe(false);
  });

  it('the clear button sends clearLabourCost and no value, then the next save sends the new version without clearing again', () => {
    const { fixture, el, input, save, saved, type } = setup();
    (el.querySelector('[data-test="clear-labour-cost"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(input('#ws-labour-cost').value).toBe('');
    save();
    expect(saved[0].clearLabourCost).toBe(true);
    expect('labourCostPerHourCents' in saved[0]).toBe(false);
    expect(saved[0].version).toBe(3);

    // The answer says "not set" now: changing something else must not clear (or set) it again.
    type('#ws-retention', '18');
    save();
    expect(saved[1].version).toBe(4);
    expect('clearLabourCost' in saved[1]).toBe(false);
    expect('labourCostPerHourCents' in saved[1]).toBe(false);
    expect(saved[1].retentionMonths).toBe(18);
  });

  it('does not save an invalid retention window or labour cost, and says why', () => {
    const { type, save, saved, el } = setup();
    type('#ws-retention', '61');
    type('#ws-labour-cost', '-2');
    save();
    expect(saved).toHaveLength(0);
    const feedback = [...el.querySelectorAll('.invalid-feedback')].map((e) => e.textContent?.trim());
    expect(feedback).toContain('Enter a whole number of months from 1 to 60.');
    expect(feedback).toContain('Enter an amount between 0 and 10 000 with at most 2 decimals, or leave it empty.');
  });

  it('shows a server validation error on the labour cost field', () => {
    const { type, save, el } = setup(() => throwError(() => new HttpErrorResponse({ status: 400, error: { code: 'validation', errors: { labourCostPerHourCents: 'must be greater than or equal to 0' } } })));
    type('#ws-labour-cost', '5');
    save();
    expect((el.querySelector('#ws-labour-cost') as HTMLElement).classList).toContain('is-invalid');
  });
});

describe('Settings screen: time zone validation from the server', () => {
  it('shows the field error when the server rejects the time zone (400 validation, errors.timezone)', () => {
    TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        {
          provide: BackofficeApi,
          useValue: {
            settings: () => of(base),
            saveSettings: () => throwError(() => new HttpErrorResponse({ status: 400, error: { code: 'validation', errors: { timezone: 'not a region id' } } })),
          },
        },
        { provide: Toasts, useValue: { show: () => undefined } },
      ],
    });
    TestBed.inject(I18nService).use('en');
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const retention = el.querySelector('#ws-retention') as HTMLInputElement;
    retention.value = '13';
    retention.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('#ws-timezone')?.classList).toContain('is-invalid');
    expect([...el.querySelectorAll('.invalid-feedback')].map((e) => e.textContent?.trim())).toContain('Choose a time zone.');
  });
});

describe('labour cost hidden from the caller (null for a non-owner)', () => {
  it('shows an empty field and sends neither the value nor the clear flag when nothing was changed', () => {
    const form = settingsForm();
    const hidden: WorkshopSettings = { ...base, labourCostPerHourCents: null };
    form.reset(toFormValue(hidden));
    expect(form.controls.labourCostEuros.value).toBeNull();
    const body = toSettings(form, 3, hidden.labourCostPerHourCents ?? null);
    expect('labourCostPerHourCents' in body).toBe(false);
    expect('clearLabourCost' in body).toBe(false);
  });
});
