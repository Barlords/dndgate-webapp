import { HttpClient } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import { getAdventureById } from '../../../core/api/generated/functions';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/errors/api-problem';

/** `missing`: unknown or private to the reader (404); `failed`: any other error. */
export type AdventureLoadState = 'loading' | 'ready' | 'missing' | 'failed';

/**
 * The adventure the reader is looking at, shared by the shell and its tab pages (provided by
 * `AdventureShell`). It also says what the reader is to it: master, player or visitor.
 */
@Injectable()
export class AdventureContext {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly current = signal<AdventureDto | null>(null);
  private readonly loadState = signal<AdventureLoadState>('loading');
  private id: string | null = null;
  private request: Subscription | null = null;

  readonly adventure = this.current.asReadonly();
  readonly state = this.loadState.asReadonly();

  private readonly me = computed(() => this.auth.account()?.id);

  /** The master has the rights; the creator of an adventure with a vacant master seat has none. */
  readonly isMaster = computed(() => {
    const adventure = this.current();
    const me = this.me();
    return !!adventure && me !== undefined && adventure.creatorId === me && !adventure.masterVacant;
  });
  readonly isPlayer = computed(() => {
    const me = this.me();
    return me !== undefined && (this.current()?.players ?? []).includes(me);
  });
  readonly isParticipant = computed(() => this.isMaster() || this.isPlayer());

  /** Starts showing another adventure: the previous one disappears at once. */
  open(id: string): void {
    this.id = id;
    this.current.set(null);
    this.loadState.set('loading');
    this.fetch();
  }

  /** Fetches the adventure again; the one shown stays until the answer. `done` gets the fresh one. */
  reload(done?: (adventure: AdventureDto) => void): void {
    this.fetch(done);
  }

  /** Takes an adventure the API just returned (after a save). */
  update(adventure: AdventureDto): void {
    this.current.set(adventure);
    this.loadState.set('ready');
  }

  private fetch(done?: (adventure: AdventureDto) => void): void {
    if (this.id === null) {
      return;
    }
    this.request?.unsubscribe();
    this.request = getAdventureById(this.http, this.rootUrl, { id: this.id })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.update(response.body);
          done?.(response.body);
        },
        error: (error: unknown) => {
          // A failed reload keeps what is shown.
          if (this.current() === null) {
            this.loadState.set(toApiProblem(error).status === 404 ? 'missing' : 'failed');
          }
        },
      });
  }
}
