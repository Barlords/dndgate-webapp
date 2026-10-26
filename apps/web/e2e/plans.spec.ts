import { Page } from '@playwright/test';
import { expect, test } from './support/test';
import { fixture, json, mockDnd5eApi, mockPlanCatalog, mockSession } from './support/mocks';

const API = '**/dndgate-api';
const CHECKOUT_URL = 'https://checkout.stripe.test/session';

async function signIn(page: Page, returnUrl?: string) {
  await page.goto(returnUrl ? `/login?returnUrl=${encodeURIComponent(returnUrl)}` : '/login');
  await page.getByLabel('Pseudo').fill('Gandalf');
  await page.getByLabel('Mot de passe').fill('mellon-1234');
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test.beforeEach(async ({ page }) => {
  await mockDnd5eApi(page);
  await mockSession(page);
  await mockPlanCatalog(page);
});

test('an anonymous reader sees the offers, signs in and comes back to them', async ({ page }) => {
  await page.goto('/plans');
  await expect(page.getByRole('heading', { name: 'Maître du Donjon' })).toBeVisible();
  await expect(page.getByText('Le plus choisi')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Conditions générales' })).toHaveAttribute(
    'href',
    '/terms',
  );
  await page.route(`${API}/accounts/me/subscription`, (route) =>
    json(route, fixture('subscription-free.json')),
  );

  await page.getByRole('link', { name: 'Se connecter' }).first().click();
  await page.getByLabel('Pseudo').fill('Gandalf');
  await page.getByLabel('Mot de passe').fill('mellon-1234');
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page).toHaveURL('/plans');
  await expect(page.getByRole('button', { name: 'Souscrire' }).first()).toBeVisible();
});

test('a free reader chooses the yearly Maître du Donjon and reaches Checkout', async ({ page }) => {
  let checkoutBody: unknown;
  await page.route(`${API}/accounts/me/subscription`, (route) =>
    json(route, fixture('subscription-free.json')),
  );
  await page.route(`${API}/billing/checkout-session`, (route) => {
    checkoutBody = route.request().postDataJSON();
    return json(route, { url: CHECKOUT_URL });
  });
  await page.route(CHECKOUT_URL, (route) =>
    route.fulfill({
      contentType: 'text/html',
      body: '<!doctype html><title>Checkout</title><h1>Stripe Checkout</h1>',
    }),
  );
  await signIn(page, '/plans');
  await expect(page).toHaveURL('/plans');

  await page.getByRole('button', { name: 'Annuel' }).click();
  await page
    .getByRole('article')
    .filter({ has: page.getByRole('heading', { name: 'Maître du Donjon' }) })
    .getByRole('button', { name: 'Souscrire' })
    .click();

  await expect(page).toHaveURL(CHECKOUT_URL);
  await expect(page.getByRole('heading', { name: 'Stripe Checkout' })).toBeVisible();
  expect(checkoutBody).toEqual({ planId: 'dungeon-master', interval: 'yearly' });
});

test('back from Checkout, the page asks again until the API grants the new plan', async ({
  page,
}) => {
  await signIn(page);
  await expect(page).toHaveURL('/tavern/adventures');
  let polls = 0;
  await page.route(`${API}/accounts/me/subscription`, (route) => {
    polls++;
    return json(
      route,
      fixture(polls === 1 ? 'subscription-free.json' : 'subscription-dungeon-master.json'),
    );
  });

  await page.goto('/plans/success');

  await expect(page.getByText('Nous vérifions ton paiement')).toBeVisible();
  await expect(page.getByText('Bienvenue dans l’offre Maître du Donjon')).toBeVisible({
    timeout: 10_000,
  });
  expect(polls).toBe(2);
});

test('a plan limit refuses the adventure and the toast leads to the offers', async ({ page }) => {
  await page.route(`${API}/adventures`, (route) =>
    json(
      route,
      {
        type: 'urn:dndgate:plan-limit-reached',
        title: 'Plan limit reached',
        status: 409,
        limit: 'activeAdventuresAsMaster',
        max: 1,
        planId: 'adventurer',
        upgradePlanId: 'hero',
        decidedBy: 'account',
      },
      409,
    ),
  );
  await page.route(`${API}/accounts/me/subscription`, (route) =>
    json(route, fixture('subscription-free.json')),
  );
  await signIn(page);
  await expect(page).toHaveURL('/tavern/adventures');
  await page.goto('/tavern/new');

  await page.getByLabel('Nom', { exact: true }).fill('La mine');
  await page.getByLabel('Description').fill('Une mine perdue');
  await page.getByLabel('Nombre de joueurs maximum').fill('4');
  await page.getByLabel('Code postal').fill('75001');
  await page.getByLabel('Ville').fill('Paris');
  await page.getByRole('button', { name: 'Créer l’aventure' }).click();

  await expect(page.getByText('Aventures actives en tant que MJ')).toBeVisible();
  await page.getByRole('button', { name: 'Voir les offres' }).click();
  await expect(page).toHaveURL('/plans');
});

test('the terms page is public and marks every section to complete', async ({ page }) => {
  await page.goto('/terms');

  await expect(page.getByRole('heading', { name: 'Conditions générales de vente' })).toBeVisible();
  await expect(page.getByRole('note')).toContainText('brouillon à valider');
  await expect(page.getByText('À compléter')).toHaveCount(11);
});
