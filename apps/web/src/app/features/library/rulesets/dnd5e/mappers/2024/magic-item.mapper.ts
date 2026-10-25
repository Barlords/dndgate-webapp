import { DetailBlock, EntryDetail } from '../../../ruleset.model';
import { ApiRef, EntryTexts, names, SOURCE_TEXTS } from '../common';

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

export function mapMagicItem(item: ApiMagicItem, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  const category = item.equipment_category.name;
  const attunement = item.attunement
    ? t.attunementRequired(item['limited-to'])
    : t.attunementNotRequired;
  const blocks: DetailBlock[] = [
    {
      kind: 'stats',
      items: [
        { label: t.category, value: category },
        { label: t.rarity, value: item.rarity.name },
        { label: t.attunement, value: attunement },
      ],
    },
    { kind: 'text', paragraphs: item.desc },
  ];
  if (item.variants && item.variants.length > 0) {
    blocks.push({ kind: 'text', title: t.variants, paragraphs: names(item.variants) });
  }
  return {
    index: item.index,
    name: item.name,
    subtitle: `${category}, ${item.rarity.name}`,
    tags: item.attunement ? [t.requiresAttunement] : [],
    blocks,
  };
}
