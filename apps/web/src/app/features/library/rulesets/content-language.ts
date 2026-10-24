import { InjectionToken } from '@angular/core';

/**
 * The language the reader wants the rules in (a BCP 47 tag such as `fr-FR`): the browser's. A
 * ruleset asks its source for that language when the source has it, and says so when it has not.
 */
export const CONTENT_LANGUAGE = new InjectionToken<string>('CONTENT_LANGUAGE', {
  providedIn: 'root',
  factory: () => (typeof navigator === 'undefined' ? 'en' : navigator.language) || 'en',
});

/** English is the language the sources are written in: nothing to translate for its readers. */
export function wantsTranslation(language: string): boolean {
  return !language.toLowerCase().startsWith('en');
}
