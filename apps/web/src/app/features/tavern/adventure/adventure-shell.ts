import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Loading } from '../../../shared/ui/loading/loading';
import { AdventureContext } from './adventure-context.service';
import { Text } from '../../../core/i18n/dictionary';
import { I18n } from '../../../core/i18n/i18n.service';

interface Tab {
  path: string;
  label: Text;
  /** Visitors of a public adventure only get the tabs that are not reserved. */
  participantsOnly: boolean;
}

export const ADVENTURE_TABS: Tab[] = [
  { path: 'overview', label: (t) => t.tavern.crumbs.overview, participantsOnly: false },
  { path: 'players', label: (t) => t.tavern.crumbs.players, participantsOnly: true },
  { path: 'characters', label: (t) => t.tavern.crumbs.characters, participantsOnly: true },
  { path: 'chronicle', label: (t) => t.tavern.crumbs.chronicle, participantsOnly: true },
  { path: 'gm-notes', label: (t) => t.tavern.crumbs.gmNotes, participantsOnly: true },
  { path: 'planning', label: (t) => t.tavern.crumbs.planning, participantsOnly: false },
  { path: 'messages', label: (t) => t.tavern.crumbs.messages, participantsOnly: true },
  { path: 'master', label: (t) => t.tavern.crumbs.master, participantsOnly: true },
];

/** One adventure: loads it, names the breadcrumb and shows the tabs the reader may use. */
@Component({
  selector: 'dg-adventure-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, RouterOutlet, RouterLink, RouterLinkActive, Parchment, Loading],
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
          <nav class="tabs" [attr.aria-label]="t().tavern.adventure.sections">
            @for (tab of visibleTabs(); track tab.path) {
              <a [routerLink]="tab.path" routerLinkActive="active">{{ tab.label(t()) }}</a>
            }
          </nav>
          <!-- A new outlet per adventure: its pages must not keep the previous one's data. -->
          @for (id of [adventureId()]; track id) {
            <router-outlet />
          }
        }
        @case ('missing') {
          <div class="state">
            <p role="alert">{{ t().tavern.adventure.notFound }}</p>
            <a routerLink="/tavern/adventures">{{ t().tavern.adventure.back }}</a>
          </div>
        }
        @case ('failed') {
          <div class="state">
            <dg-load-error
              [message]="t().tavern.adventure.loadError"
              [problem]="context.problem()"
              (retry)="retry()"
            />
          </div>
        }
        @default {
          <div class="state">
            <dg-loading
              [label]="t().tavern.adventure.loading"
              [message]="t().tavern.adventure.loadingFlavour"
            />
          </div>
        }
      }
    </dg-parchment>
  `,
})
export class AdventureShell {
  protected readonly t = inject(I18n).t;

  protected readonly context = inject(AdventureContext);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private id = '';
  protected readonly adventureId = signal('');

  // The tab shown, as the path of the child route.
  private readonly activeTab = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.route.snapshot.firstChild?.routeConfig?.path),
    ),
  );

  protected readonly visibleTabs = computed(() =>
    ADVENTURE_TABS.filter((tab) => this.context.isParticipant() || !tab.participantsOnly),
  );

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? '';
      this.adventureId.set(this.id);
      this.context.open(this.id);
    });
    effect(() => {
      const name = this.context.adventure()?.name;
      if (name !== undefined) {
        this.breadcrumb.setLabelAt(`/tavern/adventures/${this.id}`, name);
      }
    });
    // Hiding the tabs is not enough: a visitor could type the address of a reserved one.
    effect(() => {
      const reserved = ADVENTURE_TABS.some(
        (tab) => tab.participantsOnly && tab.path === this.activeTab(),
      );
      if (this.context.state() === 'ready' && !this.context.isParticipant() && reserved) {
        void this.router.navigate(['overview'], { relativeTo: this.route, replaceUrl: true });
      }
    });
  }

  protected retry(): void {
    this.context.open(this.id);
  }
}
