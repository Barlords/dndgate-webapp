import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { IconButton } from '../../shared/ui/icon-button/icon-button';
import { ViewToggle, ViewMode } from '../../shared/ui/view-toggle/view-toggle';

/**
 * Top of a Tavern list: its heading followed by the « + Créer… » button, whose label unfolds on
 * hover, and, aligned to the right, the choice between a table and cards (remembered under
 * `storageKey`).
 */
@Component({
  selector: 'dg-list-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ViewToggle, IconButton],
  template: `
    <header class="list-head">
      <div class="list-head__title">
        <h1>{{ heading() }}</h1>
        @if (createLabel(); as label) {
          <dg-icon-button icon="plus" [label]="label" (click)="create.emit()" />
        }
      </div>
      <div class="list-head__tools">
        <ng-content />
        <dg-view-toggle [(mode)]="mode" [storageKey]="storageKey()" />
      </div>
    </header>
  `,
  styles: `
    .list-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--dg-space-3);
      margin-bottom: var(--dg-space-4);
    }

    .list-head__title {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-3);
    }

    h1 {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-xl);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-title-color);
    }

    .list-head__tools {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: var(--dg-space-2);
      margin-left: auto;
    }
  `,
})
export class ListHeader {
  readonly heading = input.required<string>();
  /** Where the choice of display is remembered: one key per list. */
  readonly storageKey = input.required<string>();
  /** The words of the creation button; no button without them. */
  readonly createLabel = input<string | null>(null);
  readonly mode = model<ViewMode>('cards');
  readonly create = output<void>();
}
