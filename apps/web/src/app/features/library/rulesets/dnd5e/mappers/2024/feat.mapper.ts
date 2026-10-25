import { DetailBlock, EntryDetail } from '../../../ruleset.model';
import { EntryTexts, paragraphs, SOURCE_TEXTS } from '../common';

export interface ApiFeat {
  index: string;
  name: string;
  type: string;
  description: string;
  prerequisites?: { minimum_level?: number; feature_named?: string };
  prerequisite_options?: { desc: string };
  repeatable?: string;
}

function prerequisites(feat: ApiFeat, t: EntryTexts): string {
  return [
    feat.prerequisites?.minimum_level !== undefined
      ? t.minimumLevel(feat.prerequisites.minimum_level)
      : '',
    feat.prerequisites?.feature_named ? t.featureNamed(feat.prerequisites.feature_named) : '',
    feat.prerequisite_options?.desc ?? '',
  ]
    .filter((part) => part !== '')
    .join('; ');
}

export function mapFeat(feat: ApiFeat, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  const blocks: DetailBlock[] = [];
  const required = prerequisites(feat, t);
  if (required !== '') {
    blocks.push({ kind: 'stats', items: [{ label: t.prerequisites, value: required }] });
  }
  blocks.push({ kind: 'text', paragraphs: paragraphs(feat.description) });
  if (feat.repeatable) {
    blocks.push({ kind: 'text', title: t.repeatable, paragraphs: [feat.repeatable] });
  }
  return {
    index: feat.index,
    name: feat.name,
    subtitle: t.featOf(feat.type.replaceAll('-', ' ')),
    tags: [],
    blocks,
  };
}
