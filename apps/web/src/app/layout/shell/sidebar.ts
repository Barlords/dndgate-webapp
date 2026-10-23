import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Brand } from '../../shared/ui/brand/brand';
import { Emblem } from '../../shared/ui/emblem/emblem';
import { Icon } from '../../shared/ui/icon/icon';
import { NavGroup } from '../nav.model';
import { SidebarStateService } from '../sidebar-state.service';

/**
 * The sidebar: the DnDGate logo (back to the home page), the part's name, a slot for what sits
 * under it (the ruleset select), the grouped navigation and, at the bottom, the toggle above the
 * account. Collapsed, it is a 56 px rail of icons; on a small
 * screen, opened, it is a drawer over the content.
 */
@Component({
  selector: 'dg-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icon, Brand, Emblem],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
  host: {
    '[class.sidebar-host--wide]': '!collapsed()',
    '[class.sidebar-host--drawer]': 'drawer()',
  },
})
export class Sidebar {
  readonly heading = input.required<string>();
  readonly groups = input.required<NavGroup[]>();

  protected readonly panelId = 'dg-sidebar-panel';
  protected readonly homeLabel = "DnDGate, retour à l'accueil";
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
