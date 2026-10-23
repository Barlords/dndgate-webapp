import { DetailBlock, EntryDetail } from '../../../ruleset.model';
import { ApiRef, names } from '../common';

export interface ApiMagicItem {
  index: string;
  name: string;
  equipment_category: ApiRef;
  rarity: { name: string };
  attunement: boolean;
  /** Who may attune, for the items limited to some creatures. */
  'limited-to'?: string;
  variants?: ApiRef[];
  desc: string[];
}

export function mapMagicItem(item: ApiMagicItem): EntryDetail {
  const category = item.equipment_category.name;
  const limit = item['limited-to'];
  const attunement = item.attunement ? `Required${limit ? ` (${limit})` : ''}` : 'Not required';
  const blocks: DetailBlock[] = [
    {
      kind: 'stats',
      items: [
        { label: 'Category', value: category },
        { label: 'Rarity', value: item.rarity.name },
        { label: 'Attunement', value: attunement },
      ],
    },
    { kind: 'text', paragraphs: item.desc },
  ];
  if (item.variants && item.variants.length > 0) {
    blocks.push({ kind: 'text', title: 'Variants', paragraphs: names(item.variants) });
  }
  return {
    index: item.index,
    name: item.name,
    subtitle: `${category}, ${item.rarity.name}`,
    tags: item.attunement ? ['Requires attunement'] : [],
    blocks,
  };
}
