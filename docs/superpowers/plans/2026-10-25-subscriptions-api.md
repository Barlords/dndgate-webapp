# Subscriptions and Stripe billing — API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plans read from a JSON catalog limit what each account and each adventure can hold and use, accounts subscribe through Stripe Checkout, and characters gain a reserve, an active flag and NPCs — the API half (PR 1) of the subscriptions design.

**Architecture:** The domain gets a plan catalog (records + enums), a `Subscription` model and an `EntitlementService` that answers "which plan applies here" and refuses what a plan does not allow; the creators that read `QuantityLimits` today ask it instead. Stripe stays behind a `PaymentProviderSpi` implemented in `server` with the official SDK; a `BillingService` drives Checkout, the customer portal and the webhook, which always re-reads the customer's subscriptions from Stripe. The web app (PR 2) gets its own plan once this PR has produced the contract.

**Tech Stack:** Java 21, Spring Boot 4.1, Flyway / PostgreSQL, Lombok, `com.stripe:stripe-java` (new), JUnit 5 + Mockito, Testcontainers, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-10-25-subscriptions-design.md` (French). Read it before any task; this plan only adds the decisions the spec leaves to the builder.

All paths below are relative to `apps/api/` unless they start with `contracts/`, `docs/`, `README.md`, `.env.example` or `docker-compose`. Java packages are abbreviated: `domain.` = `fr.barlords.dndgateapi.domain.functional.`, `port.` = `fr.barlords.dndgateapi.domain.port.`, `client.`, `server.`, `bootstrap.` = `fr.barlords.dndgateapi.<layer>.`. Tests mirror the main package under `src/test/java`.

## Global Constraints

- Every command runs from `apps/api` (`mvnw.cmd` on Windows); `./mvnw clean verify` must pass at the end of every task (Docker running), coverage ≥ 90 % of lines.
- Code, comments, logs and API messages in English; no accented letter in `src/main/java` (`LanguagePolicyTest`). User-facing French text only in resources.
- `SecureRandom` only, never `java.util.Random`.
- The domain depends only on `java`, Lombok, Vavr, SLF4J; new services are wired by `@Bean` in `bootstrap/config/domain/*ServiceConfiguration`. Atomic operations get a `Transactional*` decorator in `bootstrap/config`.
- Routes: kebab-case nouns, the HTTP method says the action, "me" is `me` / `mine`. Every JSON body is `@Valid`.
- `contracts/openapi.yaml` is generated, never edited: any task that adds or changes a route or a DTO regenerates it (`./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true`) and commits it.
- Database changes = a new `src/main/resources/db/migration/V<n>__<description>.sql` (next free numbers start at `V4`); never edit an existing migration; `clean` after renaming one.
- Every irreversible `DELETE` is confirmed in the body and checked by the API (retyped name for a character).
- New error handlers are `@RestControllerAdvice` with `@Order(Ordered.HIGHEST_PRECEDENCE)`; answers are `ProblemDetail` (problem+json).
- Plan identifiers: `adventurer`, `hero`, `dungeon-master`, `legendary`; default plan `adventurer`; currency `EUR`; amounts in cents.
- Values of the real catalog (`src/main/resources/plans/plans.json`), in plan order adventurer / hero / dungeon-master / legendary:
  - prices monthly: none / 99 / 499 / 999; yearly: none / 990 / 4990 / 9990; lookup keys `<id-with-underscores>_monthly|_yearly` (`hero_monthly`, `dungeon_master_yearly`, …)
  - `activeAdventuresAsMaster` 1 / 2 / 10 / 25; `charactersWithoutAdventure` 3 / 15 / 50 / 75; `spellBooks` 2 / 10 / 50 / 100; `magicItems` 10 / 50 / 200 / 500; `masterNotesPerAdventure` and `historicalNotesPerAdventure` 50 / 100 / 500 / 500; `charactersPerPlayerAndAdventure` 1 / 1 / 5 / 10; `npcsPerAdventure` 5 / 10 / 50 / 100; `messagesPerAdventure` 200 / 200 / 1000 / 2000
  - `characterPictures` false / true / true / true; `video` false / false / true / true; `equippedTable` false / false / true / true; `earlyAccess` and `prioritySupport` false / false / false / true
  - `badge` null / null / `dungeon-master` / `legend`; `highlighted` only on `dungeon-master`
- Technical ceilings kept in `QuantityLimits` for everybody: 1 000 spells per spell book, 30 messages per account per minute.
- Each rule added by a task gets a mutation check before its commit: break the rule in the code, see its test fail, restore.

## Review Focus

1. **A character entering a full reserve through a path the player did not choose** (adventure deleted by the master) must still land in the reserve even beyond its quota — Task 9 pins it with `deleting_an_adventure_moves_player_characters_to_their_reserve_even_when_full`.
2. **Stripe events arriving twice, late or out of order** must leave the subscription the customer really has (the webhook re-reads Stripe) — Task 12 pins it with `a_deleted_event_for_a_duplicate_keeps_the_remaining_active_subscription` and `the_same_event_twice_changes_nothing`.
3. **Private NPCs leaking through a list, a picture or a direct id** to a player — Task 10 pins it with `a_player_never_reads_a_private_npc_by_list_by_id_or_by_picture`.
4. **A master who drops to a lower plan with a full chat** must see the excess removed only at the next message, not before and not more — Task 7 pins it with `after_a_downgrade_the_next_message_trims_the_history_to_the_new_limit`.
5. **The plan of an adventure whose master seat is vacant** must fall back to the default plan for everything the master's plan decides — Task 2 pins it with `a_vacant_master_seat_gives_the_default_plan`.

---

### Task 1: Plan catalog read from JSON

**Files:**
- Create: `src/main/java/.../domain/functional/model/plan/PlanLimit.java`, `Feature.java`, `PlanBadge.java`, `BillingInterval.java`, `PlanPrice.java`, `Plan.java`, `PlanCatalog.java`
- Create: `src/main/java/.../server/plan/PlanCatalogReader.java`
- Create: `src/main/java/.../bootstrap/config/domain/PlanCatalogConfiguration.java`
- Create: `src/main/resources/plans/plans.json`, `src/test/resources/plans/test-plans.json`
- Modify: `src/main/resources/application.properties` (add `plans.file=${PLANS_FILE:classpath:plans/plans.json}`)
- Modify: `src/test/java/.../integration/ApiIntegrationTestSupport.java` (add property `plans.file=classpath:plans/test-plans.json`)
- Test: `src/test/java/.../domain/functional/model/plan/PlanCatalogTest.java`, `src/test/java/.../server/plan/PlanCatalogReaderTest.java`

**Interfaces:**
- Produces:
  - `enum PlanLimit { ACTIVE_ADVENTURES_AS_MASTER, CHARACTERS_WITHOUT_ADVENTURE, SPELL_BOOKS, MAGIC_ITEMS, MASTER_NOTES_PER_ADVENTURE, HISTORICAL_NOTES_PER_ADVENTURE, CHARACTERS_PER_PLAYER_AND_ADVENTURE, NPCS_PER_ADVENTURE, MESSAGES_PER_ADVENTURE; String key(); Scope scope(); static Optional<PlanLimit> fromKey(String) }` with `enum Scope { ACCOUNT, ADVENTURE }` (the first four are `ACCOUNT`, the rest `ADVENTURE`); keys are the JSON keys of the Global Constraints.
  - `enum Feature { CHARACTER_PICTURES, VIDEO, EQUIPPED_TABLE, EARLY_ACCESS, PRIORITY_SUPPORT; String key(); Reach reach(); static Optional<Feature> fromKey(String) }` with `enum Reach { SHAREABLE, MASTER, ACCOUNT }`: `CHARACTER_PICTURES` is `SHAREABLE`, `VIDEO` is `MASTER`, the others `ACCOUNT`.
  - `enum PlanBadge { DUNGEON_MASTER("dungeon-master"), LEGEND("legend"); String key(); static Optional<PlanBadge> fromKey(String) }`
  - `enum BillingInterval { MONTHLY("monthly"), YEARLY("yearly"); String key(); static Optional<BillingInterval> fromKey(String) }`
  - `record PlanPrice(int amount, String stripeLookupKey)`
  - `record Plan(String id, boolean highlighted, Map<BillingInterval, PlanPrice> prices, Map<PlanLimit, Integer> limits, Map<Feature, Boolean> features, PlanBadge badge)` with `int limit(PlanLimit)`, `boolean has(Feature)`, `boolean isFree()` (no price), `Optional<PlanBadge> badgeIfAny()`.
  - `record PlanCatalog(String currency, String defaultPlanId, List<Plan> plans)` with `Plan defaultPlan()`, `Optional<Plan> find(String id)`, `Plan findOrDefault(String id)`, `int rank(Plan)` (index in `plans`), `Optional<Plan> lowestAbove(Plan current, PlanLimit limit)` (first plan after `current` in order whose limit is higher), `Optional<Plan> lowestWith(Plan current, Feature feature)` (first plan after `current` that has it).
  - `PlanCatalogReader.read(InputStream json): PlanCatalog` (Jackson `JsonMapper`, already on the classpath).
  - Bean `PlanCatalog planCatalog(@Value("${plans.file}") Resource file)`.

- [ ] **Step 1: Write the failing tests**

`PlanCatalogTest` (builds catalogs in code):
- `a_valid_catalog_gives_its_default_plan` → `defaultPlan().id()` equals `"free"` for a two-plan catalog `free` (no price) / `paid`.
- `a_catalog_is_refused_when` — one `@ParameterizedTest` or one test per case, each `assertThrows(IllegalArgumentException.class, …)`: duplicated plan id; `defaultPlanId` unknown; default plan with a price; paid plan (not default) without any price; a limit below 1; a plan missing one `PlanLimit`; a plan missing one `Feature`; empty plan list.
- `lowestAbove_names_the_first_plan_with_a_higher_limit` → with limits `SPELL_BOOKS` 2 / 2 / 10, `lowestAbove(plans[0], SPELL_BOOKS)` is `plans[2]`; from the last plan it is empty.
- `lowestWith_names_the_first_plan_that_has_the_feature`.

`PlanCatalogReaderTest`:
- `the_real_catalog_holds_the_offers_of_the_design` → reads `plans/plans.json` from the classpath and asserts every value of the Global Constraints (ids in order, prices, lookup keys, each limit, each feature, badges, `highlighted`).
- `an_unknown_limit_key_is_refused`, `an_unknown_feature_key_is_refused`, `an_unknown_badge_is_refused`, `an_unknown_interval_is_refused` → `IllegalArgumentException` whose message names the key.

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -Dtest='PlanCatalogTest,PlanCatalogReaderTest'`
Expected: compilation failure (classes missing).

- [ ] **Step 3: Implement the model, the reader, `plans.json`, the bean**

Validation lives in the compact constructors of `Plan` (every enum value present, limits ≥ 1) and `PlanCatalog` (the other rules). The reader maps JSON keys through the `fromKey` methods and fails on unknown keys. `test-plans.json` declares three plans used by every integration test: `test-default` (default, no price, every limit 1000, every feature true, no badge), `test-tight` (every limit 1, every feature false, monthly price 100 with lookup key `test_tight_monthly`) and `test-paid` (every limit 1000, every feature true, badge `legend`, monthly 500 `test_paid_monthly`, yearly 5000 `test_paid_yearly`). The generous default keeps the existing integration tests meaningful; plan tests subscribe an account to `test-tight` (Task 2 helper).

- [ ] **Step 4: Run the tests and the full build**

Run: `./mvnw test -Dtest='PlanCatalogTest,PlanCatalogReaderTest'` then `./mvnw clean verify`
Expected: PASS; the application context starts with the test catalog.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/main/resources src/test
git commit -m "feat(api): plan catalog read from plans.json"
```

---

### Task 2: Subscriptions table and effective plan

**Files:**
- Create: `src/main/resources/db/migration/V4__subscription.sql`
- Create: `domain/functional/model/plan/Subscription.java`, `SubscriptionStatus.java`
- Create: `port/out/SubscriptionPersistenceSpi.java`
- Create: `server/entity/SubscriptionEntity.java`, `server/repository/SubscriptionRepository.java`, `server/mapper/SubscriptionEntityMapper.java`, `server/adapter/SubscriptionDatabaseAdapter.java`
- Create: `port/in/plan/EntitlementApi.java`, `domain/functional/service/plan/EntitlementService.java`
- Create: `domain/functional/exception/plan/PlanLimitReachedException.java`, `FeatureNotIncludedException.java`
- Create: `client/error/PlanErrorHandler.java`
- Create: `bootstrap/config/domain/PlanServiceConfiguration.java`
- Modify: `integration/ApiIntegrationTestSupport.java` (helper `void subscribe(TestAccount account, String planId)`)
- Test: `domain/functional/service/plan/EntitlementServiceTest.java`, `integration/SubscriptionPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 `PlanCatalog`, `Plan`, `PlanLimit`, `Feature`, `PlanBadge`.
- Produces:
  - Table `subscription(account_id uuid PRIMARY KEY REFERENCES accounts(id), stripe_customer_id varchar(255) NOT NULL UNIQUE, stripe_subscription_id varchar(255) UNIQUE, plan_id varchar(50), billing_interval varchar(20), status varchar(30), current_period_end timestamptz, cancel_at_period_end boolean NOT NULL DEFAULT false, updated_at timestamptz NOT NULL)`. A row exists as soon as the account has a Stripe customer; `status` is null until a subscription is known.
  - `enum SubscriptionStatus { ACTIVE, TRIALING, PAST_DUE, CANCELED, UNPAID, INCOMPLETE, INCOMPLETE_EXPIRED, PAUSED; boolean grantsPlan() /* ACTIVE, TRIALING, PAST_DUE */; boolean isOngoing() /* grantsPlan or INCOMPLETE */; static Optional<SubscriptionStatus> fromStripe(String) }`
  - `@Value @Builder @With class Subscription { UUID accountId; String stripeCustomerId; String stripeSubscriptionId; String planId; BillingInterval interval; SubscriptionStatus status; Instant currentPeriodEnd; boolean cancelAtPeriodEnd; Instant updatedAt; }`
  - `SubscriptionPersistenceSpi { Optional<Subscription> findByAccountId(UUID); Optional<Subscription> findByStripeCustomerId(String); Map<UUID, Subscription> findByAccountIds(Collection<UUID>); Subscription save(Subscription); void deleteByAccountId(UUID); }`
  - `EntitlementApi`:
    - `Plan planOf(UUID accountId)` — the subscription's plan when `status.grantsPlan()`, else the default; an unknown `plan_id` falls back to the default and logs an error.
    - `Plan masterPlanOf(Adventure adventure)` — default plan when `adventure.isMasterVacant()`, else `planOf(adventure.getOwnerId())`.
    - `void verifyAccountLimit(UUID accountId, PlanLimit limit, long used)` — throws `PlanLimitReachedException` when `used >= planOf(accountId).limit(limit)`.
    - `void verifyAdventureLimit(Adventure adventure, PlanLimit limit, long used)` — same against `masterPlanOf`.
    - `boolean allows(UUID accountId, Adventure adventureOrNull, Feature feature)` — `SHAREABLE`: own plan has it, or (adventure not null and master plan has `EQUIPPED_TABLE` and the feature); `MASTER`: adventure not null and master plan has it; `ACCOUNT`: own plan has it.
    - `void verifyFeature(UUID accountId, Adventure adventureOrNull, Feature feature)` — throws `FeatureNotIncludedException` when `!allows(…)`.
    - `Set<Feature> availableFeatures(UUID accountId, Adventure adventure)` — the `SHAREABLE` and `MASTER` features `allows` grants.
    - `Optional<PlanBadge> badgeOf(UUID accountId)`
  - `PlanLimitReachedException(PlanLimit limit, int max, String planId, String upgradePlanId /* nullable */)` and `FeatureNotIncludedException(Feature feature, String planId, String upgradePlanId)`, both with getters; `upgradePlanId` is computed by the service with `PlanCatalog.lowestAbove` / `lowestWith`.
  - Problem answers: `409` with `type` `urn:dndgate:plan-limit-reached` and properties `limit` (the JSON key), `max`, `planId`, `upgradePlanId`; `403` with `type` `urn:dndgate:feature-not-included` and properties `feature`, `planId`, `upgradePlanId`.
  - Test helper `subscribe(account, planId)`: inserts or replaces the account's `subscription` row with `stripe_customer_id = 'cus_test_' + account id`, `status = 'ACTIVE'`.

- [ ] **Step 1: Write the failing unit tests** (`EntitlementServiceTest`, Mockito on `SubscriptionPersistenceSpi`, catalog built in code with plans `free`, `mid`, `top`)
- `an_account_without_subscription_has_the_default_plan`
- `an_active_trialing_or_past_due_subscription_gives_its_plan` (parameterized on the three statuses)
- `a_canceled_unpaid_incomplete_or_paused_subscription_gives_the_default_plan` (parameterized)
- `an_unknown_plan_id_gives_the_default_plan`
- `a_vacant_master_seat_gives_the_default_plan`
- `the_limit_refuses_at_the_maximum_and_names_the_upgrade` → used = max ⇒ exception with `max`, `planId`, `upgradePlanId == "mid"`; used = max − 1 ⇒ no exception.
- `a_shareable_feature_comes_from_the_master_only_with_an_equipped_table`
- `a_master_feature_ignores_the_plan_of_the_caller`
- `available_features_lists_what_works_in_the_adventure`
- `the_badge_follows_the_effective_plan`

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -Dtest=EntitlementServiceTest`
Expected: compilation failure.

- [ ] **Step 3: Implement migration, model, adapter, service, exceptions, handler, bean, test helper**

`SubscriptionDatabaseAdapter` follows `SignupNoticeDatabaseAdapter`/`MagicItemDatabaseAdapter` (entity ↔ model mapper, `@Transactional` methods).

- [ ] **Step 4: Write `SubscriptionPersistenceIntegrationTest`**
- `a_subscription_is_saved_and_found_by_account_and_by_customer` (the problem bodies are asserted through real routes from Task 3 on).

- [ ] **Step 5: Run the tests and the full build**

Run: `./mvnw test -Dtest='EntitlementServiceTest,SubscriptionPersistenceIntegrationTest'` then `./mvnw clean verify`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat(api): subscriptions table and effective plan of an account and of an adventure"
```

---

### Task 3: Account quotas come from the plan

**Files:**
- Modify: `domain/functional/model/QuantityLimits.java` (remove `activeAdventuresPerMaster`, `spellBooksPerAccount`, `charactersWithoutAdventurePerAccount`, `magicItemsPerAccount`; Task 4 removes the adventure ones)
- Modify: `bootstrap/config/domain/QuantityLimitsConfiguration.java`, `application-dev.properties`, `application-prod.properties` (remove the matching `limits.*` keys)
- Modify: `domain/functional/service/adventure/AdventureCreatorService.java`, `gamecharacter/GameCharacterCreatorService.java` (reserve branch only), `spellbook/SpellBookCreatorService.java`, `spellbook/SpellBookImporterService.java`, `magicitem/MagicItemCreatorService.java` and their beans
- Modify: `domain/functional/exception/QuantityLimitExceededException.java` (drop the moved factories)
- Test: rewrite `domain/functional/service/QuantityLimitsServicesTest.java` (technical ceilings only), new `domain/functional/service/plan/AccountPlanLimitsTest.java`, rewrite `integration/QuantityLimitIntegrationTest.java` → `integration/PlanLimitIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2 `EntitlementApi.verifyAccountLimit`, test helper `subscribe`.
- Produces: the four creators take `EntitlementApi` instead of the removed `QuantityLimits` fields and call `verifyAccountLimit(accountId, <limit>, spi.count…)` with `ACTIVE_ADVENTURES_AS_MASTER` (`countActiveByOwnerId`), `CHARACTERS_WITHOUT_ADVENTURE` (`countByOwnerIdWithoutAdventure`), `SPELL_BOOKS` (`countByOwnerId`, creation and import), `MAGIC_ITEMS` (`countByOwnerId`).

- [ ] **Step 1: Write the failing tests**

`AccountPlanLimitsTest` (Mockito, `EntitlementApi` mocked to throw for used ≥ 1): one test per creator, e.g. `an_account_at_its_plan_limit_cannot_create_an_adventure`, `…_a_character_in_its_reserve`, `…_a_spell_book`, `…_import_a_spell_book`, `…_a_magic_item`, each asserting `PlanLimitReachedException` and that nothing is saved (and, for the character, that no picture is uploaded).

`PlanLimitIntegrationTest` (account subscribed to `test-tight`, limit 1): `a_second_active_adventure_is_refused_with_the_plan_problem` asserts `409`, `type == "urn:dndgate:plan-limit-reached"`, `limit == "activeAdventuresAsMaster"`, `max == 1`, `planId == "test-tight"`, `upgradePlanId == "test-paid"`; same shape, lighter assertions, for spell book, import, magic item, reserve character. `a_finished_adventure_frees_its_place` keeps the existing behaviour.

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -Dtest='AccountPlanLimitsTest,PlanLimitIntegrationTest'`
Expected: FAIL (creators still read `QuantityLimits`).

- [ ] **Step 3: Switch the creators, trim `QuantityLimits` and the properties**

- [ ] **Step 4: Run the tests and the full build**

Run: `./mvnw clean verify`
Expected: PASS (fix compile errors in tests that built `QuantityLimits` with ten arguments).

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(api): account quotas come from the plan of the account"
```

---

### Task 4: Adventure quotas come from the master's plan

**Files:**
- Modify: `domain/functional/service/adventurenote/AdventureNoteCreatorService.java`, `historicnote/HistoricalNoteCreatorService.java`, `gamecharacter/GameCharacterCreatorService.java` (adventure branch), their beans
- Modify: `QuantityLimits` (remove `charactersPerPlayerAndAdventure`, `adventureNotesPerAdventure`, `historicalNotesPerAdventure`: only `spellsPerSpellBook`, `messagesPerWindow`, `messageWindow` remain), `QuantityLimitsConfiguration`, both profile files, the unused `QuantityLimitExceededException` factories
- Modify: `port/out/GameCharacterPersistenceSpi.java` (no new method yet; `countByOwnerIdAndAdventureId` keeps counting the player's characters of the adventure)
- Test: `domain/functional/service/plan/AdventurePlanLimitsTest.java`, add cases to `integration/PlanLimitIntegrationTest.java`

**Interfaces:**
- Consumes: `EntitlementApi.verifyAdventureLimit(Adventure, PlanLimit, long)`.
- Produces: `MASTER_NOTES_PER_ADVENTURE` (notes), `HISTORICAL_NOTES_PER_ADVENTURE` (chronicle, whoever writes it), `CHARACTERS_PER_PLAYER_AND_ADVENTURE` (a player creating a character in an adventure) are checked against the master's plan. The master creating in their own adventure is handled in Task 10 (NPC); until then it keeps the player rule.

- [ ] **Step 1: Write the failing tests**
- `a_note_is_refused_when_the_master_plan_is_full`, `a_chronicle_entry_written_by_a_player_counts_against_the_master_plan`, `a_player_character_counts_against_the_master_plan_not_the_player_plan` (player on `test-paid`, master on `test-tight` ⇒ second character refused).
- Integration: the same three through HTTP, asserting `limit` keys `masterNotesPerAdventure`, `historicalNotesPerAdventure`, `charactersPerPlayerAndAdventure`.

- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='AdventurePlanLimitsTest,PlanLimitIntegrationTest'` → FAIL.

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(api): adventure quotas come from the plan of the game master"`

---

### Task 5: Optional picture, picture and video features, adventure entitlements route

**Files:**
- Create: `src/main/resources/db/migration/V5__character_picture_optional.sql` (`ALTER TABLE "character" ALTER COLUMN picture_path DROP NOT NULL;`)
- Modify: `client/controller/GameCharacterController.java` (`@RequestPart(value = "picturePresentation", required = false)`), `client/mapper/GameCharacterDtoMapper.java` (`pictureUrl` null without picture), `domain/functional/service/gamecharacter/GameCharacterCreatorService.java`, `GameCharacterFinderService.findPicture` (no picture ⇒ `GameCharacterNotFoundException`), `server/entity/GameCharacterEntity.java` (nullable column)
- Modify: `client/controller/WebRTCController.java` (verify `VIDEO` after the participant check)
- Create: `client/dto/adventure/AdventureEntitlementsDto.java`, `client/dto/plan/LimitUsageDto.java`; add `GET /adventures/{id}/entitlements` to `client/controller/AdventureController.java`
- Create: `port/in/plan/AdventureEntitlementsApi.java`, `domain/functional/service/plan/AdventureEntitlementsService.java`, `domain/functional/model/plan/AdventureEntitlements.java`
- Test: `GameCharacterCreatorServiceTest` (new cases), `integration/FeatureIntegrationTest.java`, contract regenerated

**Interfaces:**
- Consumes: `EntitlementApi.verifyFeature`, `availableFeatures`, `masterPlanOf`.
- Produces:
  - Picture given ⇒ `verifyFeature(accountId, adventureOrNull, CHARACTER_PICTURES)` before reading it; no picture ⇒ character saved with `picturePath` null, nothing uploaded.
  - `record LimitUsage(long used, int max)`; `record AdventureEntitlements(Set<Feature> features, Map<PlanLimit, LimitUsage> limits)`.
  - `AdventureEntitlementsApi.of(UUID accountId, UUID adventureId): AdventureEntitlements` — participants only (`verifyParticipant`); `limits` holds the `ADVENTURE` scope limits of the master's plan: `CHARACTERS_PER_PLAYER_AND_ADVENTURE` (used = the caller's characters there), `NPCS_PER_ADVENTURE` (used = 0 until Task 10), `MESSAGES_PER_ADVENTURE` (used = messages of the adventure, `MessagePersistenceSpi.countByAdventureId` added here), `MASTER_NOTES_PER_ADVENTURE`, `HISTORICAL_NOTES_PER_ADVENTURE`. No plan id in the answer.
  - `GET /adventures/{id}/entitlements` → `AdventureEntitlementsDto(List<String> features, Map<String, LimitUsageDto> limits)` with JSON keys; `LimitUsageDto(long used, int max)`.

- [ ] **Step 1: Write the failing tests**
- Unit: `a_character_without_picture_is_saved_without_upload`, `a_picture_without_the_feature_is_refused_before_upload`, `a_free_player_sends_a_picture_in_an_equipped_table`.
- Integration `FeatureIntegrationTest`: `a_tight_account_creates_a_character_without_picture` (201, `pictureUrl` null, picture route 404); `a_tight_account_sending_a_picture_gets_the_feature_problem` (403, `feature == "characterPictures"`, `upgradePlanId == "test-paid"`); `video_signals_are_refused_in_the_adventure_of_a_tight_master` (403 on `POST /webrtc/offer`, `feature == "video"`), accepted for a `test-paid` master; `the_entitlements_of_an_adventure_list_features_and_usage` (keys and numbers); `a_stranger_cannot_read_the_entitlements` (403).

- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='GameCharacterCreatorServiceTest,FeatureIntegrationTest'` → FAIL.

- [ ] **Step 3: Implement, then regenerate the contract**

Run: `./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true`

- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.

- [ ] **Step 5: Commit** — `git add src ../../contracts/openapi.yaml && git commit -m "feat(api): optional character picture, picture and video features, adventure entitlements"`

---

### Task 6: Account badges

**Files:**
- Create: `src/main/resources/db/migration/V6__account_founder.sql` (`ALTER TABLE accounts ADD COLUMN founder boolean NOT NULL DEFAULT false;`)
- Modify: `domain/functional/model/account/Account.java` (`boolean founder`, `@Builder.Default false`), `server/entity/AccountEntity.java`, `server/mapper/AccountEntityMapper.java`
- Modify: `domain/functional/service/account/AccountAnonymizerService.java` (`withFounder(false)` in `anonymousVersionOf`)
- Modify: `client/dto/account/AccountDto.java`, `PublicAccountDto.java` (add `boolean founder`, `String planBadge`), `client/mapper/AccountDtoMapper.java` (`toDto(Account, Optional<PlanBadge>)`, `toPublicDto(Account, Optional<PlanBadge>)`), `client/controller/AccountController.java` (the three account reads ask `EntitlementApi.badgeOf`)
- Test: `integration/BadgeIntegrationTest.java`, `AccountAnonymizerServiceTest` (founder reset), contract regenerated

**Interfaces:**
- Consumes: `EntitlementApi.badgeOf`.
- Produces: `founder` and `planBadge` (`"dungeon-master"`, `"legend"` or null) on `GET /accounts/me`, `/accounts/id/{id}`, `/accounts/pseudo/{pseudo}`.

- [ ] **Step 1: Failing tests** — `a_founder_set_in_the_database_is_seen_by_others` (jdbc `UPDATE accounts SET founder = true`), `the_plan_badge_is_seen_by_others_while_the_plan_is_effective` (subscribe `test-paid` ⇒ `"legend"`; set status `CANCELED` ⇒ null), `anonymizing_an_account_removes_its_founder_badge`.
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest=BadgeIntegrationTest` → FAIL.
- [ ] **Step 3: Implement, regenerate the contract.**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): founder and plan badges on accounts"`

---

### Task 7: Message history capped by the master's plan

**Files:**
- Modify: `port/out/MessagePersistenceSpi.java` (`long countByAdventureId(UUID)` if not added in Task 5; `int deleteOldestBeyond(UUID adventureId, int keep)`), `server/adapter/MessageDatabaseAdapter.java`, `server/repository/MessageRepository.java`
- Modify: `domain/functional/service/message/MessageCreatorService.java` (takes `EntitlementApi`), its bean
- Create: `bootstrap/config/TransactionalMessageCreator.java` (wraps `MessageCreatorApi` in one transaction; wired in `MessageServiceConfiguration`)
- Test: `domain/functional/service/message/MessageServicesTest.java` (new cases), `integration/MessageHistoryIntegrationTest.java`

**Interfaces:**
- Consumes: `EntitlementApi.masterPlanOf`, `PlanLimit.MESSAGES_PER_ADVENTURE`.
- Produces: after `spi.save`, `spi.deleteOldestBeyond(adventureId, masterPlanOf(adventure).limit(MESSAGES_PER_ADVENTURE))` in the same transaction. "Oldest" = order by `send_date DESC, id DESC`, keep the first `keep` rows. The rate limit (`QuantityLimits.messagesPerWindow`) is unchanged and checked first. The 80 % / 100 % banners are the web app's job from `GET /adventures/{id}/entitlements`.

- [ ] **Step 1: Failing tests**
- Unit: `a_message_beyond_the_limit_removes_the_oldest`, `a_message_below_the_limit_removes_nothing` (verify `deleteOldestBeyond` called with the master plan's limit).
- Integration: add a `test-chat` plan to `test-plans.json`, placed right after `test-default` (so that `test-paid` stays the upgrade of `test-tight`), with `messagesPerAdventure` 3, every other value as `test-default`, and monthly price 200 `test_chat_monthly`. Master on `test-chat`: `the_fourth_message_removes_the_first`; `after_a_downgrade_the_next_message_trims_the_history_to_the_new_limit` (5 messages on `test-default`, switch master to `test-chat`, history still 5 before sending, 3 after the next message, the 3 newest kept).
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='MessageServicesTest,MessageHistoryIntegrationTest'` → FAIL.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): message history of an adventure capped by the plan of the game master"`

---

### Task 8: Reserve, active character, moving a character in and out of an adventure

**Files:**
- Create: `src/main/resources/db/migration/V7__character_active.sql`: `active boolean NOT NULL DEFAULT false` on `"character"`, and `CREATE UNIQUE INDEX uq_character_active ON "character" (adventure_id, account_id) WHERE active;`
- Modify: `domain/functional/model/GameCharacter.java` (`boolean active`), entity, mappers, `GameCharacterDto` (`boolean active`)
- Modify: `port/out/GameCharacterPersistenceSpi.java` (`Optional<GameCharacter> findActive(UUID ownerId, UUID adventureId)`)
- Create: `port/in/gamecharacter/GameCharacterMoverApi.java`, `domain/functional/service/gamecharacter/GameCharacterMoverService.java`, `bootstrap/config/TransactionalGameCharacterMover.java`
- Create: `client/dto/gamecharacter/CharacterAdventureRequest.java` (`@NotNull UUID adventureId`), `client/dto/adventure/ActiveCharacterRequest.java` (`@NotNull UUID characterId`)
- Modify: `GameCharacterController` (two routes), `AdventureController` (one route), `GameCharacterCreatorService` (first character of the player in the adventure is created active)
- Test: `domain/functional/service/gamecharacter/GameCharacterMoverServiceTest.java`, `integration/CharacterReserveIntegrationTest.java`, contract regenerated

**Interfaces:**
- Consumes: `EntitlementApi.verifyAccountLimit`, `verifyAdventureLimit`, `AdventureAccessVerifierApi`, `GameCharacterOwnerVerifierService`.
- Produces:
  - `GameCharacterMoverApi`:
    - `GameCharacter moveToReserve(UUID accountId, UUID characterId)` — owner only; refused with `PlanLimitReachedException(CHARACTERS_WITHOUT_ADVENTURE, …)` when the reserve is full (the web dialog keys on `limit == "charactersWithoutAdventure"`); sets `adventure` null and `active` false.
    - `GameCharacter moveToAdventure(UUID accountId, UUID characterId, UUID adventureId)` — owner only, character in the reserve, caller a player of the adventure (not its master), `verifyAdventureLimit(adventure, CHARACTERS_PER_PLAYER_AND_ADVENTURE, count)`; becomes active when the player has no active character there.
    - `GameCharacter activate(UUID accountId, UUID adventureId, UUID characterId)` — owner only, character in that adventure; the previous active one is set inactive first (same transaction).
  - Routes: `DELETE /game-characters/{id}/adventure` (to the reserve, no body: reversible), `PUT /game-characters/{id}/adventure` with `CharacterAdventureRequest`, `PUT /adventures/{id}/players/me/active-character` with `ActiveCharacterRequest`; each answers the `GameCharacterDto`.

- [ ] **Step 1: Failing tests**
- Unit: `moving_to_a_full_reserve_is_refused_and_changes_nothing`, `moving_to_the_reserve_frees_the_place_and_deactivates`, `moving_into_an_adventure_respects_the_master_plan`, `the_first_character_of_a_player_becomes_active`, `activating_a_character_deactivates_the_previous_one`, `only_the_owner_moves_or_activates`.
- Integration: `a_player_moves_a_character_out_and_back_in`; `a_full_reserve_answers_the_plan_problem_with_the_reserve_limit`; `two_active_characters_are_impossible` (the unique index answers `409` if the service is bypassed — assert through two parallel `PUT …/active-character` calls that at most one character is active).
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='GameCharacterMoverServiceTest,CharacterReserveIntegrationTest'` → FAIL.
- [ ] **Step 3: Implement, regenerate the contract.**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): character reserve, active character, moving in and out of an adventure"`

---

### Task 9: Leaving an adventure and deleting one keep the players' characters

**Files:**
- Create: `client/dto/adventure/QuitAdventureRequest.java` (`@Valid @Size(max = 10) List<CharacterDeletion> deletions`, nullable), `client/dto/adventure/CharacterDeletion.java` (`@NotNull UUID characterId`, `@NotNull @Size(max = 300) String confirmation`)
- Modify: `AdventureController.quitAdventure` (optional `@Valid @RequestBody(required = false) QuitAdventureRequest`), `port/in/adventure/AdventureUpdaterApi.quitAdventure(UUID adventureId, UUID accountId, Map<UUID, String> deletions)`, `AdventureUpdaterService.quitAdventure`, a new `bootstrap/config/TransactionalAdventureQuitter.java` if `quitAdventure` is not already inside one transaction
- Modify: `port/out/GameCharacterPersistenceSpi.java` (`void moveToReserveByAdventureId(UUID adventureId)`: player characters of the adventure get `adventure_id` null and `active` false), `AdventureDeleterService` (move player characters before deleting; collect only the remaining characters' pictures), `server/adapter/AdventureDatabaseAdapter.deleteById` (keeps deleting whatever characters remain)
- Test: `AdventureUpdaterServiceQuitTest`, `AdventureDeleterServiceTest`, `integration/AdventureQuitIntegrationTest.java`, `integration/AdventureDeletionIntegrationTest.java`

**Interfaces:**
- Consumes: Task 8 rules, `DeletionConfirmation.requireName`, `GameCharacterDeleterService` logic (picture deleted after commit).
- Produces:
  - Quitting: the player's characters of the adventure listed in `deletions` are deleted (each name checked; an id that is not the player's character there ⇒ `400` via `DeletionNotConfirmedException`); the others move to the reserve. If they do not fit (`used + moving > limit`), `PlanLimitReachedException(CHARACTERS_WITHOUT_ADVENTURE, …)` and **nothing** changes (the seat is kept). All in one transaction.
  - Deleting an adventure: player characters move to their owners' reserve whatever the quota; then the rest (NPCs from Task 10) is deleted with its pictures.

- [ ] **Step 1: Failing tests**
- `quitting_moves_the_characters_to_the_reserve`, `quitting_with_a_full_reserve_is_refused_and_keeps_the_seat`, `quitting_with_confirmed_deletions_deletes_them_and_moves_the_rest`, `a_wrong_name_in_a_deletion_refuses_the_whole_quit`, `deleting_an_adventure_moves_player_characters_to_their_reserve_even_when_full`.
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='AdventureUpdaterServiceQuitTest,AdventureDeleterServiceTest,AdventureQuitIntegrationTest,AdventureDeletionIntegrationTest'` → FAIL.
- [ ] **Step 3: Implement, regenerate the contract.**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS (update `AdventureDeletionIntegrationTest` cases that expected the characters to disappear).
- [ ] **Step 5: Commit** — `git commit -m "feat(api): leaving or deleting an adventure keeps the characters of the players"`

---

### Task 10: NPCs of the game master

**Files:**
- Create: `src/main/resources/db/migration/V8__character_npc.sql`: `npc boolean NOT NULL DEFAULT false`, `visibility varchar(20) NOT NULL DEFAULT 'PUBLIC'` with check `IN ('PRIVATE', 'PUBLIC')` on `"character"`
- Modify: `GameCharacter` (`boolean npc`, `CharacterVisibility visibility`), new `domain/functional/model/CharacterVisibility.java` (`PRIVATE`, `PUBLIC`), entity, mappers, `GameCharacterDto` (`boolean npc`, `String visibility`)
- Modify: `GameCharacterPersistenceSpi` (`List<GameCharacter> findVisibleByAdventureId(UUID adventureId, boolean withPrivateNpcs)`, `long countNpcsByAdventureId(UUID)`; `countByOwnerIdAndAdventureId` and `findByOwnerId` exclude NPCs), repository queries
- Modify: `GameCharacterCreatorService` (the master creating in their own adventure creates a `PRIVATE` NPC, checked with `NPCS_PER_ADVENTURE`; never active), `GameCharacterFinderService` (private NPCs only for the current master: list, by id, picture), `GameCharacterOwnerVerifierService` (an NPC is managed by the current master of its adventure, not by its owner), `GameCharacterMoverService` (NPCs cannot move), `AdventureEntitlementsService` (`NPCS_PER_ADVENTURE` used)
- Create: `client/dto/gamecharacter/CharacterVisibilityRequest.java` (`@NotNull CharacterVisibility visibility`); route `PUT /game-characters/{id}/visibility` in `GameCharacterController`, served by a new method `GameCharacter setVisibility(UUID accountId, UUID characterId, CharacterVisibility visibility)` on `GameCharacterUpdaterApi`
- Test: `GameCharacterFinderServiceTest`, `GameCharacterCreatorServiceTest`, `integration/NpcIntegrationTest.java`; adjust existing integration tests where the master created a character in their own adventure (now a private NPC): have a player create it, or make it public first
- Contract regenerated

**Interfaces:**
- Consumes: `Adventure.isMaster`, `EntitlementApi.verifyAdventureLimit`, Task 8 mover.
- Produces: the NPC rules above; `findMyCharacter` / `findByOwnerId` never return NPCs; after a master transfer the new master manages the NPCs.

- [ ] **Step 1: Failing tests**
- `a_master_creates_a_private_npc`, `the_npc_limit_comes_from_the_master_plan`, `a_player_never_reads_a_private_npc_by_list_by_id_or_by_picture`, `a_public_npc_is_read_only_for_players`, `one_call_makes_an_npc_public_or_private`, `the_new_master_manages_the_npcs_after_a_transfer`, `an_npc_cannot_go_to_a_reserve`, `my_characters_do_not_list_npcs`.
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='GameCharacterFinderServiceTest,GameCharacterCreatorServiceTest,NpcIntegrationTest'` → FAIL.
- [ ] **Step 3: Implement, regenerate the contract.**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): non-player characters of the game master, private or public"`

---

### Task 11: Stripe adapter

**Files:**
- Modify: `pom.xml` (dependency `com.stripe:stripe-java`, version in a `stripe.version` property: the latest stable release on Maven Central when implementing, written exactly)
- Create: `port/out/PaymentProviderSpi.java`, `domain/functional/model/plan/ProviderSubscription.java`, `CheckoutRequest.java`, `BillingEvent.java`
- Create: `domain/functional/exception/plan/PaymentProviderUnavailableException.java`, `InvalidWebhookSignatureException.java`
- Create: `server/adapter/StripePaymentAdapter.java`
- Create: `bootstrap/config/StripeConfig.java`
- Modify: `application.properties` (`stripe.secret-key=${STRIPE_SECRET_KEY:}`, `stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}`, `billing.checkout.terms-acceptance-message=…` French text in the resource), `application-prod.properties` (`stripe.secret-key=${STRIPE_SECRET_KEY}`, `stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET}`: required in production)
- Test: `server/adapter/StripePaymentAdapterTest.java`, `bootstrap/config/ProductionConfigurationTest.java` (new case)

**Interfaces:**
- Produces:
  - `record ProviderSubscription(String id, String planId /* product metadata plan_id, nullable */, BillingInterval interval, SubscriptionStatus status, Instant currentPeriodEnd, boolean cancelAtPeriodEnd)`
  - `record CheckoutRequest(String customerId, UUID accountId, String priceLookupKey, String successUrl, String cancelUrl)`
  - `record BillingEvent(String type, String customerId /* nullable */)`
  - `PaymentProviderSpi { boolean isEnabled(); String createCustomer(UUID accountId, String email); List<ProviderSubscription> findSubscriptions(String customerId); String createCheckoutSession(CheckoutRequest); String createPortalSession(String customerId, String returnUrl); BillingEvent parseWebhookEvent(String payload, String signatureHeader); void cancelNow(String subscriptionId); }` — every call but `isEnabled` throws `PaymentProviderUnavailableException` when disabled or when Stripe fails; `parseWebhookEvent` throws `InvalidWebhookSignatureException` for a missing or wrong signature.
- Adapter decisions:
  - One `StripeClient` built in `StripeConfig` from `stripe.secret-key`; blank key ⇒ the adapter is disabled (`isEnabled() == false`). The builder's API base is overridable (`stripe.api-base`, test only) so the test can aim at a local fake server.
  - Customer: email + metadata `account_id`.
  - Checkout: mode subscription, the price found by `lookup_key` (active price, else `PaymentProviderUnavailableException` logged as configuration error), `client_reference_id` and `subscription_data.metadata.account_id` = account id, `expires_at` = now + 30 min, `consent_collection.terms_of_service = required`, `custom_text.terms_of_service_acceptance.message` = `billing.checkout.terms-acceptance-message`, no promotion codes, no automatic tax, `locale = auto`.
  - Subscriptions: list with `status = all` and the price's product expanded; `planId` from the product metadata `plan_id`; the period end from the subscription item (recent API versions moved it there).
  - Webhook: `Webhook.constructEvent` with `stripe.webhook-secret`; the customer id is read from `data.object.customer` of the raw payload with Jackson (independent from the SDK's API version).
- `billing.checkout.terms-acceptance-message` (French, resource): `J'accepte les [CGV](https://dndgate.barlords.fr/terms) et je demande l'accès immédiat au service : je renonce ainsi à mon droit de rétractation de 14 jours.` — the URL is built from `app.web-base-url` + `/terms` in `StripeConfig`, not hard-coded.

- [ ] **Step 1: Failing tests** (`StripePaymentAdapterTest`, a `com.sun.net.httpserver.HttpServer` on a free port answering canned Stripe JSON and recording requests)
- `a_blank_key_disables_the_adapter` (every call throws `PaymentProviderUnavailableException`).
- `the_checkout_session_carries_the_price_the_customer_the_consent_and_the_expiry` (asserts the form parameters sent to `/v1/checkout/sessions`).
- `subscriptions_are_read_with_their_plan_status_and_period_end`.
- `a_valid_signature_gives_the_event_type_and_customer` (payload signed in the test: header `t=<ts>,v1=<hex HMAC-SHA256(secret, ts + "." + payload)>`).
- `a_missing_or_wrong_signature_is_refused`.
- `a_stripe_error_becomes_unavailable`.
- `ProductionConfigurationTest.the_stripe_keys_have_no_default_in_production`.
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='StripePaymentAdapterTest,ProductionConfigurationTest'` → FAIL.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): Stripe payment adapter"`

---

### Task 12: Billing — catalog, my subscription, checkout, portal, webhook

**Files:**
- Create: `port/in/plan/BillingApi.java`, `domain/functional/service/plan/BillingService.java`, `domain/functional/model/plan/BillingUrls.java`, `SubscriptionOverview.java`
- Create: `domain/functional/exception/plan/SubscriptionAlreadyActiveException.java`, `NoBillingAccountException.java`, `PlanNotPurchasableException.java`
- Create: `client/controller/PlanController.java`, `client/controller/BillingController.java`, `client/dto/plan/PlanCatalogDto.java`, `PlanDto.java`, `PlanPriceDto.java`, `SubscriptionDto.java`, `CheckoutSessionRequest.java`, `RedirectDto.java`, `client/mapper/PlanDtoMapper.java`
- Modify: `client/controller/AccountController.java` (`GET /accounts/me/subscription`), `client/error/PlanErrorHandler.java` (new exceptions), `bootstrap/config/SecurityConfig.java` (`PUBLIC_GET_ROUTES = {"/plans"}`; `"/billing/stripe-webhook"` added to `PUBLIC_POST_ROUTES`), `bootstrap/config/OpenApiConfig.java` (`isPublic` also knows the GET list), `bootstrap/config/domain/PlanServiceConfiguration.java`
- Modify: `integration/ApiIntegrationTestSupport.java` (`@MockitoBean PaymentProviderSpi paymentProviderSpi`)
- Test: `domain/functional/service/plan/BillingServiceTest.java`, `integration/BillingIntegrationTest.java`, contract regenerated

**Interfaces:**
- Consumes: Tasks 1, 2, 11.
- Produces:
  - `record BillingUrls(String checkoutSuccess, String checkoutCancel, String portalReturn)` = `app.web-base-url` + `/plans/success`, `/plans`, `/account`.
  - `record SubscriptionOverview(Plan plan, Subscription subscription /* nullable */, Map<PlanLimit, LimitUsage> usage, boolean billingEnabled)` — `usage` holds the four `ACCOUNT` limits with the same counts as Task 3.
  - `BillingApi`:
    - `SubscriptionOverview mine(UUID accountId)`
    - `String startCheckout(UUID accountId, String planId, BillingInterval interval)` — order: provider enabled (else 503); plan exists, not free, has the interval (else `PlanNotPurchasableException`, 400); customer row created if missing (`createCustomer` then `save` with null status); `findSubscriptions` has an `isOngoing()` one ⇒ `SubscriptionAlreadyActiveException` (409); else the Checkout URL.
    - `String openPortal(UUID accountId)` — no row ⇒ `NoBillingAccountException` (409).
    - `void handleWebhook(String payload, String signatureHeader)` — `parseWebhookEvent`; types `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted` with a known customer ⇒ `sync(customerId)`; other types or unknown customer ⇒ nothing (unknown customer logged as a warning).
    - `void cancelForAnonymizedAccount(UUID accountId)` (used by Task 13)
  - `sync(customerId)`: read every subscription of the customer; keep the best one that `grantsPlan()` (highest `PlanCatalog.rank`, then latest `currentPeriodEnd`); none ⇒ the most recent one by period end, or keep the row with its status set from it; save plan id (unknown ids are kept as read and resolved to the default by `planOf`), interval, status, period end, cancel flag, `updatedAt`; more than one granting ⇒ `log.error` naming the customer and the subscription ids (manual refund).
  - Routes and statuses: `GET /plans` (public) → `PlanCatalogDto(String currency, String defaultPlan, List<PlanDto> plans)`, `PlanDto(String id, boolean highlighted, Map<String, PlanPriceDto> prices, Map<String, Integer> limits, Map<String, Boolean> features, String badge)`, `PlanPriceDto(int amount)` (no lookup key); `GET /accounts/me/subscription` → `SubscriptionDto(String planId, String interval, String status, Instant currentPeriodEnd, boolean cancelAtPeriodEnd, boolean billingEnabled, Map<String, LimitUsageDto> usage)`; `POST /billing/checkout-session` with `CheckoutSessionRequest(@NotBlank @Size(max = 50) String planId, @NotNull BillingInterval interval)` → `201 RedirectDto(String url)`; `POST /billing/portal-session` → `201 RedirectDto`; `POST /billing/stripe-webhook` (public, `@RequestBody String payload`, header `Stripe-Signature`) → `200`, `400` on a bad signature.
  - Problem answers: `PaymentProviderUnavailableException` 503, `InvalidWebhookSignatureException` 400, `SubscriptionAlreadyActiveException` 409, `NoBillingAccountException` 409, `PlanNotPurchasableException` 400.

- [ ] **Step 1: Failing unit tests** (`BillingServiceTest`, Mockito on the SPIs)
- `checkout_is_unavailable_without_stripe`, `a_free_or_unknown_plan_or_missing_interval_cannot_be_bought`, `the_first_checkout_creates_the_customer_once`, `an_ongoing_subscription_at_stripe_refuses_a_new_checkout_even_without_local_row_status`, `the_checkout_uses_the_lookup_key_of_the_interval_and_the_web_urls`, `the_portal_needs_a_customer`, `a_webhook_syncs_the_best_granting_subscription`, `a_deleted_event_for_a_duplicate_keeps_the_remaining_active_subscription`, `the_same_event_twice_changes_nothing`, `an_event_of_an_unknown_customer_or_type_does_nothing`, `two_granting_subscriptions_are_logged`.
- [ ] **Step 2: Failing integration tests** (`BillingIntegrationTest`, mocked `PaymentProviderSpi`)
- `the_catalog_is_public_and_hides_the_lookup_keys`, `my_subscription_shows_plan_usage_and_billing_state`, `checkout_answers_the_stripe_url`, `checkout_without_stripe_answers_503`, `the_webhook_is_public_and_refuses_a_bad_signature`, `a_webhook_event_changes_the_effective_plan` (then `GET /accounts/me/subscription` shows the new plan and a creation beyond the old limit succeeds).
- [ ] **Step 3: Run to verify they fail** — `./mvnw test -Dtest='BillingServiceTest,BillingIntegrationTest'` → FAIL.
- [ ] **Step 4: Implement, regenerate the contract.**
- [ ] **Step 5: Run** `./mvnw clean verify` → PASS (`RouteNamingIntegrationTest` included).
- [ ] **Step 6: Commit** — `git commit -m "feat(api): plan catalog, my subscription, Stripe checkout, portal and webhook"`

---

### Task 13: Deleting an account cancels its subscription

**Files:**
- Modify: `domain/functional/service/account/AccountAnonymizerService.java` (takes `BillingApi`; `apply` calls `billing.cancelForAnonymizedAccount(accountId)` **last**, after the other steps), its bean in `AccountServiceConfiguration`
- Modify: `BillingService.cancelForAnonymizedAccount`: for every subscription of the customer that `isOngoing()`, `cancelNow`; then `deleteByAccountId`; no row ⇒ nothing; provider disabled with a row ⇒ `PaymentProviderUnavailableException`
- Test: `AccountAnonymizerServiceTest`, `BillingServiceTest`, `integration/AccountAnonymizationIntegrationTest.java`

**Interfaces:**
- Consumes: Task 12 `BillingApi`.
- Produces: a failing Stripe call makes the anonymization fail with `503` and roll back (the account stays as it was).

- [ ] **Step 1: Failing tests** — `anonymizing_cancels_the_ongoing_subscriptions_and_forgets_the_row`, `an_account_without_customer_is_anonymized_without_stripe`, `a_stripe_failure_refuses_the_anonymization_and_keeps_the_account` (integration: mock throws ⇒ `503`, the account can still log in).
- [ ] **Step 2: Run to verify they fail** — `./mvnw test -Dtest='AccountAnonymizerServiceTest,BillingServiceTest,AccountAnonymizationIntegrationTest'` → FAIL.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** `./mvnw clean verify` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): deleting an account cancels its Stripe subscription"`

---

### Task 14: Configuration and documentation

**Files:**
- Modify: `.env.example` (`STRIPE_SECRET_KEY=`, `STRIPE_WEBHOOK_SECRET=`, `# PLANS_FILE=file:/chemin/plans.json`), `docker-compose.prod.yml` (`STRIPE_SECRET_KEY: ${STRIPE_SECRET_KEY:?STRIPE_SECRET_KEY is required}`, same for the webhook secret, `PLANS_FILE: ${PLANS_FILE:-}`)
- Modify: `README.md` (French): replace the "Quantités" list of "Limites et validation des entrées" with the plan rules (table of the offers, which plan applies, what a downgrade does, the technical ceilings); new section "Abonnements et paiement" with: the Stripe setup (one product per paid plan with metadata `plan_id`, two recurring prices with the lookup keys, customer portal settings, terms URL `WEB_BASE_URL/terms` in the public details, webhook URL `<api>/dndgate-api/billing/stripe-webhook` and its four events, test with `stripe listen --forward-to localhost:4000/dndgate-api/billing/stripe-webhook`), how to change a price (new price + `transfer_lookup_key`), the environment variables, `PLANS_FILE`, and the founder SQL: `UPDATE accounts SET founder = true WHERE lower(pseudo) = lower('<pseudo>');`; document the new character routes (reserve, active character, NPCs, quitting, deletion of an adventure) and the message history rule.
- Modify: `.claude/CLAUDE.md` only if a command or rule it states changed (it mentions `docs/adr/` and `docs/api-review.md`, which no longer exist: leave them unless asked).
- Test: none new; the full build is the check.

- [ ] **Step 1: Write the configuration and README changes.**
- [ ] **Step 2: Run** `./mvnw clean verify` → PASS, `contracts/openapi.yaml` unchanged by a regeneration.
- [ ] **Step 3: Commit** — `git commit -m "docs: subscriptions, Stripe setup and new character rules"`
