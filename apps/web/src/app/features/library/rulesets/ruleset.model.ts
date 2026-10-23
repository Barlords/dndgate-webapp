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

/** A list column. The key `name` reads `EntrySummary.name`; any other key reads `fields[key]`. */
export interface ColumnDef {
  key: string;
  label: string;
  sortable: boolean;
}

/** A select over `fields[key]`; an entry matches when its value equals (or, for a list, holds) the option. */
export interface FilterDef {
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
}

export type FieldValue = string | number | boolean | string[];

export interface EntrySummary {
  index: string;
  name: string;
  fields: Record<string, FieldValue>;
}

export type DetailBlock =
  // Light markdown: **bold**, *italic*, lists, ## headings and pipe tables.
  | { kind: 'text'; title?: string; paragraphs: string[] }
  | { kind: 'stats'; title?: string; items: { label: string; value: string }[] }
  | { kind: 'table'; title?: string; columns: string[]; rows: string[][] }
  | {
      kind: 'entries';
      title?: string;
      items: { name: string; text: string; link?: { section: string; index: string } }[];
    };

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
