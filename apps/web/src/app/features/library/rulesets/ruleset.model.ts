import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { IconName } from '../../../shared/ui/icon/icon-paths';

/**
 * The only thing the Library knows about a game: a ruleset declares its stable id (the one stored
 * in spell books), its full name, its sections, whether it has spells, and how to list and detail
 * the entries of a section. Adding a game means adding an implementation and registering it under
 * `RULESETS`.
 */

export type RulesetId = string; // ^[a-z0-9]+(-[a-z0-9]+)*$
export type SectionGroup = 'players' | 'master' | 'rules';

/**
 * A list column. The key `name` reads `EntrySummary.name`; any other key reads `fields[key]`.
 * `format` changes how a value is shown; sorting and filtering keep using the raw value.
 */
export interface ColumnDef {
  key: string;
  label: string;
  sortable: boolean;
  format?: (value: FieldValue) => string;
}

/**
 * A list of boxes over `fields[key]`, any number of them ticked: an entry matches when its value
 * equals (or, for a list, holds) one of the ticked options.
 */
export interface FilterDef {
  /** Not `q`, `sort`, `dir`, `page` or `size`: the list's URL state already uses those. */
  key: string;
  label: string;
  /** Absent: the values the entries have, in alphabetical order. */
  options?: { value: string; label: string }[];
}

/** Tabs over `fields[key]`: one per option, the first one shown by default. */
export interface TabsDef {
  key: string;
  label: string;
  options: { value: string; label: string }[];
}

export interface RulesetSection {
  id: string;
  label: string;
  group: SectionGroup;
  icon: IconName;
  columns: ColumnDef[];
  filters: FilterDef[];
  /**
   * The sortable column the list is sorted by until the reader picks one; by name when absent.
   * Entries that tie are always put in alphabetical order.
   */
  defaultSort?: string;
  /** Splits the list into tabs. Same key rules as a filter. */
  tabs?: TabsDef;
  /** The flavour line under the loader of this section's pages; a generic one when absent. */
  loadingMessage?: string;
}

export type FieldValue = string | number | boolean | string[];

export interface EntrySummary {
  index: string;
  name: string;
  fields: Record<string, FieldValue>;
}

/** A part of a table cell; with an `anchor`, a link to the block or entry of that anchor. */
export interface CellPart {
  text: string;
  anchor?: string;
}

/** Plain text, or parts shown one after the other, separated by commas. */
export type TableCell = string | CellPart[];

/**
 * What every block may have: a `title`; an `anchor`, the id that in-page links point at; and
 * `nested`, for a block that belongs to the block before it (its title is a sub-heading).
 */
interface BlockBase {
  title?: string;
  anchor?: string;
  nested?: boolean;
}

/**
 * A block of an entry page. Texts are light markdown: **bold**, *italic*, lists, ## headings and
 * pipe tables. Stats in `rows` take one line each, for values too long to share one (proficiencies).
 */
export type DetailBlock = BlockBase &
  (
    | { kind: 'text'; paragraphs: string[] }
    | { kind: 'stats'; layout?: 'grid' | 'rows'; items: { label: string; value: string }[] }
    | { kind: 'table'; columns: string[]; rows: TableCell[][] }
    | {
        kind: 'entries';
        items: {
          name: string;
          text: string;
          link?: { section: string; index: string };
          anchor?: string;
        }[];
      }
  );

export interface EntryDetail {
  index: string;
  name: string;
  subtitle?: string;
  tags: string[];
  blocks: DetailBlock[];
}

export interface Ruleset {
  readonly id: RulesetId;
  readonly label: string;
  readonly sections: readonly RulesetSection[];
  /** The section whose entries go in spell books; null: no spell books for this game. */
  readonly spellSectionId: string | null;
  list(sectionId: string): Observable<EntrySummary[]>;
  detail(sectionId: string, index: string): Observable<EntryDetail>;
}

export const RULESETS = new InjectionToken<Ruleset[]>('RULESETS');

/** A ruleset's `detail` fails with this when the source has no such entry (as opposed to being down). */
export class RulesetEntryNotFoundError extends Error {
  constructor(message = 'Entry not found') {
    super(message);
    this.name = 'RulesetEntryNotFoundError';
  }
}
