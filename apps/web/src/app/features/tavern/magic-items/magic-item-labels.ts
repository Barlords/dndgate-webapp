import { MagicItemDto } from '../../../core/api/generated/models/magic-item-dto';

export type MagicItemType = NonNullable<MagicItemDto['type']>;
export type MagicItemRarity = NonNullable<MagicItemDto['rarity']>;

/** In the order the form offers them. */
export const TYPE_LABEL: Record<MagicItemType, string> = {
  ARMOR: 'Armure',
  POTION: 'Potion',
  RING: 'Anneau',
  ROD: 'Sceptre',
  SCROLL: 'Parchemin',
  STAFF: 'Bâton',
  WAND: 'Baguette',
  WEAPON: 'Arme',
  WONDROUS_ITEM: 'Objet merveilleux',
};

/** From the most common to the rarest. */
export const RARITY_LABEL: Record<MagicItemRarity, string> = {
  COMMON: 'Commun',
  UNCOMMON: 'Peu commun',
  RARE: 'Rare',
  VERY_RARE: 'Très rare',
  LEGENDARY: 'Légendaire',
  ARTIFACT: 'Artefact',
};

export const TYPES = Object.keys(TYPE_LABEL) as MagicItemType[];
export const RARITIES = Object.keys(RARITY_LABEL) as MagicItemRarity[];

export function typeLabel(type: string | undefined): string {
  return TYPE_LABEL[type as MagicItemType] ?? '';
}

export function rarityLabel(rarity: string | undefined): string {
  return RARITY_LABEL[rarity as MagicItemRarity] ?? '';
}
