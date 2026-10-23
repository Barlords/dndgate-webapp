import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Icon } from '../../shared/ui/icon/icon';
import { NavGroup } from '../nav.model';
import { SidebarStateService } from '../sidebar-state.service';

/**
 * The sidebar: the part's name, a slot for what sits under it (the ruleset select), the grouped
 * navigation and the account at the bottom. Collapsed, it is a 56 px rail of icons; on a small
 * screen, opened, it is a drawer over the content.
 */
@Component({
  selector: 'dg-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icon],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
  host: {
    '[class.sidebar-host--wide]': '!collapsed()',
    '[class.sidebar-host--drawer]': 'drawer()',
  },
})
export class Sidebar {
  readonly title = input.required<string>();
  readonly groups = input.required<NavGroup[]>();

  protected readonly panelId = 'dg-sidebar-panel';
  private readonly state = inject(SidebarStateService);
  protected readonly collapsed = this.state.collapsed;
  protected readonly drawer = computed(() => this.state.small() && !this.state.collapsed());
  protected readonly account = inject(AuthService).account;

  private readonly router = inject(Router);
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  protected readonly loginQuery = computed(() => ({ returnUrl: this.url() }));
  protected readonly initial = computed(() =>
    (this.account()?.pseudo ?? '?').charAt(0).toUpperCase(),
  );

  protected toggle(): void {
    this.state.toggle();
  }

  /** On a small screen the drawer closes once a link has been followed. */
  protected linkFollowed(): void {
    if (this.state.small()) {
      this.state.collapse();
    }
  }
}
