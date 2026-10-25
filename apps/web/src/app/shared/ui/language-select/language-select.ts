import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { LANGUAGES } from '../../../core/i18n/dictionary';
import { I18n } from '../../../core/i18n/i18n.service';

/**
 * The choice of the interface language: one short button per language (« FR », « EN »), named in
 * full for assistive technologies. `vertical` stacks them (the folded sidebar is narrow).
 */
@Component({
  selector: 'dg-language-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.vertical]': 'vertical()' },
  template: `
    <div class="languages" role="group" [attr.aria-label]="i18n.t().common.shell.language">
      @for (language of languages; track language.id) {
        <button
          type="button"
          class="languages__option"
          [attr.lang]="language.id"
          [attr.aria-label]="language.label"
          [attr.title]="language.label"
          [attr.aria-pressed]="i18n.language() === language.id"
          (click)="i18n.use(language.id)"
        >
          {{ language.id.toUpperCase() }}
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: center;
      margin-bottom: var(--dg-space-2);
    }

    .languages {
      display: inline-flex;
      color: var(--dg-gold-light);
      border: 1px solid #c9a24a55;
      border-radius: var(--dg-radius);
      overflow: hidden;
    }

    :host(.vertical) .languages {
      flex-direction: column;
    }

    .languages__option {
      min-width: 2.5rem;
      height: 1.75rem;
      padding: 0 0.5rem;
      font-family: var(--dg-font-title);
      font-size: 0.75rem;
      letter-spacing: 0.1em;
      color: inherit;
      background: none;
      border: 0;
      cursor: pointer;
      transition: background var(--dg-transition);

      &:hover:not([aria-pressed='true']) {
        background: #d9b45a1f;
      }

      &[aria-pressed='true'] {
        color: var(--dg-leather-0);
        background: var(--dg-gold);
        cursor: default;
      }

      &:focus-visible {
        outline: 2px solid var(--dg-focus-color);
        outline-offset: -2px;
      }
    }
  `,
})
export class LanguageSelect {
  protected readonly i18n = inject(I18n);
  protected readonly languages = LANGUAGES;

  readonly vertical = input(false);
}
