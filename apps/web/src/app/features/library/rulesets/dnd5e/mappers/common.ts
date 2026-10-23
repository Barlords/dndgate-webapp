/** What the SRD API returns for a reference to another resource. */
export interface ApiRef {
  index: string;
  name: string;
}

export interface ApiList {
  count: number;
  results: ApiRef[];
}

export function names(refs: readonly ApiRef[] | undefined): string[] {
  return (refs ?? []).map((ref) => ref.name);
}

export function joinNames(refs: readonly ApiRef[] | undefined, fallback = 'None'): string {
  const list = names(refs);
  return list.length > 0 ? list.join(', ') : fallback;
}

export function ordinal(n: number): string {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) {
    return `${n}th`;
  }
  return `${n}${({ 1: 'st', 2: 'nd', 3: 'rd' } as Record<number, string>)[n % 10] ?? 'th'}`;
}

export function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function signed(n: number): string {
  return n >= 0 ? `+${n}` : `${n}`;
}

/**
 * Paragraphs of a description: SRD 2014 gives a list, SRD 2024 a single string with line breaks
 * (and sometimes padding spaces), which is cut into trimmed, non-empty lines.
 */
export function paragraphs(text: string | readonly string[] | undefined): string[] {
  if (text === undefined) {
    return [];
  }
  return typeof text === 'string'
    ? text
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== '')
    : [...text];
}
