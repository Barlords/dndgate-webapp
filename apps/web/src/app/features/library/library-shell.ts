import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NavGroup } from '../../layout/nav.model';
import { Shell } from '../../layout/shell/shell';
import { RulesetSelect } from './ruleset-select';
import { SectionGroup } from './rulesets/ruleset.model';
import { SelectedRulesetService } from './rulesets/selected-ruleset.service';
import { Text } from '../../core/i18n/dictionary';
import { I18n } from '../../core/i18n/i18n.service';

/** The headings of the section groups, in the order the sidebar shows them. */
export const SECTION_GROUPS: readonly { id: SectionGroup; label: Text }[] = [
  { id: 'players', label: (t) => t.library.shell.groups.players },
  { id: 'master', label: (t) => t.library.shell.groups.master },
  { id: 'rules', label: (t) => t.library.shell.groups.rules },
];

/**
 * Layout of every Library page: the ruleset select under the heading, the selected ruleset's
 * sections grouped in the sidebar and, for a signed-in reader of a game with spells, the spell books.
 */
@Component({
  selector: 'dg-library-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Shell, RulesetSelect, RouterOutlet],
  template: `
    <dg-shell [heading]="t().library.shell.heading" [groups]="groups()">
      <dg-ruleset-select sidebarHeader />
      <router-outlet />
    </dg-shell>
  `,
})
export class LibraryShell {
  protected readonly t = inject(I18n).t;

  private readonly selected = inject(SelectedRulesetService);
  private readonly auth = inject(AuthService);

  protected readonly groups = computed<NavGroup[]>(() => {
    const ruleset = this.selected.current();
    const t = this.t();
    const groups: NavGroup[] = SECTION_GROUPS.map((group) => ({
      label: group.label(t),
      items: ruleset.sections
        .filter((section) => section.group === group.id)
        .map((section) => ({
          label: section.label,
          icon: section.icon,
          link: `/library/${ruleset.id}/${section.id}`,
        })),
    })).filter((group) => group.items.length > 0);
    if (this.auth.isAuthenticated() && ruleset.spellSectionId !== null) {
      groups.push({
        label: t.library.shell.groups.spellbooks,
        items: [{ label: t.library.spellbooks.title, icon: 'book', link: '/library/grimoires' }],
      });
    }
    return groups;
  });
}
