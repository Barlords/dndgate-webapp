import { HttpHeaders } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { MessageDto } from '../../../core/api/generated/models/message-dto';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { ChatConnection, StompService } from '../../../core/realtime/stomp.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';
import { notesPage, Req } from './notes.testing';

describe('MessagesPage', () => {
  let tavern: TavernTestbed;
  let chat: Subject<MessageDto>;
  let stomp: {
    watchChat: ReturnType<typeof vi.fn>;
    reconnect: ReturnType<typeof vi.fn>;
    connection: ReturnType<typeof signal<ChatConnection>>;
  };

  const list = (r: Req) =>
    r.method === 'GET' && /\/messages$/.test(r.url) && r.params.get('adventureId') === 'a1';
  const post = (r: Req) => r.method === 'POST' && /\/messages$/.test(r.url);
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);

  const message = (n: number, overrides: Partial<MessageDto> = {}): MessageDto => ({
    id: `m${n}`,
    adventureId: 'a1',
    senderId: 'p1',
    content: `Message ${n}`,
    sendDate: `2026-10-23T10:${String(n).padStart(2, '0')}:00Z`,
    ...overrides,
  });

  async function open(overrides: Partial<AdventureDto> = {}) {
    chat = new Subject<MessageDto>();
    stomp = {
      watchChat: vi.fn(() => chat.asObservable()),
      reconnect: vi.fn(),
      connection: signal<ChatConnection>('open'),
    };
    TestBed.configureTestingModule({ providers: [{ provide: StompService, useValue: stomp }] });
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/messages');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url))
      .flush(adventure({ players: ['me', 'p1'], ...overrides }));
    await tavern.render();
  }

  async function answer(messages: MessageDto[], totalPages = 1, number = 0) {
    tavern.backend
      .expectOne((r) => list(r) && r.params.get('page') === String(number))
      .flush(notesPage(messages, totalPages, number));
    await tavern.render();
    tavern.backend.match(accounts).forEach((r) => r.flush({ pseudo: 'Alice' }));
    await tavern.render();
  }

  const main = () => tavern.main();
  const items = () => [...main().querySelectorAll('.message')];
  const contents = () => items().map((item) => item.querySelector('.content')!.textContent);
  const textarea = () => main().querySelector('textarea')!;

  async function type(value: string) {
    textarea().value = value;
    textarea().dispatchEvent(new Event('input'));
    await tavern.render();
  }

  async function press(key: string, init: KeyboardEventInit = {}) {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
    textarea().dispatchEvent(event);
    await tavern.render();
    return event;
  }

  afterEach(() => {
    tavern.backend.match(accounts).forEach((r) => r.flush({ pseudo: 'Alice' }));
    tavern.backend.verify();
  });

  it('messages shows history oldest first and loads older pages', async () => {
    await open();
    expect(stomp.watchChat).toHaveBeenCalledWith('a1');
    await answer([message(3), message(2)], 2);
    expect(contents()).toEqual(['Message 2', 'Message 3']);
    const time = items()[0].querySelector('time')!;
    expect(time.getAttribute('datetime')).toBe('2026-10-23T10:02:00Z');
    const local = new Date('2026-10-23T10:02:00Z').toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
    });
    expect(time.textContent).toContain(local);
    expect(items()[0].textContent).toContain('Alice');

    tavern.button('Messages plus anciens').click();
    const older = tavern.backend.expectOne((r) => list(r) && r.params.get('page') === '1');
    expect(older.request.params.get('size')).toBe('20');
    older.flush(notesPage([message(1)], 2, 1));
    await tavern.render();

    expect(contents()).toEqual(['Message 1', 'Message 2', 'Message 3']);
    expect(tavern.button('Messages plus anciens')).toBeUndefined();
  });

  it('a received message is appended once even if also returned by send', async () => {
    await open();
    await answer([message(1)]);
    await type('  Bonjour la table  ');
    const enter = await press('Enter');
    expect(enter.defaultPrevented).toBe(true);
    const sent = tavern.backend.expectOne(post);
    expect(sent.request.body).toEqual({ adventureId: 'a1', content: 'Bonjour la table' });

    const mine = message(2, { senderId: 'me', content: 'Bonjour la table' });
    chat.next(mine);
    await tavern.render();
    sent.flush(mine, { status: 201, statusText: 'Created' });
    await tavern.render();
    expect(contents()).toEqual(['Message 1', 'Bonjour la table']);
    chat.next(message(3));
    await tavern.render();

    expect(contents()).toEqual(['Message 1', 'Bonjour la table', 'Message 3']);
    expect(items()[1].classList).toContain('mine');
    expect(items()[0].classList).not.toContain('mine');
    expect(textarea().value).toBe('');
  });

  it('send shows the retry delay on 429', async () => {
    await open();
    await answer([]);
    await type('Encore un');
    tavern.button('Envoyer').click();
    tavern.backend.expectOne(post).flush(
      { status: 429 },
      {
        status: 429,
        statusText: 'Too Many Requests',
        headers: new HttpHeaders({ 'Retry-After': '12' }),
      },
    );
    await tavern.render();

    expect(main().querySelector('[role="alert"]')!.textContent).toContain(
      'Tu envoies trop de messages, réessaie dans 12 s.',
    );
    expect(textarea().value).toBe('Encore un');
  });

  it('shift+enter keeps writing on a new line, an empty message is not sent', async () => {
    await open();
    await answer([]);
    await type('Ligne');
    const shifted = await press('Enter', { shiftKey: true });
    expect(shifted.defaultPrevented).toBe(false);
    await type('   ');
    await press('Enter');
    tavern.backend.expectNone(post);
  });

  it('refuses a message over 5000 characters', async () => {
    await open();
    await answer([]);
    await type('x'.repeat(5001));
    tavern.button('Envoyer').click();
    await tavern.render();
    tavern.backend.expectNone(post);
    expect(main().textContent).toContain('5000 caractères au maximum.');
  });

  it('reports another refusal as a toast', async () => {
    await open();
    await answer([]);
    await type('Salut');
    await press('Enter');
    tavern.backend.expectOne(post).flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('droits');
  });

  it('renders the content as text, never as markup', async () => {
    await open();
    await answer([message(1, { content: '<b>gras</b>\nsuite' })]);
    expect(items()[0].querySelector('b')).toBeNull();
    expect(contents()).toEqual(['<b>gras</b>\nsuite']);
  });

  it('says so when nobody has written yet', async () => {
    await open();
    await answer([]);
    expect(main().textContent).toContain('Aucun message pour le moment.');
  });

  it('shows when the connection is lost, and catches up once it is back', async () => {
    await open();
    await answer([message(1)]);
    expect(main().textContent).not.toContain('Connexion perdue');

    stomp.connection.set('lost');
    await tavern.render();
    expect(main().textContent).toContain('Connexion perdue');
    expect(tavern.button('Se reconnecter')).toBeUndefined();

    stomp.connection.set('stopped');
    await tavern.render();
    tavern.button('Se reconnecter').click();
    expect(stomp.reconnect).toHaveBeenCalled();

    stomp.connection.set('connecting');
    await tavern.render();
    stomp.connection.set('open');
    await tavern.render();
    await answer([message(2), message(1)]);
    expect(main().textContent).not.toContain('Connexion perdue');
    expect(contents()).toEqual(['Message 1', 'Message 2']);
  });

  it('follows new messages only when the reader is at the bottom', async () => {
    await open();
    await answer([message(1)]);
    const log = main().querySelector<HTMLElement>('.log')!;
    let height = 1000;
    Object.defineProperty(log, 'scrollHeight', { get: () => height });
    Object.defineProperty(log, 'clientHeight', { get: () => 200 });

    log.scrollTop = 800;
    chat.next(message(2));
    height = 1100;
    await tavern.render();
    expect(log.scrollTop).toBe(1100);

    log.scrollTop = 100;
    chat.next(message(3));
    height = 1200;
    await tavern.render();
    expect(log.scrollTop).toBe(100);
  });

  it('keeps the reading position when older messages come in', async () => {
    await open();
    await answer([message(3)], 2);
    const log = main().querySelector<HTMLElement>('.log')!;
    let height = 1000;
    Object.defineProperty(log, 'scrollHeight', { get: () => height });
    Object.defineProperty(log, 'clientHeight', { get: () => 200 });
    log.scrollTop = 0;

    tavern.button('Messages plus anciens').click();
    tavern.backend
      .expectOne((r) => list(r) && r.params.get('page') === '1')
      .flush(notesPage([message(1)], 2, 1));
    height = 1500;
    await tavern.render();

    expect(log.scrollTop).toBe(500);
  });

  it('asks nothing of the API for someone who is not a participant', async () => {
    await open({ players: ['p1'], creatorId: 'master' });
    tavern.backend.expectNone(list);
    expect(stomp.watchChat).not.toHaveBeenCalled();
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
  });

  it('offers to retry when the history cannot be loaded', async () => {
    await open();
    tavern.backend.expectOne(list).flush({}, { status: 500, statusText: 'Server' });
    await tavern.render();
    expect(main().querySelector('[role="alert"]')!.textContent).toContain('Impossible');
    tavern.button('Réessayer').click();
    await answer([message(1)]);
    expect(contents()).toEqual(['Message 1']);
  });
});
