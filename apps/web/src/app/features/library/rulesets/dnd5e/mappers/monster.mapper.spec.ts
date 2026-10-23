import list from '../__fixtures__/2014/gql-monsters.json';
import dragon from '../__fixtures__/2014/monsters-adult-red-dragon.json';
import goblin from '../__fixtures__/2014/monsters-goblin.json';
import { formatChallenge, mapMonster, mapMonsterSummary } from './monster.mapper';

describe('monster mapper', () => {
  it('maps a list entry to type, size and challenge rating', () => {
    expect(list.data.monsters.map(mapMonsterSummary)[0]).toEqual({
      index: 'goblin',
      name: 'Goblin',
      fields: { type: 'humanoid', size: 'Small', cr: 0.25 },
    });
  });

  it('files every swarm under the swarm type', () => {
    const swarm = {
      index: 'swarm-of-rats',
      name: 'Swarm of Rats',
      size: 'Medium',
      type: 'swarm of Tiny beasts',
      challenge_rating: 0.25,
    };
    expect(mapMonsterSummary(swarm).fields['type']).toBe('swarm');
  });

  it('writes challenge ratings as fractions', () => {
    expect([0, 0.125, 0.25, 0.5, 10].map(formatChallenge)).toEqual([
      '0',
      '1/8',
      '1/4',
      '1/2',
      '10',
    ]);
  });

  it('maps a goblin: core stats, abilities with modifiers, challenge and actions', () => {
    const detail = mapMonster(goblin);
    expect(detail.subtitle).toBe('Small humanoid (goblinoid), neutral evil');
    const [core, abilities, other] = detail.blocks;
    expect(core).toEqual({
      kind: 'stats',
      items: [
        { label: 'Armor Class', value: '15 (leather armor, shield)' },
        { label: 'Hit Points', value: '7 (2d6)' },
        { label: 'Speed', value: '30 ft.' },
      ],
    });
    expect(abilities).toMatchObject({
      kind: 'stats',
      items: [
        { label: 'STR', value: '8 (-1)' },
        { label: 'DEX', value: '14 (+2)' },
        { label: 'CON', value: '10 (+0)' },
        { label: 'INT', value: '10 (+0)' },
        { label: 'WIS', value: '8 (-1)' },
        { label: 'CHA', value: '8 (-1)' },
      ],
    });
    expect(other).toEqual({
      kind: 'stats',
      items: [
        { label: 'Senses', value: 'Darkvision 60 ft., Passive perception 9' },
        { label: 'Languages', value: 'Common, Goblin' },
        { label: 'Challenge', value: '1/4 (50 XP)' },
      ],
    });
    const entries = detail.blocks.flatMap((b) => (b.kind === 'entries' ? [b] : []));
    expect(entries.map((b) => b.title)).toEqual(['Special abilities', 'Actions']);
    expect(entries[0].items[0].name).toBe('Nimble Escape');
  });

  it('maps a dragon: several speeds, immunities, natural armor, legendary actions', () => {
    const detail = mapMonster(dragon);
    expect(detail.blocks[0]).toMatchObject({
      items: [
        { label: 'Armor Class', value: '19 (natural armor)' },
        {},
        { label: 'Speed', value: '40 ft., climb 40 ft., fly 80 ft.' },
      ],
    });
    expect(JSON.stringify(detail.blocks[2])).toContain('Damage immunities');
    expect(detail.blocks.flatMap((b) => (b.kind === 'entries' ? [b.title] : []))).toEqual([
      'Special abilities',
      'Actions',
      'Legendary actions',
    ]);
  });

  it('shows a dash for a creature without a language', () => {
    const detail = mapMonster({ ...goblin, languages: '' });
    expect(JSON.stringify(detail.blocks[2])).toContain('{"label":"Languages","value":"—"}');
  });
});
