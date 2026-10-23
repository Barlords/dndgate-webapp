import wizard from '../__fixtures__/2014/classes-wizard.json';
import levels from '../__fixtures__/2014/classes-wizard-levels.json';
import { mapClass } from './class.mapper';

describe('class mapper', () => {
  it('maps the wizard: hit die, saves, spellcasting ability, subclasses and equipment', () => {
    const detail = mapClass(wizard, levels);
    expect(detail.subtitle).toBeUndefined();
    expect(detail.blocks[0]).toMatchObject({
      kind: 'stats',
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

  it('builds the levels table with the spellcasting columns in use', () => {
    const table = mapClass(wizard, levels).blocks.find((b) => b.kind === 'table');
    expect(table).toEqual({
      kind: 'table',
      title: 'Class levels',
      columns: ['Level', 'Proficiency bonus', 'Features', 'Cantrips', '1st', '2nd'],
      rows: [
        ['1', '+2', 'Spellcasting: Wizard, Arcane Recovery', '3', '2', '0'],
        ['2', '+2', 'Arcane Tradition', '3', '3', '0'],
        ['3', '+2', '—', '3', '4', '2'],
      ],
    });
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
});
