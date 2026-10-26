import { HttpHeaders } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { MessageDto } from '../../../core/api/generated/models/message-dto';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { ChatConnection, StompService } from '../../../core/realtime/stomp.service';
import { AdventureEntitlementsDto } from '../../../core/api/generated/models/adventure-entitlements-dto';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';
import { Req } from './notes.testing';

describe('MessagesPage', () => {
  let tavern: TavernTestbed;
  let chat: Subject<MessageDto>;
  /** What the API holds, oldest first. */
  let server: MessageDto[];
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
    sendDate: new Date(Date.UTC(2026, 9, 23, 10, n)).toISOString(),
    ...overrides,
  });
  const range = (from: number, to: number) =>
    Array.from({ length: to - from + 1 }, (_, i) => message(from + i));
  const texts = (from: number, to: number) => range(from, to).map((m) => m.content);

  async function open(
    overrides: Partial<AdventureDto> = {},
    entitlements?: AdventureEntitlementsDto | null,
  ) {
    server = [];
    chat = new Subject<MessageDto>();
    stomp = {
      watchChat: vi.fn(() => chat.asObservable()),
      reconnect: vi.fn(),
      connection: signal<ChatConnection>('open'),
    };
    TestBed.configureTestingModule({ providers: [{ provide: StompService, useValue: stomp }] });
    tavern = await setupTavern(entitlements);
    await tavern.go('/tavern/adventures/a1/messages');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url))
      .flush(adventure({ players: ['me', 'p1'], ...overrides }));
    await tavern.render();
  }

  /** Answers the pending request for page `number` from what the server holds, newest first. */
  async function answer(number = 0) {
    const request = tavern.backend.expectOne(
      (r) => list(r) && r.params.get('page') === String(number),
    );
    const size = Number(request.request.params.get('size'));
    expect(size).toBe(20);
    const newest = [...server].reverse();
    request.flush({
      content: newest.slice(number * size, (number + 1) * size),
      page: number,
      size,
      totalElements: server.length,
      totalPages: Math.ceil(server.length / size),
    });
    await tavern.render();
    tavern.backend.match(accounts).forEach((r) => r.flush({ pseudo: 'Alice' }));
    await tavern.render();
  }

  /** The connection drops, `missed` are posted meanwhile, then it comes back. */
  async function outage(missed: MessageDto[]) {
    stomp.connection.set('lost');
    await tavern.render();
    server.push(...missed);
    stomp.connection.set('open');
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
    server = range(1, 25);
    await answer(0);
    expect(contents()).toEqual(texts(6, 25));
    const time = items()[0].querySelector('time')!;
    expect(time.getAttribute('datetime')).toBe('2026-10-23T10:06:00.000Z');
    const local = new Date('2026-10-23T10:06:00Z').toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
    });
    expect(time.textContent).toContain(local);
    expect(items()[0].textContent).toContain('Alice');

    // A message arriving meanwhile shifts the pages: nothing older may be skipped.
    server.push(message(26));
    chat.next(message(26));
    await tavern.render();
    tavern.button('Messages plus anciens').click();
    await answer(1);

    expect(contents()).toEqual(texts(1, 26));
    expect(tavern.button('Messages plus anciens')).toBeUndefined();
  });

  it('a received message is appended once even if also returned by send', async () => {
    await open();
    server = [message(1)];
    await answer();
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
    await answer();
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
    await answer();
    await type('Ligne');
    const shifted = await press('Enter', { shiftKey: true });
    expect(shifted.defaultPrevented).toBe(false);
    await type('   ');
    await press('Enter');
    tavern.backend.expectNone(post);
  });

  it('refuses a message over 5000 characters', async () => {
    await open();
    await answer();
    await type('x'.repeat(5001));
    tavern.button('Envoyer').click();
    await tavern.render();
    tavern.backend.expectNone(post);
    expect(main().textContent).toContain('5000 caractères au maximum.');
  });

  it('reports another refusal as a toast', async () => {
    await open();
    await answer();
    await type('Salut');
    await press('Enter');
    tavern.backend.expectOne(post).flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('droits');
  });

  it('renders the content as text, never as markup', async () => {
    await open();
    server = [message(1, { content: '<b>gras</b>\nsuite' })];
    await answer();
    expect(items()[0].querySelector('b')).toBeNull();
    expect(contents()).toEqual(['<b>gras</b>\nsuite']);
  });

  it('says so when nobody has written yet', async () => {
    await open();
    await answer();
    expect(main().textContent).toContain('Aucun message pour le moment.');
  });

  it('shows when the connection is lost, and catches up once it is back', async () => {
    await open();
    server = [message(1)];
    await answer();
    expect(main().textContent).not.toContain('Connexion perdue');

    stomp.connection.set('lost');
    await tavern.render();
    expect(main().textContent).toContain('Connexion perdue');
    expect(tavern.button('Se reconnecter')).toBeUndefined();

    stomp.connection.set('stopped');
    await tavern.render();
    tavern.button('Se reconnecter').click();
    expect(stomp.reconnect).toHaveBeenCalled();

    server.push(message(2));
    stomp.connection.set('connecting');
    await tavern.render();
    stomp.connection.set('open');
    await tavern.render();
    await answer(0);
    expect(main().textContent).not.toContain('Connexion perdue');
    expect(contents()).toEqual(['Message 1', 'Message 2']);
  });

  it('catches up on more than a page of missed messages, without a hole', async () => {
    await open();
    server = range(1, 3);
    await answer();

    await outage(range(4, 30));
    await answer(0);
    await answer(1);

    tavern.backend.expectNone(list);
    expect(contents()).toEqual(texts(1, 30));
    expect(tavern.button('Messages plus anciens')).toBeUndefined();
  });

  it('after a very long outage, keeps the newest pages and lets the reader load the rest', async () => {
    await open();
    server = [message(1)];
    await answer();

    await outage(range(2, 151));
    for (const page of [0, 1, 2, 3, 4]) {
      await answer(page);
    }
    tavern.backend.expectNone(list);
    expect(contents()).toEqual(texts(52, 151));

    tavern.button('Messages plus anciens').click();
    await answer(5);
    expect(contents()).toEqual(texts(32, 151));
  });

  it('follows new messages only when the reader is at the bottom', async () => {
    await open();
    server = [message(1)];
    await answer();
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
    server = range(1, 21);
    await answer();
    const log = main().querySelector<HTMLElement>('.log')!;
    let height = 1000;
    Object.defineProperty(log, 'scrollHeight', { get: () => height });
    Object.defineProperty(log, 'clientHeight', { get: () => 200 });
    log.scrollTop = 0;

    tavern.button('Messages plus anciens').click();
    tavern.backend
      .expectOne((r) => list(r) && r.params.get('page') === '1')
      .flush({ content: [message(1)], page: 1, size: 20, totalElements: 21, totalPages: 2 });
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
    server = [message(1)];
    await answer();
    expect(contents()).toEqual(['Message 1']);
  });

  describe('history banners', () => {
    const banner = () => main().querySelector('.history[role="status"]');
    const withUsage = (used: number, max = 100): AdventureEntitlementsDto => ({
      features: [],
      limits: { messagesPerAdventure: { used, max } },
    });
    const plans = (r: Req) => r.method === 'GET' && /\/plans$/.test(r.url);
    const CATALOG = {
      currency: 'EUR',
      defaultPlan: 'adventurer',
      plans: [
        { id: 'adventurer', limits: { messagesPerAdventure: 100 } },
        { id: 'hero', limits: { messagesPerAdventure: 100 } },
        { id: 'dungeon-master', limits: { messagesPerAdventure: 500 } },
      ],
    };

    it('no_banner_below_80_percent', async () => {
      await open({}, withUsage(79));
      await answer();
      expect(banner()).toBeNull();
    });

    it('no_banner_when_the_usage_is_unknown', async () => {
      await open({}, null);
      await answer();
      expect(banner()).toBeNull();
    });

    it('the_almost_full_banner_from_80_percent', async () => {
      await open({}, withUsage(80));
      await answer();
      expect(banner()!.textContent).toContain(
        'L’historique est bientôt plein : les plus anciens messages seront effacés',
      );
      expect(
        main().querySelector('.history')!.compareDocumentPosition(main().querySelector('.log')!),
      ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    });

    it('the_almost_full_banner_stops_at_99_percent', async () => {
      await open({}, withUsage(99));
      await answer();
      expect(banner()!.textContent).toContain('bientôt plein');
    });

    it('the_full_banner_at_100_percent', async () => {
      await open({}, withUsage(100));
      await answer();
      expect(banner()!.textContent).toContain(
        'Historique plein : chaque nouveau message efface le plus ancien',
      );
      expect(banner()!.textContent).not.toContain('bientôt plein');
    });

    it('the_master_sees_the_offer_that_raises_the_limit', async () => {
      await open({ creatorId: 'me' }, withUsage(100));
      await answer();
      tavern.backend.expectOne(plans).flush(CATALOG);
      await tavern.render();
      expect(banner()!.querySelector('dg-plan-hint')!.textContent).toContain(
        'Plus de place à partir de l’offre Maître du Donjon.',
      );
    });

    it('a_player_does_not_see_the_offer', async () => {
      await open({}, withUsage(100));
      await answer();
      expect(banner()!.querySelector('dg-plan-hint')).toBeNull();
    });

    it('messages_received_or_sent_count_once_and_resync_after_a_pause', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
      try {
        await open({}, withUsage(78));
        await answer();
        expect(banner()).toBeNull();

        const mine = message(1, { senderId: 'me' });
        chat.next(mine);
        await tavern.render();
        await type('Un');
        await press('Enter');
        tavern.backend.expectOne(post).flush(mine, { status: 201, statusText: 'Created' });
        await tavern.render();
        // Message 1 came on the topic and as the answer: 79, still below 80 %.
        expect(banner()).toBeNull();

        chat.next(message(2));
        await tavern.render();
        expect(banner()!.textContent).toContain('bientôt plein');

        chat.next(message(3));
        chat.next(message(4));
        await tavern.render();
        // Capped at the maximum.
        for (let n = 5; n <= 30; n++) {
          chat.next(message(n));
        }
        await tavern.render();
        expect(banner()!.textContent).toContain('Historique plein');

        // The count is asked again 2 s after the last message: the API says 78 (the test double).
        vi.advanceTimersByTime(1999);
        await tavern.render();
        expect(banner()!.textContent).toContain('Historique plein');
        vi.advanceTimersByTime(1);
        await tavern.render();
        expect(banner()).toBeNull();
      } finally {
        vi.useRealTimers();
      }
    });
  });
});
