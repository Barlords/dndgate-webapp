import { HttpClient } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { map, startWith } from 'rxjs';
import { AccountDirectory } from '../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import { getMessageByAdventureId, sendMessage } from '../../../core/api/generated/functions';
import { MessageDto } from '../../../core/api/generated/models/message-dto';
import { PageDtoMessageDto } from '../../../core/api/generated/models/page-dto-message-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { StompService } from '../../../core/realtime/stomp.service';
import { Button } from '../../../shared/ui/button/button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { Loading } from '../../../shared/ui/loading/loading';
import { AdventureContext } from './adventure-context.service';
import { I18n } from '../../../core/i18n/i18n.service';

export const MESSAGES_PAGE_SIZE = 20;
export const MESSAGE_MAX = 5000;
/** After an outage, at most this many pages are fetched to join up with the messages shown. */
export const MAX_CATCH_UP_PAGES = 5;
/** Closer than this to the bottom of the log, the reader follows the conversation. */
const NEAR_BOTTOM_PX = 48;

const TIME_FORMAT: Intl.DateTimeFormatOptions = {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
};

/** Where the log scrolls once new messages are on screen. */
type Scroll = 'bottom' | 'follow' | 'keep';

function notBlank(control: AbstractControl<string>) {
  return control.value.trim() === '' ? { required: true } : null;
}

/** How many messages are older than the last one of page `page`, as the API counted them. */
function olderThan(body: PageDtoMessageDto, page: number): number {
  const seen = page * MESSAGES_PAGE_SIZE + (body.content?.length ?? 0);
  return Math.max(0, (body.totalElements ?? 0) - seen);
}

function byDate(a: MessageDto, b: MessageDto): number {
  return Date.parse(a.sendDate) - Date.parse(b.sendDate) || a.id.localeCompare(b.id);
}

/**
 * « Messagerie »: the adventure's conversation. The history comes page by page, newest first,
 * and is shown oldest first; new messages arrive through STOMP. A message the reader sends comes
 * back both in the answer and on the topic: messages are kept once per id.
 */
@Component({
  selector: 'dg-messages-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, ReactiveFormsModule, Button, FormField, Loading],
  styleUrl: '../tavern-page.scss',
  styles: `
    .connection {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-3);
      margin: 0 0 var(--dg-space-3);
      padding: var(--dg-space-2) var(--dg-space-3);
      color: var(--dg-blood);
      font-weight: 600;
      background: var(--dg-vellum);
      border: 1px solid var(--dg-blood);
      border-radius: var(--dg-radius);
    }

    .connection p {
      margin: 0;
    }

    .log {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-3);
      height: min(60vh, 32rem);
      margin: 0 0 var(--dg-space-4);
      padding: var(--dg-space-3);
      overflow-y: auto;
      background: linear-gradient(180deg, var(--dg-parchment-0), var(--dg-parchment-1));
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow: inset 0 0 12px var(--dg-parchment-shadow);
    }

    .older {
      align-self: center;
    }

    .empty {
      margin: auto;
      color: var(--dg-ink-muted);
      font-style: italic;
    }

    ol {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .message {
      align-self: flex-start;
      max-width: min(80%, 40rem);
      padding: var(--dg-space-2) var(--dg-space-3);
      color: var(--dg-ink);
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-shadow);
      border-radius: var(--dg-radius);
    }

    .message.mine {
      align-self: flex-end;
      background: color-mix(in srgb, var(--dg-gold-light) 45%, var(--dg-vellum));
      border-color: var(--dg-gold);
    }

    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
      margin: 0 0 var(--dg-space-1);
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }

    .sender {
      font-weight: 700;
      color: var(--dg-ink);
    }

    .content {
      margin: 0;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    textarea {
      min-height: 4.5rem;
      resize: vertical;
    }

    .composer-foot {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--dg-space-3);
      margin-top: calc(var(--dg-space-2) * -1);
    }

    .hint {
      margin: 0;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }

    .problem {
      margin: var(--dg-space-3) 0 0;
      color: var(--dg-blood);
      font-weight: 600;
    }
  `,
  template: `
    <h1>{{ t().tavern.messages.title }}</h1>
    @switch (connection()) {
      @case ('lost') {
        <div class="connection" role="status">
          <p>{{ t().tavern.messages.lost }}</p>
        </div>
      }
      @case ('stopped') {
        <div class="connection" role="status">
          <p>{{ t().tavern.messages.stopped }}</p>
          <dg-button variant="ghost" (click)="reconnect()">{{
            t().tavern.messages.reconnect
          }}</dg-button>
        </div>
      }
    }
    @switch (state()) {
      @case ('ready') {
        <div class="log" #log role="log" [attr.aria-label]="t().tavern.messages.log" tabindex="0">
          @if (hasOlder()) {
            <dg-button
              class="older"
              variant="ghost"
              [loading]="loadingOlder()"
              (click)="loadOlder()"
            >
              {{ t().tavern.messages.older }}
            </dg-button>
          }
          @if (messages().length) {
            <ol>
              @for (message of messages(); track message.id) {
                <li class="message" [class.mine]="message.senderId === me()">
                  <p class="meta">
                    <span class="sender">{{ pseudo(message.senderId) }}</span>
                    <time [attr.datetime]="message.sendDate">{{ time(message.sendDate) }}</time>
                  </p>
                  <p class="content">{{ message.content }}</p>
                </li>
              }
            </ol>
          } @else {
            <p class="empty">{{ t().tavern.messages.empty }}</p>
          }
        </div>

        <form [formGroup]="form" (ngSubmit)="send()" novalidate>
          <dg-form-field [label]="t().tavern.messages.yours" [control]="draft">
            <textarea formControlName="content" rows="3" (keydown)="onKeydown($event)"></textarea>
          </dg-form-field>
          <div class="composer-foot">
            <p class="hint">
              {{ t().tavern.messages.hint(length(), max) }}
            </p>
            <dg-button type="submit" [loading]="sending()">{{
              t().tavern.messages.send
            }}</dg-button>
          </div>
          @if (sendProblem()) {
            <p class="problem" role="alert">{{ sendProblem() }}</p>
          }
        </form>
      }
      @case ('failed') {
        <div class="state">
          <dg-load-error
            [message]="t().tavern.messages.loadError"
            [problem]="loadProblem()"
            (retry)="loadHistory()"
          />
        </div>
      }
      @default {
        <div class="state">
          <dg-loading
            [label]="t().tavern.messages.loading"
            [message]="t().tavern.messages.loadingFlavour"
          />
        </div>
      }
    }
  `,
})
export class MessagesPage {
  protected readonly t = inject(I18n).t;

  private readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly directory = inject(AccountDirectory);
  private readonly notifier = inject(NotifierService);
  private readonly stomp = inject(StompService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly log = viewChild<ElementRef<HTMLElement>>('log');

  protected readonly max = MESSAGE_MAX;
  protected readonly connection = this.stomp.connection;
  protected readonly me = computed(() => this.auth.account()?.id);

  protected readonly state = signal<'loading' | 'ready' | 'failed'>('loading');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  /**
   * Oldest first, once per id. Always an unbroken run of the newest messages: the next older
   * ones start at offset `messages().length` of the API's newest-first list.
   */
  protected readonly messages = signal<MessageDto[]>([]);
  /** How many messages are older than the oldest one shown, as of the last page read. */
  private readonly olderCount = signal(0);
  protected readonly hasOlder = computed(() => this.olderCount() > 0);
  protected readonly loadingOlder = signal(false);

  protected readonly form = new FormGroup({
    content: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(MESSAGE_MAX)],
    }),
  });
  protected readonly draft = this.form.controls.content;
  protected readonly length = toSignal(
    this.draft.valueChanges.pipe(
      startWith(''),
      map((value) => value.length),
    ),
    { initialValue: 0 },
  );
  protected readonly sending = signal(false);
  protected readonly sendProblem = signal<string | null>(null);

  private started = false;

  constructor() {
    effect(() => {
      // Participants only: a visitor is redirected away by the shell, and the API would refuse.
      if (this.context.state() === 'ready' && this.context.isParticipant()) {
        untracked(() => this.start());
      }
    });
    // Back from a lost connection: fetch the newest page for what was said meanwhile.
    let wasOpen = false;
    effect(() => {
      const open = this.connection() === 'open';
      if (open && !wasOpen && untracked(this.state) === 'ready') {
        untracked(() => this.catchUp());
      }
      wasOpen = open;
    });
  }

  protected pseudo(id: string): string {
    return this.directory.pseudo(id)();
  }

  protected time(sendDate: string): string {
    return new Date(sendDate).toLocaleString(this.t().common.locale, TIME_FORMAT);
  }

  protected reconnect(): void {
    this.stomp.reconnect();
  }

  private adventureId(): string {
    return this.context.adventure()!.id!;
  }

  private start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.stomp
      .watchChat(this.adventureId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.show([message], 'follow'));
    this.loadHistory();
  }

  protected loadHistory(): void {
    this.state.set('loading');
    this.fetch(0).subscribe({
      next: ({ body }) => {
        this.olderCount.set(olderThan(body, 0));
        this.state.set('ready');
        this.show(body.content ?? [], 'bottom');
      },
      error: (error: unknown) => {
        this.loadProblem.set(toApiProblem(error));
        this.state.set('failed');
      },
    });
  }

  /**
   * Messages that arrived since the history was read have shifted the pages: the page holding the
   * next older message is found from how many are shown. It may repeat a few, kept once by id.
   */
  protected loadOlder(): void {
    const page = Math.floor(this.messages().length / MESSAGES_PAGE_SIZE);
    this.loadingOlder.set(true);
    this.fetch(page).subscribe({
      next: ({ body }) => {
        this.loadingOlder.set(false);
        this.olderCount.set(olderThan(body, page));
        this.show(body.content ?? [], 'keep');
      },
      error: (error: unknown) => {
        this.loadingOlder.set(false);
        this.notifier.error(toApiProblem(error), this.t().tavern.messages.loading);
      },
    });
  }

  /**
   * Fetches the newest pages until one reaches a message already shown (or the first message
   * ever), so that what was said during the outage leaves no hole. Past the last page allowed,
   * the list restarts from the pages fetched and the reader loads the rest as older messages.
   */
  private catchUp(page = 0, fetched: MessageDto[] = []): void {
    this.fetch(page).subscribe({
      next: ({ body }) => {
        const content = body.content ?? [];
        const shown = new Set(this.messages().map((message) => message.id));
        const all = [...fetched, ...content];
        const older = olderThan(body, page);
        if (content.some((message) => shown.has(message.id)) || older === 0) {
          if (older === 0) {
            this.olderCount.set(0);
          }
          this.show(all, 'follow');
        } else if (page + 1 < MAX_CATCH_UP_PAGES) {
          this.catchUp(page + 1, all);
        } else {
          this.messages.set([]);
          this.olderCount.set(older);
          this.show(all, 'bottom');
        }
      },
      // The reader can still load the history again; nothing to interrupt them for.
      error: () => undefined,
    });
  }

  private fetch(page: number) {
    return getMessageByAdventureId(this.http, this.rootUrl, {
      adventureId: this.adventureId(),
      page,
      size: MESSAGES_PAGE_SIZE,
    }).pipe(takeUntilDestroyed(this.destroyRef));
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      if (this.draft.value.trim() !== '') {
        this.send();
      }
    }
  }

  protected send(): void {
    if (this.sending()) {
      return;
    }
    if (this.draft.invalid) {
      this.draft.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.sendProblem.set(null);
    sendMessage(this.http, this.rootUrl, {
      body: { adventureId: this.adventureId(), content: this.draft.value.trim() },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.sending.set(false);
          this.draft.reset();
          this.show([body], 'bottom');
        },
        error: (error: unknown) => {
          this.sending.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 429) {
            const wait = problem.retryAfterSeconds;
            this.sendProblem.set(
              wait === undefined
                ? this.t().tavern.messages.tooMany
                : this.t().tavern.messages.tooManyFor(wait),
            );
          } else {
            this.notifier.error(problem, this.t().tavern.messages.sending);
          }
        },
      });
  }

  /** Adds messages (once per id, in date order), then scrolls as `scroll` says. */
  private show(incoming: MessageDto[], scroll: Scroll): void {
    const log = this.log()?.nativeElement;
    const fromBottom = log ? log.scrollHeight - log.scrollTop - log.clientHeight : 0;
    const byId = new Map(this.messages().map((message) => [message.id, message]));
    for (const message of incoming) {
      byId.set(message.id, message);
    }
    this.messages.set([...byId.values()].sort(byDate));
    afterNextRender(
      {
        write: () => {
          const element = this.log()?.nativeElement;
          if (!element) {
            return;
          }
          if (scroll === 'bottom' || (scroll === 'follow' && fromBottom <= NEAR_BOTTOM_PX)) {
            element.scrollTop = element.scrollHeight;
          } else if (scroll === 'keep') {
            element.scrollTop = element.scrollHeight - element.clientHeight - fromBottom;
          }
        },
      },
      { injector: this.injector },
    );
  }
}
