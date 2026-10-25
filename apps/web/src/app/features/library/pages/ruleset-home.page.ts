import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { Icon } from '../../../shared/ui/icon/icon';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { SECTION_GROUPS } from '../library-shell';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { I18n } from '../../../core/i18n/i18n.service';

/** Front page of a ruleset: its full name and its sections, by group. */
@Component({
  selector: 'dg-ruleset-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, RouterLink, Icon],
  template: `
    @if (ruleset(); as shown) {
      <dg-parchment>
        <h1 class="home__title">{{ shown.label }}</h1>
        @for (group of groups(); track group.label) {
          <h2 class="home__group">{{ group.label }}</h2>
          <ul class="home__sections">
            @for (section of group.sections; track section.id) {
              <li>
                <a class="home__section" [routerLink]="['/library', shown.id, section.id]">
                  <dg-icon [name]="section.icon" />
                  <span>{{ section.label }}</span>
                </a>
              </li>
            }
          </ul>
        }
      </dg-parchment>
    }
  `,
  styles: `
    .home__title {
      margin: 0 0 var(--dg-space-4);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-2xl);
      letter-spacing: 0.12em;
      text-align: center;
      color: var(--dg-title-color);
    }

    .home__group {
      margin: var(--dg-space-5) 0 var(--dg-space-3);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.2em;
      color: var(--dg-ink-muted);
    }

    .home__sections {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
      gap: var(--dg-space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .home__section {
      display: flex;
      align-items: center;
      gap: var(--dg-space-3);
      padding: var(--dg-space-3) var(--dg-space-4);
      font-family: var(--dg-font-title);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
      text-decoration: none;
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      transition:
        background-color var(--dg-transition),
        box-shadow var(--dg-transition);

      &:hover {
        background: color-mix(in srgb, var(--dg-parchment-border) 15%, transparent);
        box-shadow: inset 0 0 0 1px var(--dg-parchment-border);
      }
    }
  `,
})
export class RulesetHomePage {
  private readonly t = inject(I18n).t;

  private readonly registry = inject(RulesetRegistry);

  protected readonly ruleset = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => this.registry.get(params.get('rulesetId') ?? '')),
    ),
  );

  protected readonly groups = computed(() => {
    const sections = this.ruleset()?.sections ?? [];
    const t = this.t();
    return SECTION_GROUPS.map((group) => ({
      label: group.label(t),
      sections: sections.filter((section) => section.group === group.id),
    })).filter((group) => group.sections.length > 0);
  });
}
