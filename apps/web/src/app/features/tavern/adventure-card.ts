import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdventureDto } from '../../core/api/generated/models/adventure-dto';
import { Icon } from '../../shared/ui/icon/icon';
import { STATUS_LABEL, Status, VISIBILITY_LABEL, Visibility } from './adventure-status';

const EXCERPT_LENGTH = 140;

/** A parchment card for one adventure; what is projected goes in the footer (actions). */
@Component({
  selector: 'dg-adventure-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  styles: `
    :host {
      display: block;
    }

    .card {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-2);
      height: 100%;
      padding: var(--dg-space-4);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      background: var(--dg-vellum);
      box-shadow: inset 0 0 0 3px var(--dg-parchment-shadow);
    }

    .card__seals {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
    }

    .seal {
      padding: 0.1rem 0.6rem;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--dg-vellum);
      background: var(--dg-ink-muted);
      border-radius: 999px;
    }

    .seal--ongoing {
      background: #3f5a2a;
    }

    .seal--finish {
      background: var(--dg-gold-deep);
    }

    .seal--abort {
      background: #6b6256;
    }

    .seal--vacant {
      background: var(--dg-blood);
    }

    .seal--role {
      color: var(--dg-ink);
      background: var(--dg-gold-light);
    }

    .card__name {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
    }

    .card__excerpt {
      flex: 1;
      margin: 0;
      color: var(--dg-ink);
    }

    .card__meta {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-1) var(--dg-space-4);
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }

    .card__footer:not(:empty) {
      margin-top: var(--dg-space-2);
    }
  `,
  template: `
    <article class="card">
      <div class="card__seals">
        <span class="seal seal--{{ statusClass() }}">{{ statusLabel() }}</span>
        @if (adventure().masterVacant) {
          <span class="seal seal--vacant">MJ recherché</span>
        }
        @if (role(); as role) {
          <span class="seal seal--role">{{ role }}</span>
        }
      </div>
      <a class="card__name" [routerLink]="['/tavern/adventures', adventure().id]">{{
        adventure().name
      }}</a>
      <p class="card__excerpt">{{ excerpt() }}</p>
      <ul class="card__meta">
        <li>{{ adventure().city }} ({{ adventure().postalCode }})</li>
        <li><dg-icon name="users" /> {{ players() }} / {{ adventure().maxPlayer }} joueurs</li>
        <li>{{ visibilityLabel() }}</li>
      </ul>
      <div class="card__footer"><ng-content /></div>
    </article>
  `,
})
export class AdventureCard {
  readonly adventure = input.required<AdventureDto>();
  /** « MJ » or « Joueur », when the card is about the reader's own adventures. */
  readonly role = input<string | null>(null);

  protected readonly statusLabel = computed(() => STATUS_LABEL[this.adventure().status as Status]);
  protected readonly statusClass = computed(() => (this.adventure().status ?? '').toLowerCase());
  protected readonly visibilityLabel = computed(
    () => VISIBILITY_LABEL[this.adventure().visibility as Visibility],
  );
  protected readonly players = computed(() => this.adventure().players?.length ?? 0);
  protected readonly excerpt = computed(() => {
    const text = (this.adventure().description ?? '').trim();
    return text.length > EXCERPT_LENGTH ? `${text.slice(0, EXCERPT_LENGTH).trimEnd()}…` : text;
  });
}
