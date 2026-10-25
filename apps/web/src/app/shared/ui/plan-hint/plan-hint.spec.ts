import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApi } from '../../../core/api/api.providers';
import { PlanHint } from './plan-hint';

const CATALOG = {
  currency: 'EUR',
  defaultPlan: 'adventurer',
  plans: [
    { id: 'adventurer', features: { characterPictures: false, video: false } },
    { id: 'hero', features: { characterPictures: true, video: false } },
    { id: 'dungeon-master', features: { characterPictures: true, video: true } },
  ],
};

describe('PlanHint', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi(), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function render(inputs: Record<string, string | null>, answer: object | number = CATALOG) {
    const fixture = TestBed.createComponent(PlanHint);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    const request = backend.expectOne((r) => r.url.endsWith('/plans'));
    if (typeof answer === 'number') {
      request.flush({}, { status: answer, statusText: 'Error' });
    } else {
      request.flush(answer);
    }
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('names the lowest plan that unlocks the feature, with a link to the plans', async () => {
    const hint = await render({ feature: 'characterPictures' });
    expect(hint.textContent).toContain('Inclus à partir de l’offre Héros.');
    expect(hint.querySelector('a')?.getAttribute('href')).toBe('/plans');
  });

  it('proposes only the plans after the reader’s own', async () => {
    const hint = await render({ feature: 'video', currentPlanId: 'hero' });
    expect(hint.textContent).toContain('Maître du Donjon');
  });

  it('says it is not included when no later plan has the feature', async () => {
    const hint = await render({ feature: 'video', currentPlanId: 'dungeon-master' });
    expect(hint.textContent).toContain('Non inclus dans ton offre.');
  });

  it('in an adventure it also mentions the equipped table of the GM', async () => {
    const hint = await render({ feature: 'characterPictures', scope: 'adventure' });
    expect(hint.textContent).toContain(
      'Inclus à partir de l’offre Héros (ou si ton MJ a une table équipée).',
    );
  });

  it('keeps the link when the plans cannot be loaded', async () => {
    const hint = await render({ feature: 'video' }, 500);
    expect(hint.textContent).toContain('Non inclus dans ton offre.');
    expect(hint.querySelector('a')).not.toBeNull();
  });
});
