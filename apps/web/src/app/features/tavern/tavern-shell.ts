import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavGroup } from '../../layout/nav.model';
import { Shell } from '../../layout/shell/shell';
import { Dictionary } from '../../core/i18n/dictionary';
import { I18n } from '../../core/i18n/i18n.service';

/** The menu of the Tavern, in the reader's language. */
export function tavernGroups(t: Dictionary): NavGroup[] {
  const shell = t.tavern.shell;
  return [
    {
      label: shell.commonRoom,
      items: [
        { label: shell.adventures, icon: 'scroll', link: '/tavern/adventures', exact: true },
        { label: shell.useCode, icon: 'key', link: '/tavern/code' },
      ],
    },
    {
      label: shell.mySpace,
      items: [
        { label: shell.myAdventures, icon: 'sword', link: '/tavern/mine' },
        { label: shell.myCharacters, icon: 'user', link: '/tavern/characters' },
        { label: shell.mySpellbooks, icon: 'book', link: '/tavern/grimoires' },
        { label: shell.myMagicItems, icon: 'gem', link: '/tavern/magic-items' },
      ],
    },
  ];
}

/** Layout of every Tavern page: the sidebar of the common room and of the reader's own space. */
@Component({
  selector: 'dg-tavern-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Shell, RouterOutlet],
  template: `
    <dg-shell [heading]="t().tavern.shell.heading" [groups]="groups()">
      <router-outlet />
    </dg-shell>
  `,
})
export class TavernShell {
  protected readonly t = inject(I18n).t;

  protected readonly groups = computed(() => tavernGroups(this.t()));
}
