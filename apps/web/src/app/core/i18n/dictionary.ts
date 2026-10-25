import { en } from './en';
import { fr } from './fr';

/**
 * The dictionaries of the interface, one per language. French is the reference: its shape is the
 * `Dictionary` type, which every other language must follow, so that a missing or misspelt
 * entry is a compilation error. A text that takes values is a function (`(n: number) => string`).
 */
export type Dictionary = typeof fr;

export type Language = 'fr' | 'en';

/** The languages offered, each named in itself. */
export const LANGUAGES: readonly { id: Language; label: string }[] = [
  { id: 'fr', label: 'Français' },
  { id: 'en', label: 'English' },
];

export const DICTIONARIES: Record<Language, Dictionary> = { fr, en };

/**
 * A text of the dictionary, for what is written once and shown later (a menu entry, a column, a
 * crumb): it is read in the language of the moment it is shown.
 */
export type Text = (t: Dictionary) => string;
