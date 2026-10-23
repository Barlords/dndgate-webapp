import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { Emblem } from '../../shared/ui/emblem/emblem';
import { HomeHalfComponent, HomeSide } from './home-half.component';

/**
 * The home page: the window split into the Library (left) and the Tavern (right). Lighting one
 * half up dims the other; only one half is active at a time.
 */
@Component({
  selector: 'dg-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Emblem, HomeHalfComponent],
  template: `
    <h1 class="dg-visually-hidden">DnDGate</h1>
    <dg-emblem class="home__emblem" />
    <dg-home-half
      side="library"
      link="/library"
      title="BIBLIOTHÈQUE"
      subtitle="Sorts, classes, monstres et règles"
      cta="ENTRER"
      [active]="active() === 'library'"
      [dimmed]="active() === 'tavern'"
      (activeChange)="activate('library', $event)"
    />
    <dg-home-half
      side="tavern"
      link="/tavern"
      title="TAVERNE"
      subtitle="Tes aventures, ta table, tes compagnons"
      cta="S’ATTABLER"
      [hint]="tavernHint()"
      [active]="active() === 'tavern'"
      [dimmed]="active() === 'library'"
      (activeChange)="activate('tavern', $event)"
    />
  `,
  styleUrl: './home.page.scss',
})
export class HomePage {
  private readonly auth = inject(AuthService);

  protected readonly active = signal<HomeSide | null>(null);
  // The tavern guard sends a signed-out visitor to the login page first: say so to screen readers.
  protected readonly tavernHint = computed(() =>
    this.auth.isAuthenticated() ? '' : 'connexion requise',
  );

  protected activate(side: HomeSide, active: boolean): void {
    if (active) {
      this.active.set(side);
    } else if (this.active() === side) {
      this.active.set(null);
    }
  }
}
