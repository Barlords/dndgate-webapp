import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../../core/api/api.providers';
import { ApiProblem } from '../../../../core/errors/api-problem';
import { CharacterPictureService } from './character-picture.service';
import { stubObjectUrls } from './characters.testing';

describe('CharacterPictureService', () => {
  let service: CharacterPictureService;
  let backend: HttpTestingController;
  let urls: ReturnType<typeof stubObjectUrls>;

  const picture = (r: { method: string; url: string }) =>
    r.method === 'GET' && /\/game-characters\/c1\/picture$/.test(r.url);

  beforeEach(() => {
    urls = stubObjectUrls();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    service = TestBed.inject(CharacterPictureService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
  });

  it('fetches a blob and caches the url', () => {
    const first: string[] = [];
    const second: string[] = [];
    service.objectUrl('c1').subscribe((url) => first.push(url));
    service.objectUrl('c1').subscribe((url) => second.push(url));
    const request = backend.expectOne(picture);
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['x'], { type: 'image/png' }));
    expect(first).toEqual(['blob:1']);
    expect(second).toEqual(['blob:1']);
    expect(urls.created).toHaveLength(1);
    // A reader that comes later gets the url without another request.
    const third: string[] = [];
    service.objectUrl('c1').subscribe((url) => third.push(url));
    expect(third).toEqual(['blob:1']);
  });

  it('keeps the url while a reader remains and revokes it with the last one', () => {
    const a = service.objectUrl('c1').subscribe();
    const b = service.objectUrl('c1').subscribe();
    backend.expectOne(picture).flush(new Blob(['x']));
    a.unsubscribe();
    expect(urls.revoked).toEqual([]);
    b.unsubscribe();
    expect(urls.revoked).toEqual(['blob:1']);
    // The next reader fetches again.
    service.objectUrl('c1').subscribe();
    backend.expectOne(picture).flush(new Blob(['y']));
    expect(urls.created).toHaveLength(2);
  });

  it('cancels a request nobody waits for any more', () => {
    service.objectUrl('c1').subscribe().unsubscribe();
    const request = backend.expectOne(picture);
    expect(request.cancelled).toBe(true);
  });

  it('turns a failure carrying a blob body into an api problem', async () => {
    const failure = new Promise<ApiProblem>((resolve) =>
      service.objectUrl('c1').subscribe({ error: resolve }),
    );
    backend
      .expectOne(picture)
      .flush(new Blob([JSON.stringify({ title: 'Forbidden' })], { type: 'application/json' }), {
        status: 403,
        statusText: 'Forbidden',
      });
    const problem = await failure;
    expect(problem.status).toBe(403);
    expect(problem.title).toBe('Forbidden');
  });
});
