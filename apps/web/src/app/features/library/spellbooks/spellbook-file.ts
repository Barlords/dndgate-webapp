import { SpellBookImportExportDto } from '../../../core/api/generated/models/spell-book-import-export-dto';

/** `Mon Grimoire d'Éclairs` becomes `mon-grimoire-d-eclairs.json`. */
export function spellBookFileName(name: string): string {
  const kebab = name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return `${kebab || 'spellbook'}.json`;
}

/** Saves an exported spell book on the reader's machine as a JSON file named after the book. */
export function downloadSpellBook(dto: SpellBookImportExportDto, name: string): void {
  const blob = new Blob([JSON.stringify(dto, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = spellBookFileName(name);
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
