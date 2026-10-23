import alert from '../../__fixtures__/2024/feats-alert.json';
import grappler from '../../__fixtures__/2024/feats-grappler.json';
import { ApiFeat, mapFeat } from './feat.mapper';

describe('feat mapper (2024)', () => {
  it('maps an origin feat: type subtitle, description paragraphs, no prerequisite', () => {
    const detail = mapFeat(alert as ApiFeat);
    expect(detail.subtitle).toBe('Origin feat');
    expect(detail.blocks).toHaveLength(1);
    const text = detail.blocks[0];
    expect(text.kind === 'text' && text.paragraphs).toHaveLength(3);
  });

  it('shows the prerequisites, including the ability score option', () => {
    const detail = mapFeat(grappler as ApiFeat);
    const stats = detail.blocks[0];
    expect(stats.kind === 'stats' && stats.items[0]).toEqual({
      label: 'Prerequisites',
      value: 'Level 4+; Strength or Dexterity 13+',
    });
  });

  it('shows a required feature, a fighting-style type and the repeatable note', () => {
    const detail = mapFeat({
      index: 'archery',
      name: 'Archery',
      type: 'fighting-style',
      description: 'Bonus.',
      prerequisites: { feature_named: 'Fighting Style' },
      repeatable: 'You can take this feat more than once.',
    });
    expect(detail.subtitle).toBe('Fighting style feat');
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [{ label: 'Prerequisites', value: 'Feature: Fighting Style' }],
    });
    expect(detail.blocks.at(-1)).toEqual({
      kind: 'text',
      title: 'Repeatable',
      paragraphs: ['You can take this feat more than once.'],
    });
  });
});
