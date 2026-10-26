import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, Subject } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { provideApi } from '../api/api.providers';
import { NotifierService } from '../errors/notifier.service';
import { Billing, REDIRECT } from './billing.service';

describe('Billing', () => {
  let backend: HttpTestingController;
  let billing: Billing;
  let redirect: ReturnType<typeof vi.fn>;
  let notifier: NotifierService;
  let sessionEnded: Subject<void>;

  beforeEach(() => {
    redirect = vi.fn();
    sessionEnded = new Subject<void>();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        { provide: REDIRECT, useValue: redirect },
        { provide: AuthService, useValue: { sessionEnded } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    billing = TestBed.inject(Billing);
    notifier = TestBed.inject(NotifierService);
  });

  afterEach(() => backend.verify());

  const post = (path: string) =>
    backend.expectOne((r) => r.method === 'POST' && r.url.endsWith(path));

  it('the_subscription_of_the_reader_is_kept', async () => {
    expect(billing.subscription()).toBeNull();
    const loading = firstValueFrom(billing.loadMine());
    backend
      .expectOne((r) => r.url.endsWith('/accounts/me/subscription'))
      .flush({ planId: 'hero', billingEnabled: true });
    expect((await loading).planId).toBe('hero');
    expect(billing.subscription()?.planId).toBe('hero');
  });

  it('a_reload_forgets_the_previous_answer_and_a_logout_clears_it', async () => {
    const subscriptionCall = () =>
      backend.expectOne((r) => r.url.endsWith('/accounts/me/subscription'));
    const first = firstValueFrom(billing.loadMine());
    subscriptionCall().flush({ planId: 'hero' });
    await first;

    const second = firstValueFrom(billing.loadMine());
    expect(billing.subscription()).toBeNull();
    subscriptionCall().flush({ planId: 'legendary' });
    await second;
    expect(billing.subscription()?.planId).toBe('legendary');

    sessionEnded.next();
    expect(billing.subscription()).toBeNull();
  });

  it('a_success_without_url_is_an_error_and_a_redirect_keeps_the_service_busy', () => {
    billing.openPortal();
    post('/billing/portal-session').flush({});
    expect(redirect).not.toHaveBeenCalled();
    expect(notifier.toasts()).toHaveLength(1);
    expect(billing.busy()).toBe(false);

    billing.openPortal();
    post('/billing/portal-session').flush({ url: 'https://stripe.test/portal' });
    expect(billing.busy()).toBe(true);
    billing.openPortal();
    backend.expectNone(() => true);
  });

  it('checkout_sends_the_reader_to_the_url_the_api_gives', () => {
    billing.checkout('hero', 'yearly');
    const request = post('/billing/checkout-session');
    expect(request.request.body).toEqual({ planId: 'hero', interval: 'yearly' });
    request.flush({ url: 'https://stripe.test/pay' });
    expect(redirect).toHaveBeenCalledExactlyOnceWith('https://stripe.test/pay');
  });

  it('the_portal_sends_the_reader_to_the_url_the_api_gives', () => {
    billing.openPortal();
    post('/billing/portal-session').flush({ url: 'https://stripe.test/portal' });
    expect(redirect).toHaveBeenCalledExactlyOnceWith('https://stripe.test/portal');
  });

  it('a_409_on_checkout_opens_the_portal', () => {
    billing.checkout('hero', 'monthly');
    post('/billing/checkout-session').flush('', { status: 409, statusText: 'Conflict' });
    post('/billing/portal-session').flush({ url: 'https://stripe.test/portal' });
    expect(redirect).toHaveBeenCalledExactlyOnceWith('https://stripe.test/portal');
  });

  it('a_503_says_that_payment_is_unavailable', () => {
    billing.checkout('hero', 'monthly');
    post('/billing/checkout-session').flush('', { status: 503, statusText: 'Unavailable' });
    expect(redirect).not.toHaveBeenCalled();
    expect(notifier.toasts().map((toast) => toast.message)).toEqual([
      'Le paiement n’est pas disponible pour le moment.',
    ]);

    billing.openPortal();
    post('/billing/portal-session').flush('', { status: 503, statusText: 'Unavailable' });
    expect(notifier.toasts()).toHaveLength(2);
  });

  it('another_failure_is_notified_with_its_context', () => {
    billing.openPortal();
    post('/billing/portal-session').flush('', { status: 500, statusText: 'Error' });
    expect(redirect).not.toHaveBeenCalled();
    expect(notifier.toasts()[0].message).toContain('Ouverture de la gestion de l’abonnement');
  });

  it('a_second_click_while_a_call_is_running_is_ignored', () => {
    billing.checkout('hero', 'monthly');
    billing.checkout('hero', 'monthly');
    billing.openPortal();
    post('/billing/checkout-session').flush({ url: 'https://stripe.test/pay' });
    // Still busy: the page is being left.
    expect(billing.busy()).toBe(true);
  });

  it('coming_back_from_the_back_forward_cache_unlocks_the_buttons', () => {
    billing.openPortal();
    post('/billing/portal-session').flush({ url: 'https://stripe.test/portal' });
    expect(billing.busy()).toBe(true);
    // A page loaded afresh is not a restore: still waiting for the redirect.
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: false }));
    expect(billing.busy()).toBe(true);
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
    expect(billing.busy()).toBe(false);
  });
});
