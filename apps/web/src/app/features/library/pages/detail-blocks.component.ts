import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DetailBlock } from '../rulesets/ruleset.model';
import { MarkdownBlock, parseMarkdown } from './light-markdown';

type ViewBlock =
  | { kind: 'text'; title?: string; markdown: MarkdownBlock[] }
  | Extract<DetailBlock, { kind: 'stats' | 'table' }>
  | {
      kind: 'entries';
      title?: string;
      items: { name: string; markdown: MarkdownBlock[]; link: string[] | null }[];
    };

/**
 * The body of an entry page: the ruleset's blocks (text, stats, table, entries), in order. Texts
 * are parsed light markdown rendered through the template, never injected as HTML.
 */
@Component({
  selector: 'dg-detail-blocks',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, RouterLink],
  template: `
    @for (block of view(); track $index) {
      <section class="block" [class]="'block--' + block.kind">
        @if (block.title) {
          <h2 class="block__title">{{ block.title }}</h2>
        }
        @switch (block.kind) {
          @case ('text') {
            <ng-container *ngTemplateOutlet="markdown; context: { $implicit: block.markdown }" />
          }
          @case ('stats') {
            <dl class="block__stats">
              @for (item of block.items; track $index) {
                <div class="block__stat">
                  <dt>{{ item.label }}</dt>
                  <dd>{{ item.value }}</dd>
                </div>
              }
            </dl>
          }
          @case ('table') {
            <div class="block__scroll">
              <table>
                <thead>
                  <tr>
                    @for (column of block.columns; track $index) {
                      <th scope="col">{{ column }}</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of block.rows; track $index) {
                    <tr>
                      @for (cell of row; track $index) {
                        <td>{{ cell }}</td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
          @case ('entries') {
            @for (item of block.items; track $index) {
              <div class="block__entry">
                <h3 class="block__entry-name">
                  @if (item.link; as link) {
                    <a [routerLink]="link">{{ item.name }}</a>
                  } @else {
                    {{ item.name }}
                  }
                </h3>
                <ng-container *ngTemplateOutlet="markdown; context: { $implicit: item.markdown }" />
              </div>
            }
          }
        }
      </section>
    }

    <ng-template #markdown let-blocks>
      @for (part of blocks; track $index) {
        @switch (part.kind) {
          @case ('paragraph') {
            <p><ng-container *ngTemplateOutlet="inline; context: { $implicit: part.spans }" /></p>
          }
          @case ('heading') {
            @if (part.level === 3) {
              <h3>
                <ng-container *ngTemplateOutlet="inline; context: { $implicit: part.spans }" />
              </h3>
            } @else {
              <h4>
                <ng-container *ngTemplateOutlet="inline; context: { $implicit: part.spans }" />
              </h4>
            }
          }
          @case ('list') {
            <ul>
              @for (item of part.items; track $index) {
                <li><ng-container *ngTemplateOutlet="inline; context: { $implicit: item }" /></li>
              }
            </ul>
          }
          @case ('table') {
            <div class="block__scroll">
              <table>
                @if (part.header) {
                  <thead>
                    <tr>
                      @for (cell of part.header; track $index) {
                        <th scope="col">
                          <ng-container *ngTemplateOutlet="inline; context: { $implicit: cell }" />
                        </th>
                      }
                    </tr>
                  </thead>
                }
                <tbody>
                  @for (row of part.rows; track $index) {
                    <tr>
                      @for (cell of row; track $index) {
                        <td>
                          <ng-container *ngTemplateOutlet="inline; context: { $implicit: cell }" />
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      }
    </ng-template>

    <ng-template #inline let-spans>
      @for (span of spans; track $index) {
        @if (span.bold && span.italic) {
          <strong
            ><em>{{ span.text }}</em></strong
          >
        } @else if (span.bold) {
          <strong>{{ span.text }}</strong>
        } @else if (span.italic) {
          <em>{{ span.text }}</em>
        } @else {
          <ng-container>{{ span.text }}</ng-container>
        }
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
    }

    .block {
      margin-top: var(--dg-space-5);
    }

    .block__title {
      margin: 0 0 var(--dg-space-3);
      padding-bottom: var(--dg-space-1);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-lg);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-title-color);
      border-bottom: 1px solid var(--dg-parchment-border);
    }

    h3,
    h4 {
      margin: var(--dg-space-4) 0 var(--dg-space-2);
      font-family: var(--dg-font-title);
      letter-spacing: 0.08em;
      color: var(--dg-title-color);
    }

    h3 {
      font-size: var(--dg-text-md);
    }

    h4 {
      font-size: var(--dg-text-sm);
    }

    p {
      margin: 0 0 var(--dg-space-3);
      line-height: 1.6;
    }

    ul {
      margin: 0 0 var(--dg-space-3);
      padding-left: var(--dg-space-5);
    }

    li::marker {
      color: var(--dg-blood);
    }

    .block__stats {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
      gap: var(--dg-space-3);
      margin: 0;
    }

    .block__stat {
      padding: var(--dg-space-2) var(--dg-space-3);
      border-left: 2px solid var(--dg-parchment-border);
    }

    dt {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      color: var(--dg-ink-muted);
    }

    dd {
      margin: 0;
      font-weight: 600;
    }

    .block__scroll {
      overflow-x: auto;
      margin-bottom: var(--dg-space-3);
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    th,
    td {
      padding: var(--dg-space-1) var(--dg-space-3);
      text-align: left;
      border-bottom: 1px solid color-mix(in srgb, var(--dg-parchment-border) 60%, transparent);
    }

    th {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
      border-bottom-color: var(--dg-parchment-border);
    }

    .block__entry {
      margin-bottom: var(--dg-space-3);
    }

    .block__entry-name {
      margin: 0 0 var(--dg-space-1);
      font-family: var(--dg-font-body);
      font-size: var(--dg-text-md);
      font-style: italic;
      letter-spacing: 0;
      color: var(--dg-ink);

      a {
        color: var(--dg-blood);
      }
    }
  `,
})
export class DetailBlocks {
  readonly blocks = input.required<DetailBlock[]>();
  /** The ruleset the links of `entries` blocks point into. */
  readonly rulesetId = input.required<string>();

  protected readonly view = computed<ViewBlock[]>(() =>
    this.blocks().map((block): ViewBlock => {
      switch (block.kind) {
        case 'text':
          return {
            kind: 'text',
            title: block.title,
            markdown: block.paragraphs.flatMap((paragraph) => parseMarkdown(paragraph)),
          };
        case 'entries':
          return {
            kind: 'entries',
            title: block.title,
            items: block.items.map((item) => ({
              name: item.name,
              markdown: parseMarkdown(item.text),
              link: item.link
                ? ['/library', this.rulesetId(), item.link.section, item.link.index]
                : null,
            })),
          };
        default:
          return block;
      }
    }),
  );
}
