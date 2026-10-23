import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Parchment panel framed by an inner gold rule with illuminated corners. */
@Component({
  selector: 'dg-parchment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
  styleUrl: './parchment.scss',
})
export class Parchment {}
