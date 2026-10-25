import { EntryDetail } from '../../../ruleset.model';
import { EntryTexts, paragraphs, SOURCE_TEXTS } from '../common';

export interface ApiWeaponMastery {
  index: string;
  name: string;
  description: string;
}

export function mapWeaponMastery(
  mastery: ApiWeaponMastery,
  t: EntryTexts = SOURCE_TEXTS,
): EntryDetail {
  return {
    index: mastery.index,
    name: mastery.name,
    subtitle: t.weaponMastery,
    tags: [],
    blocks: [{ kind: 'text', paragraphs: paragraphs(mastery.description) }],
  };
}
