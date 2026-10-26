import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Icon } from '../../../../shared/ui/icon/icon';
import { CharacterPictureDirective } from './character-picture.directive';
import { I18n } from '../../../../core/i18n/i18n.service';

/**
 * A character's portrait in a gilded frame. A parchment plate with a silhouette stands in while
 * the picture loads and when there is none or it cannot be shown.
 */
@Component({
  selector: 'dg-character-portrait',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CharacterPictureDirective, Icon],
  styles: `
    :host {
      display: block;
      width: var(--dg-portrait-width, 9rem);
    }

    .frame {
      position: relative;
      aspect-ratio: 3 / 4;
      overflow: hidden;
      padding: 3px;
      background: linear-gradient(145deg, var(--dg-gold-light), var(--dg-gold-deep));
      border-radius: var(--dg-radius);
      box-shadow:
        0 2px 6px #17100a66,
        inset 0 0 0 1px var(--dg-gold-deep);
    }

    .inner {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: hidden;
      border-radius: var(--dg-radius-sm);
      border: 2px solid var(--dg-leather-0);
      background: linear-gradient(160deg, var(--dg-parchment-0), var(--dg-parchment-1));
    }

    img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    img.hidden {
      display: none;
    }

    .placeholder {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--dg-ink-muted);
      opacity: 0.55;
      --dg-icon-size: 45%;
    }
  `,
  template: `
    <div class="frame">
      <div class="inner">
        <img
          [dgCharacterPicture]="hasPicture() ? characterId() : null"
          #picture="dgCharacterPicture"
          [alt]="t().tavern.portrait(name())"
          [class.hidden]="picture.status() !== 'ready'"
        />
        @if (picture.status() !== 'ready') {
          <span class="placeholder"><dg-icon name="user" /></span>
        }
      </div>
    </div>
  `,
})
export class CharacterPortrait {
  protected readonly t = inject(I18n).t;

  readonly characterId = input.required<string>();
  readonly name = input('');
  /** False for a character without picture: nothing is fetched, the plate stays. */
  readonly hasPicture = input(true);
}
