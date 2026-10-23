import { expect, test } from './support/test';
import { mockDnd5eApi } from './support/mocks';

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

  await page.getByLabel('Niveau').selectOption('3');
  await expect(page.getByText('1 entrée', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/level=3/);
  await expect(page.getByRole('row')).toHaveCount(2); // header + Fireball

  await page.getByRole('link', { name: 'Fireball' }).click();

  await expect(page).toHaveURL('/library/dnd5e-2014/spells/fireball');
  await expect(page.getByRole('heading', { name: 'Fireball' })).toBeVisible();
  await expect(page.getByText('150 feet')).toBeVisible();
});

test('the query string alone restores the search and the filter', async ({ page }) => {
  await page.goto('/library/dnd5e-2014/spells?q=fire&level=3');

  await expect(page.getByRole('searchbox', { name: 'Rechercher' })).toHaveValue('fire');
  await expect(page.getByLabel('Niveau')).toHaveValue('3');
  await expect(page.getByText('1 entrée', { exact: true })).toBeVisible();
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
