import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { map, Subject } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import { getMyGameCharacters } from '../../../core/api/generated/functions';
import { GameCharacterDto } from '../../../core/api/generated/models/game-character-dto';
import { toApiProblem } from '../../../core/errors/api-problem';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Loading } from '../../../shared/ui/loading/loading';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { ViewMode } from '../../../shared/ui/view-toggle/view-toggle';
import { LOADING, LoadState, loadWithRetry } from '../../library/pages/load-state';
import { CharacterPortrait } from '../adventure/characters/character-portrait';
import { ListHeader } from '../list-header';

/**
 * « Mes personnages »: every character of the reader, those of their adventures and those kept
 * outside any, as a table or as portrait cards.
 */
@Component({
  selector: 'dg-my-characters-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Loading, Parchment, RouterLink, CharacterPortrait, ListHeader],
  styleUrl: '../tavern-page.scss',
  styles: `
    .portraits {
      grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
    }

    .card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      height: 100%;
      padding: var(--dg-space-4);
      text-align: center;
      color: var(--dg-ink);
      text-decoration: none;
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow:
        inset 0 0 0 3px var(--dg-vellum),
        inset 0 0 0 4px #c9a24a66;
      transition:
        transform var(--dg-transition),
        box-shadow var(--dg-transition);
    }

    .card:hover,
    .card:focus-visible {
      transform: translateY(-2px);
      box-shadow:
        inset 0 0 0 3px var(--dg-vellum),
        inset 0 0 0 4px var(--dg-gold),
        0 4px 10px #17100a40;
    }

    .name {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-lg);
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--dg-blood);
    }

    .kind,
    .where {
      margin: 0;
    }

    .where {
      font-size: var(--dg-text-sm);
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    @media (prefers-reduced-motion: reduce) {
      .card {
        transition: none;
      }

      .card:hover,
      .card:focus-visible {
        transform: none;
      }
    }
  `,
  template: `
    <dg-parchment>
      <dg-list-header
        heading="Mes personnages"
        storageKey="dg.tavern.characters.view"
        createLabel="Créer un personnage"
        [(mode)]="view"
        (create)="create()"
      />
      @if (loaded().value !== null) {
        @if (characters().length > 0) {
          @if (view() === 'table') {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Nom</th>
                    <th scope="col">Race</th>
                    <th scope="col">Classe</th>
                    <th scope="col">Aventure</th>
                  </tr>
                </thead>
                <tbody>
                  @for (character of characters(); track character.id) {
                    <tr>
                      <td>
                        <a [routerLink]="['/tavern/characters', character.id]">{{
                          character.name
                        }}</a>
                      </td>
                      <td>{{ character.race }}</td>
                      <td>{{ character.martialClass }}</td>
                      <td>
                        @if (character.adventureId) {
                          <a [routerLink]="['/tavern/adventures', character.adventureId]">
                            Voir l’aventure
                          </a>
                        } @else {
                          <span class="muted">Hors aventure</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <ul class="cards portraits">
              @for (character of characters(); track character.id) {
                <li>
                  <a class="card" [routerLink]="['/tavern/characters', character.id]">
                    <dg-character-portrait
                      [characterId]="character.id!"
                      [name]="character.name ?? ''"
                    />
                    <span class="name">{{ character.name }}</span>
                    <p class="kind">{{ character.race }} · {{ character.martialClass }}</p>
                    <p class="where">
                      {{ character.adventureId ? 'Dans une aventure' : 'Hors aventure' }}
                    </p>
                  </a>
                </li>
              }
            </ul>
          }
        } @else {
          <p>Tu n’as pas encore de personnage.</p>
        }
      } @else if (loaded().failed) {
        <div class="state">
          <dg-load-error
            message="Impossible de charger tes personnages."
            [problem]="problem()"
            (retry)="retries.next()"
          />
        </div>
      } @else {
        <div class="state">
          <dg-loading label="Chargement des personnages" message="Les héros se rassemblent…" />
        </div>
      }
    </dg-parchment>
  `,
})
export class MyCharactersPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly router = inject(Router);

  protected readonly view = signal<ViewMode>('cards');
  protected readonly retries = new Subject<void>();
  protected readonly loaded = signal<LoadState<GameCharacterDto[]>>(LOADING);
  protected readonly characters = signal<GameCharacterDto[]>([]);
  protected readonly problem = computed(() => toApiProblem(this.loaded().error));

  constructor() {
    loadWithRetry(
      () => getMyGameCharacters(this.http, this.rootUrl).pipe(map((response) => response.body)),
      this.retries,
    )
      .pipe(takeUntilDestroyed())
      .subscribe((state) => {
        this.loaded.set(state);
        if (state.value !== null) {
          this.characters.set(
            [...state.value].sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '', 'fr')),
          );
        }
      });
  }

  protected create(): void {
    void this.router.navigate(['/tavern/characters/new']);
  }
}
