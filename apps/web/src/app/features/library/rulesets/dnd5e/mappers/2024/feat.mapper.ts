import { DetailBlock, EntryDetail } from '../../../ruleset.model';
import { capitalize, paragraphs } from '../common';

export interface ApiFeat {
  index: string;
  name: string;
  type: string;
  description: string;
  prerequisites?: { minimum_level?: number; feature_named?: string };
  prerequisite_options?: { desc: string };
  repeatable?: string;
}

function prerequisites(feat: ApiFeat): string {
  return [
    feat.prerequisites?.minimum_level !== undefined
      ? `Level ${feat.prerequisites.minimum_level}+`
      : '',
    feat.prerequisites?.feature_named ? `Feature: ${feat.prerequisites.feature_named}` : '',
    feat.prerequisite_options?.desc ?? '',
  ]
    .filter((part) => part !== '')
    .join('; ');
}

export function mapFeat(feat: ApiFeat): EntryDetail {
  const blocks: DetailBlock[] = [];
  const required = prerequisites(feat);
  if (required !== '') {
    blocks.push({ kind: 'stats', items: [{ label: 'Prerequisites', value: required }] });
  }
  blocks.push({ kind: 'text', paragraphs: paragraphs(feat.description) });
  if (feat.repeatable) {
    blocks.push({ kind: 'text', title: 'Repeatable', paragraphs: [feat.repeatable] });
  }
  return {
    index: feat.index,
    name: feat.name,
    subtitle: `${capitalize(feat.type.replaceAll('-', ' '))} feat`,
    tags: [],
    blocks,
  };
}
