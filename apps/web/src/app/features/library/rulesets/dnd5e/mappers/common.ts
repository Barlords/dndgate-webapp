import { Dictionary } from '../../../../../core/i18n/dictionary';
import { en } from '../../../../../core/i18n/en/index';
/** The texts an entry is written with, in the reader's language (labels, titles). */
export type EntryTexts = Dictionary['library']['entry'];

/** Without the reader's texts, an entry is written in English, the language of the API. */
export const SOURCE_TEXTS: EntryTexts = en.library.entry;

/** What the SRD API returns for a reference to another resource. */
export interface ApiRef {
  index: string;
  name: string;
}

export interface ApiList {
  count: number;
  results: ApiRef[];
}

export function names(refs: readonly ApiRef[] | undefined): string[] {
  return (refs ?? []).map((ref) => ref.name);
}

export function joinNames(refs: readonly ApiRef[] | undefined, fallback = 'None'): string {
  const list = names(refs);
  return list.length > 0 ? list.join(', ') : fallback;
}

/** Lower case, accents dropped, anything but letters and digits made a single dash. */
export function slug(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

export function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function signed(n: number): string {
  return n >= 0 ? `+${n}` : `${n}`;
}

/**
 * Paragraphs of a description: SRD 2014 gives a list, SRD 2024 a single string with line breaks
 * (and sometimes padding spaces), which is cut into trimmed, non-empty lines.
 */
export function paragraphs(text: string | readonly string[] | undefined): string[] {
  if (text === undefined) {
    return [];
  }
  return typeof text === 'string'
    ? text
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== '')
    : [...text];
}
