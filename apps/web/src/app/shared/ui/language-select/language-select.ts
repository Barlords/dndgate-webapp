import { CdkMenu, CdkMenuItemRadio, CdkMenuTrigger } from '@angular/cdk/menu';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { LANGUAGES } from '../../../core/i18n/dictionary';
import { I18n } from '../../../core/i18n/i18n.service';

/**
 * The choice of the interface language: a small select showing the flag and the short name of the
 * current language (« FR », « EN »), opening the list of the languages, each with its flag and its
 * name in itself. `compact` keeps the flag alone (the folded sidebar is narrow).
 */
@Component({
  selector: 'dg-language-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItemRadio, NgTemplateOutlet],
  host: { '[class.compact]': 'compact()' },
  template: `
    <button
      type="button"
      class="language-select"
      [cdkMenuTriggerFor]="menu"
      [cdkMenuPosition]="positions"
      aria-haspopup="menu"
      [attr.aria-label]="t().common.shell.languageCurrent(current().label)"
      [attr.title]="t().common.shell.languageCurrent(current().label)"
    >
      <ng-container *ngTemplateOutlet="flag; context: { $implicit: current().id }" />
      @if (!compact()) {
        <span class="language-select__code" aria-hidden="true">{{
          current().id.toUpperCase()
        }}</span>
        <span class="language-select__chevron" aria-hidden="true"></span>
      }
    </button>

    <ng-template #menu>
      <ul cdkMenu class="language-select__menu" [attr.aria-label]="t().common.shell.language">
        @for (language of languages; track language.id) {
          <li
            cdkMenuItemRadio
            class="language-select__item"
            [attr.lang]="language.id"
            [cdkMenuItemChecked]="language.id === current().id"
            (cdkMenuItemTriggered)="i18n.use(language.id)"
          >
            <ng-container *ngTemplateOutlet="flag; context: { $implicit: language.id }" />
            <span class="language-select__label">{{ language.label }}</span>
            <span class="language-select__mark" aria-hidden="true">{{
              language.id === current().id ? '✓' : ''
            }}</span>
          </li>
        }
      </ul>
    </ng-template>

    <!-- The flags, drawn here: an emoji flag shows as two letters on Windows. -->
    <ng-template #flag let-id>
      @switch (id) {
        @case ('fr') {
          <svg class="language-select__flag" viewBox="0 0 3 2" aria-hidden="true">
            <rect width="1" height="2" fill="#002654" />
            <rect x="1" width="1" height="2" fill="#ffffff" />
            <rect x="2" width="1" height="2" fill="#ce1126" />
          </svg>
        }
        @case ('en') {
          <svg class="language-select__flag" viewBox="0 0 60 40" aria-hidden="true">
            <rect width="60" height="40" fill="#012169" />
            <path d="M0,0 L60,40 M60,0 L0,40" stroke="#ffffff" stroke-width="8" />
            <path d="M0,0 L60,40 M60,0 L0,40" stroke="#c8102e" stroke-width="3" />
            <path d="M30,0 V40 M0,20 H60" stroke="#ffffff" stroke-width="12" />
            <path d="M30,0 V40 M0,20 H60" stroke="#c8102e" stroke-width="7" />
          </svg>
        }
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: center;
    }

    .language-select {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      height: 1.75rem;
      padding: 0 0.55rem;
      font-family: var(--dg-font-title);
      font-size: 0.75rem;
      letter-spacing: 0.1em;
      color: var(--dg-gold-light);
      background: linear-gradient(var(--dg-leather-1), var(--dg-leather-0));
      border: 1px solid color-mix(in srgb, var(--dg-gold) 55%, transparent);
      border-radius: var(--dg-radius-sm);
      cursor: pointer;
      transition: border-color var(--dg-transition);

      &:hover,
      &[aria-expanded='true'] {
        border-color: var(--dg-gold);
      }

      &:focus-visible {
        outline: 2px solid var(--dg-focus-color);
        outline-offset: 2px;
      }
    }

    // The flag alone, centred in the rail.
    :host(.compact) .language-select {
      padding: 0 0.45rem;
    }

    // The letter spacing also follows the last letter: taken back so that the code stays centred.
    .language-select__code {
      margin-right: -0.1em;
    }

    .language-select__chevron {
      width: 0.35rem;
      height: 0.35rem;
      margin: 0 0.1rem 0.2rem 0.1rem;
      border-right: 1.5px solid var(--dg-gold);
      border-bottom: 1.5px solid var(--dg-gold);
      transform: rotate(45deg);
      transition: transform var(--dg-transition);
    }

    [aria-expanded='true'] .language-select__chevron {
      margin-bottom: -0.15rem;
      transform: rotate(225deg);
    }

    .language-select__flag {
      flex: none;
      width: 18px;
      height: 12px;
      border-radius: 2px;
      box-shadow: 0 0 0 1px #00000066;
    }

    .language-select__menu {
      min-width: 9rem;
      margin: 0;
      padding: var(--dg-space-1);
      list-style: none;
      font-family: var(--dg-font-body);
      font-size: var(--dg-text-sm);
      color: var(--dg-text-light);
      background: var(--dg-leather-0);
      border: 1px solid color-mix(in srgb, var(--dg-gold) 55%, transparent);
      border-radius: var(--dg-radius);
      box-shadow: 0 6px 18px #000000aa;
    }

    .language-select__item {
      display: flex;
      align-items: center;
      gap: var(--dg-space-2);
      padding: var(--dg-space-2) var(--dg-space-3);
      border-radius: var(--dg-radius-sm);
      cursor: pointer;

      &:hover,
      &.cdk-menu-item-active,
      &:focus-visible {
        background: #d9b45a26;
      }

      &[aria-checked='true'] {
        color: var(--dg-gold-light);
      }
    }

    .language-select__label {
      flex: 1;
    }

    .language-select__mark {
      flex: 0 0 1rem;
      text-align: right;
      color: var(--dg-gold);
    }
  `,
})
export class LanguageSelect {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly languages = LANGUAGES;
  protected readonly current = computed(() =>
    LANGUAGES.find((language) => language.id === this.i18n.language())!,
  );

  /** The list opens under the select, centred on it; above it when there is no room below. */
  protected readonly positions: ConnectedPosition[] = [
    { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 4 },
    { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -4 },
  ];

  readonly compact = input(false);
}
