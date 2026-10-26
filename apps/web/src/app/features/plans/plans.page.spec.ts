import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER } from 'rxjs';
import { provideRouter } from '@angular/router';
import { provideApi } from '../../core/api/api.providers';
import { SubscriptionDto } from '../../core/api/generated/models/subscription-dto';
import { AuthService } from '../../core/auth/auth.service';
import { Billing, REDIRECT } from '../../core/plans/billing.service';
import { PlansPage } from './plans.page';

const LIMITS = { spellBooks: 2, messagesPerAdventure: 1000 };

const CATALOG = {
  currency: 'EUR',
  defaultPlan: 'adventurer',
  plans: [
    { id: 'adventurer', highlighted: false, badge: null, limits: { spellBooks: 2 }, prices: {} },
    {
      id: 'hero',
      highlighted: false,
      badge: null,
      limits: LIMITS,
      features: { characterPictures: true, video: false },
      prices: { monthly: { amount: 99 }, yearly: { amount: 990 } },
    },
    {
      id: 'dungeon-master',
      highlighted: true,
      badge: 'dungeon-master',
      limits: LIMITS,
      features: { video: true },
      prices: { monthly: { amount: 499 }, yearly: { amount: 4990 } },
    },
    {
      id: 'legendary',
      highlighted: false,
      badge: 'legend',
      limits: LIMITS,
      prices: { monthly: { amount: 999 }, yearly: { amount: 9990 } },
    },
  ],
};

describe('PlansPage', () => {
  let backend: HttpTestingController;
  let redirect: ReturnType<typeof vi.fn>;

  async function open(subscription: SubscriptionDto | 'anonymous') {
    localStorage.clear();
    redirect = vi.fn();
    const account = signal(subscription === 'anonymous' ? null : { id: 'me', pseudo: 'Me' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        { provide: REDIRECT, useValue: redirect },
        {
          provide: AuthService,
          useValue: { account, isAuthenticated: () => account() !== null, sessionEnded: NEVER },
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PlansPage);
    fixture.detectChanges();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    if (subscription !== 'anonymous') {
      backend.expectOne((r) => r.url.endsWith('/accounts/me/subscription')).flush(subscription);
    }
    await settle(fixture);
    return fixture;
  }

  async function settle(fixture: ComponentFixture<PlansPage>) {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const cards = (fixture: ComponentFixture<PlansPage>) =>
    [...(fixture.nativeElement as HTMLElement).querySelectorAll('dg-plan-card')] as HTMLElement[];

  const text = (element: Element) => (element.textContent ?? '').replace(/\s+/g, ' ').trim();

  /** Without any space: the price and its period sit in two spans. */
  const plain = (value: string) => value.replace(/\s/g, '');

  afterEach(() => backend.verify());

  it('the_four_offers_are_shown_in_catalog_order_with_the_most_chosen', async () => {
    const fixture = await open('anonymous');
    const shown = cards(fixture);
    expect(shown.map((card) => card.querySelector('h2')?.textContent)).toEqual([
      'Aventurier',
      'Héros',
      'Maître du Donjon',
      'Légendaire',
    ]);
    expect(text(shown[0])).toContain('Gratuit');
    expect(plain(text(shown[1]))).toContain('0,99€/mois');
    expect(text(shown[1])).toContain('Images de personnage');
    expect(text(shown[1])).not.toContain('Visio');
    expect(plain(text(shown[1]))).toContain('Grimoires2');
    const chosen = shown.filter((card) => text(card).includes('Le plus choisi'));
    expect(chosen).toEqual([shown[2]]);
    expect(text(shown[2].querySelector('.seal--badge')!)).toBe('MJ');
    expect(text(shown[3].querySelector('.seal--badge')!)).toBe('Légende');
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/terms"]')).not.toBeNull();
  });

  it('the_yearly_toggle_shows_yearly_prices_and_two_months_free', async () => {
    const fixture = await open('anonymous');
    const root = fixture.nativeElement as HTMLElement;
    expect(text(root)).not.toContain('2 mois offerts');
    const yearly = [...root.querySelectorAll('button')].find((b) => text(b) === 'Annuel')!;
    yearly.click();
    await settle(fixture);
    const shown = cards(fixture);
    expect(plain(text(shown[1]))).toContain('9,90€/an');
    expect(plain(text(shown[3]))).toContain('99,90€/an');
    expect(text(shown[1])).toContain('2 mois offerts');
    expect(text(shown[0])).not.toContain('2 mois offerts');
    expect(yearly.getAttribute('aria-pressed')).toBe('true');
  });

  it('an_anonymous_reader_is_sent_to_login_and_back', async () => {
    const fixture = await open('anonymous');
    for (const card of cards(fixture)) {
      const link = card.querySelector('a')!;
      expect(text(link)).toBe('Se connecter');
      expect(link.getAttribute('href')).toBe('/login?returnUrl=%2Fplans');
    }
  });

  it('choosing_a_paid_offer_redirects_to_checkout', async () => {
    const fixture = await open({ planId: 'adventurer', billingEnabled: true, status: null });
    const shown = cards(fixture);
    const current = shown[0].querySelector('button')!;
    expect(text(current)).toBe('Offre actuelle');
    expect(current.disabled).toBe(true);

    const yearly = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(
      (b) => text(b) === 'Annuel',
    )!;
    yearly.click();
    await settle(fixture);
    const choose = shown[2].querySelector('button')!;
    expect(text(choose)).toBe('Souscrire');
    choose.click();
    const request = backend.expectOne(
      (r) => r.method === 'POST' && r.url.endsWith('/billing/checkout-session'),
    );
    expect(request.request.body).toEqual({ planId: 'dungeon-master', interval: 'yearly' });
    request.flush({ url: 'https://stripe.test/pay' });
    expect(redirect).toHaveBeenCalledExactlyOnceWith('https://stripe.test/pay');
  });

  it('a_subscribed_reader_manages_in_the_portal', async () => {
    const fixture = await open({ planId: 'hero', billingEnabled: true, status: 'active' });
    const shown = cards(fixture);
    expect(text(shown[1].querySelector('button')!)).toBe('Offre actuelle');
    expect(shown[1].querySelector('button')!.disabled).toBe(true);
    // Only the reader's own offer wears the seal.
    expect(shown.map((card) => card.querySelector('.seal--current') !== null)).toEqual([
      false,
      true,
      false,
      false,
    ]);
    for (const index of [0, 2, 3]) {
      expect(text(shown[index].querySelector('button')!)).toBe('Souscrire');
    }
    shown[3].querySelector('button')!.click();
    backend
      .expectOne((r) => r.method === 'POST' && r.url.endsWith('/billing/portal-session'))
      .flush({ url: 'https://stripe.test/portal' });
    expect(redirect).toHaveBeenCalledExactlyOnceWith('https://stripe.test/portal');
  });

  it('with_billing_off_the_buttons_say_unavailable', async () => {
    const fixture = await open({ planId: 'adventurer', billingEnabled: false, status: null });
    const buttons = cards(fixture).map((card) => card.querySelector('button')!);
    expect(buttons.map(text)).toEqual([
      'Offre actuelle',
      'Paiement indisponible',
      'Paiement indisponible',
      'Paiement indisponible',
    ]);
    expect(buttons.every((button) => button.disabled)).toBe(true);
  });

  it('a_409_on_checkout_opens_the_portal', async () => {
    const fixture = await open({ planId: 'adventurer', billingEnabled: true, status: null });
    cards(fixture)[1].querySelector('button')!.click();
    backend
      .expectOne((r) => r.url.endsWith('/billing/checkout-session'))
      .flush('', { status: 409, statusText: 'Conflict' });
    backend
      .expectOne((r) => r.url.endsWith('/billing/portal-session'))
      .flush({ url: 'https://stripe.test/portal' });
    expect(redirect).toHaveBeenCalledExactlyOnceWith('https://stripe.test/portal');
  });

  it('a_stale_subscription_is_never_shown_while_the_reload_is_pending', async () => {
    const first = await open({ planId: 'hero', billingEnabled: true, status: 'active' });
    expect(cards(first)).toHaveLength(4);
    first.destroy();

    const second = TestBed.createComponent(PlansPage);
    second.detectChanges();
    // The plan of the previous visit is not shown: nothing but the loader until the new answer.
    expect(TestBed.inject(Billing).subscription()).toBeNull();
    expect(cards(second)).toHaveLength(0);
    // The catalog is kept for the session: only the subscription is asked again.
    backend
      .expectOne((r) => r.url.endsWith('/accounts/me/subscription'))
      .flush({ planId: 'legendary', billingEnabled: true, status: 'active' });
    await settle(second);
    expect(text(cards(second)[3].querySelector('button')!)).toBe('Offre actuelle');
  });

  it('the_spinner_is_only_on_the_clicked_card', async () => {
    const fixture = await open({ planId: 'adventurer', billingEnabled: true, status: null });
    cards(fixture)[1].querySelector('button')!.click();
    await settle(fixture);
    const busy = cards(fixture).map((card) => card.querySelector('dg-spinner') !== null);
    expect(busy).toEqual([false, true, false, false]);
    backend
      .expectOne((r) => r.url.endsWith('/billing/checkout-session'))
      .flush({ url: 'https://stripe.test/pay' });
  });

  it('a_logged_reader_whose_subscription_fails_to_load_sees_the_load_error', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            account: signal({ id: 'me', pseudo: 'Me' }),
            isAuthenticated: () => true,
            sessionEnded: NEVER,
          },
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PlansPage);
    fixture.detectChanges();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    backend
      .expectOne((r) => r.url.endsWith('/accounts/me/subscription'))
      .flush('', { status: 500, statusText: 'x' });
    await settle(fixture);
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
    expect(cards(fixture)).toHaveLength(0);
  });

  it('a_failed_load_offers_a_retry', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { account: signal(null), isAuthenticated: () => false, sessionEnded: NEVER },
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PlansPage);
    fixture.detectChanges();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush('', { status: 500, statusText: 'x' });
    await settle(fixture);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role="alert"]')).not.toBeNull();
    [...root.querySelectorAll('button')].find((b) => text(b) === 'Réessayer')!.click();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    await settle(fixture);
    expect(cards(fixture)).toHaveLength(4);
  });
});
