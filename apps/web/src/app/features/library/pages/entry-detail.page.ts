import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, Subject, switchMap } from 'rxjs';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { Button } from '../../../shared/ui/button/button';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { EntryDetail, Ruleset, RulesetEntryNotFoundError } from '../rulesets/ruleset.model';
import { DetailBlocks } from './detail-blocks.component';
import { LOADING, LoadState, loadWithRetry } from './load-state';

/** One entry of a section: its name, subtitle and tags over the ruleset's blocks. */
@Component({
  selector: 'dg-entry-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, Spinner, Button, DetailBlocks, RouterLink],
  template: `
    <dg-parchment>
      @if (entry(); as shown) {
        <article class="entry">
          <header class="entry__header">
            <h1 class="entry__title">{{ shown.name }}</h1>
            @if (shown.subtitle) {
              <p class="entry__subtitle">{{ shown.subtitle }}</p>
            }
            @if (shown.tags.length > 0) {
              <ul class="entry__tags">
                @for (tag of shown.tags; track $index) {
                  <li class="entry__tag">{{ tag }}</li>
                }
              </ul>
            }
          </header>
          @if (spell()) {
            <!-- Extension point: the "add to a spell book" action of a spell goes here. -->
          }
          <dg-detail-blocks [rulesetId]="ruleset().id" [blocks]="shown.blocks" />
        </article>
      } @else if (notFound()) {
        <div class="entry__state">
          <p role="alert">Entrée introuvable.</p>
          <a [routerLink]="['/library', ruleset().id, sectionId()]">Retour à la rubrique</a>
        </div>
      } @else if (failed()) {
        <div class="entry__state">
          <p role="alert">Impossible de charger cette entrée.</p>
          <dg-button variant="ghost" (click)="retry()">Réessayer</dg-button>
        </div>
      } @else {
        <div class="entry__state"><dg-spinner label="Chargement de l'entrée" /></div>
      }
    </dg-parchment>
  `,
  styles: `
    .entry__header {
      text-align: center;
    }

    .entry__title {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-2xl);
      letter-spacing: 0.12em;
      color: var(--dg-title-color);
    }

    .entry__subtitle {
      margin: var(--dg-space-1) 0 0;
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    .entry__tags {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--dg-space-2);
      margin: var(--dg-space-3) 0 0;
      padding: 0;
      list-style: none;
    }

    .entry__tag {
      padding: 0.1rem 0.7rem;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
      border: 1px solid var(--dg-parchment-border);
      border-radius: 999px;
    }

    .entry__state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      margin: var(--dg-space-6) 0;
      text-align: center;

      p {
        margin: 0;
        color: var(--dg-blood);
        font-weight: 600;
      }
    }
  `,
})
export class EntryDetailPage {
  private readonly registry = inject(RulesetRegistry);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly route = inject(ActivatedRoute);

  private readonly retries = new Subject<void>();
  private readonly loaded = signal<LoadState<EntryDetail>>(LOADING);
  protected readonly entry = computed(() => this.loaded().value);
  protected readonly failed = computed(() => this.loaded().failed);
  protected readonly notFound = computed(
    () => this.loaded().error instanceof RulesetEntryNotFoundError,
  );

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  // The routes only match a registered ruleset.
  protected readonly ruleset = computed<Ruleset>(() =>
    this.registry.get(this.params().get('rulesetId') ?? '')!,
  );
  protected readonly sectionId = computed(() => this.params().get('sectionId') ?? '');
  /** Whether the entry is one of the spells of its ruleset (which spell books hold). */
  protected readonly spell = computed(
    () => this.params().get('sectionId') === this.ruleset().spellSectionId,
  );

  constructor() {
    // A response arriving after the reader has left (another entry or another page) is dropped.
    this.route.paramMap
      .pipe(
        map((params) => ({
          ruleset: this.registry.get(params.get('rulesetId') ?? '')!,
          sectionId: params.get('sectionId') ?? '',
          index: params.get('index') ?? '',
        })),
        switchMap(({ ruleset, sectionId, index }) =>
          loadWithRetry(() => ruleset.detail(sectionId, index), this.retries),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((loaded) => {
        this.loaded.set(loaded);
        if (loaded.value !== null) {
          this.breadcrumb.setLast(loaded.value.name);
        }
      });
  }

  protected retry(): void {
    this.retries.next();
  }
}
