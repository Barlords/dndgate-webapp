import { adventure, setupTavern, TavernTestbed } from './tavern.testing';

describe('JoinByCodePage', () => {
  let tavern: TavernTestbed;

  const accept = (r: { url: string; method: string }) =>
    r.method === 'POST' && /adventures\/invitations\/accept/.test(r.url);

  async function open() {
    tavern = await setupTavern();
    await tavern.go('/tavern/join');
  }

  function type(value: string): void {
    const input = tavern.main().querySelector<HTMLInputElement>('input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const submit = () => tavern.main().querySelector('form')!.dispatchEvent(new Event('submit'));

  afterEach(() => tavern.backend.verify());

  it('joins with the code and opens the adventure', async () => {
    await open();
    type(' ABC123 ');
    submit();
    const request = tavern.backend.expectOne(accept);
    expect(request.request.body).toEqual({ code: 'ABC123' });
    request.flush(adventure({ id: 'z9' }));
    await tavern.render();
    // The adventure page opens on its information tab and starts loading.
    expect(tavern.router.url).toBe('/tavern/adventures/z9/overview');
    tavern.backend.match(() => true);
  });

  it('refuses an empty or too long code without calling the API', async () => {
    await open();
    submit();
    await tavern.render();
    expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
    type('x'.repeat(51));
    submit();
    await tavern.render();
    expect(tavern.main().textContent).toContain('50 caractères au maximum.');
  });

  it('join by code shows the 409 message', async () => {
    await open();
    type('ABC123');
    submit();
    tavern.backend.expectOne(accept).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();
    expect(tavern.main().textContent).toContain('Cette aventure a déjà commencé ou est complète.');
  });

  it('shows an inline message for an unknown code', async () => {
    await open();
    type('NOPE');
    submit();
    tavern.backend.expectOne(accept).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/join');
    expect(tavern.main().textContent).toContain('Ce code n’existe pas ou n’est plus valable.');
    expect(document.body.textContent).not.toContain('Introuvable');
  });
});
