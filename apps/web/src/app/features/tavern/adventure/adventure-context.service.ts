import { HttpClient } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import { getAdventureById, getAdventureEntitlements } from '../../../core/api/generated/functions';
import { AdventureEntitlementsDto } from '../../../core/api/generated/models/adventure-entitlements-dto';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../../core/errors/api-problem';

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
  private entitlementsRequest: Subscription | null = null;
  private readonly offer = signal<AdventureEntitlementsDto | null>(null);
  /** What the reader may do here (tools and limits); null for a visitor, while loading or if unknown. */
  readonly entitlements = this.offer.asReadonly();

  readonly adventure = this.current.asReadonly();
  readonly state = this.loadState.asReadonly();
  private readonly failure = signal<ApiProblem | null>(null);
  /** Why the adventure failed to load, when it did. */
  readonly problem = this.failure.asReadonly();

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
    this.offer.set(null);
    this.entitlementsRequest?.unsubscribe();
    this.loadState.set('loading');
    this.fetch();
  }

  /** Whether a tool is available to the reader in this adventure; false while it is unknown. */
  has(feature: string): boolean {
    return this.offer()?.features?.includes(feature) ?? false;
  }

  /** Usage of one of the adventure's limits, or null when unknown. */
  usage(limit: string): { used: number; max: number } | null {
    const usage = this.offer()?.limits?.[limit];
    return usage?.used === undefined || usage.max === undefined
      ? null
      : { used: usage.used, max: usage.max };
  }

  /** Asks the entitlements again; a failure keeps what is known. */
  reloadEntitlements(): void {
    if (this.id === null) {
      return;
    }
    this.entitlementsRequest?.unsubscribe();
    this.entitlementsRequest = getAdventureEntitlements(this.http, this.rootUrl, { id: this.id })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.offer.set(response.body),
        error: () => undefined,
      });
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
          // Only a participant has entitlements here; a visitor's request would be refused.
          if (this.isParticipant() && this.offer() === null) {
            this.reloadEntitlements();
          }
          done?.(response.body);
        },
        error: (error: unknown) => {
          // A failed reload keeps what is shown.
          if (this.current() === null) {
            const problem = toApiProblem(error);
            this.failure.set(problem);
            this.loadState.set(problem.status === 404 ? 'missing' : 'failed');
          }
        },
      });
  }
}
