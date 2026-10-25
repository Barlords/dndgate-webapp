import { DetailBlock, EntryDetail } from '../../ruleset.model';
import { ApiRef, EntryTexts, SOURCE_TEXTS } from './common';

export interface ApiRule {
  index: string;
  name: string;
  /** Markdown, opening with a `# <name>` heading that the page already shows as its title. */
  desc: string;
  children?: ApiRef[];
}

export function mapRule(rule: ApiRule, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  const body = rule.desc.replace(/^#\s+[^\n]*\n?/, '').trim();
  const blocks: DetailBlock[] = body === '' ? [] : [{ kind: 'text', paragraphs: [body] }];
  if (rule.children && rule.children.length > 0) {
    blocks.push({
      kind: 'entries',
      title: t.inThisSection,
      items: rule.children.map((child) => ({
        name: child.name,
        text: '',
        link: { section: 'rules', index: child.index },
      })),
    });
  }
  return { index: rule.index, name: rule.name, tags: [], blocks };
}
