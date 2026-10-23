import combat from '../__fixtures__/2014/rules-combat.json';
import { mapRule } from './rule.mapper';

describe('rule mapper', () => {
  it('drops the leading title heading and links the children', () => {
    const detail = mapRule(combat);
    expect(detail.name).toBe('Combat');
    expect(detail.blocks).toHaveLength(1);
    const block = detail.blocks[0];
    expect(block).toMatchObject({ kind: 'entries', title: 'In this section' });
    const links = block.kind === 'entries' ? block.items : [];
    expect(links).toHaveLength(8);
    expect(links[0]).toEqual({
      name: 'The Order of Combat',
      text: '',
      link: { section: 'rules', index: 'the-order-of-combat' },
    });
  });

  it('keeps the markdown body, tables included, as one text', () => {
    const body = '# Cover\n\nHalf cover.\n\n| Cover | Bonus |\n|---|---|\n| Half | +2 |\n';
    const detail = mapRule({ index: 'cover', name: 'Cover', desc: body });
    expect(detail.blocks).toEqual([
      { kind: 'text', paragraphs: ['Half cover.\n\n| Cover | Bonus |\n|---|---|\n| Half | +2 |'] },
    ]);
  });
});
