import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Says, discreetly, that what follows is not in the reader's language: the source does not have it
 * translated yet, so it is shown in English.
 */
@Component({
  selector: 'dg-untranslated-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p role="note">Pas encore traduit dans ta langue : affiché en anglais.</p>`,
  styles: `
    p {
      margin: var(--dg-space-1) 0 var(--dg-space-4);
      font-size: var(--dg-text-sm);
      font-style: italic;
      text-align: center;
      color: var(--dg-ink-muted);
    }
  `,
})
export class UntranslatedNote {}
