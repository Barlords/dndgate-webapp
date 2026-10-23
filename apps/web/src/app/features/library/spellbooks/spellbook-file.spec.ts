import { downloadSpellBook, spellBookFileName } from './spellbook-file';

describe('spellbook-file', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('names the file after the book in kebab case', () => {
    expect(spellBookFileName('Mon Grimoire d’Éclairs !')).toBe('mon-grimoire-d-eclairs.json');
    expect(spellBookFileName('   ')).toBe('spellbook.json');
  });

  it('export downloads a json file named after the book', async () => {
    let blob: Blob | undefined;
    const revoke = vi.fn();
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: (value: Blob) => {
          blob = value;
          return 'blob:fake';
        },
        revokeObjectURL: revoke,
      }),
    );
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    const dto = { name: 'Arcane Pages', description: 'd', spellList: ['shield'], ruleset: 'x' };
    downloadSpellBook(dto, 'Arcane Pages');

    const clicked = click.mock.contexts[0] as HTMLAnchorElement;
    expect(clicked.download).toBe('arcane-pages.json');
    expect(clicked.href).toBe('blob:fake');
    expect(blob?.type).toBe('application/json');
    expect(JSON.parse(await blob!.text())).toEqual(dto);
    expect(revoke).toHaveBeenCalledWith('blob:fake');
    expect(document.querySelector('a[download]')).toBeNull();
  });
});
