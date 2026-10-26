import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApi } from '../../core/api/api.providers';
import { SubscriptionDto } from '../../core/api/generated/models/subscription-dto';
import { I18n } from '../../core/i18n/i18n.service';
import { Billing, REDIRECT } from '../../core/plans/billing.service';
import { SubscriptionPanel } from './subscription-panel';

const CATALOG = {
  currency: 'EUR',
  defaultPlan: 'adventurer',
  plans: [
    {
      id: 'adventurer',
      highlighted: false,
      badge: null,
      limits: { spellBooks: 2, masterNotesPerAdventure: 10 },
      features: { characterPictures: false, video: false },
      prices: {},
    },
    {
      id: 'dungeon-master',
      highlighted: true,
      badge: 'dungeon-master',
      limits: { spellBooks: 10, masterNotesPerAdventure: 1000 },
      features: { characterPictures: true, video: true, equippedTable: true },
      prices: {},
    },
  ],
};

const USAGE = {
  activeAdventuresAsMaster: { used: 1, max: 3 },
  charactersWithoutAdventure: { used: 2, max: 3 },
  spellBooks: { used: 2, max: 2 },
  magicItems: { used: 0, max: 5 },
};

describe('SubscriptionPanel', () => {
  let backend: HttpTestingController;

  async function open(subscription: SubscriptionDto) {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        { provide: REDIRECT, useValue: vi.fn() },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SubscriptionPanel);
    fixture.detectChanges();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    backend.expectOne((r) => r.url.endsWith('/accounts/me/subscription')).flush(subscription);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (element: Element) => (element.textContent ?? '').replace(/\s+/g, ' ').trim();
  const button = (root: HTMLElement, label: string) =>
    [...root.querySelectorAll<HTMLButtonElement>('button')].find((b) => text(b) === label);

  afterEach(() => backend.verify());

  it('a_free_account_sees_its_usage_and_the_offers_link', async () => {
    const root = await open({
      planId: 'adventurer',
      interval: null,
      status: null,
      billingEnabled: true,
      usage: USAGE,
    });
    expect(text(root.querySelector('.plan')!)).toBe('Ton offre :Aventurier');
    expect(text(root)).not.toContain('Se renouvelle');
    const bars = [...root.querySelectorAll('progress')];
    expect(bars.map((bar) => bar.getAttribute('aria-label'))).toContain(
      '2 / 3 · Personnages hors aventure',
    );
    expect(bars).toHaveLength(4);
    expect(root.querySelector('a[href="/plans"]')?.textContent?.trim()).toBe(
      'Changer d’abonnement',
    );
    expect(button(root, 'Gérer mon abonnement')).toBeUndefined();
    // A full limit is in the alert colour, the others are not.
    const full = root.querySelectorAll('.usage__item--full');
    expect(full).toHaveLength(1);
    expect(text(full[0])).toContain('Grimoires');
  });

  it('lists_every_feature_of_the_catalog_ticked_or_crossed_and_the_other_limits', async () => {
    const root = await open({
      planId: 'adventurer',
      interval: null,
      status: null,
      billingEnabled: true,
      usage: USAGE,
    });
    // The limits with a usage bar are not repeated: only the per adventure one is listed.
    expect([...root.querySelectorAll('.included__limit')].map(text)).toEqual([
      'Notes du MJ par aventure10',
    ]);
    // A feature only a higher plan has is listed too, crossed out.
    const features = [...root.querySelectorAll('.included__feature')];
    expect(features.map((feature) => feature.getAttribute('aria-label'))).toEqual([
      'Images de personnage : non inclus',
      'Visio : non inclus',
      'Table équipée : non inclus',
    ]);
    expect(features.every((feature) => feature.matches('.included__feature--off'))).toBe(true);
  });

  it('a_paid_plan_ticks_its_features_and_keeps_both_ways_out', async () => {
    const root = await open({
      planId: 'dungeon-master',
      interval: 'monthly',
      status: 'active',
      billingEnabled: true,
      usage: USAGE,
    });
    const features = [...root.querySelectorAll('.included__feature')];
    expect(features.map((feature) => feature.getAttribute('aria-label'))).toEqual([
      'Images de personnage : inclus',
      'Visio : inclus',
      'Table équipée : inclus',
    ]);
    expect(root.querySelector('.included__feature--off')).toBeNull();
    expect([...root.querySelectorAll('.included__limit')].map(text)).toEqual([
      'Notes du MJ par aventure1 000',
    ]);
    expect(root.querySelector('a[href="/plans"]')?.textContent?.trim()).toBe(
      'Changer d’abonnement',
    );
    expect(button(root, 'Gérer mon abonnement')).toBeDefined();
  });

  it('the_colon_after_the_plan_label_follows_the_language', async () => {
    const root = await open({
      planId: 'adventurer',
      interval: null,
      status: null,
      billingEnabled: true,
      usage: USAGE,
    });
    TestBed.inject(I18n).use('en');
    TestBed.tick();
    expect(text(root.querySelector('.plan')!)).toBe('Your plan:Adventurer');
  });

  it('a_paid_account_sees_its_renewal_date_and_the_portal', async () => {
    const root = await open({
      planId: 'dungeon-master',
      interval: 'yearly',
      status: 'active',
      currentPeriodEnd: '2027-03-15T10:00:00Z',
      cancelAtPeriodEnd: false,
      billingEnabled: true,
      usage: USAGE,
    });
    expect(root.querySelector('.plan__name')?.textContent).toBe('Maître du Donjon');
    expect(root.querySelector('.seal--badge')?.textContent?.trim()).toBe('MJ');
    expect(text(root)).toContain('Facturation annuelle');
    expect(text(root)).toContain('Actif');
    expect(text(root)).toContain('Se renouvelle le 15 mars 2027');
    expect(button(root, 'Gérer mon abonnement')).toBeDefined();
    expect(root.querySelector('.warning')).toBeNull();
    const billing = TestBed.inject(Billing);
    const portal = vi.spyOn(billing, 'openPortal').mockReturnValue();
    button(root, 'Gérer mon abonnement')!.click();
    expect(portal).toHaveBeenCalled();
  });

  it('a_cancelled_subscription_says_when_it_ends', async () => {
    const root = await open({
      planId: 'dungeon-master',
      interval: 'monthly',
      status: 'active',
      currentPeriodEnd: '2027-03-15T10:00:00Z',
      cancelAtPeriodEnd: true,
      billingEnabled: true,
      usage: USAGE,
    });
    expect(text(root)).toContain('Prend fin le 15 mars 2027');
    expect(text(root)).not.toContain('Se renouvelle');
  });

  it('a_past_due_payment_is_flagged', async () => {
    const root = await open({
      planId: 'dungeon-master',
      interval: 'monthly',
      status: 'past_due',
      currentPeriodEnd: '2027-03-15T10:00:00Z',
      cancelAtPeriodEnd: false,
      billingEnabled: true,
      usage: USAGE,
    });
    expect(root.querySelector('.warning')?.getAttribute('role')).toBe('alert');
    expect(text(root.querySelector('.warning')!)).toContain('mets ta carte à jour');
    expect(text(root)).toContain('Paiement en retard');
    expect(button(root, 'Gérer mon abonnement')).toBeDefined();
  });

  it('without_billing_the_portal_is_hidden_and_a_note_says_why', async () => {
    const root = await open({
      planId: 'dungeon-master',
      interval: 'monthly',
      status: 'active',
      billingEnabled: false,
      usage: USAGE,
    });
    expect(button(root, 'Gérer mon abonnement')).toBeUndefined();
    expect(text(root)).toContain('Le paiement est indisponible');
  });

  it('a_failed_load_offers_a_retry', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi(), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SubscriptionPanel);
    fixture.detectChanges();
    backend.expectOne((r) => r.url.endsWith('/plans')).flush(CATALOG);
    backend
      .expectOne((r) => r.url.endsWith('/accounts/me/subscription'))
      .flush({ title: 'x' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dg-load-error')).not.toBeNull();
    fixture.nativeElement.querySelector('dg-load-error button').click();
    backend
      .expectOne((r) => r.url.endsWith('/accounts/me/subscription'))
      .flush({
        planId: 'adventurer',
        billingEnabled: true,
      });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dg-load-error')).toBeNull();
  });
});
