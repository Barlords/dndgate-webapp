import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, Subject, switchMap } from 'rxjs';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { toApiProblem } from '../../../core/errors/api-problem';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { DEFAULT_LOADING_MESSAGE, Loading } from '../../../shared/ui/loading/loading';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { AddToSpellbook } from '../spellbooks/add-to-spellbook.component';
import { MySpellBooksStore } from '../spellbooks/my-spellbooks.store';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { EntryDetail, Ruleset, RulesetEntryNotFoundError } from '../rulesets/ruleset.model';
import { DetailBlocks } from './detail-blocks.component';
import { LOADING, LoadState, loadWithRetry } from './load-state';
import { UntranslatedNote } from './untranslated-note.component';

/** One entry of a section: its name, subtitle and tags over the ruleset's blocks. */
@Component({
  selector: 'dg-entry-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LoadError,
    Loading,
    Parchment,
    DetailBlocks,
    RouterLink,
    AddToSpellbook,
    UntranslatedNote,
  ],
  // One read of the reader's spell books per visit.
  providers: [MySpellBooksStore],
  template: `
    <dg-parchment>
      @if (entry(); as shown) {
        <article class="entry">
          <header class="entry__header">
            <div class="entry__heading">
              <h1 class="entry__title">{{ shown.name }}</h1>
              @if (spell()) {
                <dg-add-to-spellbook
                  class="entry__add"
                  [rulesetId]="ruleset().id"
                  [spellIndex]="shown.index"
                />
              }
            </div>
            @if (shown.subtitle) {
              <p class="entry__subtitle">{{ shown.subtitle }}</p>
            }
            @if (ruleset().untranslated(sectionId())) {
              <dg-untranslated-note />
            }
            @if (shown.tags.length > 0) {
              <ul class="entry__tags">
                @for (tag of shown.tags; track $index) {
                  <li class="entry__tag">{{ tag }}</li>
                }
              </ul>
            }
          </header>
          <dg-detail-blocks [rulesetId]="ruleset().id" [blocks]="shown.blocks" />
        </article>
      } @else if (notFound()) {
        <div class="entry__state">
          <p role="alert">Entrée introuvable.</p>
          <a [routerLink]="['/library', ruleset().id, sectionId()]">Retour à la rubrique</a>
        </div>
      } @else if (failed()) {
        <div class="entry__state">
          <dg-load-error
            message="Impossible de charger cette entrée."
            [problem]="loadProblem()"
            (retry)="retry()"
          />
        </div>
      } @else {
        <div class="entry__state">
          <dg-loading label="Chargement de l'entrée" [message]="loadingMessage()" />
        </div>
      }
    </dg-parchment>
  `,
  styles: `
    .entry__header {
      text-align: center;
    }

    // The name stays centred; the button sits right after it and unfolds into the free space.
    .entry__heading {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: center;
      gap: var(--dg-space-3);
    }

    .entry__title {
      grid-column: 2;
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-2xl);
      letter-spacing: 0.12em;
      color: var(--dg-title-color);
    }

    .entry__add {
      justify-self: start;
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
  protected readonly loadProblem = computed(() => toApiProblem(this.loaded().error));
  protected readonly notFound = computed(
    () => this.loaded().error instanceof RulesetEntryNotFoundError,
  );

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  // The routes only match a registered ruleset.
  protected readonly ruleset = computed<Ruleset>(() =>
    this.registry.get(this.params().get('rulesetId') ?? '')!,
  );
  protected readonly sectionId = computed(() => this.params().get('sectionId') ?? '');
  protected readonly loadingMessage = computed(
    () =>
      this.ruleset().sections.find((section) => section.id === this.sectionId())?.loadingMessage ??
      DEFAULT_LOADING_MESSAGE,
  );
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
