import { TestBed } from '@angular/core/testing';
import {
  IFrame,
  IMessage,
  IWatchParams,
  RxStomp,
  RxStompConfig,
  RxStompState,
} from '@stomp/rx-stomp';
import { BehaviorSubject, filter, firstValueFrom, Observable, of, Subject, throwError } from 'rxjs';
import { provideApi } from '../api/api.providers';
import { MessageDto } from '../api/generated/models/message-dto';
import { AuthService } from '../auth/auth.service';
import { TokenStore } from '../auth/token-store';
import { RX_STOMP_FACTORY, StompService } from './stomp.service';

/** A JWT whose payload only says when it expires, `seconds` from now. */
function jwt(seconds: number): string {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds }));
  return `e30.${payload.replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')}.sig`;
}

const ADVENTURE = '0B5E5C3A-1F2D-4E6B-9A7C-8D9E0F1A2B3C';
const TOPIC = '/topic/adventure/chat/0b5e5c3a-1f2d-4e6b-9a7c-8d9e0f1a2b3c';

/** Stands in for the browser WebSocket: records what is sent, and is driven by the test. */
class FakeSocket extends EventTarget {
  static instances: FakeSocket[] = [];
  readyState = 0;
  binaryType = 'blob';
  protocol = '';
  readonly sent: string[] = [];
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;

  constructor(
    readonly url: string,
    readonly protocols?: string | string[],
  ) {
    super();
    FakeSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.closeWith(1000);
  }

  open(): void {
    this.readyState = 1;
    this.onopen?.(new Event('open'));
  }

  receive(frame: string): void {
    this.onmessage?.({ data: frame });
  }

  closeWith(code: number): void {
    if (this.readyState === 3) {
      return;
    }
    this.readyState = 3;
    const event = new CloseEvent('close', { code });
    this.dispatchEvent(event);
    this.onclose?.(event);
  }
}

/**
 * Stands in for RxStomp: the test drives the connection (`connect`, `drop`), and every
 * subscription sends a SUBSCRIBE on each connection, as rx-stomp does.
 */
class FakeRxStomp {
  readonly connectionState$ = new BehaviorSubject(RxStompState.CLOSED);
  readonly stompErrors$ = new Subject<IFrame>();
  config: RxStompConfig = {};
  active = false;
  readonly activations = vi.fn();
  readonly subscribes: { destination: string; id: string }[] = [];
  readonly unsubscribes: string[] = [];
  private readonly frames = new Subject<{ destination: string; body: string }>();

  configure(config: RxStompConfig): void {
    this.config = { ...this.config, ...config };
  }

  activate(): void {
    this.activations();
    this.active = true;
    this.connectionState$.next(RxStompState.CONNECTING);
  }

  async deactivate(): Promise<void> {
    this.active = false;
    this.connectionState$.next(RxStompState.CLOSING);
    this.connectionState$.next(RxStompState.CLOSED);
  }

  /** One connection attempt: `beforeConnect`, then open unless it deactivated the client. */
  async connect(): Promise<void> {
    this.connectionState$.next(RxStompState.CONNECTING);
    await this.config.beforeConnect?.(this as unknown as RxStomp);
    if (this.active) {
      this.connectionState$.next(RxStompState.OPEN);
    }
  }

  /** The socket is lost; the client stays active and will try again. */
  drop(): void {
    this.connectionState$.next(RxStompState.CLOSED);
  }

  deliver(destination: string, body: object): void {
    this.deliverRaw(destination, JSON.stringify(body));
  }

  deliverRaw(destination: string, body: string): void {
    this.frames.next({ destination, body });
  }

  watch(params: IWatchParams): Observable<IMessage> {
    return new Observable<IMessage>((subscriber) => {
      let id = '';
      const opened = this.connectionState$
        .pipe(filter((state) => state === RxStompState.OPEN))
        .subscribe(() => {
          const headers =
            typeof params.subHeaders === 'function' ? params.subHeaders() : params.subHeaders;
          id = String(headers?.['id']);
          this.subscribes.push({ destination: params.destination!, id });
        });
      const frames = this.frames
        .pipe(filter((frame) => frame.destination === params.destination))
        .subscribe((frame) => subscriber.next({ body: frame.body } as IMessage));
      return () => {
        opened.unsubscribe();
        frames.unsubscribe();
        this.unsubscribes.push(id);
      };
    });
  }
}

describe('StompService', () => {
  let tokens: TokenStore;
  let auth: { refreshAccessToken: ReturnType<typeof vi.fn>; sessionEnded: Subject<void> };

  function setup(client?: FakeRxStomp): StompService {
    auth = { refreshAccessToken: vi.fn(() => of('fresh')), sessionEnded: new Subject<void>() };
    TestBed.configureTestingModule({
      providers: [
        provideApi(),
        { provide: AuthService, useValue: auth },
        ...(client ? [{ provide: RX_STOMP_FACTORY, useValue: () => client }] : []),
      ],
    });
    tokens = TestBed.inject(TokenStore);
    return TestBed.inject(StompService);
  }

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    FakeSocket.instances = [];
  });

  it('subscribes to the lowercase chat topic with the bearer header', async () => {
    vi.stubGlobal('WebSocket', FakeSocket);
    const stomp = setup();
    const token = jwt(600);
    tokens.save({ token });
    const received: MessageDto[] = [];

    const watching = stomp.watchChat(ADVENTURE).subscribe((message) => received.push(message));
    const socket = await vi.waitFor(() => FakeSocket.instances[0] ?? Promise.reject());
    expect(socket.url).toBe(`ws://${location.host}/dndgate-api/ws/websocket`);

    socket.open();
    expect(socket.sent[0]).toMatch(/^CONNECT\n/);
    expect(socket.sent[0]).toContain(`\nAuthorization:Bearer ${token}\n`);
    expect(auth.refreshAccessToken).not.toHaveBeenCalled();

    socket.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
    const subscribe = socket.sent.find((frame) => frame.startsWith('SUBSCRIBE\n'))!;
    expect(subscribe).toContain(`\ndestination:${TOPIC}\n`);
    const id = /\nid:([^\n]+)\n/.exec(subscribe)![1];
    expect(stomp.connection()).toBe('open');

    const message = {
      id: 'm1',
      adventureId: ADVENTURE.toLowerCase(),
      senderId: 's1',
      content: 'Bonjour',
      sendDate: '2026-10-23T10:00:00Z',
    };
    socket.receive(
      `MESSAGE\ndestination:${TOPIC}\nsubscription:${id}\nmessage-id:1\n\n${JSON.stringify(message)}\0`,
    );
    expect(received).toEqual([message]);

    watching.unsubscribe();
    expect(socket.sent).toContainEqual(expect.stringContaining(`UNSUBSCRIBE\nid:${id}\n`));
    // Nothing watched any more: the connection is closed.
    expect(socket.sent.at(-1)).toMatch(/^DISCONNECT\n/);
  });

  it('waits longer after each failed attempt, up to a minute', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('WebSocket', FakeSocket);
    const stomp = setup();
    tokens.save({ token: jwt(3600) });
    stomp.watchChat(ADVENTURE).subscribe();
    await vi.advanceTimersByTimeAsync(0);
    expect(FakeSocket.instances.length).toBe(1);

    const waits: number[] = [];
    for (let attempt = 1; attempt <= 6; attempt++) {
      FakeSocket.instances.at(-1)!.closeWith(1006);
      let waited = 0;
      while (FakeSocket.instances.length === attempt && waited < 120_000) {
        await vi.advanceTimersByTimeAsync(1000);
        waited += 1000;
      }
      waits.push(waited);
    }

    expect(waits).toEqual([5000, 10_000, 20_000, 40_000, 60_000, 60_000]);
  });

  it('uses a fresh subscription id on resubscribe', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });

    const first = stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();
    client.drop();
    await client.connect();
    first.unsubscribe();
    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    expect(client.subscribes.map((s) => s.destination)).toEqual([TOPIC, TOPIC, TOPIC]);
    const ids = client.subscribes.map((s) => s.id);
    expect(new Set(ids).size).toBe(3);
    expect(client.unsubscribes).toEqual([ids[1]]);
  });

  it('connects lazily and once', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    expect(client.activations).not.toHaveBeenCalled();

    stomp.watchChat('a').subscribe();
    stomp.watchChat('b').subscribe();

    expect(client.activations).toHaveBeenCalledTimes(1);
  });

  it('delivers the messages of the watched adventure', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    const next = firstValueFrom(stomp.watchChat(ADVENTURE));
    await client.connect();

    client.deliver(TOPIC, { id: 'm1', content: 'Salut' });

    expect(await next).toEqual({ id: 'm1', content: 'Salut' });
  });

  it('skips a frame it cannot read and keeps listening', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    const received: MessageDto[] = [];
    const failed = vi.fn();
    stomp.watchChat(ADVENTURE).subscribe({ next: (m) => received.push(m), error: failed });
    await client.connect();

    client.deliverRaw(TOPIC, 'not json');
    client.deliverRaw(TOPIC, 'null');
    client.deliverRaw(TOPIC, '{"content":"no id"}');
    client.deliver(TOPIC, { id: 'm1', content: 'Salut' });

    expect(received).toEqual([{ id: 'm1', content: 'Salut' }]);
    expect(failed).not.toHaveBeenCalled();
  });

  it('closes the connection once nothing is watched any more', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    const first = stomp.watchChat('a').subscribe();
    const second = stomp.watchChat('b').subscribe();
    await client.connect();

    first.unsubscribe();
    expect(client.active).toBe(true);
    second.unsubscribe();
    expect(client.active).toBe(false);
    expect(stomp.connection()).toBe('idle');

    stomp.reconnect();
    expect(client.activations).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['missing', null],
    ['about to expire', jwt(10)],
    ['unreadable', 'not-a-jwt'],
  ])('refreshes the access token before connecting when it is %s', async (_, token) => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    if (token !== null) {
      tokens.save({ token });
    }

    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    expect(auth.refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(client.config.connectHeaders).toEqual({ Authorization: 'Bearer fresh' });
    expect(stomp.connection()).toBe('open');
  });

  it('stops reconnecting when the refresh fails, until the next watch', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    auth.refreshAccessToken.mockReturnValue(throwError(() => new Error('refused')));

    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    expect(client.active).toBe(false);
    expect(stomp.connection()).toBe('stopped');

    stomp.watchChat(ADVENTURE).subscribe();
    expect(client.activations).toHaveBeenCalledTimes(2);
    expect(stomp.connection()).toBe('connecting');
  });

  it('stops on a policy-violation close (session revoked), not on a plain drop', async () => {
    vi.stubGlobal('WebSocket', FakeSocket);
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    const lost = client.config.webSocketFactory!() as FakeSocket;
    lost.closeWith(1006);
    client.drop();
    expect(client.active).toBe(true);
    expect(stomp.connection()).toBe('lost');

    await client.connect();
    const revoked = client.config.webSocketFactory!() as FakeSocket;
    revoked.closeWith(1008);
    expect(client.active).toBe(false);
    expect(stomp.connection()).toBe('stopped');
  });

  it('stops when the server answers with an ERROR frame', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    client.stompErrors$.next({} as IFrame);

    expect(client.active).toBe(false);
    expect(stomp.connection()).toBe('stopped');
  });

  it('disconnects when the session ends', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    auth.sessionEnded.next();

    expect(client.active).toBe(false);
  });

  it('reconnect starts a stopped client again and leaves a running one alone', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    tokens.save({ token: jwt(600) });
    stomp.watchChat(ADVENTURE).subscribe();
    await client.connect();

    stomp.reconnect();
    expect(client.activations).toHaveBeenCalledTimes(1);

    client.stompErrors$.next({} as IFrame);
    stomp.reconnect();
    expect(client.activations).toHaveBeenCalledTimes(2);
    await client.connect();
    expect(stomp.connection()).toBe('open');
  });

  it('closes the connection when the application goes away', async () => {
    const client = new FakeRxStomp();
    const stomp = setup(client);
    stomp.watchChat(ADVENTURE).subscribe();

    TestBed.resetTestingModule();

    expect(client.active).toBe(false);
  });
});
