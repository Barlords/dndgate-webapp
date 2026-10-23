import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Emblem } from '../../shared/ui/emblem/emblem';

/** The bar across the top: the DnDGate emblem and name, centred, leading back to the home page. */
@Component({
  selector: 'dg-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Emblem],
  template: `
    <a class="topbar__brand" routerLink="/" aria-label="DnDGate, retour à l'accueil">
      <dg-emblem />
      <span aria-hidden="true">DNDGATE</span>
    </a>
  `,
  styleUrl: './topbar.scss',
})
export class Topbar {}
