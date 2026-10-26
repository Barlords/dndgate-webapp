import { ViewportScroller } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { NavGroup } from '../nav.model';
import { Breadcrumb } from './breadcrumb';
import { SiteFooter } from '../site-footer';
import { Sidebar } from './sidebar';

/**
 * Layout of the inner pages: the sidebar (the DnDGate logo, `heading`, `groups`, and whatever is
 * projected into `[sidebarHeader]` under the title) and, beside it, the breadcrumb above the
 * projected content, the site footer under it.
 */
@Component({
  selector: 'dg-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Sidebar, Breadcrumb, SiteFooter],
  template: `
    <div class="shell__body">
      <dg-sidebar [heading]="heading()" [groups]="groups()">
        <ng-container ngProjectAs="[sidebarHeader]">
          <ng-content select="[sidebarHeader]" />
        </ng-container>
      </dg-sidebar>
      <main class="shell__main">
        <dg-breadcrumb />
        <ng-content />
        <dg-site-footer />
      </main>
    </div>
  `,
  styleUrl: './shell.scss',
})
export class Shell {
  readonly heading = input.required<string>();
  readonly groups = input.required<NavGroup[]>();

  constructor() {
    // A link to an anchor leaves a little room above what it leads to.
    inject(ViewportScroller).setOffset([0, 16]);
  }
}
