import { DetailBlock, EntryDetail, EntrySummary } from '../../ruleset.model';
import { ApiRef, capitalize, joinNames, signed } from './common';

export interface GqlMonster {
  index: string;
  name: string;
  type: string;
  size: string;
  challenge_rating: number;
}

interface ApiMonsterAction {
  name: string;
  desc: string;
}

export interface ApiMonster {
  index: string;
  name: string;
  size: string;
  type: string;
  subtype?: string | null;
  alignment: string;
  /** SRD 2014 names the kind of armor (`type`); SRD 2024 only lists the `armor` worn. */
  armor_class: { type?: string; value: number; armor?: ApiRef[] }[];
  hit_points: number;
  hit_dice: string;
  /** A speed is a text; SRD 2024 also has the flag `hover`. */
  speed: Record<string, string | boolean>;
  strength: number;
  dexterity: number;
  constitution: number;
  intelligence: number;
  wisdom: number;
  charisma: number;
  damage_vulnerabilities: string[];
  damage_resistances: string[];
  damage_immunities: string[];
  condition_immunities: ApiRef[];
  senses: Record<string, string | number>;
  languages: string;
  challenge_rating: number;
  xp: number;
  /** SRD 2024, for the monsters that have a lair. */
  xp_in_lair?: number;
  special_abilities?: ApiMonsterAction[];
  actions?: ApiMonsterAction[];
  /** SRD 2024 only. */
  bonus_actions?: ApiMonsterAction[];
  reactions?: ApiMonsterAction[];
  legendary_actions?: ApiMonsterAction[];
}

const FRACTIONS: Record<number, string> = { 0.125: '1/8', 0.25: '1/4', 0.5: '1/2' };

export function formatChallenge(rating: number): string {
  return FRACTIONS[rating] ?? String(rating);
}

/** "swarm of Tiny beasts" and the like are all filtered as "swarm". */
function baseType(type: string): string {
  return type.startsWith('swarm') ? 'swarm' : type;
}

export function mapMonsterSummary(monster: GqlMonster): EntrySummary {
  return {
    index: monster.index,
    name: monster.name,
    fields: {
      type: baseType(monster.type),
      size: monster.size,
      cr: monster.challenge_rating,
    },
  };
}

function modifier(score: number): string {
  return signed(Math.floor((score - 10) / 2));
}

function armorClass(monster: ApiMonster): string {
  return monster.armor_class
    .map((ac) => {
      const armors = (ac.armor ?? []).map((armor) => armor.name.toLowerCase());
      const source = armors.length > 0 ? armors.join(', ') : ac.type;
      if (source === undefined) {
        return String(ac.value);
      }
      return `${ac.value} (${source === 'natural' ? 'natural armor' : source})`;
    })
    .join(', ');
}

function entries(title: string, list: ApiMonsterAction[] | undefined): DetailBlock[] {
  return list && list.length > 0
    ? [{ kind: 'entries', title, items: list.map((a) => ({ name: a.name, text: a.desc })) }]
    : [];
}

function xp(monster: ApiMonster): string {
  return monster.xp_in_lair
    ? `${monster.xp} XP, or ${monster.xp_in_lair} XP in lair`
    : `${monster.xp} XP`;
}

export function mapMonster(monster: ApiMonster): EntryDetail {
  const abilities: [string, number][] = [
    ['STR', monster.strength],
    ['DEX', monster.dexterity],
    ['CON', monster.constitution],
    ['INT', monster.intelligence],
    ['WIS', monster.wisdom],
    ['CHA', monster.charisma],
  ];
  const speeds = Object.entries(monster.speed)
    .filter(([, value]) => value !== false)
    .map(([mode, value]) => {
      if (typeof value === 'boolean') {
        return mode;
      }
      return mode === 'walk' ? value : `${mode} ${value}`;
    });
  const senses = Object.entries(monster.senses).map(
    ([sense, value]) => `${capitalize(sense.replace('_', ' '))} ${value}`,
  );
  const defenses = [
    { label: 'Damage vulnerabilities', value: monster.damage_vulnerabilities.join(', ') },
    { label: 'Damage resistances', value: monster.damage_resistances.join(', ') },
    { label: 'Damage immunities', value: monster.damage_immunities.join(', ') },
    { label: 'Condition immunities', value: joinNames(monster.condition_immunities, '') },
  ].filter((item) => item.value !== '');
  const kind = monster.subtype ? `${monster.type} (${monster.subtype})` : monster.type;

  return {
    index: monster.index,
    name: monster.name,
    subtitle: `${monster.size} ${kind}, ${monster.alignment}`,
    tags: [],
    blocks: [
      {
        kind: 'stats',
        items: [
          { label: 'Armor Class', value: armorClass(monster) },
          { label: 'Hit Points', value: `${monster.hit_points} (${monster.hit_dice})` },
          { label: 'Speed', value: speeds.join(', ') },
        ],
      },
      {
        kind: 'stats',
        title: 'Abilities',
        items: abilities.map(([label, score]) => ({
          label,
          value: `${score} (${modifier(score)})`,
        })),
      },
      {
        kind: 'stats',
        items: [
          ...defenses,
          { label: 'Senses', value: senses.join(', ') },
          { label: 'Languages', value: monster.languages || '—' },
          {
            label: 'Challenge',
            value: `${formatChallenge(monster.challenge_rating)} (${xp(monster)})`,
          },
        ],
      },
      ...entries('Special abilities', monster.special_abilities),
      ...entries('Actions', monster.actions),
      ...entries('Bonus actions', monster.bonus_actions),
      ...entries('Reactions', monster.reactions),
      ...entries('Legendary actions', monster.legendary_actions),
    ],
  };
}
