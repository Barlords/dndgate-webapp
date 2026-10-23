import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { map, Subject } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import { getMyMagicItems } from '../../../core/api/generated/functions';
import { MagicItemDto } from '../../../core/api/generated/models/magic-item-dto';
import { toApiProblem } from '../../../core/errors/api-problem';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Loading } from '../../../shared/ui/loading/loading';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { ViewMode } from '../../../shared/ui/view-toggle/view-toggle';
import { LOADING, LoadState, loadWithRetry } from '../../library/pages/load-state';
import { ListHeader } from '../list-header';
import { rarityLabel, typeLabel } from './magic-item-labels';

const EXCERPT_LENGTH = 140;

/** « Mes objets magiques »: the reader's magic items (sorted by name by the API), as a table or cards. */
@Component({
  selector: 'dg-my-magic-items-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Loading, Parchment, RouterLink, ListHeader],
  styleUrl: '../tavern-page.scss',
  styles: `
    .item {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-2);
      height: 100%;
      padding: var(--dg-space-4);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      background: var(--dg-vellum);
      box-shadow: inset 0 0 0 3px var(--dg-parchment-shadow);
    }

    .item__seals {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
    }

    .seal {
      padding: 0.1rem 0.6rem;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--dg-vellum);
      background: var(--dg-ink-muted);
      border-radius: 999px;
    }

    // The rarer, the warmer.
    .seal--uncommon {
      background: #3f5a2a;
    }

    .seal--rare {
      background: #2a4a6b;
    }

    .seal--very_rare {
      background: #5a2a6b;
    }

    .seal--legendary {
      background: var(--dg-gold-deep);
    }

    .seal--artifact {
      background: var(--dg-blood);
    }

    .seal--attunement {
      color: var(--dg-ink);
      background: var(--dg-gold-light);
    }

    .item__name {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
    }

    .item__type {
      margin: 0;
      font-size: var(--dg-text-sm);
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    .item__excerpt {
      flex: 1;
      margin: 0;
      color: var(--dg-ink);
    }
  `,
  template: `
    <dg-parchment>
      <dg-list-header
        heading="Mes objets magiques"
        storageKey="dg.tavern.magic-items.view"
        createLabel="Créer un objet magique"
        [(mode)]="view"
        (create)="create()"
      />
      @if (loaded().value !== null) {
        @if (items().length > 0) {
          @if (view() === 'table') {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Nom</th>
                    <th scope="col">Type</th>
                    <th scope="col">Rareté</th>
                    <th scope="col">Harmonisation</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of items(); track item.id) {
                    <tr>
                      <td>
                        <a [routerLink]="['/tavern/magic-items', item.id]">{{ item.name }}</a>
                      </td>
                      <td>{{ type(item) }}</td>
                      <td>{{ rarity(item) }}</td>
                      <td>{{ item.requiresAttunement ? 'Requise' : '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <ul class="cards">
              @for (item of items(); track item.id) {
                <li>
                  <article class="item">
                    <div class="item__seals">
                      <span class="seal seal--{{ (item.rarity ?? '').toLowerCase() }}">{{
                        rarity(item)
                      }}</span>
                      @if (item.requiresAttunement) {
                        <span class="seal seal--attunement">Harmonisation</span>
                      }
                    </div>
                    <a class="item__name" [routerLink]="['/tavern/magic-items', item.id]">{{
                      item.name
                    }}</a>
                    <p class="item__type">{{ type(item) }}</p>
                    <p class="item__excerpt">{{ excerpt(item) }}</p>
                  </article>
                </li>
              }
            </ul>
          }
        } @else {
          <p>Tu n’as pas encore d’objet magique.</p>
        }
      } @else if (loaded().failed) {
        <div class="state">
          <dg-load-error
            message="Impossible de charger tes objets magiques."
            [problem]="problem()"
            (retry)="retries.next()"
          />
        </div>
      } @else {
        <div class="state">
          <dg-loading label="Chargement des objets magiques" message="Inventaire du coffre…" />
        </div>
      }
    </dg-parchment>
  `,
})
export class MyMagicItemsPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly router = inject(Router);

  protected readonly view = signal<ViewMode>('cards');
  protected readonly retries = new Subject<void>();
  protected readonly loaded = signal<LoadState<MagicItemDto[]>>(LOADING);
  protected readonly items = computed(() => this.loaded().value ?? []);
  protected readonly problem = computed(() => toApiProblem(this.loaded().error));

  constructor() {
    loadWithRetry(
      () => getMyMagicItems(this.http, this.rootUrl).pipe(map((response) => response.body)),
      this.retries,
    )
      .pipe(takeUntilDestroyed())
      .subscribe((state) => this.loaded.set(state));
  }

  protected type(item: MagicItemDto): string {
    return typeLabel(item.type);
  }

  protected rarity(item: MagicItemDto): string {
    return rarityLabel(item.rarity);
  }

  protected excerpt(item: MagicItemDto): string {
    const text = (item.description ?? '').trim();
    return text.length > EXCERPT_LENGTH ? `${text.slice(0, EXCERPT_LENGTH).trimEnd()}…` : text;
  }

  protected create(): void {
    void this.router.navigate(['/tavern/magic-items/new']);
  }
}
