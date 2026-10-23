import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Emblem } from '../shared/ui/emblem/emblem';

/** Shown for any address no route knows (and, for now, for pages not built yet). */
@Component({
  selector: 'dg-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Emblem],
  template: `
    <main class="not-found">
      <dg-emblem />
      <h1>Cette page s'est perdue dans les brumes.</h1>
      <a routerLink="/">Retour à l'accueil</a>
    </main>
  `,
  styles: `
    .not-found {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-4);
      min-height: 100vh;
      justify-content: center;
      padding: var(--dg-space-5);
      text-align: center;
      --dg-emblem-size: 4rem;
    }

    h1 {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-xl);
      letter-spacing: 0.08em;
      color: var(--dg-title-color);
    }

    a {
      color: var(--dg-gold);
    }
  `,
})
export class NotFoundPage {}
