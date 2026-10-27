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

  const trigger = () => host.querySelector<HTMLButtonElement>('.language-select')!;
  // The list opens in an overlay, out of the host.
  const items = () => [...document.querySelectorAll<HTMLElement>('.language-select__item')];

  async function open(): Promise<void> {
    trigger().click();
    await fixture.whenStable();
  }

  it('shows the flag and the short name of the current language, named in full', () => {
    expect(trigger().textContent!.trim()).toBe('FR');
    expect(trigger().querySelector('svg.language-select__flag')).not.toBeNull();
    expect(trigger().querySelector('.language-select__chevron')).not.toBeNull();
    expect(trigger().getAttribute('aria-label')).toBe('Langue : Français');
    expect(trigger().getAttribute('aria-haspopup')).toBe('menu');
  });

  it('lists every language with its flag and its name in itself, the current one checked', async () => {
    await open();

    expect(document.querySelector('.language-select__menu')!.getAttribute('aria-label')).toBe(
      'Langue',
    );
    expect(
      items().map((item) => item.querySelector('.language-select__label')!.textContent),
    ).toEqual(['Français', 'English']);
    expect(items().map((item) => item.getAttribute('lang'))).toEqual(['fr', 'en']);
    expect(items().every((item) => item.querySelector('svg.language-select__flag'))).toBe(true);
    expect(items().map((item) => item.getAttribute('aria-checked'))).toEqual(['true', 'false']);
  });

  it('switches the interface language on a choice, and its own texts with it', async () => {
    await open();
    items()[1].click();
    await fixture.whenStable();

    expect(TestBed.inject(I18n).language()).toBe('en');
    expect(items()).toEqual([]);
    expect(trigger().textContent!.trim()).toBe('EN');
    expect(trigger().getAttribute('aria-label')).toBe('Language: English');
  });

  it('keeps the flag alone when compact', async () => {
    fixture.componentRef.setInput('compact', true);
    await fixture.whenStable();

    expect(host.classList).toContain('compact');
    expect(trigger().textContent!.trim()).toBe('');
    expect(trigger().querySelector('svg.language-select__flag')).not.toBeNull();
    expect(trigger().getAttribute('aria-label')).toBe('Langue : Français');
  });
});
