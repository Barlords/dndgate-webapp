import { Page } from '@playwright/test';
import { expect, test } from './support/test';
import { ACCOUNT, authDto, emptyPage, json, mockDnd5eApi } from './support/mocks';

const API = '**/dndgate-api';

/** Mocks the account routes; the returned counters tell how often the API was called. */
async function mockAccountApi(page: Page) {
  const calls = { refreshes: 0, logouts: 0 };
  await page.route(`${API}/accounts/login`, (route) =>
    json(route, authDto('token-1', 'refresh-1')),
  );
  await page.route(`${API}/accounts/me`, (route) => json(route, ACCOUNT));
  await page.route(`${API}/accounts/refresh`, async (route) => {
    calls.refreshes++;
    // Slow enough for the second 401 to arrive while this refresh is in flight.
    await new Promise((resolve) => setTimeout(resolve, 300));
    await json(route, authDto('token-2', 'refresh-2'));
  });
  await page.route(`${API}/accounts/logout`, (route) => {
    calls.logouts++;
    return route.fulfill({ status: 204 });
  });
  await page.route(`${API}/adventures/available*`, (route) => json(route, emptyPage));
  return calls;
}

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Pseudo').fill('Gandalf');
  await page.getByLabel('Mot de passe').fill('mellon-1234');
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test.beforeEach(async ({ page }) => {
  await mockDnd5eApi(page);
});

test('signing in leads to the Tavern', async ({ page }) => {
  await mockAccountApi(page);

  await signIn(page);

  await expect(page).toHaveURL('/tavern/quests');
  await expect(page.getByRole('heading', { name: 'Tableau des quêtes' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Gandalf' })).toBeVisible();
});

test('two simultaneous 401 trigger a single refresh', async ({ page }) => {
  const calls = await mockAccountApi(page);
  const retried: string[] = [];
  // The two lists of "my adventures" are requested together; the first token is expired.
  for (const path of ['as-player', 'as-master']) {
    await page.route(`${API}/adventures/${path}*`, (route) => {
      const token = route.request().headers()['authorization'];
      if (token === 'Bearer token-2') {
        retried.push(path);
        return json(route, emptyPage);
      }
      return json(route, { status: 401, title: 'Unauthorized' }, 401);
    });
  }
  await signIn(page);
  await expect(page).toHaveURL('/tavern/quests');

  await page.getByRole('link', { name: 'En cours' }).click();

  await expect.poll(() => retried.sort()).toEqual(['as-master', 'as-player']);
  expect(calls.refreshes).toBe(1);
});

test('signing out returns to the home page and closes the Tavern', async ({ page }) => {
  const calls = await mockAccountApi(page);
  await signIn(page);
  await expect(page).toHaveURL('/tavern/quests');

  await page.getByRole('link', { name: 'Gandalf' }).click();
  await page.getByRole('button', { name: 'Se déconnecter', exact: true }).click();

  await expect(page).toHaveURL('/');
  expect(calls.logouts).toBe(1);
  await page.goto('/tavern');
  await expect(page).toHaveURL('/login?returnUrl=%2Ftavern%2Fquests');
});
