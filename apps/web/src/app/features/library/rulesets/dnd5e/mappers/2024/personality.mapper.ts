import { EntryDetail } from '../../../ruleset.model';
import { paragraphs } from '../common';
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

export function mapAlignment(alignment: ApiAlignment): EntryDetail {
  return {
    index: ALIGNMENT_PREFIX + alignment.index,
    name: alignment.name,
    subtitle: 'Alignment',
    tags: [alignment.abbreviation],
    blocks: [{ kind: 'text', paragraphs: paragraphs(alignment.description) }],
  };
}

export function mapLanguage(language: ApiLanguage): EntryDetail {
  return {
    index: LANGUAGE_PREFIX + language.index,
    name: language.name,
    subtitle: 'Language',
    tags: [],
    blocks: [
      {
        kind: 'stats',
        items: [
          { label: 'Rarity', value: language.is_rare ? 'Rare' : 'Standard' },
          { label: 'Note', value: language.note || '—' },
        ],
      },
    ],
  };
}
