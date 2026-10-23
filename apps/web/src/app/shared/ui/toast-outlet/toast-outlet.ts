import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../button/button';

/** Bottom-right stack of the notifier's toasts, announced politely to assistive technologies. */
@Component({
  selector: 'dg-toast-outlet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button],
  template: `
    <div class="dg-toasts" aria-live="polite">
      @for (toast of notifier.toasts(); track toast.id) {
        <div class="dg-toast" [class.dg-toast--error]="toast.kind === 'error'">
          <div class="dg-toast__body">
            <p class="dg-toast__message">{{ toast.message }}</p>
            @if (toast.detail) {
              <p class="dg-toast__detail">{{ toast.detail }}</p>
            }
            @if (toast.reference) {
              <p class="dg-toast__reference">
                Référence : <code>{{ toast.reference }}</code>
              </p>
            }
            @if (toast.action; as action) {
              <dg-button variant="ghost" (click)="notifier.act(toast)">{{
                action.label
              }}</dg-button>
            }
          </div>
          <button
            type="button"
            class="dg-toast__close"
            aria-label="Fermer la notification"
            (click)="notifier.dismiss(toast.id)"
          >
            ×
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .dg-toasts {
      position: fixed;
      right: var(--dg-space-4);
      bottom: var(--dg-space-4);
      z-index: 2000;
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-3);
      width: min(24rem, calc(100vw - 2 * var(--dg-space-4)));
      pointer-events: none;
    }

    .dg-toast {
      pointer-events: auto;
      display: flex;
      gap: var(--dg-space-3);
      align-items: flex-start;
      padding: var(--dg-space-3) var(--dg-space-4);
      color: var(--dg-ink);
      background: linear-gradient(160deg, var(--dg-parchment-0), var(--dg-parchment-1));
      border: 1px solid var(--dg-parchment-border);
      border-left: 5px solid var(--dg-gold);
      border-radius: var(--dg-radius);
      box-shadow:
        inset 0 0 18px var(--dg-parchment-shadow),
        0 6px 18px #0009;
      animation: dg-toast-in 180ms ease-out;
    }

    .dg-toast--error {
      border-left-color: var(--dg-blood);
    }

    .dg-toast__body {
      flex: 1;
      min-width: 0;
    }

    .dg-toast__message {
      margin: 0;
      font-weight: 600;
    }

    .dg-toast--error .dg-toast__message {
      color: var(--dg-blood);
    }

    .dg-toast__detail {
      margin: var(--dg-space-1) 0 0;
      overflow-wrap: anywhere;
    }

    .dg-toast__reference {
      margin: var(--dg-space-2) 0 0;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }

    .dg-toast__reference code {
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
      user-select: all;
      overflow-wrap: anywhere;
    }

    dg-button {
      display: inline-block;
      margin-top: var(--dg-space-2);
    }

    .dg-toast__close {
      flex: none;
      padding: 0 var(--dg-space-1);
      font-size: var(--dg-text-lg);
      line-height: 1;
      color: var(--dg-ink-muted);
      background: none;
      border: 0;
      cursor: pointer;
    }

    .dg-toast__close:focus-visible {
      outline: 2px solid var(--dg-gold-deep);
      outline-offset: 2px;
    }

    @keyframes dg-toast-in {
      from {
        opacity: 0;
        transform: translateY(0.5rem);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .dg-toast {
        animation: none;
      }
    }
  `,
})
export class ToastOutlet {
  protected readonly notifier = inject(NotifierService);
}
