import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormField } from './form-field';

@Component({
  imports: [FormField, ReactiveFormsModule],
  template: `
    <dg-form-field label="Pseudo" [control]="control">
      <input [formControl]="control" />
    </dg-form-field>
  `,
})
class Host {
  readonly control = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(5),
      Validators.pattern(/^[a-z]+$/),
      Validators.email,
    ],
  });
}

describe('FormField', () => {
  let fixture: ComponentFixture<Host>;
  let control: FormControl<string>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    control = fixture.componentInstance.control;
    await fixture.whenStable();
  });

  function messages(): string[] {
    const element = fixture.nativeElement as HTMLElement;
    return Array.from(element.querySelectorAll('.dg-form-field__error')).map(
      (message) => message.textContent?.trim() ?? '',
    );
  }

  function field(): HTMLInputElement {
    return (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;
  }

  it('labels the projected field', () => {
    const label = (fixture.nativeElement as HTMLElement).querySelector('label');
    expect(label?.textContent).toContain('Pseudo');
    expect(label?.contains(field())).toBe(true);
  });

  it('shows the server message', async () => {
    control.setErrors({ server: 'Ce pseudo est déjà pris.' });
    await fixture.whenStable();

    expect(messages()).toEqual(['Ce pseudo est déjà pris.']);
  });

  it('waits for the field to be touched before showing local errors', async () => {
    expect(control.invalid).toBe(true);
    expect(messages()).toEqual([]);
    expect(field().getAttribute('aria-invalid')).toBeNull();

    control.markAsTouched();
    await fixture.whenStable();

    expect(messages()).toEqual(['Ce champ est obligatoire.']);
  });

  it('explains each validator in French', async () => {
    control.markAsTouched();
    control.setValue('AB');
    await fixture.whenStable();
    expect(messages()).toEqual([
      'Saisis au moins 3 caractères.',
      'Le format attendu n’est pas respecté.',
      'Cette adresse e-mail n’est pas valide.',
    ]);

    control.setValue('abcdef');
    await fixture.whenStable();
    expect(messages()).toEqual([
      '5 caractères au maximum.',
      'Cette adresse e-mail n’est pas valide.',
    ]);
  });

  it('falls back to a generic message for an unknown error', async () => {
    control.setErrors({ notAnAdventurer: true });
    control.markAsTouched();
    await fixture.whenStable();

    expect(messages()).toEqual(['Cette valeur n’est pas valide.']);
  });

  it('links the messages to the field for assistive technologies', async () => {
    control.setErrors({ server: 'Ce pseudo est déjà pris.' });
    await fixture.whenStable();

    const describedBy = field().getAttribute('aria-describedby');
    expect(field().getAttribute('aria-invalid')).toBe('true');
    expect(describedBy).toBeTruthy();
    const description = (fixture.nativeElement as HTMLElement).querySelector(`#${describedBy}`);
    expect(description?.textContent).toContain('Ce pseudo est déjà pris.');

    control.setValue('abc');
    await fixture.whenStable();
    expect(field().getAttribute('aria-invalid')).toBeNull();
    expect(field().getAttribute('aria-describedby')).toBeNull();
  });
});

describe('FormField number bounds', () => {
  @Component({
    imports: [FormField, ReactiveFormsModule],
    template: `
      <dg-form-field label="Niveau" [control]="control">
        <input type="number" [formControl]="control" />
      </dg-form-field>
    `,
  })
  class NumberHost {
    readonly control = new FormControl<number | null>(null, [
      Validators.min(0),
      Validators.max(10),
    ]);
  }

  async function messageFor(value: number): Promise<string> {
    const fixture = TestBed.createComponent(NumberHost);
    fixture.componentInstance.control.setValue(value);
    fixture.componentInstance.control.markAsTouched();
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement)
      .querySelector('.dg-form-field__error')!
      .textContent!.trim();
  }

  it('states the bound that was crossed, whatever its value', async () => {
    expect(await messageFor(-1)).toBe('Saisis un nombre supérieur ou égal à 0.');
    expect(await messageFor(11)).toBe('Saisis un nombre inférieur ou égal à 10.');
  });
});
