import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Icon } from '../icon/icon';
import { IconButton } from './icon-button';

describe('IconButton', () => {
  let fixture: ComponentFixture<IconButton>;
  let host: HTMLElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(IconButton);
    fixture.componentRef.setInput('icon', 'trash');
    fixture.componentRef.setInput('label', 'Retirer');
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  const button = () => host.querySelector('button')!;

  it('shows its icon, names itself and keeps its words for hover', () => {
    expect(button().getAttribute('aria-label')).toBe('Retirer');
    expect(fixture.debugElement.query(By.directive(Icon)).componentInstance.name()).toBe('trash');
    const label = host.querySelector('.icon-button__label')!;
    expect(label.textContent).toBe('Retirer');
    expect(label.getAttribute('aria-hidden')).toBe('true');
    expect(button().classList).toContain('icon-button--ghost');
    expect(button().hasAttribute('aria-expanded')).toBe(false);
  });

  it('draws a danger variant, a badge and the state of the panel it opens', async () => {
    fixture.componentRef.setInput('variant', 'danger');
    fixture.componentRef.setInput('badge', 2);
    fixture.componentRef.setInput('expanded', false);
    fixture.componentRef.setInput('controls', 'panel');
    await fixture.whenStable();
    expect(button().classList).toContain('icon-button--danger');
    expect(host.querySelector('.icon-button__badge')?.textContent).toBe('2');
    expect(button().getAttribute('aria-label')).toBe('Retirer, 2 actif(s)');
    expect(button().getAttribute('aria-expanded')).toBe('false');
    expect(button().getAttribute('aria-controls')).toBe('panel');
  });

  it('is disabled with a spinner while loading', async () => {
    fixture.componentRef.setInput('loading', true);
    await fixture.whenStable();
    expect(button().disabled).toBe(true);
    expect(button().getAttribute('aria-busy')).toBe('true');
    expect(host.querySelector('dg-spinner')).not.toBeNull();
    expect(host.querySelector('dg-icon')).toBeNull();
  });
});
