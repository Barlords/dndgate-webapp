import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideApi } from '../api/api.providers';
import { PlanCatalog } from './plan-catalog.service';

const CATALOG = {
  currency: 'EUR',
  defaultPlan: 'adventurer',
  plans: [{ id: 'adventurer' }, { id: 'hero' }],
};

describe('PlanCatalog', () => {
  let backend: HttpTestingController;
  let plans: PlanCatalog;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    backend = TestBed.inject(HttpTestingController);
    plans = TestBed.inject(PlanCatalog);
  });

  afterEach(() => backend.verify());

  it('the_catalog_is_fetched_once', async () => {
    expect(plans.catalog()).toBeNull();
    const first = firstValueFrom(plans.load());
    const second = firstValueFrom(plans.load());
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    expect((await first).currency).toBe('EUR');
    expect((await second).defaultPlan).toBe('adventurer');

    await firstValueFrom(plans.load());
    backend.expectNone(() => true);
    expect(plans.catalog()?.plans).toHaveLength(2);
    expect(plans.plan('hero')?.id).toBe('hero');
    expect(plans.plan('nope')).toBeUndefined();
  });

  it('a_failed_fetch_is_retried_by_the_next_call', async () => {
    const failed = firstValueFrom(plans.load()).catch((error: unknown) => error);
    backend.expectOne((r) => r.url.endsWith('/plans')).flush('', { status: 503, statusText: 'x' });
    expect(await failed).toBeInstanceOf(HttpErrorResponse);
    expect(plans.catalog()).toBeNull();

    const retried = firstValueFrom(plans.load());
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    expect((await retried).currency).toBe('EUR');
    expect(plans.catalog()).not.toBeNull();
  });
});
