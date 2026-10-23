import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Stands in for the tabs of an adventure that are not built yet. */
@Component({
  selector: 'dg-coming-soon-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p>Bientôt disponible.</p>`,
})
export class ComingSoonPage {}
