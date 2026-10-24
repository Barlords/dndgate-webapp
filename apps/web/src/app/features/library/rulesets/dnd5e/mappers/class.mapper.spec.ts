import wizard from '../__fixtures__/2014/classes-wizard.json';
import levels from '../__fixtures__/2014/classes-wizard-levels.json';
import features2014 from '../__fixtures__/2014/gql-classes-wizard-features.json';
import wizard2024 from '../__fixtures__/2024/classes-wizard.json';
import features2024 from '../__fixtures__/2024/gql-classes-wizard-features.json';
import { DetailBlock } from '../../ruleset.model';
import { GqlFeature, mapClass } from './class.mapper';

function entries(blocks: DetailBlock[], title: string): { name: string; text: string }[] {
  const block = blocks.find((b) => b.title === title);
  return block?.kind === 'entries' ? block.items : [];
}

function feature(name: string, level: number, text: string, subclass?: string): GqlFeature {
  return {
    index: `${name}-${level}`,
    name,
    level,
    desc: text === '' ? [] : [text],
    subclass: subclass ? { index: subclass } : null,
  };
}

describe('class mapper', () => {
  it('maps the wizard: hit die, saves, spellcasting ability, subclasses and equipment', () => {
    const detail = mapClass(wizard, levels);
    expect(detail.subtitle).toBeUndefined();
    expect(detail.blocks[0]).toMatchObject({
      kind: 'stats',
      layout: 'rows',
      items: expect.arrayContaining([
        { label: 'Hit die', value: 'd6' },
        { label: 'Saving throws', value: 'INT, WIS' },
        { label: 'Spellcasting ability', value: 'INT' },
        { label: 'Subclasses', value: 'Evocation' },
      ]),
    });
    const equipment = detail.blocks.find(
      (b) => b.kind === 'text' && b.title === 'Starting equipment',
    );
    expect(equipment).toMatchObject({ paragraphs: expect.arrayContaining(['Spellbook']) });
  });

  /** The value of one line of the stats of a class. */
  function stat(cls: Parameters<typeof mapClass>[0], label: string): string | undefined {
    const stats = mapClass(cls, levels).blocks[0];
    return stats.kind === 'stats' ? stats.items.find((item) => item.label === label)?.value : '';
  }

  it('gives the saving throws a line of their own and keeps them out of the proficiencies', () => {
    expect(stat(wizard, 'Saving throws')).toBe('INT, WIS');
    expect(stat(wizard, 'Proficiencies')).toBe(
      'Daggers, Darts, Slings, Quarterstaffs, Crossbows, light',
    );
    expect(stat(wizard2024, 'Saving throws')).toBe('INT, WIS');
    expect(stat(wizard2024, 'Proficiencies')).toBe('Simple Weapons');
  });

  it('reads the saving throws from the proficiencies when the class does not list them', () => {
    expect(stat({ ...wizard, saving_throws: [] }, 'Saving throws')).toBe('INT, WIS');
  });

  it('recognises the saving throws by their identifier, whatever the language of their names', () => {
    const french = {
      ...wizard,
      proficiencies: [
        { index: 'daggers', name: 'Dagues' },
        { index: 'saving-throw-int', name: 'Jet de sauvegarde : INT' },
        { index: 'saving-throw-wis', name: 'Jet de sauvegarde : SAG' },
      ],
      saving_throws: [
        { index: 'int', name: 'INT' },
        { index: 'wis', name: 'SAG' },
      ],
    };
    expect(stat(french, 'Proficiencies')).toBe('Dagues');
    expect(stat(french, 'Saving throws')).toBe('INT, SAG');
    // Without the list of the class, the abilities are read from the identifiers.
    expect(stat({ ...french, saving_throws: [] }, 'Saving throws')).toBe('INT, WIS');
  });

  it('says so when a class has no proficiency left once the saving throws are apart', () => {
    const onlySaves = wizard.proficiencies.filter((p) => p.name.startsWith('Saving Throw'));
    expect(stat({ ...wizard, proficiencies: onlySaves }, 'Proficiencies')).toBe('None');
  });

  it('builds the levels table with the spellcasting columns in use', () => {
    const table = mapClass(wizard, levels).blocks.find((b) => b.kind === 'table');
    expect(table).toEqual({
      kind: 'table',
      title: 'Class levels',
      columns: ['Level', 'Proficiency bonus', 'Features', 'Cantrips', '1st', '2nd'],
      rows: [
        ['1', '+2', [{ text: 'Spellcasting: Wizard' }, { text: 'Arcane Recovery' }], '3', '2', '0'],
        ['2', '+2', [{ text: 'Arcane Tradition' }], '3', '3', '0'],
        ['3', '+2', '—', '3', '4', '2'],
      ],
    });
  });

  it('links each feature of the levels table to its entry, a subclass one to the subclasses', () => {
    const blocks = mapClass(wizard, levels, features2014.data).blocks;
    const table = blocks.find((b) => b.kind === 'table');
    expect(table?.kind === 'table' && table.rows.map((row) => row[2])).toEqual([
      [
        { text: 'Spellcasting: Wizard', anchor: 'feature-spellcasting-wizard' },
        { text: 'Arcane Recovery', anchor: 'feature-arcane-recovery' },
      ],
      // "Arcane Tradition" is how SRD 2014 names the wizard's subclasses.
      [{ text: 'Arcane Tradition', anchor: 'subclasses' }],
      '—',
    ]);
    // Every anchor of the table leads somewhere on the page.
    const anchors = blocks.flatMap((b) => [
      ...(b.anchor ? [b.anchor] : []),
      ...(b.kind === 'entries' ? b.items.flatMap((item) => item.anchor ?? []) : []),
    ]);
    expect(anchors).toContain('feature-spellcasting-wizard');
    expect(anchors).toContain('feature-arcane-recovery');
    expect(anchors).toContain('subclasses');
  });

  it('links a 2024 « <class> subclass » feature to the subclasses, a repeat to its first entry', () => {
    const blocks = mapClass(
      wizard,
      [
        {
          level: 3,
          prof_bonus: 2,
          features: [{ index: 'wizard-subclass', name: 'Wizard Subclass' }],
        },
        {
          level: 11,
          prof_bonus: 4,
          features: [{ index: 'extra-attack-2', name: 'Extra Attack (2)' }],
        },
      ],
      {
        features: [
          { ...feature('Extra Attack', 5, 'Attack twice.'), index: 'extra-attack-1' },
          { ...feature('Extra Attack (2)', 11, 'Attack twice.'), index: 'extra-attack-2' },
        ],
        class: { subclasses: [{ index: 'evoker', name: 'Evoker', description: 'Booms.' }] },
      },
    ).blocks;
    const table = blocks.find((b) => b.kind === 'table');
    expect(table?.kind === 'table' && table.rows.map((row) => row[2])).toEqual([
      [{ text: 'Wizard Subclass', anchor: 'subclasses' }],
      [{ text: 'Extra Attack (2)', anchor: 'feature-extra-attack-1' }],
    ]);
  });

  it('leads nowhere from a subclass feature when the class has no subclass', () => {
    const blocks = mapClass(
      wizard,
      [
        {
          level: 3,
          prof_bonus: 2,
          features: [{ index: 'wizard-subclass', name: 'Wizard Subclass' }],
        },
      ],
      { features: [], class: { subclasses: [] } },
    ).blocks;
    const table = blocks.find((b) => b.kind === 'table');
    expect(table?.kind === 'table' && table.rows[0][2]).toEqual([
      { text: 'Wizard Subclass', anchor: undefined },
    ]);
    expect(blocks.some((b) => b.anchor === 'subclasses')).toBe(false);
  });

  it('leaves out the spellcasting parts of a martial class and the table without levels', () => {
    const fighter = { ...wizard, spellcasting: undefined, subclasses: [] };
    const detail = mapClass(fighter, [{ level: 1, prof_bonus: 2, features: [] }]);
    expect(JSON.stringify(detail.blocks[0])).not.toContain('Spellcasting ability');
    expect(detail.blocks.find((b) => b.kind === 'table')).toMatchObject({
      columns: ['Level', 'Proficiency bonus', 'Features'],
    });
    expect(mapClass(fighter, []).blocks.some((b) => b.kind === 'table')).toBe(false);
  });

  it('lists the class features by level, one entry for a feature gained again', () => {
    const blocks = mapClass(wizard, levels, features2014.data).blocks;
    expect(entries(blocks, 'Class features').map((item) => item.name)).toEqual([
      'Arcane Recovery (level 1)',
      'Spellcasting: Wizard (level 1)',
      'Arcane Tradition (levels 2, 6, 10, 14)',
      'Ability Score Improvement (levels 4, 8, 12, 16, 19)',
      'Spell Mastery (level 18)',
      'Signature Spell (level 20)',
    ]);
    expect(entries(blocks, 'Class features')[0].text).toContain('regain some of your magical');
  });

  it('follows the class with each subclass, its description then its own features', () => {
    const blocks = mapClass(wizard, levels, features2014.data).blocks;
    const titles = blocks.map((b) => b.title);
    expect(titles.slice(titles.indexOf('Class levels'))).toEqual([
      'Class levels',
      'Class features',
      'Subclasses',
      'Arcane Tradition: Evocation',
      'Evocation features',
    ]);
    // Under the « Subclasses » heading, each subclass is a sub-section.
    expect(blocks.find((b) => b.title === 'Subclasses')).toMatchObject({
      anchor: 'subclasses',
      paragraphs: [],
    });
    expect(blocks.filter((b) => b.nested).map((b) => b.title)).toEqual([
      'Arcane Tradition: Evocation',
      'Evocation features',
    ]);
    expect(blocks.find((b) => b.title === 'Arcane Tradition: Evocation')).toMatchObject({
      kind: 'text',
      paragraphs: [expect.stringContaining('powerful elemental effects')],
    });
    expect(entries(blocks, 'Evocation features').map((item) => item.name)).toEqual([
      'Evocation Savant (level 2)',
      'Sculpt Spells (level 2)',
      'Potent Cantrip (level 6)',
      'Empowered Evocation (level 10)',
      'Overchannel (level 14)',
    ]);
  });

  it('reads the 2024 shapes: level object, one-string description and subclass summary', () => {
    const blocks = mapClass(wizard2024, [], features2024.data).blocks;
    expect(entries(blocks, 'Class features')[0]).toEqual({
      name: 'Arcane Recovery (level 1)',
      text: expect.stringContaining('\n\nThe spell slots'),
      anchor: 'feature-wizard-arcane-recovery',
    });
    expect(blocks.find((b) => b.title === 'Subclass: Evoker')).toMatchObject({
      kind: 'text',
      paragraphs: ['*Create Explosive Elemental Effects*', expect.stringContaining('Evokers')],
    });
    expect(entries(blocks, 'Evoker features')[0].name).toBe('Evocation Savant (level 3)');
  });

  it('drops the count of a repeated feature and never merges features without text', () => {
    const blocks = mapClass(wizard, [], {
      features: [
        feature('Extra Attack (2)', 11, 'Attack twice.'),
        feature('Extra Attack', 5, 'Attack twice.'),
        feature('Extra Attack (3)', 20, 'Attack twice.'),
        feature('Action Surge (two uses)', 17, 'Act again.'),
        feature('Action Surge (one use)', 2, 'Act again.'),
        feature('Mystery', 3, ''),
        feature('Enigma', 3, ''),
        feature('Champion trick', 3, 'Attack twice.', 'champion'),
      ],
      class: { subclasses: [{ index: 'champion', name: 'Champion', desc: [] }] },
    }).blocks;
    expect(entries(blocks, 'Class features').map((item) => item.name)).toEqual([
      'Action Surge (levels 2, 17)',
      'Enigma (level 3)',
      'Mystery (level 3)',
      'Extra Attack (levels 5, 11, 20)',
    ]);
    expect(entries(blocks, 'Champion features').map((item) => item.name)).toEqual([
      'Champion trick (level 3)',
    ]);
  });

  it('adds no feature block without features nor subclasses', () => {
    const empty = mapClass(wizard, levels, { features: [], class: null });
    expect(empty.blocks).toEqual(mapClass(wizard, levels).blocks);
    const noFeatures = mapClass(wizard, levels, {
      features: [],
      class: { subclasses: [{ index: 'lore', name: 'Lore', desc: ['Bards.'] }] },
    });
    expect(noFeatures.blocks.map((b) => b.title)).toContain('Subclass: Lore');
    expect(noFeatures.blocks.map((b) => b.title)).not.toContain('Lore features');
  });
});
