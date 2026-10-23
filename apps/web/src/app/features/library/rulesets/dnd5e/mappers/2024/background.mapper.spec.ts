import acolyte from '../../__fixtures__/2024/backgrounds-acolyte.json';
import { ApiBackground, mapBackground } from './background.mapper';

describe('background mapper (2024)', () => {
  it('maps ability scores, origin feat, proficiencies and equipment', () => {
    const detail = mapBackground(acolyte as ApiBackground);
    expect(detail.name).toBe('Acolyte');
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Ability scores', value: 'INT, WIS, CHA' },
        { label: 'Origin feat', value: 'Magic Initiate (Cleric)' },
        {
          label: 'Proficiencies',
          value: "Skill: Insight, Skill: Religion, Tool: Calligrapher's Supplies",
        },
      ],
    });
    const equipment = detail.blocks[1];
    expect(equipment.kind === 'text' && equipment.title).toBe('Starting equipment');
    expect(equipment.kind === 'text' && equipment.paragraphs[0]).toContain('Choose A or B');
  });

  it('writes a feat without a note as its name only', () => {
    const detail = mapBackground({
      ...acolyte,
      feat: { index: 'alert', name: 'Alert', url: '' },
    } as ApiBackground);
    const stats = detail.blocks[0];
    expect(stats.kind === 'stats' && stats.items[1].value).toBe('Alert');
  });
});
