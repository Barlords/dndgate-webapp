import { CdkMenu, CdkMenuItem, CdkMenuItemCheckbox, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { SpellBookDto } from '../../../core/api/generated/models/spell-book-dto';
import { Icon } from '../../../shared/ui/icon/icon';
import { MySpellBooksStore } from './my-spellbooks.store';

/**
 * The « + grimoire » button of a spell, on its page and on each row of the spell list: a menu of
 * the reader's spell books of the spell's ruleset, ticked when they already hold the spell.
 * Choosing one adds or removes the spell. The books come from the page's `MySpellBooksStore`.
 *
 * With `revealLabel`, the words « Ajouter au grimoire » unfold beside the icons on hover or focus;
 * in a table row they would push the columns, so there the button only has its accessible name.
 */
@Component({
  selector: 'dg-add-to-spellbook',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItem, CdkMenuItemCheckbox, RouterLink, Icon],
  styles: `
    :host {
      display: inline-flex;
    }

    .add-to-book__trigger {
      display: inline-flex;
      align-items: center;
      padding: var(--dg-space-1) var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      white-space: nowrap;
      color: var(--dg-blood);
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      cursor: pointer;
      transition:
        background var(--dg-transition),
        border-color var(--dg-transition);

      &:hover,
      &[aria-expanded='true'] {
        background: var(--dg-parchment-0);
        border-color: var(--dg-blood);
      }
    }

    .add-to-book__plus {
      --dg-icon-size: 0.85em;
      margin-right: -0.1em;
    }

    // Folded to nothing until the button is hovered, focused or open.
    .add-to-book__label {
      display: inline-block;
      max-width: 0;
      overflow: hidden;
      opacity: 0;
      transition:
        max-width 250ms ease-out,
        opacity 250ms ease-out,
        margin-left 250ms ease-out;

      @media (prefers-reduced-motion: reduce) {
        transition: none;
      }
    }

    .add-to-book__trigger:hover,
    .add-to-book__trigger:focus-visible,
    .add-to-book__trigger[aria-expanded='true'] {
      .add-to-book__label {
        max-width: 14rem;
        margin-left: var(--dg-space-2);
        opacity: 1;
      }
    }

    .add-to-book__menu {
      min-width: 14rem;
      margin: var(--dg-space-1) 0 0;
      padding: var(--dg-space-1);
      list-style: none;
      font-family: var(--dg-font-body);
      color: var(--dg-ink);
      text-align: left;
      background: linear-gradient(var(--dg-parchment-0), var(--dg-parchment-1));
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow:
        inset 0 0 12px var(--dg-parchment-shadow),
        0 6px 18px #17100a99;
    }

    .add-to-book__item {
      display: flex;
      align-items: center;
      gap: var(--dg-space-2);
      padding: var(--dg-space-2) var(--dg-space-3);
      color: var(--dg-ink);
      text-decoration: none;
      border-radius: var(--dg-radius-sm);
      cursor: pointer;
      transition: background var(--dg-transition);
    }

    .add-to-book__item:hover,
    .add-to-book__item.cdk-menu-item-active,
    .add-to-book__item:focus-visible {
      background: var(--dg-gold-light);
    }

    .add-to-book__item[aria-disabled='true'] {
      opacity: 0.55;
      cursor: progress;
    }

    .add-to-book__mark {
      flex: 0 0 1.1rem;
      text-align: center;
      font-weight: 700;
      color: var(--dg-blood);
    }

    .add-to-book__create {
      margin-top: var(--dg-space-1);
      padding-top: var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
      border-top: 1px solid var(--dg-parchment-border);
      border-radius: 0 0 var(--dg-radius-sm) var(--dg-radius-sm);
    }

    .add-to-book__empty {
      padding: var(--dg-space-2) var(--dg-space-3);
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    .add-to-book__trigger:focus-visible,
    .add-to-book__item:focus-visible {
      outline: 2px solid var(--dg-focus-color);
      outline-offset: 2px;
    }
  `,
  template: `
    @if (auth.isAuthenticated()) {
      <button
        type="button"
        class="add-to-book__trigger"
        [cdkMenuTriggerFor]="menu"
        aria-haspopup="menu"
        [attr.aria-label]="accessibleName()"
        [attr.title]="revealLabel() ? null : accessibleName()"
      >
        <dg-icon class="add-to-book__plus" name="plus" />
        <dg-icon name="book" />
        @if (revealLabel()) {
          <span class="add-to-book__label" aria-hidden="true">Ajouter au grimoire</span>
        }
      </button>
      <ng-template #menu>
        <ul cdkMenu class="add-to-book__menu" aria-label="Mes grimoires">
          @for (book of fitting(); track book.id) {
            <li
              cdkMenuItemCheckbox
              class="add-to-book__item"
              [cdkMenuItemChecked]="holds(book)"
              [attr.aria-disabled]="isPending(book) || null"
              (cdkMenuItemTriggered)="toggle(book)"
            >
              <span class="add-to-book__mark" aria-hidden="true">{{ holds(book) ? '✓' : '' }}</span>
              {{ book.name }}
            </li>
          } @empty {
            <li class="add-to-book__empty" role="none">Aucun grimoire pour ce jeu de règles.</li>
          }
          <li role="none">
            <a
              cdkMenuItem
              class="add-to-book__item add-to-book__create"
              [routerLink]="['/library/grimoires']"
              [queryParams]="{ ruleset: rulesetId() }"
            >
              Créer un grimoire
            </a>
          </li>
        </ul>
      </ng-template>
    }
  `,
})
export class AddToSpellbook {
  protected readonly auth = inject(AuthService);
  private readonly store = inject(MySpellBooksStore);

  readonly rulesetId = input.required<string>();
  readonly spellIndex = input.required<string>();
  /** Named in the button's accessible name, so that the buttons of a list tell apart. */
  readonly spellName = input<string>();
  readonly revealLabel = input(true);

  protected readonly accessibleName = computed(() => {
    const name = this.spellName();
    return name ? `Ajouter ${name} au grimoire` : 'Ajouter au grimoire';
  });
  protected readonly fitting = computed(() =>
    this.store.books().filter((book) => book.ruleset === this.rulesetId()),
  );

  constructor() {
    if (this.auth.isAuthenticated()) {
      this.store.load();
    }
  }

  protected isPending(book: SpellBookDto): boolean {
    return this.store.isPending(book, this.spellIndex());
  }

  protected holds(book: SpellBookDto): boolean {
    return this.store.holds(book, this.spellIndex());
  }

  protected toggle(book: SpellBookDto): void {
    this.store.toggle(book, this.spellIndex());
  }
}
