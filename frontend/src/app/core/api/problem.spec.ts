import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { applyProblem, fieldMessage, problemCode, problemOf, shownOnFields } from './problem';

const httpError = (status: number, error: unknown) => new HttpErrorResponse({ status, error });

describe('problem helpers', () => {
  const form = () =>
    new FormGroup({
      plate: new FormControl('AA-11-BB', { nonNullable: true }),
      mileage: new FormControl<number | null>(5),
      address: new FormGroup({ postalCode: new FormControl('1000', { nonNullable: true }) }),
    });

  it('reads the problem detail and its code', () => {
    const err = httpError(409, { code: 'duplicate-plate', detail: 'x' });
    expect(problemOf(err)).toEqual({ code: 'duplicate-plate', detail: 'x', status: 409 });
    expect(problemCode(err)).toBe('duplicate-plate');
    expect(problemOf(new Error('boom'))).toBeNull();
    expect(problemCode(httpError(0, null))).toBeNull();
  });

  it('renames API fields whose form control has another name', () => {
    const f = form();
    const err = httpError(400, { code: 'validation', errors: { mileageKm: 'must not be negative' } });
    expect(applyProblem(f, err, {}, { mileageKm: 'mileage' })).toBe(1);
    expect(f.controls.mileage.hasError('server')).toBe(true);
  });

  it('marks the fields named by a validation problem, including nested ones', () => {
    const f = form();
    const err = httpError(400, { code: 'validation', errors: { plate: 'must not be blank', 'address.postalCode': 'bad', unknown: 'x' } });
    expect(applyProblem(f, err)).toBe(2);
    expect(f.controls.plate.hasError('server')).toBe(true);
    expect(f.get('address.postalCode')?.touched).toBe(true);
    expect(fieldMessage(f.controls.plate)).toBe('error.field');
    expect(shownOnFields(err, 2)).toBe(false);
  });

  it('puts a conflict code on the field it belongs to and clears it once the field is edited', () => {
    const f = form();
    const err = httpError(409, { code: 'duplicate-plate' });
    expect(applyProblem(f, err, { 'duplicate-plate': 'plate' })).toBe(1);
    expect(fieldMessage(f.controls.plate)).toBe('error.duplicate-plate');
    expect(shownOnFields(err, 1)).toBe(true);
    f.controls.plate.setValue('BB-22-CC');
    expect(fieldMessage(f.controls.plate)).toBeNull();
  });

  it('leaves the form alone for codes without a field', () => {
    const f = form();
    expect(applyProblem(f, httpError(404, { code: 'vehicle-not-found' }), { 'duplicate-plate': 'plate' })).toBe(0);
    expect(f.valid).toBe(true);
  });

  it('maps client validators to message keys only after the control was touched', () => {
    const control = new FormControl('', Validators.required);
    expect(fieldMessage(control, { required: 'common.required' })).toBeNull();
    control.markAsTouched();
    expect(fieldMessage(control, { required: 'common.required' })).toBe('common.required');
  });
});
