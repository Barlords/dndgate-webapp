# Subscriptions — Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Angular site shows the offers, sells them through Stripe Checkout, shows "my subscription", turns every plan refusal into an upgrade prompt, and exposes the new character rules (reserve, active character, NPCs), the chat history banners and the badges — PR 2 of the subscriptions design.

**Architecture:** Everything is built on the generated client (`npm run generate:api` from `contracts/openapi.yaml`, functions mode: `fn(http, rootUrl, params)`). Two shared services carry the new state: `PlanCatalog` (the offers, `GET /plans`, cached) and the extended `AdventureContext` (the caller's entitlements in an adventure). Plan refusals are read by an extended `ApiProblem` and shown by `NotifierService` with a "see the offers" action. Texts live in a new `plans` section of the fr/en dictionaries.

**Tech Stack:** Angular 22 (standalone, signals, OnPush), `@angular/cdk` dialogs, Vitest (`@angular/build:unit-test`), Playwright, ng-openapi-gen.

**Spec:** `docs/superpowers/specs/2026-10-25-subscriptions-design.md` (French), section "Front (web)" and the rules it relies on; the API behaviour is described in `README.md` section "Abonnements et paiement".

All paths are relative to `apps/web/` unless they start with `apps/api/`, `contracts/` or `docs/`.

## Global Constraints

- Gate of every task, from `apps/web`: `npm run lint`, `npm run test:ci`, `npm run build` all pass (the CI runs exactly these); `npm run e2e` passes from Task 12 on. One spec file: `npx ng test --watch=false --include=<path>`.
- Follow the patterns of the codebase: standalone components with `dg-` selectors, `ChangeDetectionStrategy.OnPush`, signals, `inject()`, the generated functions with `ApiConfiguration.rootUrl`, `toApiProblem`, `NotifierService`, `ConfirmService` (`confirm`, `typeToConfirm`), the `shared/ui` kit (`dg-button`, `dg-parchment`, `dg-loading`, `dg-load-error`), the visual tokens `--dg-*`.
- Every visible text comes from the dictionaries: add it to `core/i18n/fr/*` (the reference, French) and the same key in `core/i18n/en/*` (the `Dictionary` type makes a missing key a compile error). Plan names and descriptions are texts keyed by plan id (`adventurer`, `hero`, `dungeon-master`, `legendary`); unknown ids show the id itself.
- Offers vocabulary (fr): Aventurier, Héros, Maître du Donjon, Légendaire; badges: Fondateur, MJ, Légende; "le plus choisi" on the `highlighted` plan; yearly price shown as "2 mois offerts".
- Prices are cents (`PlanPriceDto.amount`), shown as euros with the reader's locale (`Intl.NumberFormat`, currency from the catalog).
- Never trust the browser return of Checkout: the plan shown is always what `GET /accounts/me/subscription` answers.
- An irreversible deletion always asks to retype the name (`ConfirmService.typeToConfirm`, `expected` = name).
- The API answers to rely on (from the contract): plan refusal 409 `type = urn:dndgate:plan-limit-reached` with `limit`, `max`, `planId`, `upgradePlanId|null`, `decidedBy` (`account`|`master`); feature refusal 403 `type = urn:dndgate:feature-not-included` with `feature`, `planId`, `upgradePlanId|null`, `decidedBy`; billing off 503; `GET /adventures/{id}/entitlements` → `{ features: string[], limits: { <key>: { used, max } } }` with keys `charactersPerPlayerAndAdventure`, `npcsPerAdventure`, `messagesPerAdventure`, `masterNotesPerAdventure`, `historicalNotesPerAdventure`; feature keys `characterPictures`, `video`.
- Chat banners: from 80 % to 99 % of `messagesPerAdventure` "historique bientôt plein", at 100 % "chaque nouveau message efface le plus ancien"; the master also sees the offer that raises the limit.
- Commit after each task with a conventional message (`feat(web): …`), ending with the Co-Authored-By trailer of your environment.

## Review Focus

1. **The reader returns from Checkout before the webhook arrived** — the success page must poll and then say "paiement en cours de confirmation", never show the old plan as final nor invent the new one. Task 4 pins it with `the_success_page_keeps_polling_until_the_plan_changes_and_then_stops`.
2. **A refusal decided by the master's plan shown to a player** must not tell the player to upgrade themselves — Task 2 pins it with `a_master_decided_refusal_tells_the_player_to_ask_the_master`.
3. **Quitting an adventure with a full reserve** must never delete a character without its name retyped, and cancelling at any step must leave the seat — Task 9 pins it with `cancelling_the_quit_dialog_sends_nothing`.
4. **An anonymous reader on the offers page** must be sent to the login page and come back to the offers, not to an error — Task 3 pins it with `an_anonymous_reader_is_sent_to_login_and_back`.
5. **Billing off (503 / `billingEnabled = false`)** must show the offers as information without broken buttons — Task 3 pins it with `with_billing_off_the_buttons_say_unavailable`.

---

### Task 1: Contract details left for the web PR (API)

**Files:**
- Modify: `apps/api/src/main/java/fr/barlords/dndgateapi/client/dto/gamecharacter/GameCharacterDto.java` (`adventureId` nullable in the schema)
- Modify: `apps/api/src/main/java/fr/barlords/dndgateapi/client/dto/plan/PlanDto.java` (`badge` nullable with allowable values `dungeon-master`, `legend`)
- Modify: `apps/api/src/main/java/fr/barlords/dndgateapi/bootstrap/config/OpenApiConfig.java` (a customizer adding `null` to the `enum` of every nullable enumerated property, so `planBadge` / `badge` validate a null)
- Modify: `contracts/openapi.yaml` (regenerated), `apps/api/src/test/.../integration/OpenApiContractIntegrationTest.java` (assertions)

**Interfaces:**
- Produces: a contract where `GameCharacterDto.adventureId`, `AccountDto.planBadge`, `PublicAccountDto.planBadge`, `PlanDto.badge` are nullable and the enums list `null`.

- [ ] **Step 1: Extend `OpenApiContractIntegrationTest.the_answers_say_which_values_and_which_nulls_they_hold`** with the three properties above (nullable, enum values, enum contains null for the badge fields).
- [ ] **Step 2: Run it** — from `apps/api`: `mvnw.cmd test -Dtest=OpenApiContractIntegrationTest` → FAIL.
- [ ] **Step 3: Annotate the DTOs, add the customizer, regenerate** (`mvnw.cmd test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true`).
- [ ] **Step 4: Verify** — `mvnw.cmd clean verify` (API) PASS; from `apps/web`: `npm run generate:api && npm run build` PASS.
- [ ] **Step 5: Commit** — `fix(api): the last nullable answers of the contract say so`

---

### Task 2: Plan catalog, plan refusals and their prompt

**Files:**
- Create: `core/plans/plan-catalog.service.ts` (+ spec), `core/plans/plan-texts.ts` (+ spec)
- Create: `core/i18n/fr/plans.ts`, `core/i18n/en/plans.ts`; Modify: `core/i18n/fr/index.ts`, `core/i18n/en/index.ts`
- Modify: `core/errors/api-problem.ts` (+ spec), `core/errors/notifier.service.ts` (+ spec)

**Interfaces:**
- Produces:
  - `ApiProblem` gains `type?: string` and `plan?: PlanRefusal` where `interface PlanRefusal { kind: 'limit' | 'feature'; key: string; max?: number; planId?: string; upgradePlanId: string | null; decidedBy: 'account' | 'master' }` — filled by `toApiProblem` only for the two plan `type` URNs.
  - `@Injectable({providedIn:'root'}) class PlanCatalog { readonly catalog: Signal<PlanCatalogDto | null>; load(): Observable<PlanCatalogDto>; plan(id: string): PlanDto | undefined }` — one `GET /plans` per session (shareReplay, retried on the next call after a failure).
  - `plan-texts.ts`: `planName(id: string, t: Dictionary): string`, `limitName(key: string, t: Dictionary): string`, `featureName(key: string, t: Dictionary): string`, `formatPrice(cents: number, currency: string, language: Language): string`.
  - Dictionary section `plans` (fr reference) with at least: `names` (four plans), `taglines` (four), `limits` (the nine limit keys, human labels), `features` (five feature keys), `badges` (`founder`, `dungeonMaster`, `legend`), `mostChosen`, `monthly`, `yearly`, `twoMonthsFree`, `perMonth`, `perYear`, `free`, and refusal texts: `limitReached(limitLabel, max)`, `featureMissing(featureLabel)`, `upgradeTo(planName)`, `askMaster(planName)`, `seeOffers`.
  - `NotifierService.error(problem, context?)`: when `problem.plan` is set, the toast message is the refusal text and, when `upgradePlanId` is not null, carries an action `seeOffers` navigating to `/plans` (`decidedBy = account`) — or the text `askMaster(plan)` without action (`decidedBy = master`). The toast stays until dismissed.

- [ ] **Step 1: Failing tests**
  - `api-problem.spec.ts`: `a_plan_limit_problem_is_read` (409 body with the URN ⇒ `plan = {kind:'limit', key:'activeAdventuresAsMaster', max:1, planId:'adventurer', upgradePlanId:'hero', decidedBy:'account'}`); `a_feature_problem_is_read`; `another_409_has_no_plan`.
  - `notifier.service.spec.ts`: `an_account_decided_refusal_offers_the_plans_page`; `a_master_decided_refusal_tells_the_player_to_ask_the_master` (no action, text names the plan); `a_refusal_without_upgrade_has_no_action`.
  - `plan-catalog.service.spec.ts`: `the_catalog_is_fetched_once`, `a_failed_fetch_is_retried_by_the_next_call`.
  - `plan-texts.spec.ts`: `known_ids_have_their_names_unknown_ids_show_the_id`; `prices_are_cents_in_the_reader_locale` (`499, 'EUR', 'fr'` ⇒ `4,99 €`).
- [ ] **Step 2: Run** — `npx ng test --watch=false --include=src/app/core` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Gate** — lint, test:ci, build PASS.
- [ ] **Step 5: Commit** — `feat(web): plan catalog and upgrade prompts on plan refusals`

---

### Task 3: The offers page

**Files:**
- Create: `features/plans/plans.page.ts` (+ spec), `features/plans/plan-card.ts`
- Create: `core/plans/billing.service.ts` (+ spec)
- Modify: `app.routes.ts` (`plans` route, public), the navigation where the account entries are (sidebar footer: an "Offres" link), `core/i18n/fr|en/plans.ts`

**Interfaces:**
- Consumes: Task 2 `PlanCatalog`, `plan-texts`.
- Produces:
  - `@Injectable({providedIn:'root'}) class Billing { readonly subscription: Signal<SubscriptionDto | null>; loadMine(): Observable<SubscriptionDto>; checkout(planId: string, interval: 'monthly' | 'yearly'): void; openPortal(): void }` — `checkout`/`openPortal` call `POST /billing/checkout-session` / `POST /billing/portal-session` and then `window.location.assign(url)` (through an injectable `REDIRECT` token so tests can observe it); a 503 shows `t.plans.billingUnavailable`; a 409 on checkout ⇒ `openPortal()` instead (already subscribed).
  - Route `/plans` → `PlansPage`: the catalog in order, a monthly/yearly toggle (default monthly), per plan: name, tagline, price (or "Gratuit"), the yearly price with "2 mois offerts", the limits and features as a readable list, badge, "le plus choisi" on `highlighted`. Call to action per state: anonymous ⇒ "Se connecter" to `/login?returnUrl=/plans`; logged and on this plan ⇒ "Votre offre" (disabled); logged on the free plan and a paid card ⇒ "Choisir" (checkout); logged with a paid subscription ⇒ "Gérer mon abonnement" (portal); `billingEnabled = false` ⇒ disabled buttons labelled "Paiement indisponible". A link to `/terms` under the cards.
- [ ] **Step 1: Failing tests** (`plans.page.spec.ts`, `billing.service.spec.ts`): `the_four_offers_are_shown_in_catalog_order_with_the_most_chosen`; `the_yearly_toggle_shows_yearly_prices_and_two_months_free`; `an_anonymous_reader_is_sent_to_login_and_back` (check that the login page honours `returnUrl`; add it if it does not); `choosing_a_paid_offer_redirects_to_checkout`; `a_subscribed_reader_manages_in_the_portal`; `with_billing_off_the_buttons_say_unavailable`; `a_409_on_checkout_opens_the_portal`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Gate** PASS.
- [ ] **Step 5: Commit** — `feat(web): offers page and Stripe checkout`

---

### Task 4: Return from Checkout

**Files:**
- Create: `features/plans/checkout-success.page.ts` (+ spec); Modify: `app.routes.ts` (`plans/success`, `authGuard`), dictionaries.

**Interfaces:**
- Consumes: `Billing.loadMine`.
- Produces: `/plans/success` polls `GET /accounts/me/subscription` every 2 s, at most 15 times, until `status` grants a plan (`active`, `trialing`, `past_due` in the DTO's casing) and `planId` is not the default plan; then shows "Bienvenue dans l'offre X" with links to the profile and the tavern; after the last try, "Votre paiement est en cours de confirmation…" with a "Vérifier à nouveau" button. It never shows a plan the API did not answer.
- [ ] **Step 1: Failing tests** (fake timers): `the_success_page_keeps_polling_until_the_plan_changes_and_then_stops`; `after_the_last_try_it_says_confirmation_is_pending_and_can_retry`; `polling_stops_when_the_page_is_left`.
- [ ] **Step 2–4:** run → FAIL, implement, gate PASS.
- [ ] **Step 5: Commit** — `feat(web): return page after Stripe checkout`

---

### Task 5: "Mon abonnement" in the profile

**Files:**
- Create: `features/account/subscription-panel.ts` (+ spec); Modify: `features/account/profile.page.ts` (insert the panel), dictionaries.

**Interfaces:**
- Consumes: `Billing`, `PlanCatalog`, `plan-texts`.
- Produces: `dg-subscription-panel`: plan name and badge; interval; status in words; "renouvelé le …" or "prend fin le …" (`cancelAtPeriodEnd`) with the reader's date format; `past_due` ⇒ a warning "paiement en retard, mettez votre carte à jour" with the portal button; the usage of the four account limits as "used / max" with a bar (≥ 100 % in the alert colour); buttons "Gérer mon abonnement" (portal, when a paid subscription exists) and "Voir les offres".
- [ ] **Step 1: Failing tests**: `a_free_account_sees_its_usage_and_the_offers_link`; `a_paid_account_sees_its_renewal_date_and_the_portal`; `a_cancelled_subscription_says_when_it_ends`; `a_past_due_payment_is_flagged`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): my subscription in the profile`

---

### Task 6: Badges next to pseudos

**Files:**
- Create: `shared/ui/account-badges/account-badges.ts` (+ spec); Modify: `core/accounts/account-directory.service.ts` (+ spec) and every template that shows `directory.pseudo(id)()` (public profile, character list/detail, players, messages, master, planning, overview), dictionaries.

**Interfaces:**
- Produces: `AccountDirectory.badges(id: string): Signal<AccountBadgeKind[]>` with `type AccountBadgeKind = 'founder' | 'dungeon-master' | 'legend'` (founder first), from the same cached `PublicAccountDto`; `<dg-account-badges [accountId]="id" />` renders small labelled marks (text, not colour only; `title` with the badge name).
- [ ] **Step 1: Failing tests**: `badges_follow_the_public_account` (founder + legend ⇒ two marks, none ⇒ nothing); `the_same_request_serves_pseudo_and_badges`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): founder and plan badges next to pseudos`

---

### Task 7: Adventure entitlements, optional picture, video gating

**Files:**
- Modify: `features/tavern/adventure/adventure-context.service.ts` (+ spec), `features/tavern/adventure/characters/character-form.ts` and `character-form.page.ts` (+ specs), the video entry point (find where WebRTC calls start in `features/tavern/adventure`), dictionaries.
- Create: `shared/ui/plan-hint/plan-hint.ts` (+ spec): a small inline "Inclus à partir de l'offre X" / "Disponible si votre MJ a l'offre X" with a link to `/plans`.

**Interfaces:**
- Produces:
  - `AdventureContext.entitlements: Signal<AdventureEntitlementsDto | null>` loaded with the adventure for participants (`GET /adventures/{id}/entitlements`), reloaded by `reloadEntitlements()`; helpers `has(feature: string): boolean` and `usage(limit: string): { used: number; max: number } | null`.
  - Character creation: the picture becomes optional; when the reader cannot send one (in an adventure: `!has('characterPictures')`; outside: the reader's own plan from `Billing`/`PlanCatalog`), the picture field is replaced by `dg-plan-hint`; a character without picture shows the default portrait (check `character-portrait` / `character-picture.directive` handle a null `pictureUrl`).
  - Video: the call button is disabled with `dg-plan-hint` when `!has('video')`.
- [ ] **Step 1: Failing tests**: `entitlements_are_loaded_for_participants_only`; `a_character_can_be_created_without_picture`; `the_picture_field_is_replaced_by_the_hint_without_the_feature`; `a_character_without_picture_shows_the_default_portrait`; `the_video_button_is_disabled_with_the_hint`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): adventure entitlements, optional picture and video gating`

---

### Task 8: Reserve and active character

**Files:**
- Modify: `features/tavern/adventure/characters/character-detail.page.ts`, `character-list.page.ts`, `features/tavern/characters/my-characters.page.ts` (+ specs), dictionaries.
- Create: `features/tavern/characters/reserve-full.dialog.ts` helper (+ spec) used by Tasks 8 and 9.

**Interfaces:**
- Produces:
  - Character detail (owner, player character in an adventure): "Mettre en réserve" → `DELETE /game-characters/{id}/adventure`; on 409 with `plan.key = charactersWithoutAdventure` ⇒ `ReserveFullDialog.ask(character): Observable<'cancel' | 'delete'>` explaining the reserve is full, with "Annuler" and "Supprimer définitivement"; "delete" ⇒ `typeToConfirm` (retype the name) ⇒ `DELETE /game-characters/{id}`.
  - My characters (reserve character): "Envoyer en aventure" → choose among the adventures where the reader is a player (`GET /adventures/as-player`, active ones) ⇒ `PUT /game-characters/{id}/adventure`; a plan refusal goes through `NotifierService`.
  - Adventure character list: the active character of each player is marked "actif"; the owner sees "Rendre actif" on their other characters ⇒ `PUT /adventures/{id}/players/me/active-character`.
- [ ] **Step 1: Failing tests**: `moving_to_the_reserve_frees_the_place`; `a_full_reserve_offers_cancel_or_delete`; `deleting_from_the_full_reserve_dialog_requires_the_name`; `a_reserve_character_is_sent_to_an_adventure_of_the_reader`; `the_active_character_is_marked_and_can_be_switched`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): character reserve and active character`

---

### Task 9: Quitting an adventure keeps or deletes the characters

**Files:**
- Modify: `features/tavern/adventure/overview.page.ts` (+ spec) (`quit()`), dictionaries; Consumes `ReserveFullDialog` (Task 8).

**Interfaces:**
- Produces: `quit()` first sends `DELETE /adventures/{id}/players/me` without body; on 409 with `plan.key = charactersWithoutAdventure` it lists the reader's characters of the adventure (`GET /game-characters?adventureId=` filtered on owner, NPCs excluded) and opens a dialog: how many places are missing (`used + moving − max`), each character with a checkbox "supprimer définitivement"; confirming asks the name of each checked character (`typeToConfirm`, one after the other; any cancel aborts everything) and resends the quit with `{ deletions: [{ characterId, confirmation }] }`. Not enough checked ⇒ the confirm button stays disabled with the count still missing.
- [ ] **Step 1: Failing tests**: `a_quit_that_fits_needs_no_dialog`; `a_full_reserve_asks_which_characters_to_delete`; `the_deletions_are_sent_with_their_names`; `cancelling_the_quit_dialog_sends_nothing`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): quitting an adventure with a full reserve`

---

### Task 10: NPCs of the game master

**Files:**
- Modify: `features/tavern/adventure/characters/character-list.page.ts`, `character-detail.page.ts`, `character-form.page.ts` (+ specs), dictionaries.

**Interfaces:**
- Produces: the adventure character list has two sections, "Personnages des joueurs" and "PNJ" (`npc = true`); the master sees every NPC with a "Privé"/"Public" toggle (one click, `PUT /game-characters/{id}/visibility`, optimistic with rollback on error) and the NPC usage "x / max" from `usage('npcsPerAdventure')`; players see public NPCs only, read-only (no edit/delete/reserve buttons). The master's "Nouveau personnage" in their adventure is labelled "Nouveau PNJ" and the created NPC is private.
- [ ] **Step 1: Failing tests**: `the_master_sees_npcs_with_their_visibility_toggle`; `toggling_visibility_is_one_call_and_rolls_back_on_error`; `players_see_public_npcs_read_only`; `the_master_creates_an_npc`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): NPCs of the game master`

---

### Task 11: Chat history banners

**Files:**
- Modify: `features/tavern/adventure/messages.page.ts` (+ spec), dictionaries.

**Interfaces:**
- Consumes: `AdventureContext.usage('messagesPerAdventure')`, `reloadEntitlements()`.
- Produces: a banner above the messages: from 80 % to 99 % "L'historique est bientôt plein : les plus anciens messages seront effacés", at 100 % "Historique plein : chaque nouveau message efface le plus ancien"; the master also gets `dg-plan-hint` naming the lowest plan with a higher `messagesPerAdventure` (`PlanCatalog`). `used` is refreshed after sending (local `+1` capped at `max`, then `reloadEntitlements()` debounced).
- [ ] **Step 1: Failing tests**: `no_banner_below_80_percent`; `the_almost_full_banner_from_80_percent`; `the_full_banner_at_100_percent`; `the_master_sees_the_offer_that_raises_the_limit`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(web): chat history banners`

---

### Task 12: Terms page and journeys

**Files:**
- Create: `features/legal/terms.page.ts` (+ spec), `core/i18n/fr/legal.ts`, `core/i18n/en/legal.ts`; Modify: `app.routes.ts` (`terms`, public), the sidebar footer (link), dictionaries indexes.
- Create: `e2e/plans.spec.ts` (+ fixtures under `e2e/fixtures`, routes in `e2e/support` following the existing style).

**Interfaces:**
- Produces: `/terms`: the structure of the CGV with headed sections (éditeur, objet, offres et prix, durée et renouvellement, paiement, résiliation, droit de rétractation et renonciation, suppression automatique de l'historique des messages, données personnelles, médiation, droit applicable), each holding a clearly marked placeholder « À compléter » — no invented legal text. The page carries a visible notice that the text is a draft to validate.
- Journeys (all API calls answered by `page.route`): offers page as an anonymous reader → login → back on offers; logged free reader chooses Maître du Donjon yearly → redirected to the (mocked) Checkout URL; return page polls then shows the new plan; a plan-limit refusal on adventure creation shows the toast with "Voir les offres".
- [ ] **Step 1: Failing tests**: `terms.page.spec.ts` `every_section_is_present_and_marked_to_complete`; the e2e journeys.
- [ ] **Step 2–4:** run → FAIL, implement, gate PASS including `npm run e2e`.
- [ ] **Step 5: Commit** — `feat(web): terms page and subscription journeys`
