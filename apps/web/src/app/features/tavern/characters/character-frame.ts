import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Parchment } from '../../../shared/ui/parchment/parchment';

/**
 * The parchment around a character's pages under « Mes personnages ». In an adventure, the
 * adventure's own page draws it; here nothing else would.
 */
@Component({
  selector: 'dg-character-frame',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, RouterOutlet],
  template: `
    <dg-parchment>
      <router-outlet />
    </dg-parchment>
  `,
})
export class CharacterFrame {}
