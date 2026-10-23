import { Observable, of } from 'rxjs';
import { fetchAllPages } from './fetch-all-pages';

interface Page {
  content?: number[];
  totalPages?: number;
}

function range(start: number, count: number): number[] {
  return Array.from({ length: count }, (_, i) => start + i);
}

describe('fetchAllPages', () => {
  it('loads every page in order', async () => {
    const calls: number[] = [];
    const pages: Page[] = [
      { content: range(0, 100), totalPages: 3 },
      { content: range(100, 100), totalPages: 3 },
      { content: range(200, 7), totalPages: 3 },
    ];
    const load = (page: number): Observable<Page> => {
      calls.push(page);
      return of(pages[page]);
    };

    const result = await new Promise<number[]>((resolve) => fetchAllPages(load).subscribe(resolve));

    expect(result).toEqual(range(0, 207));
    expect(calls).toEqual([0, 1, 2]);
  });

  it('stops after one page when totalPages is 1', async () => {
    const calls: number[] = [];
    const load = (page: number): Observable<Page> => {
      calls.push(page);
      return of({ content: [1, 2], totalPages: 1 });
    };

    const result = await new Promise<number[]>((resolve) => fetchAllPages(load).subscribe(resolve));

    expect(result).toEqual([1, 2]);
    expect(calls).toEqual([0]);
  });

  it('returns an empty list when content is missing', async () => {
    const result = await new Promise<number[]>((resolve) =>
      fetchAllPages<number>(() => of({})).subscribe(resolve),
    );

    expect(result).toEqual([]);
  });
});
