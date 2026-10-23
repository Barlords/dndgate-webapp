import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Button } from './button';

describe('Button', () => {
  let fixture: ComponentFixture<Button>;

  function native(): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Button);
  });

  it('is a primary, non-submitting button by default', async () => {
    await fixture.whenStable();

    expect(native().type).toBe('button');
    expect(native().classList).toContain('dg-button--primary');
    expect(native().disabled).toBe(false);
  });

  it('applies the variant and the type', async () => {
    fixture.componentRef.setInput('variant', 'danger');
    fixture.componentRef.setInput('type', 'submit');
    await fixture.whenStable();

    expect(native().classList).toContain('dg-button--danger');
    expect(native().type).toBe('submit');
  });

  it('is disabled and busy while loading', async () => {
    fixture.componentRef.setInput('loading', true);
    await fixture.whenStable();

    expect(native().disabled).toBe(true);
    expect(native().getAttribute('aria-busy')).toBe('true');
    expect(native().querySelector('dg-spinner')).not.toBeNull();
  });

  it('can be disabled without loading', async () => {
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();

    expect(native().disabled).toBe(true);
    expect(native().getAttribute('aria-busy')).toBeNull();
    expect(native().querySelector('dg-spinner')).toBeNull();
  });
});
