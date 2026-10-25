import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { SafeStorage } from '../storage/safe-storage';
import { Dictionary, DICTIONARIES, Language, LANGUAGES } from './dictionary';

const STORAGE_KEY = 'dg.language';

/** The reader's choice if they made one, otherwise the browser's language, French by default. */
function initialLanguage(stored: string | null): Language {
  if (LANGUAGES.some((language) => language.id === stored)) {
    return stored as Language;
  }
  const browser = typeof navigator === 'undefined' ? '' : (navigator.language ?? '');
  return browser.toLowerCase().startsWith('en') ? 'en' : 'fr';
}

/**
 * The language of the interface and its dictionary. `t()` is the dictionary of the current
 * language: read in a template or a `computed`, what it gives follows a change of language.
 * The choice is remembered in this browser; the page's `lang` attribute follows it.
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  private readonly storage = inject(SafeStorage);
  private readonly document = inject(DOCUMENT);

  private readonly current = signal<Language>(initialLanguage(this.storage.get(STORAGE_KEY)));
  readonly language = this.current.asReadonly();
  readonly t = computed<Dictionary>(() => DICTIONARIES[this.current()]);

  constructor() {
    effect(() => {
      this.document.documentElement.lang = this.current();
    });
  }

  use(language: Language): void {
    this.current.set(language);
    this.storage.set(STORAGE_KEY, language);
  }
}
