import { test as base } from '@playwright/test';

const APP_ORIGIN = 'http://localhost:4200';
const RULES_ORIGIN = 'https://www.dnd5eapi.co';

/**
 * `test` with a safety net: any request to our API or to a foreign host that a spec did not mock
 * is aborted and recorded, and fails the test at teardown. The net is registered first; routes a
 * spec adds afterwards win (Playwright tries the latest route first).
 */
export const test = base.extend<{ unmocked: string[] }>({
  unmocked: [
    async ({ page }, use) => {
      const recorded: string[] = [];
      const record = (route: import('@playwright/test').Route) => {
        recorded.push(`${route.request().method()} ${route.request().url()}`);
        return route.abort();
      };
      await page.route((url) => url.origin !== APP_ORIGIN && url.origin !== RULES_ORIGIN, record);
      await page.route('**/dndgate-api/**', record);
      await use(recorded);
      if (recorded.length > 0) {
        throw new Error(`Unmocked requests:\n${recorded.join('\n')}`);
      }
    },
    { auto: true },
  ],
});

export { expect } from '@playwright/test';
