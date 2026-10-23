import barbarian from '../../__fixtures__/2024/classes-barbarian.json';
import barbarianLevels from '../../__fixtures__/2024/classes-barbarian-levels.json';
import wizard from '../../__fixtures__/2024/classes-wizard.json';
import wizardLevels from '../../__fixtures__/2024/classes-wizard-levels.json';
import airElemental from '../../__fixtures__/2024/monsters-air-elemental.json';
import goblin from '../../__fixtures__/2024/monsters-goblin-warrior.json';
import lich from '../../__fixtures__/2024/monsters-lich.json';
import acidSplash from '../../__fixtures__/2024/spells-acid-splash.json';
import fireball from '../../__fixtures__/2024/spells-fireball.json';
import { ApiClass, ApiClassLevel, mapClass } from '../class.mapper';
import { ApiMonster, mapMonster } from '../monster.mapper';
import { ApiSpell, mapSpell } from '../spell.mapper';

/** The 2014 mappers also read the (slightly different) shapes of the 2024 SRD. */
describe('mappers on SRD 2024 data', () => {
  describe('spell', () => {
    it('maps a 2024 spell description', () => {
      const detail = mapSpell(fireball as ApiSpell);
      expect(detail.subtitle).toBe('3rd-level evocation');
      expect(detail.tags).toEqual(['Sorcerer', 'Wizard']);
      const stats = detail.blocks[0];
      expect(stats.kind === 'stats' && stats.items[0]).toEqual({
        label: 'Casting time',
        value: 'Action',
      });
      const texts = detail.blocks.flatMap((b) => (b.kind === 'text' ? [b] : []));
      expect(texts.map((b) => b.title)).toEqual(['Description', 'At higher levels', 'Materials']);
      // The single string is split on line breaks, without empty or padded paragraphs.
      expect(texts[0].paragraphs).toHaveLength(2);
      expect(texts[0].paragraphs[0]).toContain('8d6 Fire damage');
      expect(texts[0].paragraphs[1]).toBe(
        "Flammable objects in the area that aren't being worn or carried start burning.",
      );
      expect(texts[1].paragraphs).toEqual([
        'The damage increases by 1d6 for each spell slot level above 3.',
      ]);
    });

    it('leaves out an empty 2024 higher-level text and calls level 0 a cantrip', () => {
      const detail = mapSpell({ ...acidSplash, higher_level: '' } as ApiSpell);
      expect(detail.subtitle).toBe('Evocation cantrip');
      expect(detail.blocks.some((b) => b.kind === 'text' && b.title === 'At higher levels')).toBe(
        false,
      );
    });
  });

  describe('monster', () => {
    const stat = (monster: unknown, label: string): string | undefined => {
      for (const block of mapMonster(monster as ApiMonster).blocks) {
        const item = block.kind === 'stats' && block.items.find((i) => i.label === label);
        if (item) {
          return item.value;
        }
      }
      return undefined;
    };

    it('writes the armor class of a 2024 monster, which has no armor type', () => {
      expect(stat(goblin, 'Armor Class')).toBe('15 (leather armor, shield)');
      expect(stat(airElemental, 'Armor Class')).toBe('15');
    });

    it('writes hover as a word and not as a speed', () => {
      expect(stat(airElemental, 'Speed')).toBe('10 ft., fly 90 ft., hover');
    });

    it('shows the lair XP and the bonus actions', () => {
      expect(stat(goblin, 'Challenge')).toBe('1/4 (50 XP)');
      expect(stat(lich, 'Challenge')).toMatch(/^21 \(33000 XP, or \d+ XP in lair\)$/);
      const titles = mapMonster(goblin as ApiMonster).blocks.map((b) => b.title);
      expect(titles).toContain('Bonus actions');
      expect(titles).not.toContain('Reactions');
      expect(titles).not.toContain('Legendary actions');
      expect(mapMonster(lich as ApiMonster).blocks.map((b) => b.title)).toEqual(
        expect.arrayContaining(['Reactions', 'Legendary actions']),
      );
    });
  });

  describe('class', () => {
    it('maps a 2024 class that has no starting equipment list, with its primary ability', () => {
      const detail = mapClass(barbarian as ApiClass, barbarianLevels as ApiClassLevel[]);
      const stats = detail.blocks[0];
      const items = stats.kind === 'stats' ? stats.items : [];
      expect(items.map((i) => i.label)).toEqual(
        expect.arrayContaining(['Hit die', 'Primary ability']),
      );
      expect(items.find((i) => i.label === 'Primary ability')?.value).toBe('Strength');
      const table = detail.blocks.find((b) => b.kind === 'table');
      expect(table?.kind === 'table' && table.columns).toEqual([
        'Level',
        'Proficiency bonus',
        'Features',
      ]);
    });

    it('adds the prepared spells column to the levels table of a caster', () => {
      const detail = mapClass(wizard as ApiClass, wizardLevels as ApiClassLevel[]);
      const table = detail.blocks.find((b) => b.kind === 'table');
      expect(table?.kind === 'table' && table.columns.slice(0, 5)).toEqual([
        'Level',
        'Proficiency bonus',
        'Features',
        'Cantrips',
        'Prepared spells',
      ]);
      expect(table?.kind === 'table' && table.rows[0].slice(3, 6)).toEqual(['3', '4', '2']);
    });
  });
});
