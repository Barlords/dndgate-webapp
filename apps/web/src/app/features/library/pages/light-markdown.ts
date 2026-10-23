/**
 * The light markdown of the rules texts, parsed into tokens that templates render with
 * interpolation only (never as HTML): paragraphs, `##` / `###` headings, `-` / `*` lists, pipe
 * tables, and inline **bold** and *italic*. Anything else stays literal text.
 */

export interface Span {
  text: string;
  bold: boolean;
  italic: boolean;
}

export type MarkdownBlock =
  | { kind: 'paragraph'; spans: Span[] }
  | { kind: 'heading'; level: 3 | 4; spans: Span[] }
  | { kind: 'list'; items: Span[][] }
  | { kind: 'table'; header: Span[][] | null; rows: Span[][][] };

const HEADING = /^(#{1,6})\s+(.*)$/;
const LIST_ITEM = /^[-*]\s+(.*)$/;
const TABLE_SEPARATOR = /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?$/;

/** Parses inline emphasis; an asterisk without its closing match stays as it is. */
export function parseInline(text: string): Span[] {
  const spans: Span[] = [];
  let bold = false;
  let italic = false;
  let buffer = '';
  const flush = () => {
    if (buffer !== '') {
      spans.push({ text: buffer, bold, italic });
      buffer = '';
    }
  };

  let i = 0;
  while (i < text.length) {
    if (text.startsWith('**', i)) {
      if (bold || text.indexOf('**', i + 2) !== -1) {
        flush();
        bold = !bold;
      } else {
        buffer += '**';
      }
      i += 2;
    } else if (text[i] === '*' && (italic || opensItalic(text, i))) {
      flush();
      italic = !italic;
      i += 1;
    } else {
      buffer += text[i];
      i += 1;
    }
  }
  flush();
  return spans;
}

/** A `*` opens italic when a word follows it and a later `*` closes it after a word. */
function opensItalic(text: string, at: number): boolean {
  const next = text[at + 1];
  if (next === undefined || /\s/.test(next)) {
    return false;
  }
  for (let i = at + 2; i < text.length; i++) {
    if (text[i] === '*' && !/\s/.test(text[i - 1])) {
      return true;
    }
  }
  return false;
}

function tableCells(line: string): Span[][] {
  return line
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => parseInline(cell.trim()));
}

function parseTable(lines: string[]): MarkdownBlock {
  const hasHeader = lines.length > 1 && TABLE_SEPARATOR.test(lines[1]);
  return {
    kind: 'table',
    header: hasHeader ? tableCells(lines[0]) : null,
    rows: (hasHeader ? lines.slice(2) : lines).map(tableCells),
  };
}

/** Parses one text (which may hold several lines and blank-line separated paragraphs). */
export function parseMarkdown(source: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  const lines = source.split(/\r?\n/).map((line) => line.trim());
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const heading = HEADING.exec(line);
    if (line === '') {
      i += 1;
    } else if (heading) {
      blocks.push({
        kind: 'heading',
        level: heading[1].length <= 2 ? 3 : 4,
        spans: parseInline(heading[2]),
      });
      i += 1;
    } else if (LIST_ITEM.test(line)) {
      const items: Span[][] = [];
      for (; i < lines.length && LIST_ITEM.test(lines[i]); i++) {
        items.push(parseInline(LIST_ITEM.exec(lines[i])![1]));
      }
      blocks.push({ kind: 'list', items });
    } else if (line.startsWith('|')) {
      const rows: string[] = [];
      for (; i < lines.length && lines[i].startsWith('|'); i++) {
        rows.push(lines[i]);
      }
      blocks.push(parseTable(rows));
    } else {
      const text: string[] = [];
      for (; i < lines.length && isPlain(lines[i]); i++) {
        text.push(lines[i]);
      }
      blocks.push({ kind: 'paragraph', spans: parseInline(text.join(' ')) });
    }
  }
  return blocks;
}

function isPlain(line: string): boolean {
  return line !== '' && !HEADING.test(line) && !LIST_ITEM.test(line) && !line.startsWith('|');
}
