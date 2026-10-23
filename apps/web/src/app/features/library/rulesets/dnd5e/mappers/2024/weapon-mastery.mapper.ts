import { EntryDetail } from '../../../ruleset.model';
import { paragraphs } from '../common';

export interface ApiWeaponMastery {
  index: string;
  name: string;
  description: string;
}

export function mapWeaponMastery(mastery: ApiWeaponMastery): EntryDetail {
  return {
    index: mastery.index,
    name: mastery.name,
    subtitle: 'Weapon mastery',
    tags: [],
    blocks: [{ kind: 'text', paragraphs: paragraphs(mastery.description) }],
  };
}
