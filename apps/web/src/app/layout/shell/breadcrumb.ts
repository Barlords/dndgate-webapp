import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BreadcrumbService } from '../breadcrumb.service';
import { I18n } from '../../core/i18n/i18n.service';

/** The trail shown at the top of the content; the last item is the current page, not a link. */
@Component({
  selector: 'dg-breadcrumb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './breadcrumb.html',
  styleUrl: './breadcrumb.scss',
})
export class Breadcrumb {
  protected readonly t = inject(I18n).t;

  protected readonly crumbs = inject(BreadcrumbService).crumbs;
}
