import { NotifierService } from '../../core/errors/notifier.service';
import { TestBed } from '@angular/core/testing';
import { adventure, setupTavern, TavernTestbed } from './tavern.testing';

describe('CreateAdventurePage', () => {
  let tavern: TavernTestbed;

  const create = (r: { url: string; method: string }) =>
    r.method === 'POST' && /\/adventures$/.test(r.url);

  async function open() {
    tavern = await setupTavern();
    await tavern.go('/tavern/new');
  }

  function type(name: string, value: string): void {
    const field = tavern
      .main()
      .querySelector<HTMLInputElement | HTMLTextAreaElement>(`[formControlName="${name}"]`)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  function fill(maxPlayer = '4'): void {
    type('name', ' La mine ');
    type('description', ' Une mine perdue ');
    type('maxPlayer', maxPlayer);
    type('postalCode', '75001');
    type('city', 'Paris');
  }

  const submit = () => tavern.main().querySelector('form')!.dispatchEvent(new Event('submit'));

  afterEach(() => tavern.backend.verify());

  it('creates the adventure and opens it', async () => {
    await open();
    fill();
    const visibility = tavern.main().querySelector<HTMLSelectElement>('select')!;
    visibility.value = 'PRIVATE';
    visibility.dispatchEvent(new Event('change'));
    submit();
    const request = tavern.backend.expectOne(create);
    expect(request.request.body).toEqual({
      name: 'La mine',
      description: 'Une mine perdue',
      maxPlayer: 4,
      visibility: 'PRIVATE',
      postalCode: '75001',
      city: 'Paris',
    });
    request.flush(adventure({ id: 'new1' }));
    await tavern.render();
    // The adventure page opens on its information tab and starts loading.
    expect(tavern.router.url).toBe('/tavern/adventures/new1/overview');
    tavern.backend.match(() => true);
  });

  it('create form enforces maxPlayer 1..100', async () => {
    await open();
    for (const [bad, message] of [
      ['0', 'Saisis un nombre supérieur ou égal à 1.'],
      ['101', 'Saisis un nombre inférieur ou égal à 100.'],
    ]) {
      fill(bad);
      submit();
      await tavern.render();
      expect(tavern.main().textContent).toContain(message);
    }
    fill('');
    submit();
    await tavern.render();
    expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
    tavern.backend.expectNone(create);
    for (const good of ['1', '100']) {
      fill(good);
      submit();
      tavern.backend.expectOne(create).flush(adventure());
      await tavern.render();
      tavern.backend.match(() => true); // The adventure page starts loading.
      await tavern.go('/tavern/new');
    }
  });

  it('refuses blank required fields', async () => {
    await open();
    fill();
    type('name', '   ');
    submit();
    await tavern.render();
    tavern.backend.expectNone(create);
    expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
  });

  it('shows the API field errors on the form', async () => {
    await open();
    fill();
    submit();
    tavern.backend
      .expectOne(create)
      .flush(
        { status: 400, errors: [{ field: 'city', message: 'Ville invalide' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    await tavern.render();
    expect(tavern.main().textContent).toContain('Ville invalide');
  });

  it('explains the limit of 50 active adventures', async () => {
    await open();
    fill();
    submit();
    tavern.backend.expectOne(create).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('50 aventures actives');
    expect(tavern.router.url).toBe('/tavern/new');
  });

  it('shows_the_plan_refusal_with_the_offers_when_the_plan_limit_is_reached', async () => {
    await open();
    fill();
    submit();
    tavern.backend.expectOne(create).flush(
      {
        type: 'urn:dndgate:plan-limit-reached',
        limit: 'activeAdventuresAsMaster',
        max: 1,
        planId: 'adventurer',
        upgradePlanId: 'hero',
        decidedBy: 'account',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await tavern.render();
    const toast = TestBed.inject(NotifierService).toasts()[0];
    expect(toast.message).toContain('Aventures actives en tant que MJ');
    expect(toast.message).not.toContain('50 aventures');
    expect(toast.action?.label).toBe('Voir les offres');
  });
});
