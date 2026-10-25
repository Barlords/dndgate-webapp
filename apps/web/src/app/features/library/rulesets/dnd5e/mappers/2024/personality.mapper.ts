import { EntryDetail } from '../../../ruleset.model';
import { EntryTexts, paragraphs, SOURCE_TEXTS } from '../common';
import { ALIGNMENT_PREFIX, LANGUAGE_PREFIX } from '../personality.mapper';

/** The summaries and prefixes are those of SRD 2014; only the detail shapes differ. */
export { mapAlignmentSummary, mapLanguageSummary } from '../personality.mapper';

export interface ApiAlignment {
  index: string;
  name: string;
  abbreviation: string;
  description: string;
}

export interface ApiLanguage {
  index: string;
  name: string;
  is_rare: boolean;
  note?: string;
}

export function mapAlignment(alignment: ApiAlignment, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  return {
    index: ALIGNMENT_PREFIX + alignment.index,
    name: alignment.name,
    subtitle: t.alignment,
    tags: [alignment.abbreviation],
    blocks: [{ kind: 'text', paragraphs: paragraphs(alignment.description) }],
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
          { label: t.rarity, value: language.is_rare ? t.rare : t.standard },
          { label: t.note, value: language.note || '—' },
        ],
      },
    ],
  };
}
