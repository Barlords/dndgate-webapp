import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavGroup } from '../../layout/nav.model';
import { Shell } from '../../layout/shell/shell';

export const TAVERN_GROUPS: NavGroup[] = [
  {
    label: 'SALLE COMMUNE',
    items: [
      { label: 'Aventures', icon: 'scroll', link: '/tavern/adventures', exact: true },
      { label: 'Utiliser un code', icon: 'key', link: '/tavern/code' },
    ],
  },
  {
    label: 'MON ESPACE',
    items: [
      { label: 'Mes aventures', icon: 'sword', link: '/tavern/mine' },
      { label: 'Mes personnages', icon: 'user', link: '/tavern/characters' },
      { label: 'Mes grimoires', icon: 'book', link: '/tavern/grimoires' },
      { label: 'Mes objets magiques', icon: 'gem', link: '/tavern/magic-items' },
    ],
  },
];

/** Layout of every Tavern page: the sidebar of the common room and of the reader's own space. */
@Component({
  selector: 'dg-tavern-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Shell, RouterOutlet],
  template: `
    <dg-shell heading="TAVERNE" [groups]="groups">
      <router-outlet />
    </dg-shell>
  `,
})
export class TavernShell {
  protected readonly groups = TAVERN_GROUPS;
}
