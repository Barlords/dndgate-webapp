import { TestBed } from '@angular/core/testing';
import { NotifierService } from '../../core/errors/notifier.service';
import { adventure, setupTavern, TavernTestbed } from './tavern.testing';

describe('UseCodePage', () => {
  let tavern: TavernTestbed;

  const asPlayer = (r: { url: string; method: string }) =>
    r.method === 'POST' && /adventures\/invitations\/accept/.test(r.url);
  const asMaster = (r: { url: string; method: string }) =>
    r.method === 'POST' && /adventures\/master\/accept/.test(r.url);

  function type(value: string): void {
    const input = tavern.main().querySelector<HTMLInputElement>('input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const submit = () => tavern.main().querySelector('form')!.dispatchEvent(new Event('submit'));
  const toasts = () => TestBed.inject(NotifierService).toasts();

  beforeEach(async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/code');
  });

  afterEach(() => tavern.backend.verify());

  it('offers a single invitation code field', () => {
    const labels = [...tavern.main().querySelectorAll('label')].map((l) => l.textContent?.trim());
    expect(labels).toEqual(['Code d’invitation']);
    expect(tavern.main().querySelectorAll('input')).toHaveLength(1);
  });

  it('joins with a player code and opens the adventure', async () => {
    type(' ABC123 ');
    submit();
    const request = tavern.backend.expectOne(asPlayer);
    expect(request.request.body).toEqual({ code: 'ABC123' });
    request.flush(adventure({ id: 'z9' }));
    await tavern.render();
    // The adventure page opens on its information tab and starts loading.
    expect(tavern.router.url).toBe('/tavern/adventures/z9/overview');
    tavern.backend.match(() => true);
  });

  it('takes a code unknown to the players as a master code', async () => {
    type('MJ-1');
    submit();
    tavern.backend.expectOne(asPlayer).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    const request = tavern.backend.expectOne(asMaster);
    expect(request.request.body).toEqual({ code: 'MJ-1' });
    request.flush(adventure({ id: 'm7' }));
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/adventures/m7/overview');
    expect(toasts()[0].message).toContain('MJ');
    tavern.backend.match(() => true);
  });

  it('does not try a player code that is refused as a master code', async () => {
    type('ABC123');
    submit();
    tavern.backend.expectOne(asPlayer).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();
    tavern.backend.expectNone(asMaster);
    expect(tavern.main().textContent).toContain('Cette aventure a déjà commencé ou est complète.');
  });

  it.each([404, 409])('shows an inline message for a code unknown to both (%i)', async (status) => {
    type('NOPE');
    submit();
    tavern.backend.expectOne(asPlayer).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    tavern.backend.expectOne(asMaster).flush({}, { status, statusText: 'Refused' });
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/code');
    expect(tavern.main().textContent).toContain(
      'Ce code n’existe pas, a expiré ou n’est plus valable.',
    );
    expect(toasts()).toHaveLength(0);
  });

  it('refuses an empty or too long code without calling the API', async () => {
    submit();
    await tavern.render();
    expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
    type('x'.repeat(51));
    submit();
    await tavern.render();
    expect(tavern.main().textContent).toContain('50 caractères au maximum.');
  });

  it('tells about other failures through a notification', async () => {
    type('X');
    submit();
    tavern.backend.expectOne(asPlayer).flush({}, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(toasts()[0].kind).toBe('error');

    type('Y');
    submit();
    tavern.backend.expectOne(asPlayer).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    tavern.backend.expectOne(asMaster).flush({}, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(toasts().filter((toast) => toast.kind === 'error')).toHaveLength(2);
  });

  it('the former code pages lead here', async () => {
    await tavern.go('/tavern/join');
    expect(tavern.router.url).toBe('/tavern/code');
    await tavern.go('/tavern/master-code');
    expect(tavern.router.url).toBe('/tavern/code');
  });
});
