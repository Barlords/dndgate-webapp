import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../core/api/api.providers';
import { AccountBadges } from './account-badges';

describe('AccountBadges', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function marks(id: string, account: object): Promise<HTMLElement[]> {
    const fixture = TestBed.createComponent(AccountBadges);
    fixture.componentRef.setInput('accountId', id);
    await fixture.whenStable();
    backend.expectOne((r) => r.url.endsWith(`/accounts/id/${id}`)).flush({ id, ...account });
    await fixture.whenStable();
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.mark'));
  }

  it('shows the founder mark first, then the plan badge, with text and title', async () => {
    const shown = await marks('1', { founder: true, planBadge: 'legend' });
    expect(shown.map((m) => m.textContent)).toEqual(['Fondateur', 'Légende']);
    expect(shown.map((m) => m.getAttribute('title'))).toEqual(['Fondateur', 'Légende']);
  });

  it('shows the dungeon master badge alone', async () => {
    const shown = await marks('2', { founder: false, planBadge: 'dungeon-master' });
    expect(shown.map((m) => m.textContent)).toEqual(['MJ']);
  });

  it('shows nothing for an account without badges', async () => {
    expect(await marks('3', { founder: false, planBadge: null })).toEqual([]);
  });

  it('shows nothing, and asks nothing, without an account id', async () => {
    const fixture = TestBed.createComponent(AccountBadges);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.mark')).toHaveLength(0);
    backend.expectNone(() => true);
  });
});
