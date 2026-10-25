import { TestBed } from '@angular/core/testing';
import { en } from './en';
import { fr } from './fr';
import { I18n } from './i18n.service';

describe('I18n', () => {
  function browserLanguage(language: string) {
    Object.defineProperty(navigator, 'language', { value: language, configurable: true });
  }

  function create(): I18n {
    const i18n = TestBed.inject(I18n);
    TestBed.tick();
    return i18n;
  }

  afterEach(() => {
    localStorage.clear();
    browserLanguage('fr-FR');
    document.documentElement.lang = '';
  });

  it('speaks French to a French browser', () => {
    const i18n = create();
    expect(i18n.language()).toBe('fr');
    expect(i18n.t()).toBe(fr);
  });

  it('speaks English to an English browser, whatever its region', () => {
    browserLanguage('en-US');
    expect(create().language()).toBe('en');
  });

  it('falls back to French for a language it does not know', () => {
    browserLanguage('de-DE');
    expect(create().language()).toBe('fr');
  });

  it('prefers the choice remembered in this browser to the browser language', () => {
    localStorage.setItem('dg.language', 'en');
    expect(create().language()).toBe('en');
  });

  it('ignores a remembered value that is not one of its languages', () => {
    localStorage.setItem('dg.language', 'tlh');
    expect(create().language()).toBe('fr');
  });

  it('switches the dictionary, remembers the choice and sets the page language', () => {
    const i18n = create();
    expect(document.documentElement.lang).toBe('fr');

    i18n.use('en');
    TestBed.tick();

    expect(i18n.t()).toBe(en);
    expect(localStorage.getItem('dg.language')).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('has the same entries in every language', () => {
    const shape = (value: unknown): unknown =>
      typeof value === 'object' && value !== null && !Array.isArray(value)
        ? Object.fromEntries(
            Object.entries(value)
              .sort(([a], [b]) => a.localeCompare(b))
              .map(([key, entry]) => [key, shape(entry)]),
          )
        : typeof value;
    expect(shape(en)).toEqual(shape(fr));
  });
});
