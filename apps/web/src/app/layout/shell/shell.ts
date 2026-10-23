import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NavGroup } from '../nav.model';
import { Breadcrumb } from './breadcrumb';
import { Sidebar } from './sidebar';
import { Topbar } from './topbar';

/**
 * Layout of the inner pages: top bar, sidebar (`heading`, `groups`, and whatever is projected into
 * `[sidebarHeader]` under the title) and, beside it, the breadcrumb above the projected content.
 */
@Component({
  selector: 'dg-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Topbar, Sidebar, Breadcrumb],
  template: `
    <dg-topbar />
    <div class="shell__body">
      <dg-sidebar [heading]="heading()" [groups]="groups()">
        <ng-container ngProjectAs="[sidebarHeader]">
          <ng-content select="[sidebarHeader]" />
        </ng-container>
      </dg-sidebar>
      <main class="shell__main">
        <dg-breadcrumb />
        <ng-content />
      </main>
    </div>
  `,
  styleUrl: './shell.scss',
})
export class Shell {
  readonly heading = input.required<string>();
  readonly groups = input.required<NavGroup[]>();
}
