import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER } from 'rxjs';
import { provideApi } from '../../core/api/api.providers';
import { AuthService } from '../../core/auth/auth.service';
import { CheckoutSuccessPage } from './checkout-success.page';

const CATALOG = { currency: 'EUR', defaultPlan: 'adventurer', plans: [] };

describe('CheckoutSuccessPage', () => {
  let backend: HttpTestingController;
  let fixture: ComponentFixture<CheckoutSuccessPage>;

  beforeEach(async () => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionEnded: NEVER } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CheckoutSuccessPage);
    fixture.detectChanges();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    await vi.advanceTimersByTimeAsync(0);
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
    // A try in flight when the test ends is cancelled by the destroy; drop it.
    subscriptionCalls();
    backend.verify();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const subscriptionCalls = () => backend.match((r) => r.url.endsWith('/accounts/me/subscription'));

  /** Answers the try in flight, then lets two seconds pass. */
  async function answer(planId: string, status = 'active') {
    const [call] = subscriptionCalls();
    call.flush({ planId, status, billingEnabled: true });
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(2000);
  }

  it('the_success_page_keeps_polling_until_the_plan_changes_and_then_stops', async () => {
    // The webhook has not arrived: the reader is still on the free plan.
    await answer('adventurer', 'active');
    await answer('adventurer', 'active');
    expect(text()).not.toContain('Bienvenue');
    await answer('hero', 'trialing');
    expect(text()).toContain('Bienvenue dans l’offre Héros');
    expect(fixture.nativeElement.querySelector('a[href="/account"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/tavern"]')).not.toBeNull();
    // Stopped: no further call.
    await vi.advanceTimersByTimeAsync(10_000);
    expect(subscriptionCalls()).toHaveLength(0);
  });

  it('a_plan_that_does_not_apply_is_not_shown_as_the_new_offer', async () => {
    await answer('hero', 'incomplete');
    expect(text()).not.toContain('Bienvenue');
  });

  it('after_the_last_try_it_says_confirmation_is_pending_and_can_retry', async () => {
    for (let i = 0; i < 15; i++) {
      await answer('adventurer');
    }
    fixture.detectChanges();
    expect(subscriptionCalls()).toHaveLength(0);
    expect(text()).toContain('Ton paiement est en cours de confirmation');
    expect(text()).not.toContain('Bienvenue');

    const retry = [...fixture.nativeElement.querySelectorAll('button')].find((b) =>
      (b as HTMLElement).textContent?.includes('Vérifier à nouveau'),
    ) as HTMLButtonElement;
    retry.click();
    await vi.advanceTimersByTimeAsync(0);
    await answer('legendary');
    expect(text()).toContain('Bienvenue dans l’offre Légendaire');
  });

  it('a_failed_try_does_not_stop_the_polling', async () => {
    const [call] = subscriptionCalls();
    call.flush('', { status: 500, statusText: 'Error' });
    await vi.advanceTimersByTimeAsync(2000);
    await answer('hero');
    expect(text()).toContain('Bienvenue dans l’offre Héros');
  });

  it('polling_stops_when_the_page_is_left', async () => {
    // Answered, so nothing is in flight: only the timer could make the next call.
    subscriptionCalls()[0].flush({ planId: 'adventurer', status: 'active' });
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(subscriptionCalls()).toHaveLength(0);
  });
});
