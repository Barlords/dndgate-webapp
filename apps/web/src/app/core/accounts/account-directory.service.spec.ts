import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideApi } from '../api/api.providers';
import { AccountDirectory } from './account-directory.service';

describe('AccountDirectory', () => {
  let backend: HttpTestingController;
  let directory: AccountDirectory;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    backend = TestBed.inject(HttpTestingController);
    directory = TestBed.inject(AccountDirectory);
  });

  afterEach(() => backend.verify());

  it('directory caches and shares requests', async () => {
    const first = firstValueFrom(directory.get('7'));
    const second = firstValueFrom(directory.get('7'));
    backend
      .expectOne((r) => r.url.endsWith('/accounts/id/7'))
      .flush({ id: '7', pseudo: 'Gandalf' });
    expect((await first).pseudo).toBe('Gandalf');
    expect((await second).pseudo).toBe('Gandalf');

    expect((await firstValueFrom(directory.get('7'))).pseudo).toBe('Gandalf');
    backend.expectNone(() => true);
    expect(directory.pseudo('7')).toBe(directory.pseudo('7'));
  });

  it('directory names anonymized accounts', () => {
    const pseudo = directory.pseudo('9');
    expect(pseudo()).toBe('…');
    backend
      .expectOne((r) => r.url.endsWith('/accounts/id/9'))
      .flush({ id: '9', pseudo: 'deleted-1a2b', anonymized: true });
    expect(pseudo()).toBe('Compte supprimé');
  });

  it('directory shows the pseudo once loaded', () => {
    const pseudo = directory.pseudo('3');
    backend
      .expectOne((r) => r.url.endsWith('/accounts/id/3'))
      .flush({ id: '3', pseudo: 'Elminster' });
    expect(pseudo()).toBe('Elminster');
  });

  it('directory falls back when the account cannot be read, and retries later', async () => {
    const pseudo = directory.pseudo('4');
    backend
      .expectOne((r) => r.url.endsWith('/accounts/id/4'))
      .flush({}, { status: 404, statusText: 'Not Found' });
    expect(pseudo()).toBe('Compte inconnu');

    const retry = firstValueFrom(directory.get('4'));
    backend.expectOne((r) => r.url.endsWith('/accounts/id/4')).flush({ id: '4', pseudo: 'Back' });
    expect((await retry).pseudo).toBe('Back');
  });

  describe('after a failed lookup', () => {
    beforeEach(() => vi.useFakeTimers({ toFake: ['Date'] }));
    afterEach(() => vi.useRealTimers());

    const accountRequest = () => backend.expectOne((r) => r.url.endsWith('/accounts/id/5'));

    it('directory reads within the cooldown do not call the API again', () => {
      const pseudo = directory.pseudo('5');
      accountRequest().flush({}, { status: 500, statusText: 'Server Error' });
      vi.advanceTimersByTime(29_000);
      directory.pseudo('5');
      directory.pseudo('5');
      backend.expectNone(() => true);
      expect(pseudo()).toBe('Compte inconnu');
    });

    it('directory retries once after the cooldown, on the same signal', () => {
      const pseudo = directory.pseudo('5');
      accountRequest().flush({}, { status: 500, statusText: 'Server Error' });
      vi.advanceTimersByTime(30_000);

      expect(directory.pseudo('5')).toBe(pseudo);
      directory.pseudo('5'); // in flight: no second request
      accountRequest().flush({ id: '5', pseudo: 'Back' });
      expect(pseudo()).toBe('Back');

      directory.pseudo('5');
      backend.expectNone(() => true);
    });
  });
});
