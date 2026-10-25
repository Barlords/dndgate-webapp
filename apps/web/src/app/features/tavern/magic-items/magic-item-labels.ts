import { MagicItemDto } from '../../../core/api/generated/models/magic-item-dto';

export type MagicItemType = NonNullable<MagicItemDto['type']>;
export type MagicItemRarity = NonNullable<MagicItemDto['rarity']>;

/** In the order the form offers them; their names are in the dictionary (`tavern.magicItems`). */
export const TYPES: readonly MagicItemType[] = [
  'ARMOR',
  'POTION',
  'RING',
  'ROD',
  'SCROLL',
  'STAFF',
  'WAND',
  'WEAPON',
  'WONDROUS_ITEM',
];

/** From the most common to the rarest. */
export const RARITIES: readonly MagicItemRarity[] = [
  'COMMON',
  'UNCOMMON',
  'RARE',
  'VERY_RARE',
  'LEGENDARY',
  'ARTIFACT',
];
