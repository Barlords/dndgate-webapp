import { AdventureNoteDto } from '../../../core/api/generated/models/adventure-note-dto';
import { HistoricalNoteDto } from '../../../core/api/generated/models/historical-note-dto';

/** For tests only: the shape of a request seen by the testing backend. */
export interface Req {
  method: string;
  url: string;
  params: { get(name: string): string | null };
}

export function historicalNote(overrides: Partial<HistoricalNoteDto> = {}): HistoricalNoteDto {
  return { id: 'n1', content: 'Le pont est tombé.', date: '2026-03-14', ...overrides };
}

export function adventureNote(overrides: Partial<AdventureNoteDto> = {}): AdventureNoteDto {
  return {
    id: 'g1',
    content: 'Le roi ment.',
    date: '2026-03-14',
    noteVisibility: 'PUBLIC',
    ...overrides,
  };
}

export function notesPage<T>(content: T[], totalPages = 1, number = 0) {
  return { content, page: number, size: 20, totalElements: content.length, totalPages };
}

/** Today as the date input holds it, in local time. */
export function today(): string {
  const now = new Date();
  const two = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${two(now.getMonth() + 1)}-${two(now.getDate())}`;
}
