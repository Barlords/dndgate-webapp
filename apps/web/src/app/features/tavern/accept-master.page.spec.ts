import { TestBed } from '@angular/core/testing';
import { NotifierService } from '../../core/errors/notifier.service';
import { adventure, setupTavern, TavernTestbed } from './tavern.testing';

describe('AcceptMasterPage', () => {
  let tavern: TavernTestbed;

  const accept = (r: { url: string; method: string }) =>
    r.method === 'POST' && /adventures\/master\/accept/.test(r.url);

  function type(value: string): void {
    const input = tavern.main().querySelector<HTMLInputElement>('input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const submit = () => tavern.main().querySelector('form')!.dispatchEvent(new Event('submit'));

  beforeEach(async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/master-code');
  });

  afterEach(() => tavern.backend.verify());

  it('accepting a code opens the adventure', async () => {
    type(' MJ-1 ');
    submit();
    const request = tavern.backend.expectOne(accept);
    expect(request.request.body).toEqual({ code: 'MJ-1' });
    request.flush(adventure({ id: 'z9' }));
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/adventures/z9/overview');
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('MJ');
    tavern.backend.match(() => true);
  });

  it('refuses an empty code without calling the API', async () => {
    submit();
    await tavern.render();
    expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
  });

  it('says so when the code is unusable', async () => {
    type('OLD');
    submit();
    tavern.backend.expectOne(accept).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();
    expect(tavern.main().textContent).toContain('invalide, expiré');
  });

  it('tells about other failures through a notification', async () => {
    type('X');
    submit();
    tavern.backend.expectOne(accept).flush({}, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].kind).toBe('error');
  });

  it('is reachable from the Tavern menu', () => {
    const links = [...tavern.root.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(links.filter((href) => href === '/tavern/master-code')).toHaveLength(1);
  });
});
