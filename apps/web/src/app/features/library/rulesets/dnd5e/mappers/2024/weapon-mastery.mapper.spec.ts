import cleave from '../../__fixtures__/2024/weapon-mastery-cleave.json';
import { ApiWeaponMastery, mapWeaponMastery } from './weapon-mastery.mapper';

describe('weapon mastery mapper (2024)', () => {
  it('maps the description', () => {
    const detail = mapWeaponMastery(cleave as ApiWeaponMastery);
    expect(detail).toMatchObject({ index: 'cleave', name: 'Cleave', subtitle: 'Weapon mastery' });
    const text = detail.blocks[0];
    expect(text.kind === 'text' && text.paragraphs[0]).toContain('second creature');
  });
});
