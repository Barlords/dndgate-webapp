import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideApi } from '../api/api.providers';
import { NotifierService } from '../errors/notifier.service';
import { Billing, REDIRECT } from './billing.service';

describe('Billing', () => {
  let backend: HttpTestingController;
  let billing: Billing;
  let redirect: ReturnType<typeof vi.fn>;
  let notifier: NotifierService;

  beforeEach(() => {
    redirect = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        { provide: REDIRECT, useValue: redirect },
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
    expect(billing.busy()).toBe(false);
  });
});
