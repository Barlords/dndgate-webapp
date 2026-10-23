import { FormControl, FormGroup } from '@angular/forms';
import { ApiProblem } from './api-problem';
import { applyFieldErrors } from './form-errors';

const problem = (errors: ApiProblem['errors']): ApiProblem => ({ status: 400, errors });

describe('applyFieldErrors', () => {
  it('marks the named controls', () => {
    const form = new FormGroup({ email: new FormControl(''), name: new FormControl('') });
    const found = applyFieldErrors(form, problem([{ field: 'email', message: 'Taken' }]));
    expect(found).toBe(true);
    expect(form.controls.email.errors).toEqual({ server: 'Taken' });
    expect(form.controls.email.touched).toBe(true);
    expect(form.controls.name.errors).toBeNull();
  });

  it('returns false for unknown fields', () => {
    const form = new FormGroup({ email: new FormControl('') });
    expect(applyFieldErrors(form, problem([{ field: 'nope', message: 'x' }]))).toBe(false);
    expect(form.controls.email.errors).toBeNull();
  });
});
