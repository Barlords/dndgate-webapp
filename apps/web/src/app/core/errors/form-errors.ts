import { FormGroup } from '@angular/forms';
import { ApiProblem } from './api-problem';

/**
 * Puts each API field error on the control of the same name as `{ server: message }` and touches it
 * so the field shows it. Returns whether at least one control was found.
 */
export function applyFieldErrors(form: FormGroup, problem: ApiProblem): boolean {
  let found = false;
  for (const { field, message } of problem.errors) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ server: message });
      control.markAsTouched();
      found = true;
    }
  }
  return found;
}
