import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { Brand } from '../../shared/ui/brand/brand';
import { HomeHalfComponent, HomeSide } from './home-half.component';
import { I18n } from '../../core/i18n/i18n.service';

/**
 * The home page: the window split into the Library (left) and the Tavern (right). Lighting one
 * half up dims the other; only one half is active at a time.
 */
@Component({
  selector: 'dg-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Brand, HomeHalfComponent],
  template: `
    <h1 class="dg-visually-hidden">DnDGate</h1>
    <dg-brand class="home__brand" />
    <dg-home-half
      side="library"
      link="/library"
      [title]="t().account.home.libraryTitle"
      [subtitle]="t().account.home.librarySubtitle"
      [cta]="t().account.home.libraryCta"
      [active]="active() === 'library'"
      [dimmed]="active() === 'tavern'"
      (activeChange)="activate('library', $event)"
    />
    <dg-home-half
      side="tavern"
      link="/tavern"
      [title]="t().account.home.tavernTitle"
      [subtitle]="t().account.home.tavernSubtitle"
      [cta]="t().account.home.tavernCta"
      [hint]="tavernHint()"
      [active]="active() === 'tavern'"
      [dimmed]="active() === 'library'"
      (activeChange)="activate('tavern', $event)"
    />
  `,
  styleUrl: './home.page.scss',
})
export class HomePage {
  protected readonly t = inject(I18n).t;

  private readonly auth = inject(AuthService);

  protected readonly active = signal<HomeSide | null>(null);
  // The tavern guard sends a signed-out visitor to the login page first: say so to screen readers.
  protected readonly tavernHint = computed(() =>
    this.auth.isAuthenticated() ? '' : this.t().account.home.signInRequired,
  );

  protected activate(side: HomeSide, active: boolean): void {
    if (active) {
      this.active.set(side);
    } else if (this.active() === side) {
      this.active.set(null);
    }
  }
}
