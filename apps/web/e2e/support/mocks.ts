import { Page, Route } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

function fixture(name: string): string {
  return readFileSync(join(__dirname, '..', 'fixtures', name), 'utf8');
}

const json = (route: Route, body: string | object, status = 200) =>
  route.fulfill({
    status,
    contentType: 'application/json',
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });

/** Answers dnd5eapi.co (REST and GraphQL) with the small fixtures of `e2e/fixtures`. */
export async function mockDnd5eApi(page: Page): Promise<void> {
  await page.route('https://www.dnd5eapi.co/**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith('/graphql/')) {
      const query = String((route.request().postDataJSON() as { query?: string }).query);
      return json(
        route,
        fixture(query.includes('features(') ? 'class-wizard-features.json' : 'spells-list.json'),
      );
    }
    if (url.pathname.endsWith('/classes/wizard')) {
      return json(route, fixture('class-wizard.json'));
    }
    if (url.pathname.endsWith('/classes/wizard/levels')) {
      return json(route, fixture('class-wizard-levels.json'));
    }
    if (url.pathname.endsWith('/spells/fireball')) {
      return json(route, fixture('spell-fireball.json'));
    }
    if (url.pathname.endsWith('/rules')) {
      return json(route, fixture('rules-list.json'));
    }
    return json(route, { error: 'Not found' }, 404);
  });
}

export const ACCOUNT = {
  id: '11111111-1111-4111-8111-111111111111',
  pseudo: 'Gandalf',
  email: 'gandalf@example.org',
};

export function authDto(token: string, refreshToken: string) {
  return {
    accountId: ACCOUNT.id,
    token,
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    refreshToken,
    refreshTokenExpiresAt: new Date(Date.now() + 7 * 24 * 3_600_000).toISOString(),
  };
}

/** Mocks the sign-in routes: the reader Gandalf, nothing in the Tavern. */
export async function mockSession(page: Page, apiPrefix = '**/dndgate-api'): Promise<void> {
  await page.route(`${apiPrefix}/accounts/login`, (route) => json(route, authDto('t1', 'r1')));
  await page.route(`${apiPrefix}/accounts/refresh`, (route) => json(route, authDto('t2', 'r2')));
  await page.route(`${apiPrefix}/accounts/me`, (route) => json(route, ACCOUNT));
  await page.route(`${apiPrefix}/adventures/available*`, (route) => json(route, emptyPage));
}

/** Answers `GET /plans` with the small catalog of `e2e/fixtures`. */
export async function mockPlanCatalog(page: Page, apiPrefix = '**/dndgate-api'): Promise<void> {
  await page.route(`${apiPrefix}/plans`, (route) => json(route, fixture('plan-catalog.json')));
}

export { fixture };

export const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

export { json };
