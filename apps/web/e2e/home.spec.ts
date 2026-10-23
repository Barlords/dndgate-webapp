import { expect, test } from './support/test';
import { mockDnd5eApi } from './support/mocks';

test.beforeEach(async ({ page }) => {
  await mockDnd5eApi(page);
});

test('hovering the Library lights it up, and a click opens it', async ({ page }) => {
  await page.goto('/');
  const library = page.locator('dg-home-half.home-half--library');
  const tavern = page.locator('dg-home-half.home-half--tavern');

  await library.getByRole('link').hover();

  await expect(library).toHaveClass(/is-active/);
  await expect(tavern).toHaveClass(/is-dimmed/);
  // The gold frame is drawn (dash offset back to zero) and the dust motes are rendered and moving.
  await expect(library.locator('.home-half__frame rect')).toHaveCSS('stroke-dashoffset', '0px');
  await expect(library.locator('dg-particles')).toHaveClass(/is-active/);
  await expect(library.locator('dg-particles .mote').first()).toBeAttached();

  await library.getByRole('link').click();
  await expect(page).toHaveURL(/\/library\/dnd5e-2014$/);
});

test('the Tavern sends a signed-out visitor to the login page first', async ({ page }) => {
  await page.goto('/');

  await page.locator('dg-home-half.home-half--tavern').getByRole('link').click();

  await expect(page).toHaveURL('/login?returnUrl=%2Ftavern%2Fadventures');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
});
