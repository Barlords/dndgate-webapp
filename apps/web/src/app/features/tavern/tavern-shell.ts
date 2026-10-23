import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavGroup } from '../../layout/nav.model';
import { Shell } from '../../layout/shell/shell';

export const TAVERN_GROUPS: NavGroup[] = [
  {
    label: 'QUÊTES',
    items: [
      { label: 'Tableau des quêtes', icon: 'scroll', link: '/tavern/quests' },
      { label: 'Rejoindre avec un code', icon: 'key', link: '/tavern/join' },
      { label: 'Devenir MJ avec un code', icon: 'crown', link: '/tavern/master-code' },
      { label: 'Devenir MJ avec un code', icon: 'crown', link: '/tavern/master-code' },
      { label: 'Créer une aventure', icon: 'plus', link: '/tavern/new' },
    ],
  },
  {
    label: 'MES AVENTURES',
    items: [
      { label: 'En cours', icon: 'sword', link: '/tavern/mine/ongoing' },
      { label: 'Terminées', icon: 'book', link: '/tavern/mine/finished' },
    ],
  },
];

/** Layout of every Tavern page: the sidebar of quests and of the reader's adventures. */
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
