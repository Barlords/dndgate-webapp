import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NavGroup } from '../../layout/nav.model';
import { Shell } from '../../layout/shell/shell';
import { RulesetSelect } from './ruleset-select';
import { SectionGroup } from './rulesets/ruleset.model';
import { SelectedRulesetService } from './rulesets/selected-ruleset.service';

/** The headings of the section groups, in the order the sidebar shows them. */
export const SECTION_GROUPS: readonly { id: SectionGroup; label: string }[] = [
  { id: 'players', label: 'POUR LES JOUEURS' },
  { id: 'master', label: 'POUR LE MJ' },
  { id: 'rules', label: 'RÈGLES' },
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
    <dg-shell heading="BIBLIOTHÈQUE" [groups]="groups()">
      <dg-ruleset-select sidebarHeader />
      <router-outlet />
    </dg-shell>
  `,
})
export class LibraryShell {
  private readonly selected = inject(SelectedRulesetService);
  private readonly auth = inject(AuthService);

  protected readonly groups = computed<NavGroup[]>(() => {
    const ruleset = this.selected.current();
    const groups: NavGroup[] = SECTION_GROUPS.map((group) => ({
      label: group.label,
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
        label: 'MES GRIMOIRES',
        items: [{ label: 'Mes grimoires', icon: 'book', link: '/library/grimoires' }],
      });
    }
    return groups;
  });
}
