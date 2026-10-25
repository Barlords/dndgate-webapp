import { EntryDetail, EntrySummary } from '../../ruleset.model';
import { ApiRef, EntryTexts, SOURCE_TEXTS } from './common';

/** The personality section merges two resources; the index prefix says which one an entry is. */
export const ALIGNMENT_PREFIX = 'alignment.';
export const LANGUAGE_PREFIX = 'language.';

export interface ApiAlignment {
  index: string;
  name: string;
  abbreviation: string;
  desc: string;
}

export interface ApiLanguage {
  index: string;
  name: string;
  type: string;
  typical_speakers: string[];
  script?: string;
}

export function mapAlignmentSummary(ref: ApiRef): EntrySummary {
  return { index: ALIGNMENT_PREFIX + ref.index, name: ref.name, fields: { kind: 'Alignment' } };
}

export function mapLanguageSummary(ref: ApiRef): EntrySummary {
  return { index: LANGUAGE_PREFIX + ref.index, name: ref.name, fields: { kind: 'Language' } };
}

export function mapAlignment(alignment: ApiAlignment, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  return {
    index: ALIGNMENT_PREFIX + alignment.index,
    name: alignment.name,
    subtitle: t.alignment,
    tags: [alignment.abbreviation],
    blocks: [{ kind: 'text', paragraphs: [alignment.desc] }],
  };
}

export function mapLanguage(language: ApiLanguage, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  return {
    index: LANGUAGE_PREFIX + language.index,
    name: language.name,
    subtitle: t.language,
    tags: [],
    blocks: [
      {
        kind: 'stats',
        items: [
          { label: t.type, value: language.type },
          { label: t.script, value: language.script || '—' },
          {
            label: t.typicalSpeakers,
            value: language.typical_speakers.join(', ') || '—',
          },
        ],
      },
    ],
  };
}
