import { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, test } from './support/test';
import { ACCOUNT, authDto, emptyPage, json, mockDnd5eApi } from './support/mocks';

const API = '**/dndgate-api';

/** Signs in, with one spell book of SRD 2014; returns the bodies of the spells added to it. */
async function signInWithABook(page: Page): Promise<unknown[]> {
  const added: unknown[] = [];
  const book = { id: 'b1', name: 'Arcane', ruleset: 'dnd5e-2014', spellList: [] as string[] };
  await page.route(`${API}/accounts/login`, (route) => json(route, authDto('t1', 'r1')));
  await page.route(`${API}/accounts/refresh`, (route) => json(route, authDto('t2', 'r2')));
  await page.route(`${API}/accounts/me`, (route) => json(route, ACCOUNT));
  await page.route(`${API}/adventures/available*`, (route) => json(route, emptyPage));
  await page.route(`${API}/spellbooks/mine`, (route) => json(route, [book]));
  await page.route(`${API}/spellbooks/b1/spells`, (route) => {
    const body = route.request().postDataJSON() as { spellIndex: string };
    added.push(body);
    book.spellList.push(body.spellIndex);
    return json(route, book);
  });
  await page.goto('/login');
  await page.getByLabel('Pseudo').fill('Gandalf');
  await page.getByLabel('Mot de passe').fill('mellon-1234');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL('/tavern/adventures');
  return added;
}

test.beforeEach(async ({ page }) => {
  await mockDnd5eApi(page);
});

test('lists spells, searches, filters by level and opens Fireball', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells');

  await expect(page.getByRole('heading', { name: 'Sorts' })).toBeVisible();
  await expect(page.getByText('5 entrées')).toBeVisible();

  await page.getByRole('searchbox', { name: 'Rechercher' }).fill('fire');
  await expect(page.getByText('3 entrées')).toBeVisible();
  await expect(page).toHaveURL(/[?&]q=fire/);

  await page.getByRole('button', { name: 'Filtres' }).click();
  await page.getByRole('checkbox', { name: 'Niveau 3' }).check();
  await expect(page.getByText('1 entrée', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/level=3/);
  await expect(page.getByRole('row')).toHaveCount(2); // header + Fireball
  await page.getByRole('button', { name: 'Fermer' }).click();

  await page.getByRole('link', { name: 'Fireball' }).click();

  await expect(page).toHaveURL('/library/dnd5e-2014/spells/fireball');
  await expect(page.getByRole('heading', { name: 'Fireball' })).toBeVisible();
  await expect(page.getByText('150 feet')).toBeVisible();
});

test('the query string alone restores the search and the filter', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells?q=fire&level=3');

  await expect(page.getByRole('searchbox', { name: 'Rechercher' })).toHaveValue('fire');
  await expect(page.getByText('1 entrée', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Filtres, 1 actif(s)' }).click();
  await expect(page.getByRole('checkbox', { name: 'Niveau 3' })).toBeChecked();
});

test('switching to SRD 2024 keeps the section', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells/fireball');

  await page.getByLabel('Jeu de règles').selectOption('dnd5e-2024');

  await expect(page).toHaveURL(/\/library\/dnd5e-2024\/spells/);
  await expect(page.getByRole('heading', { name: 'Sorts' })).toBeVisible();
});

test('the collapsed summary stays collapsed after a reload', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells');
  const toggle = page.getByRole('button', { name: 'Réduire le sommaire' });
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');

  await toggle.click();

  const reopen = page.getByRole('button', { name: 'Ouvrir le sommaire' });
  await expect(reopen).toHaveAttribute('aria-expanded', 'false');
  expect(await page.evaluate(() => localStorage.getItem('dg.sidebar.collapsed'))).toBe('true');

  await page.reload();
  await expect(reopen).toHaveAttribute('aria-expanded', 'false');

  await reopen.click();
  await expect(page.getByRole('button', { name: 'Réduire le sommaire' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  await page.reload();
  await expect(page.getByRole('button', { name: 'Réduire le sommaire' })).toBeVisible();
});

test('the summary stays in place while the page scrolls, its toggle at the bottom', async ({
  page,
}) => {
  // A short window, so that the list of spells is taller than it and the page scrolls.
  await page.setViewportSize({ width: 1280, height: 420 });
  await page.goto('/library/dnd5e-2014/spells');
  await expect(page.getByText('5 entrées')).toBeVisible();

  const sidebar = page.locator('dg-sidebar');
  const logo = page.getByRole('link', { name: "DnDGate, retour à l'accueil" });
  const toggle = page.getByRole('button', { name: 'Réduire le sommaire' });
  const before = (await sidebar.boundingBox())!;
  // Nothing above the sidebar any more: it starts at the top of the window, the logo at its top.
  expect(before.y).toBe(0);
  const logoBox = (await logo.boundingBox())!;
  expect(logoBox.y).toBeLessThan(40);
  // Across the width of the sidebar, centred.
  expect(logoBox.width).toBeGreaterThan(before.width - 40);
  expect(Math.abs(logoBox.x + logoBox.width / 2 - (before.x + before.width / 2))).toBeLessThan(2);
  // The title of the part too, between two rules.
  const titleBox = (await page.getByRole('heading', { name: 'BIBLIOTHÈQUE' }).boundingBox())!;
  expect(Math.abs(titleBox.x + titleBox.width / 2 - (before.x + before.width / 2))).toBeLessThan(2);
  // The toggle spans the sidebar, just above the account.
  const [toggleBox, accountBox] = [
    (await toggle.boundingBox())!,
    (await page.getByRole('link', { name: 'Se connecter' }).boundingBox())!,
  ];
  expect(toggleBox.width).toBeGreaterThan(before.width - 20);
  expect(toggleBox.y).toBeLessThan(accountBox.y);

  // Over the page, not over the sidebar, which has its own scroll.
  await page.mouse.move(800, 300);
  await page.mouse.wheel(0, 2000);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100);

  expect((await sidebar.boundingBox())!.y).toBeCloseTo(before.y, 0);
  await expect(logo).toBeInViewport();
  // The toggle has not moved either (a window this short shows it once the sidebar is scrolled).
  expect((await toggle.boundingBox())!.y).toBeCloseTo(toggleBox.y, 0);
});

test('texts use the whole width of the parchment and the ruleset name is centred', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1600, height: 900 });
  await page.goto('/library/dnd5e-2014/spells/fireball');
  const article = (await page.locator('article.entry').boundingBox())!;
  const paragraph = (await page.locator('.block--text p').first().boundingBox())!;
  expect(paragraph.width).toBeGreaterThan(article.width - 2);

  await page.goto('/library/dnd5e-2014/spells');
  const ruleset = (await page.locator('.list__ruleset').boundingBox())!;
  const title = (await page.getByRole('heading', { name: 'Sorts' }).boundingBox())!;
  expect(Math.abs(ruleset.x + ruleset.width / 2 - (title.x + title.width / 2))).toBeLessThan(2);
});

test('adds a spell to a spell book straight from its row of the list', async ({ page }) => {
  const added = await signInWithABook(page);
  await page.goto('/library/dnd5e-2014/spells');

  await page.getByRole('button', { name: 'Ajouter Fire Bolt au grimoire' }).click();
  await page.getByRole('menuitemcheckbox', { name: 'Arcane' }).click();

  await expect(page.getByText('Ajouté à Arcane')).toBeVisible();
  expect(added).toEqual([{ spellIndex: 'fire-bolt' }]);
});

test('on a spell, the button follows its name and unfolds its words on hover', async ({ page }) => {
  await signInWithABook(page);
  await page.goto('/library/dnd5e-2014/spells/fireball');

  const title = (await page.getByRole('heading', { name: 'Fireball' }).boundingBox())!;
  const button = page.getByRole('button', { name: 'Ajouter au grimoire' });
  const box = (await button.boundingBox())!;
  expect(box.x).toBeGreaterThan(title.x + title.width);
  expect(Math.abs(box.y + box.height / 2 - (title.y + title.height / 2))).toBeLessThan(8);

  const label = button.locator('.add-to-book__label');
  expect((await label.boundingBox())?.width ?? 0).toBeLessThan(1);
  await button.hover();
  await expect.poll(async () => (await label.boundingBox())?.width ?? 0).toBeGreaterThan(80);
  // The name does not move while the words unfold.
  expect((await page.getByRole('heading', { name: 'Fireball' }).boundingBox())!.x).toBe(title.x);
});

test('ticks several boxes of one filter and chooses the columns of the table', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells');
  await expect(page.getByText('5 entrées')).toBeVisible();
  const filters = page.getByRole('button', { name: 'Filtres' });

  // Folded at rest, the words of the button unfold on hover.
  const words = filters.locator('.icon-button__label');
  expect((await words.boundingBox())?.width ?? 0).toBeLessThan(1);
  await filters.hover();
  await expect.poll(async () => (await words.boundingBox())?.width ?? 0).toBeGreaterThan(30);

  await filters.click();
  const panel = page.getByRole('dialog', { name: 'Filtres' });
  await expect(panel).toBeInViewport();
  await panel.getByRole('checkbox', { name: 'Cleric' }).check();
  await expect(page.getByText('1 entrée', { exact: true })).toBeVisible();
  await panel.getByRole('checkbox', { name: 'Wizard' }).check();
  await expect(page).toHaveURL(/classes=Cleric&classes=Wizard/);
  await expect(page.getByText('5 entrées')).toBeVisible();

  await panel.getByRole('checkbox', { name: 'Composantes' }).uncheck();
  await expect(page.getByRole('columnheader', { name: 'Composantes' })).toHaveCount(0);
  await expect(page.getByRole('columnheader', { name: /Rituel/ })).toBeVisible();

  await page.keyboard.press('Escape');
  await expect(panel).not.toBeInViewport();
  await expect(page.getByRole('button', { name: 'Filtres, 2 actif(s)' })).toBeFocused();
});

test('the menu items keep their place when the summary is folded', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells');
  const items = page.locator('.sidebar__link dg-icon');
  await expect(items).toHaveCount(7);
  const places = async () =>
    Promise.all(
      (await items.all()).map(async (icon) => {
        const box = (await icon.boundingBox())!;
        return [Math.round(box.x + box.width / 2), Math.round(box.y)];
      }),
    );
  const open = await places();

  await page.getByRole('button', { name: 'Réduire le sommaire' }).click();
  await expect.poll(async () => (await page.locator('dg-sidebar').boundingBox())!.width).toBe(56);
  expect(await places()).toEqual(open);
  // The ruleset choice stays, folded into an icon in the middle of the rail.
  const ruleset = (await page.getByRole('button', { name: /Jeu de règles/ }).boundingBox())!;
  expect(Math.round(ruleset.x + ruleset.width / 2)).toBe(open[0][0]);
});

test('a feature of the levels table leads to its description', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/classes/wizard');
  const table = page.getByRole('table');

  await table.getByRole('link', { name: 'Arcane Recovery' }).click();
  await expect(page).toHaveURL(/#feature-arcane-recovery$/);
  await expect(page.locator('#feature-arcane-recovery')).toBeInViewport();

  await table.getByRole('link', { name: 'Arcane Tradition' }).click();
  await expect(page).toHaveURL(/#subclasses$/);
  await expect(page.getByRole('heading', { name: 'Sous-classes' })).toBeInViewport();
  // A little below the top of the window, not glued to it.
  expect(
    (await page.getByRole('heading', { name: 'Sous-classes' }).boundingBox())!.y,
  ).toBeGreaterThan(8);
});

test('on a small screen, the opened drawer lies over the page and takes the clicks', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 700 });
  await page.goto('/library/dnd5e-2014/spells');
  await page.getByRole('button', { name: 'Ouvrir le sommaire' }).click();

  await page.getByRole('link', { name: 'Classes' }).click();

  await expect(page).toHaveURL(/\/library\/dnd5e-2014\/classes$/);
});

test('the language select turns the interface and the SRD texts to English, and keeps it', async ({
  page,
}) => {
  const asked: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('dnd5eapi.co')) asked.push(request.url());
  });
  await page.goto('/library/dnd5e-2014/spells');
  await expect(page.getByRole('heading', { name: 'Sorts' })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  expect(asked.some((url) => url.includes('lang=fr-FR'))).toBe(true);

  asked.length = 0;
  await page.getByRole('button', { name: 'English' }).click();
  await expect(page.getByRole('heading', { name: 'Spells' })).toBeVisible();
  await expect(page.getByText('5 entries')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  expect(asked.length).toBeGreaterThan(0);
  expect(asked.every((url) => url.includes('lang=en'))).toBe(true);

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Spells' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'English' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});

test('an open entry is read again from the SRD in the language chosen', async ({ page }) => {
  const wizard = JSON.parse(
    readFileSync(join(__dirname, 'fixtures', 'class-wizard.json'), 'utf8'),
  ) as object;
  // Like the API: `?lang=` first, else the browser's language (`Accept-Language`, French here).
  const asked: string[] = [];
  await page.route('https://www.dnd5eapi.co/api/2014/classes/wizard*', async (route) => {
    const request = route.request();
    if (!request.url().includes('/wizard?') && !request.url().endsWith('/wizard')) {
      return route.fallback();
    }
    asked.push(request.url());
    const lang =
      new URL(request.url()).searchParams.get('lang') ??
      (await request.allHeaders())['accept-language'];
    return json(route, { ...wizard, name: lang?.startsWith('fr') ? 'Magicien' : 'Wizard' });
  });

  await page.goto('/library/dnd5e-2014/classes/wizard');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Magicien');

  await page.getByRole('button', { name: 'English' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Wizard');
  expect(asked.map((url) => new URL(url).search)).toEqual(['?lang=fr-FR', '?lang=en']);

  // Back in French, the answer already read in French is shown again.
  await page.getByRole('button', { name: 'Français' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Magicien');
});
