import { ChangeDetectionStrategy, Component, inject, input, model, OnInit } from '@angular/core';
import { SafeStorage } from '../../../core/storage/safe-storage';
import { Icon } from '../icon/icon';

export type ViewMode = 'table' | 'cards';

const MODES: readonly ViewMode[] = ['table', 'cards'];

/**
 * Two-way switch between showing a list as a table (`list` icon) or as cards (`widget` icon). With
 * a `storageKey`, the reader's last choice is remembered in this browser and restored on the next
 * visit.
 */
@Component({
  selector: 'dg-view-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="toggle" role="group" aria-label="Affichage">
      <button
        type="button"
        class="toggle__option"
        aria-label="Afficher en tableau"
        title="Tableau"
        [attr.aria-pressed]="mode() === 'table'"
        (click)="choose('table')"
      >
        <dg-icon name="list" />
      </button>
      <button
        type="button"
        class="toggle__option"
        aria-label="Afficher en cartes"
        title="Cartes"
        [attr.aria-pressed]="mode() === 'cards'"
        (click)="choose('cards')"
      >
        <dg-icon name="widget" />
      </button>
    </div>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .toggle {
      display: inline-flex;
      color: var(--dg-quiet-color);
      border: 1px solid color-mix(in srgb, currentColor 55%, transparent);
      border-radius: var(--dg-radius);
      overflow: hidden;
    }

    .toggle__option {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 2.25rem;
      min-height: 2.25rem;
      padding: 0 0.5rem;
      color: inherit;
      background: transparent;
      border: 0;
      cursor: pointer;
      transition:
        color var(--dg-transition),
        background-color var(--dg-transition);

      & + & {
        border-left: 1px solid color-mix(in srgb, currentColor 55%, transparent);
      }

      &:hover:not([aria-pressed='true']) {
        background: color-mix(in srgb, currentColor 10%, transparent);
      }

      &[aria-pressed='true'] {
        color: var(--dg-parchment-0);
        background: var(--dg-blood);
        cursor: default;
      }

      &:focus-visible {
        outline: 2px solid var(--dg-focus-color);
        outline-offset: -2px;
      }
    }
  `,
})
export class ViewToggle implements OnInit {
  private readonly storage = inject(SafeStorage);

  readonly mode = model<ViewMode>('cards');
  /** Where the choice is remembered; nothing is stored without it. */
  readonly storageKey = input<string | null>(null);

  ngOnInit(): void {
    const key = this.storageKey();
    const saved = key === null ? null : this.storage.get(key);
    if (MODES.includes(saved as ViewMode)) {
      this.mode.set(saved as ViewMode);
    }
  }

  protected choose(mode: ViewMode): void {
    this.mode.set(mode);
    const key = this.storageKey();
    if (key !== null) {
      this.storage.set(key, mode);
    }
  }
}
