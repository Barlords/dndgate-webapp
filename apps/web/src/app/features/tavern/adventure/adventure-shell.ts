import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { Button } from '../../../shared/ui/button/button';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { AdventureContext } from './adventure-context.service';

interface Tab {
  path: string;
  label: string;
  /** Visitors of a public adventure only get the tabs that are not reserved. */
  participantsOnly: boolean;
}

export const ADVENTURE_TABS: Tab[] = [
  { path: 'overview', label: 'Informations', participantsOnly: false },
  { path: 'players', label: 'Joueurs', participantsOnly: true },
  { path: 'characters', label: 'Personnages', participantsOnly: true },
  { path: 'chronicle', label: 'Chronique', participantsOnly: true },
  { path: 'gm-notes', label: 'Notes du MJ', participantsOnly: true },
  { path: 'planning', label: 'Planning', participantsOnly: false },
  { path: 'messages', label: 'Messagerie', participantsOnly: true },
  { path: 'master', label: 'MJ', participantsOnly: true },
];

/** One adventure: loads it, names the breadcrumb and shows the tabs the reader may use. */
@Component({
  selector: 'dg-adventure-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Parchment, Spinner, Button],
  providers: [AdventureContext],
  styleUrl: '../tavern-page.scss',
  styles: `
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
      margin: 0 0 var(--dg-space-4);
      padding: 0 0 var(--dg-space-2);
      border-bottom: 1px solid var(--dg-parchment-border);
    }

    .tabs a {
      padding: var(--dg-space-1) var(--dg-space-3);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--dg-ink);
      text-decoration: none;
      border: 1px solid transparent;
      border-radius: var(--dg-radius);
    }

    .tabs a.active {
      border-color: var(--dg-parchment-border);
      background: var(--dg-vellum);
      color: var(--dg-blood);
    }
  `,
  template: `
    <dg-parchment>
      @switch (context.state()) {
        @case ('ready') {
          <nav class="tabs" aria-label="Sections de l’aventure">
            @for (tab of visibleTabs(); track tab.path) {
              <a [routerLink]="tab.path" routerLinkActive="active">{{ tab.label }}</a>
            }
          </nav>
          <router-outlet />
        }
        @case ('missing') {
          <div class="state">
            <p role="alert">Cette aventure est introuvable ou privée.</p>
            <a routerLink="/tavern/quests">Retour au tableau des quêtes</a>
          </div>
        }
        @case ('failed') {
          <div class="state">
            <p role="alert">Impossible de charger l’aventure.</p>
            <dg-button variant="ghost" (click)="retry()">Réessayer</dg-button>
          </div>
        }
        @default {
          <div class="state"><dg-spinner label="Chargement de l’aventure" /></div>
        }
      }
    </dg-parchment>
  `,
})
export class AdventureShell {
  protected readonly context = inject(AdventureContext);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly route = inject(ActivatedRoute);
  private id = '';

  protected readonly visibleTabs = computed(() =>
    ADVENTURE_TABS.filter((tab) => this.context.isParticipant() || !tab.participantsOnly),
  );

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? '';
      this.context.open(this.id);
    });
    effect(() => {
      const name = this.context.adventure()?.name;
      if (name !== undefined) {
        this.breadcrumb.setLabelAt(`/tavern/adventures/${this.id}`, name);
      }
    });
  }

  protected retry(): void {
    this.context.open(this.id);
  }
}
