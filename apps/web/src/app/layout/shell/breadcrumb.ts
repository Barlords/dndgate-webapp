import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BreadcrumbService } from '../breadcrumb.service';

/** The trail shown at the top of the content; the last item is the current page, not a link. */
@Component({
  selector: 'dg-breadcrumb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './breadcrumb.html',
  styleUrl: './breadcrumb.scss',
})
export class Breadcrumb {
  protected readonly crumbs = inject(BreadcrumbService).crumbs;
}
