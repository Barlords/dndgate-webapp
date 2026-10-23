import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RxStomp, RxStompState, Versions } from '@stomp/rx-stomp';
import { defer, firstValueFrom, map, Observable } from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { MessageDto } from '../api/generated/models/message-dto';
import { AuthService } from '../auth/auth.service';
import { TokenStore } from '../auth/token-store';

/**
 * `idle`: nothing watched yet; `lost`: the connection dropped and is retried every 5 s;
 * `stopped`: no more retries (session revoked, refused by the server, or no token to present)
 * until something is watched again or `reconnect()` is called.
 */
export type ChatConnection = 'idle' | 'connecting' | 'open' | 'lost' | 'stopped';

/** Makes the STOMP client; tests swap it for a fake so that no socket is ever opened. */
export const RX_STOMP_FACTORY = new InjectionToken<() => RxStomp>('RX_STOMP_FACTORY', {
  providedIn: 'root',
  factory: () => () => new RxStomp(),
});

const RECONNECT_DELAY_MS = 5000;
/** The close code the API uses when it revokes the session of an open connection. */
const POLICY_VIOLATION = 1008;
/** An access token this close to its expiry is refreshed before it is presented at CONNECT. */
const EXPIRY_MARGIN_S = 30;

/** Whether the access token expires within the margin; a token whose expiry cannot be read does. */
function expiresSoon(token: string): boolean {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const exp = (JSON.parse(atob(payload)) as { exp?: unknown }).exp;
    return typeof exp !== 'number' || exp - Date.now() / 1000 < EXPIRY_MARGIN_S;
  } catch {
    return true;
  }
}

/**
 * The single STOMP connection to the API, opened on the first watch. The token goes in the
 * `Authorization` header of CONNECT; the client never publishes, it only subscribes.
 */
@Injectable({ providedIn: 'root' })
export class StompService {
  private readonly auth = inject(AuthService);
  private readonly tokens = inject(TokenStore);
  private readonly client = inject(RX_STOMP_FACTORY)();
  private readonly url = socketUrl(inject(ApiConfiguration).rootUrl, inject(DOCUMENT).baseURI);

  private readonly state = signal<ChatConnection>('idle');
  readonly connection = this.state.asReadonly();

  private halted = false;
  private lastSubscriptionId = 0;

  constructor() {
    this.client.configure({
      webSocketFactory: () => this.openSocket(),
      reconnectDelay: RECONNECT_DELAY_MS,
      beforeConnect: () => this.authenticate(),
    });
    this.client.connectionState$.pipe(takeUntilDestroyed()).subscribe((state) => this.track(state));
    // An ERROR frame means the server refused us (bad token, refused subscription): retrying the
    // same thing every 5 s would not help.
    this.client.stompErrors$.pipe(takeUntilDestroyed()).subscribe(() => this.halt());
    this.auth.sessionEnded.pipe(takeUntilDestroyed()).subscribe(() => this.halt());
    inject(DestroyRef).onDestroy(() => void this.client.deactivate());
  }

  /**
   * The messages posted in an adventure, as the server broadcasts them. Each subscription (and
   * each resubscription after a reconnection) uses a new id: the API may refuse a reused one.
   */
  watchChat(adventureId: string): Observable<MessageDto> {
    const destination = `/topic/adventure/chat/${adventureId.toLowerCase()}`;
    return defer(() => {
      this.reconnect();
      return this.client.watch({
        destination,
        subHeaders: () => ({ id: `chat-${++this.lastSubscriptionId}` }),
      });
    }).pipe(map((message) => JSON.parse(message.body) as MessageDto));
  }

  /** Starts the connection again if it was stopped; does nothing while it runs or retries. */
  reconnect(): void {
    if (!this.client.active) {
      this.halted = false;
      this.state.set('connecting');
      this.client.activate();
    }
  }

  private track(state: RxStompState): void {
    if (state === RxStompState.OPEN) {
      this.state.set('open');
    } else if (state === RxStompState.CONNECTING) {
      this.state.update((current) => (current === 'lost' ? 'lost' : 'connecting'));
    } else if (this.halted) {
      this.state.set('stopped');
    } else {
      this.state.update((current) => (current === 'idle' ? 'idle' : 'lost'));
    }
  }

  private halt(): void {
    this.halted = true;
    void this.client.deactivate();
  }

  /** Puts a valid access token in the CONNECT headers; without one, stops trying. */
  private async authenticate(): Promise<void> {
    let token = this.tokens.accessToken();
    try {
      if (token === null || expiresSoon(token)) {
        token = await firstValueFrom(this.auth.refreshAccessToken());
      }
    } catch {
      // Deactivated here, the client gives up this attempt and the following ones.
      this.halt();
      return;
    }
    this.client.configure({ connectHeaders: { Authorization: `Bearer ${token}` } });
  }

  private openSocket(): WebSocket {
    const socket = new WebSocket(this.url, Versions.default.protocolVersions());
    socket.addEventListener('close', (event) => {
      if (event.code === POLICY_VIOLATION) {
        this.halt();
      }
    });
    return socket;
  }
}

/** The raw WebSocket behind the API's SockJS endpoint: `ws(s)://<host><base>/ws/websocket`. */
function socketUrl(rootUrl: string, baseUri: string): string {
  const url = new URL(`${rootUrl}/ws/websocket`, baseUri);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}
