import { ComponentFixture, TestBed } from '@angular/core/testing';
import { I18n } from '../../../core/i18n/i18n.service';
import { LanguageSelect } from './language-select';

describe('LanguageSelect', () => {
  let fixture: ComponentFixture<LanguageSelect>;
  let host: HTMLElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(LanguageSelect);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => localStorage.clear());

  const option = (label: string) =>
    host.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!;
  const pressed = () =>
    [...host.querySelectorAll('button[aria-pressed="true"]')].map((b) => b.textContent!.trim());

  it('offers every language, named in itself, with the current one pressed', () => {
    expect(host.querySelector('[role="group"]')!.getAttribute('aria-label')).toBe('Langue');
    expect(option('Français').getAttribute('lang')).toBe('fr');
    expect(option('English').getAttribute('lang')).toBe('en');
    expect(pressed()).toEqual(['FR']);
  });

  it('switches the interface language on a click, and its own texts with it', async () => {
    option('English').click();
    await fixture.whenStable();

    expect(TestBed.inject(I18n).language()).toBe('en');
    expect(pressed()).toEqual(['EN']);
    expect(host.querySelector('[role="group"]')!.getAttribute('aria-label')).toBe('Language');
  });

  it('stacks the languages when asked to', async () => {
    fixture.componentRef.setInput('vertical', true);
    await fixture.whenStable();
    expect(host.classList).toContain('vertical');
  });
});
