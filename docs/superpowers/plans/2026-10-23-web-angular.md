# Front web Angular de DnDGate : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** livrer `apps/web/`, le front Angular de DnDGate (accueil Bibliothèque / Taverne, Bibliothèque publique multi-jeux de règles avec grimoires, Taverne connectée, comptes), après un lot qui corrige le contrat de l'API.

**Architecture :** un lot préalable dans `apps/api` rend le contrat exact et rattache les grimoires à un jeu de règles. Le front est une application Angular 22 autonome (composants standalone, signaux, sans zone) qui parle à notre API **uniquement** par un client généré depuis `contracts/openapi.yaml`, et à dnd5eapi.co **uniquement** par des adaptateurs derrière une interface `Ruleset`. Un kit d'interface maison porte l'identité « Grimoire enluminé ».

**Tech Stack :** Java 21 / Spring Boot 4 / springdoc / Flyway (lot 0) ; Angular 22, Angular CDK, Vitest (builder `@angular/build:unit-test`), `ng-openapi-gen` 1.1.0, `@stomp/rx-stomp`, `@fontsource/cinzel` + `@fontsource/spectral`, Playwright, angular-eslint.

**Spec :** `docs/superpowers/specs/2026-10-23-web-angular-design.md` (à lire avant toute tâche ; fait foi en cas de doute). Règles métier de l'API : `README.md` racine. Règles du dépôt : `.claude/CLAUDE.md`.

## Global Constraints

- Une branche et une PR **par lot** (lot 0 : `feat/api-contract-for-web` ; lots suivants : `feat/web-<lot>`). Le lot 0 ne touche que `apps/api/`, `contracts/`, `README.md` ; les lots 1 à 5 ne touchent que `apps/web/`, `.github/workflows/web-ci.yml`, `.gitignore`.
- Lot 0 : toutes les règles de `.claude/CLAUDE.md` (anglais dans le code, tests + **contrôle par mutation** pour chaque correctif, contrat régénéré par `./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true` et commité, `./mvnw clean verify` vert avec ≥ 90 % de lignes couvertes, Docker démarré).
- Front : le code, les noms et les commentaires sont en anglais ; **tout texte affiché est en français**, en **tutoiement** (comme les maquettes validées : « Tes aventures, ta table, tes compagnons »). Le contenu des règles reste en anglais.
- Front : aucun appel HTTP à notre API hors du client généré (`src/app/core/api/generated`, non commité). Aucun appel à dnd5eapi.co hors de `src/app/features/library/rulesets/`.
- Front : pas de Bootstrap ni Angular Material ; Angular CDK autorisé (overlay, dialog, a11y, layout).
- Front : `npm run lint`, `npm run test:ci` (couverture lignes **≥ 80 %**) et `npm run build` passent avant chaque commit de fin de tâche.
- Identité « Grimoire enluminé » : fond cuir `#17100a` → `#3b2a1a`, parchemin `#f3e6c8` → `#e6d3a8` (ombre intérieure `#b8945a66`, liseré `#c9a24a`), or `#d9b45a` / or clair `#e9cf8a`, rouge sang `#7a1f12`, encre `#3a2a17`, texte clair `#f1e4c8`. Titres : Cinzel (capitales, `letter-spacing` 0,12–0,22 em). Texte : Spectral.
- `prefers-reduced-motion: reduce` coupe particules, rotation du sceau et transitions décoratives.
- `localStorage` : chaque lecture / écriture est dans un `try/catch` ; l'application fonctionne sans.
- Chemins imposés par les e-mails de l'API : `/verify-email?email=&code=`, `/confirm-email?email=&code=`, `/login`, `/adventures/:id`.
- Identifiants de jeux de règles : `dnd5e-2014` (« DnD 5e — SRD 2014 »), `dnd5e-2024` (« DnD 5e — SRD 2024 »). Format : `^[a-z0-9]+(-[a-z0-9]+)*$`, 50 caractères au plus. Jeu par défaut et jeu des grimoires existants : `dnd5e-2014`.
- Hors périmètre : Forge, WebRTC / visio, autres JDR que D&D 5e, i18n, CD.

## Review Focus

1. **Deux requêtes expirent en même temps** (jeton d'accès périmé) : un seul `POST /accounts/refresh` doit partir, les deux requêtes sont rejouées avec le nouveau jeton (un second refresh réutiliserait un jeton à usage unique et révoquerait la session). → Tâche 6, test `refreshes once for concurrent 401s`.
2. **Jeton de rafraîchissement rejeté** (expiré, révoqué, rejoué) : déconnexion propre vers `/login?returnUrl=…`, sans boucle de refresh ni erreur affichée en double. → Tâche 6, test `logs out and redirects when the refresh fails`.
3. **dnd5eapi.co indisponible ou lente** : la Bibliothèque affiche un état d'erreur avec « Réessayer », le reste du site (Taverne, compte) n'est pas touché. → Tâche 11, test `shows a retry state when the ruleset fails`.
4. **Sort d'un grimoire absent du jeu de règles** (index inconnu → 404 côté dnd5eapi) : la ligne affiche « Sort introuvable » avec l'index, et reste retirable ; le reste du grimoire s'affiche. → Tâche 14, test `shows unknown spells and lets them be removed`.
5. **Modification concurrente** (`409` sur un `PATCH` d'aventure, de personnage, de note…) : message clair « quelqu'un a modifié … entre-temps » avec un bouton « Recharger » qui relit l'objet, sans perdre la saisie tant que l'utilisateur n'a pas rechargé. → Tâche 5 (mécanisme), Tâche 17 test `offers to reload on a 409 while editing`.

---

## Carte des fichiers (front)

```
apps/web/
  angular.json, package.json, tsconfig*.json, eslint.config.js, proxy.conf.json, ng-openapi-gen.json
  Dockerfile, nginx.conf, playwright.config.ts, README.md
  public/images/emblem.svg                 emblème provisoire (remplaçable)
  public/images/home/bibliotheque.webp     (absent au départ : déposé par l'utilisateur)
  public/images/home/taverne.webp          (idem)
  src/styles/            _tokens.scss, _typography.scss, _parchment.scss, styles.scss
  src/environments/      environment.ts (apiBaseUrl: '/dndgate-api'), environment.development.ts
  src/app/
    app.config.ts, app.routes.ts, app.ts
    core/
      api/api.providers.ts                 ApiConfiguration.rootUrl ← environment.apiBaseUrl
      api/generated/                       généré (gitignore)
      api/fetch-all-pages.ts
      auth/token-store.ts, auth.service.ts, auth.interceptor.ts, auth.guard.ts
      errors/api-problem.ts, notifier.service.ts, form-errors.ts
      storage/safe-storage.ts
      accounts/account-directory.service.ts
      realtime/stomp.service.ts
    shared/ui/           button, parchment, form-field, icon, emblem, toast-outlet, spinner,
                         confirm-dialog (+ confirm.service.ts), paginator, data-list, star-rating, date-picker
    layout/              shell/ (topbar, sidebar, breadcrumb), nav.model.ts, sidebar-state.service.ts,
                         breadcrumb.service.ts
    features/
      home/              home.page.ts, home-half.component.ts, particles.component.ts
      account/           login, signup, verify-email, confirm-email, profile, public-profile pages
      library/           library.routes.ts, library-shell.ts, ruleset-select.ts
        rulesets/        ruleset.model.ts, ruleset-registry.ts, selected-ruleset.service.ts,
                         dnd5e/dnd5e-client.ts, dnd5e/dnd5e-2014.ruleset.ts, dnd5e/dnd5e-2024.ruleset.ts,
                         dnd5e/mappers/*.ts
        pages/           section-list.page.ts, entry-detail.page.ts, detail-blocks.component.ts, ruleset-home.page.ts
        spellbooks/      spellbook-list.page.ts, spellbook-detail.page.ts, spellbook-form.ts,
                         add-to-spellbook.component.ts, spellbook-file.ts
      tavern/            tavern.routes.ts, tavern-shell.ts, adventure-card.ts, quest-board.page.ts,
                         join-by-code.page.ts, adventure-form.ts, create-adventure.page.ts, my-adventures.page.ts,
                         adventure-status.ts
        adventure/       adventure-shell.ts, adventure-context.service.ts, overview.page.ts, players.page.ts,
                         characters/*, chronicle.page.ts, gm-notes.page.ts, note-editor.ts, planning.page.ts,
                         messages.page.ts, master.page.ts
  e2e/                   home.spec.ts, library.spec.ts, auth.spec.ts
```

Tests unitaires : à côté du fichier testé (`*.spec.ts`).

---

# Lot 0 — API et contrat (branche `feat/api-contract-for-web`)

### Task 1 : rendre le contrat exact pour un client généré

**Files :**
- Modify : `apps/api/src/main/java/fr/barlords/dndgateapi/client/controller/GameCharacterController.java` (les deux `@GetMapping(params = …)`)
- Modify : `apps/api/src/main/java/fr/barlords/dndgateapi/client/controller/SpellBookController.java` (`importSpellBookFile`, `exportSpellBookFile`)
- Modify : `GameCharacterController#getGameCharacterPicture` (documentation de la réponse)
- Modify : `apps/api/src/main/java/fr/barlords/dndgateapi/bootstrap/config/OpenApiConfig.java` (`CONTRACT_VERSION` → `"2.0.0"`)
- Modify : `contracts/openapi.yaml` (régénéré), `README.md` (tableau « Contrat et noms des routes » si un nom change)
- Test : intégration existante des personnages (ou nouvelle `GameCharacterListIntegrationTest`), `SpellBookImportExportIntegrationTest`, `OpenApiContractIntegrationTest`

**Interfaces :**
- Produces (contrat) :
  - `GET /game-characters` : **une** opération `getGameCharacters`, paramètres `ownerId` et `adventureId` **facultatifs**, exactement un des deux attendu ; les deux ou aucun → `400` problem+json, `detail` : `"Give exactly one of ownerId or adventureId"`. Comportement et droits de chaque filtre inchangés.
  - `POST /spellbooks/import-file` : `requestBody` `multipart/form-data`, propriété `file` (`string`, `binary`), requise.
  - `GET /game-characters/{id}/picture` : réponse `200` en `image/png` et `image/jpeg`, schéma `string` / `binary` (documentation seulement : ne pas ajouter de `produces`, pour ne pas changer la négociation).
  - `GET /spellbooks/{id}/export-file` : réponse `200` `application/json`, schéma `SpellBookImportExportDto` (documentation seulement).

- [ ] **Step 1 : tests rouges**
  - `a_list_of_characters_needs_exactly_one_filter` : `GET /game-characters` sans paramètre → 400 ; avec `ownerId` et `adventureId` → 400 ; chaque filtre seul → 200 (réutiliser les données des tests existants).
  - `a_spell_book_file_is_imported_as_multipart` : l'import envoyé en `multipart/form-data` (partie `file`) répond 201 (vérifier que les tests existants l'envoient déjà ainsi ; sinon les y passer).
  - Contrat : ajouter dans le test du contrat (ou un test dédié qui lit `contracts/openapi.yaml`) les assertions des quatre points ci-dessus (une seule opération sous `/game-characters` GET avec deux paramètres non requis ; `multipart/form-data` sur l'import ; `image/png` + `image/jpeg` binaires sur l'image ; `$ref` `SpellBookImportExportDto` sur l'export).
- [ ] **Step 2 : lancer** `./mvnw test -Dtest='GameCharacter*IntegrationTest,SpellBookImportExportIntegrationTest,OpenApiContractIntegrationTest'` → FAIL attendu sur les nouvelles assertions.
- [ ] **Step 3 : implémenter**
  - Un seul handler `getGameCharacters(@RequestParam(required = false) String ownerId, @RequestParam(required = false) String adventureId)` qui délègue aux deux recherches du domaine ; l'erreur 400 passe par le mécanisme 400 existant de `client/error` (problem+json avec `correlationId`).
  - Import : `@PostMapping(value = "/import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` et `@RequestPart("file") MultipartFile file`.
  - Image et export : `@ApiResponse(responseCode = "200", content = …)` springdoc ; vérifier que `OpenApiConfig.declareJson` ne réécrit pas les types image (il garde déjà `binary`).
  - `CONTRACT_VERSION = "2.0.0"` (rupture : nom d'opération et corps de l'import).
- [ ] **Step 4 : régénérer le contrat** `./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true`, relire le diff de `contracts/openapi.yaml` (seuls les quatre points et la version changent), puis relancer les tests du Step 2 → PASS.
- [ ] **Step 5 : contrôle par mutation** : remettre provisoirement l'ancien `@GetMapping(params = "ownerId")`, puis `@RequestParam("file")` sans `consumes` : le test correspondant devient rouge ; restaurer.
- [ ] **Step 6 : `./mvnw clean verify`** → BUILD SUCCESS, couverture ≥ 90 %.
- [ ] **Step 7 : commit** `fix(api): describe character listing, spell book import, picture and export exactly in the contract`

### Task 2 : un grimoire appartient à un jeu de règles

**Files :**
- Create : `apps/api/src/main/resources/db/migration/V18__spellbook_ruleset.sql`
- Modify : `domain/functional/model/SpellBook.java`, `server/entity/SpellBookEntity.java`, `server/mapper/SpellBookEntityMapper.java`, `client/dto/spellbook/SpellBookCreationRequest.java`, `SpellBookDto.java`, `SpellBookImportExportDto.java`, `client/mapper/SpellBookDtoMapper.java`, `SpellBookImportExportDtoMapper.java` ; le service de mise à jour si le test du Step 1 le montre fautif
- Modify : `contracts/openapi.yaml` (régénéré), `README.md` (paragraphe sur les grimoires : jeu de règles, format, valeur par défaut à l'import)
- Test : `SpellBookCreatorServiceTest`, `SpellBookImportExportIntegrationTest`, test d'intégration de création / patch des grimoires

**Interfaces :**
- Produces :
  - `SpellBook.ruleset` (`String`, `@With`) et `SpellBook.LEGACY_RULESET = "dnd5e-2014"`.
  - `SpellBookCreationRequest.ruleset` : `@NotBlank @Size(max = 50) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")`, requis.
  - `SpellBookDto.ruleset` (toujours présent).
  - `SpellBookImportExportDto.ruleset` : facultatif à l'import (`@Size(max = 50) @Pattern(...)`), absent → `LEGACY_RULESET` ; toujours écrit à l'export.
  - `SpellBookPatchRequest` n'a **pas** de `ruleset` : un patch ne le change jamais.
  - Colonne `spellbook.ruleset varchar(50) not null`, `updatable = false` dans l'entité.

- [ ] **Step 1 : tests rouges**
  - Domaine : `creating_a_spell_book_keeps_its_ruleset`.
  - Intégration : `a_spell_book_is_created_with_its_ruleset` (201, `ruleset` renvoyé) ; `a_spell_book_without_ruleset_is_a_400` ; `a_ruleset_that_is_not_an_identifier_is_a_400` (ex. `"DnD 5e"`) ; `patching_a_spell_book_keeps_its_ruleset` ; `the_export_contains_the_ruleset` ; `an_import_keeps_the_ruleset_of_the_file` (`dnd5e-2024`) ; `an_import_without_ruleset_is_dnd5e_2014`.
- [ ] **Step 2 : lancer** ces tests → FAIL.
- [ ] **Step 3 : migration** `V18__spellbook_ruleset.sql` : ajouter la colonne avec `DEFAULT 'dnd5e-2014'` (remplit les lignes existantes) puis `DROP DEFAULT`.
- [ ] **Step 4 : implémenter** le champ dans le modèle, l'entité, les deux mappers d'entité, les DTO et mappers client ; le patch construit l'objet à partir du grimoire existant (le `ruleset` lu en base est conservé).
- [ ] **Step 5 : régénérer le contrat** (`-Dopenapi.update=true`) et relancer les tests → PASS.
- [ ] **Step 6 : contrôle par mutation** : faire ignorer `ruleset` par `SpellBookImportExportDtoMapper.importToDomain` (toujours `LEGACY_RULESET`) → `an_import_keeps_the_ruleset_of_the_file` rouge ; retirer `@Pattern` → test du 400 rouge ; restaurer.
- [ ] **Step 7 : `./mvnw clean verify`** (faire un `clean` : nouvelle migration) → BUILD SUCCESS.
- [ ] **Step 8 : commit** `feat(api): a spell book belongs to a ruleset`, puis ouvrir la PR du lot 0. Les lots suivants partent de `main` une fois cette PR fusionnée (le client généré en dépend).

---

# Lot 1 — Fondations du front (branche `feat/web-foundation`)

### Task 3 : squelette `apps/web`, client généré, CI

**Files :**
- Create : `apps/web/**` (via `npx @angular/cli@22 new web --directory apps/web --style=scss --routing --ssr=false --prefix=dg --skip-git`), `apps/web/ng-openapi-gen.json`, `apps/web/proxy.conf.json`, `apps/web/src/app/core/api/api.providers.ts`, `apps/web/src/app/core/api/fetch-all-pages.ts`, `apps/web/src/environments/*`, `apps/web/Dockerfile`, `apps/web/nginx.conf`, `apps/web/README.md`, `.github/workflows/web-ci.yml`
- Modify : `.gitignore` (`apps/web/src/app/core/api/generated/`)
- Test : `apps/web/src/app/core/api/fetch-all-pages.spec.ts`, `apps/web/src/app/app.spec.ts`

**Interfaces :**
- Produces :
  - Scripts npm : `generate:api` (`ng-openapi-gen -c ng-openapi-gen.json` ; entrée `../../contracts/openapi.yaml`, sortie `src/app/core/api/generated`), `start` (`ng serve --proxy-config proxy.conf.json`), `build`, `test` (`ng test`), `test:ci` (`ng test --watch=false --coverage`), `lint` (`ng lint`), `e2e` (`playwright test`) ; `prestart` / `prebuild` / `pretest` / `pretest:ci` lancent `generate:api`.
  - `environment.apiBaseUrl = '/dndgate-api'` ; `proxy.conf.json` : `/dndgate-api` → `http://localhost:4000`, `ws: true`, `changeOrigin: true`.
  - `provideApi()` dans `api.providers.ts` : fixe `ApiConfiguration.rootUrl` à `environment.apiBaseUrl` ; utilisé par `app.config.ts`.
  - `fetchAllPages<T>(load: (page: number) => Observable<{ content?: T[]; totalPages?: number }>): Observable<T[]>` : charge les pages avec `size = 100` jusqu'à `totalPages`, concatène dans l'ordre.
  - `app.config.ts` : `provideZonelessChangeDetection()` (si ce n'est pas déjà le défaut), `provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'top' }))`, `provideHttpClient(withInterceptors([]))` (l'intercepteur arrive en Task 6).
  - Couverture : seuil lignes 80 % dans la configuration de test (`coverageThresholds` du builder `unit-test`, ou `vitest` équivalent).
  - `web-ci.yml` : déclenché sur PR vers `main` pour `apps/web/**`, `contracts/**`, `.github/workflows/web-ci.yml` ; Node 24, `npm ci`, `npm run lint`, `npm run test:ci`, `npm run build` dans `apps/web`. Calqué sur `api-ci.yml` (versions d'actions identiques).
  - `Dockerfile` multi-étapes (build Node 24 → `nginx:alpine`), `nginx.conf` avec repli SPA (`try_files $uri /index.html`).

- [ ] **Step 1 : générer le projet** avec la commande ci-dessus ; ajouter `ng-openapi-gen@1.1.0`, `@angular/cdk@22`, angular-eslint (`ng add @angular-eslint/schematics`).
- [ ] **Step 2 : test rouge** `fetch-all-pages.spec.ts` : `loads every page in order` (3 pages de 100/100/7 → 207 éléments, ordre conservé, appels page 0,1,2) ; `stops after one page when totalPages is 1` ; `returns an empty list when content is missing`. Lancer `npm test` → FAIL.
- [ ] **Step 3 : implémenter** `fetchAllPages`, `provideApi`, environnements, proxy, scripts.
- [ ] **Step 4 : vérifier** `npm run generate:api` crée 11 services, `npm run test:ci` PASS avec seuil, `npm run lint` et `npm run build` OK.
- [ ] **Step 5 : README** `apps/web/README.md` : prérequis, commandes, régénération du client, emplacement des images de l'accueil et de l'emblème à remplacer.
- [ ] **Step 6 : commit** `feat(web): scaffold the Angular app with the generated API client and CI`

### Task 4 : identité visuelle et kit d'interface

**Files :**
- Create : `apps/web/src/styles/_tokens.scss`, `_typography.scss`, `_parchment.scss`, `styles.scss` ; `apps/web/src/app/shared/ui/{button,parchment,form-field,icon,emblem,spinner,toast-outlet,paginator,star-rating}/*.ts` ; `confirm-dialog/confirm-dialog.ts`, `confirm-dialog/confirm.service.ts` ; `apps/web/public/images/emblem.svg`
- Test : `confirm.service.spec.ts`, `form-field.spec.ts`, `paginator.spec.ts`, `star-rating.spec.ts`

**Interfaces :**
- Produces :
  - Variables CSS dans `:root` (`--dg-leather-0`, `--dg-leather-1`, `--dg-parchment-0`, `--dg-parchment-1`, `--dg-gold`, `--dg-gold-light`, `--dg-blood`, `--dg-ink`, `--dg-text-light`, `--dg-font-title`, `--dg-font-body`) avec les valeurs des Global Constraints ; polices `@fontsource/cinzel` (500, 700) et `@fontsource/spectral` (400, 600, 400 italique).
  - `<dg-button variant="primary|ghost|danger" [loading]>` ; `<dg-parchment>` (panneau parchemin) ; `<dg-form-field label="…" [control]>` qui affiche sous le champ les messages de `control.errors` (`required` → « Ce champ est obligatoire. », `minlength`, `maxlength`, `pattern`, `email`, `server` → texte venu de l'API) ; `<dg-icon name="…">` (SVG en ligne, jeu fixe : `book, scroll, sword, skull, wand, users, map, calendar, chat, crown, plus, key, user, gear, chevron-left, chevron-right, menu, logout, upload, download, trash, edit, star`) ; `<dg-emblem>` (affiche `/images/emblem.svg`, emblème provisoire : hexagone doré + triangle, comme la maquette) ; `<dg-spinner>` ; `<dg-paginator [page] [totalPages] (pageChange)>` ; `<dg-star-rating [value] [readonly] (valueChange)>` (1 à 5).
  - `ConfirmService.confirm(opts: { title: string; message: string; confirmLabel: string }): Observable<boolean>`.
  - `ConfirmService.typeToConfirm(opts: { title: string; message: string; expected?: string; inputLabel: string; inputType: 'text' | 'password'; confirmLabel: string }): Observable<string | null>` : dialogue CDK ; si `expected` est donné, le bouton reste désactivé tant que la saisie (rognée) n'est pas égale à `expected` ; renvoie la saisie **telle quelle**, ou `null` si annulé.

- [ ] **Step 1 : tests rouges** :
  - `typeToConfirm keeps confirm disabled until the name matches` ; `typeToConfirm returns the typed password unchanged` (espaces conservés) ; `confirm emits false on cancel` ;
  - `form-field shows the server message` ; `paginator hides itself with one page` ;
  - `star-rating emits the clicked value` et `ignores clicks when readonly`.
  Lancer → FAIL.
- [ ] **Step 2 : implémenter** jetons, typographie, mixin parchemin (dégradé + ombre intérieure + liseré), composants. Focus visible doré sur tout élément interactif ; contraste AA du texte encre sur parchemin.
- [ ] **Step 3 : tests** → PASS ; `lint`, `build`.
- [ ] **Step 4 : commit** `feat(web): illuminated grimoire design tokens and UI kit`

### Task 5 : erreurs de l'API (problem+json)

**Files :**
- Create : `apps/web/src/app/core/errors/api-problem.ts`, `notifier.service.ts`, `form-errors.ts`, `apps/web/src/app/shared/ui/toast-outlet/toast-outlet.ts` (si pas déjà fait)
- Test : `api-problem.spec.ts`, `form-errors.spec.ts`, `notifier.service.spec.ts`

**Interfaces :**
- Produces :
  - `interface ApiProblem { status: number; title?: string; detail?: string; correlationId?: string; errors: { field: string; message: string }[]; retryAfterSeconds?: number }`.
  - `toApiProblem(error: unknown): ApiProblem` : lit un `HttpErrorResponse` (corps problem+json, y compris quand il arrive en `Blob` pour les requêtes binaires), l'en-tête `Retry-After`, et produit `status: 0` + `detail: 'network'` pour une erreur réseau.
  - `NotifierService.success(message: string): void` ; `NotifierService.error(problem: ApiProblem, context?: string): void` : toast parchemin rouge avec un message français choisi par statut (`0` « Le serveur ne répond pas. », `403` « Tu n'as pas les droits pour cette action. », `404` « Introuvable. », `409` « Conflit : » + `detail`, `429` « Trop de tentatives, réessaie dans N s. », `5xx` « Erreur du serveur. »), puis `detail` de l'API s'il existe, puis « Référence : <correlationId> » (sélectionnable).
  - `NotifierService.conflict(onReload: () => void): void` : toast « Quelqu'un a modifié cet élément entre-temps. » avec bouton « Recharger ».
  - `applyFieldErrors(form: FormGroup, problem: ApiProblem): boolean` : pose `{ server: message }` sur chaque contrôle nommé par `errors[].field` ; renvoie `true` si au moins un champ a été trouvé.

- [ ] **Step 1 : tests rouges** : `reads detail, correlationId and field errors` ; `reads a problem from a Blob body` ; `reads Retry-After as seconds` ; `maps a network error to status 0` ; `applyFieldErrors marks the named controls` ; `applyFieldErrors returns false for unknown fields` ; `error toast shows the correlation id`. → FAIL.
- [ ] **Step 2 : implémenter** ; `ToastOutlet` monté dans `app.ts`, toasts empilés en bas à droite, fermeture auto 6 s (sauf `conflict`), annoncés par `aria-live="polite"`.
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): show API problems with their correlation id`

### Task 6 : authentification

**Files :**
- Create : `apps/web/src/app/core/storage/safe-storage.ts`, `apps/web/src/app/core/auth/{token-store,auth.service,auth.interceptor,auth.guard}.ts`
- Modify : `apps/web/src/app/app.config.ts` (intercepteur, chargement du profil au démarrage)
- Test : `safe-storage.spec.ts`, `token-store.spec.ts`, `auth.interceptor.spec.ts`, `auth.service.spec.ts`, `auth.guard.spec.ts`

**Interfaces :**
- Consumes : client généré (`login`, `refresh`, `logout`, `logoutAll`, `getMyProfile`), `toApiProblem`.
- Produces :
  - `SafeStorage.get(key: string): string | null`, `set(key: string, value: string): void`, `remove(key: string): void` (jamais d'exception).
  - `TokenStore` : jeton d'accès **en mémoire** ; jeton de rafraîchissement dans `SafeStorage` sous `dg.refresh` ; `accessToken(): string | null`, `refreshToken(): string | null`, `save(auth: AuthDto): void`, `clear(): void`.
  - `AuthService` : `account: Signal<AccountDto | null>`, `isAuthenticated: Signal<boolean>`, `restore(): Promise<void>` (au démarrage : s'il y a un jeton de rafraîchissement, refresh puis `getMyProfile`, sinon rien ; jamais d'erreur affichée), `login(pseudo: string, password: string): Observable<void>`, `logout(): Observable<void>`, `logoutAll(): Observable<void>`, `refreshAccessToken(): Observable<string>` (**partagé** : tant qu'un refresh est en vol, tout appel reçoit le même Observable), `reloadAccount(): Observable<AccountDto>`, `forgetSession(): void` (vide le store et le compte sans appel réseau).
  - `authInterceptor: HttpInterceptorFn` : ajoute `Authorization: Bearer` **seulement** aux URL qui commencent par `environment.apiBaseUrl` ; sur `401` d'une route autre que `/accounts/login` et `/accounts/refresh` : `refreshAccessToken()` puis rejoue une fois ; si le refresh échoue : `forgetSession()`, navigation vers `/login?returnUrl=<url courante>`, et l'erreur d'origine est propagée sans toast supplémentaire. Un `403` ne déconnecte jamais.
  - `authGuard: CanActivateFn` : laisse passer si connecté, sinon `UrlTree` `/login?returnUrl=<url demandée>`.

- [ ] **Step 1 : tests rouges** (HttpTestingController) :
  - `adds the bearer only to API calls` (pas sur `https://www.dnd5eapi.co/...`) ;
  - **`refreshes once for concurrent 401s`** : deux requêtes → deux 401 → une seule requête `POST /dndgate-api/accounts/refresh`, puis les deux rejouées avec le nouveau jeton ;
  - **`logs out and redirects when the refresh fails`** : refresh → 401 ; store vidé, `Router.navigateByUrl('/login?returnUrl=…')` appelé une fois, pas de second refresh ;
  - `does not refresh on a 403` ; `never refreshes the refresh call itself` ;
  - `restore loads the profile from a stored refresh token` ; `restore stays logged out without token` ;
  - `login saves tokens and loads the account` ; `logout clears the session even if the call fails` ;
  - `guard redirects with returnUrl` ; `safe storage survives a throwing localStorage`.
  → FAIL.
- [ ] **Step 2 : implémenter** ; `restore()` via `provideAppInitializer`.
- [ ] **Step 3 : tests** → PASS ; `lint`, `build`.
- [ ] **Step 4 : commit** `feat(web): token-based session with a single shared refresh`

### Task 7 : page d'accueil

**Files :**
- Create : `apps/web/src/app/features/home/home.page.ts`, `home-half.component.ts`, `particles.component.ts` (+ `.scss`)
- Modify : `apps/web/src/app/app.routes.ts` (route `''`)
- Test : `home.page.spec.ts`, `particles.component.spec.ts`

**Interfaces :**
- Consumes : `AuthService.isAuthenticated`.
- Produces : `HomePage` (route `''`), `<dg-home-half side="library|tavern" [link] title subtitle cta>`, `<dg-particles kind="dust|embers" [active]>`.

Valeurs fixées par la spec et la maquette validée (`accueil-hover-v3.html`, reprise ici) :
- Deux moitiés plein écran (50 vw × 100 vh), pas de barre du haut sur l'accueil, emblème DnDGate discret centré en haut.
- Bibliothèque à gauche : titre « BIBLIOTHÈQUE », sous-titre « Sorts, classes, monstres et règles », bouton « ENTRER », lien `/library`. Taverne à droite : « TAVERNE », « Tes aventures, ta table, tes compagnons », « S'ATTABLER », lien `/tavern` (le garde envoie à la connexion si besoin).
- Fond : `url('/images/home/bibliotheque.webp')` (resp. `taverne.webp`), `cover`, **par-dessus** un dégradé de remplacement (Bibliothèque : `radial-gradient(circle at 50% 30%, #6a5a3c, #2d2416 45%, #120d08)` + rayures de rayonnages ; Taverne : `radial-gradient(circle at 50% 75%, #c8641e, #5a2a10 40%, #170c06)`) : sans fichier, le dégradé reste visible.
- Ombre bas → haut `linear-gradient(to top, #0d0906ee, #0d090655 45%, transparent 70%)` ; titre Cinzel 700, `letter-spacing: .18em`, couleur `#e9cf8a`.
- Au survol **ou au focus clavier** d'une moitié : cadre SVG à 18 px des bords, `stroke #d9b45a` 1,5 px, tracé de `stroke-dashoffset` 1400 → 0 en 1,1 s `cubic-bezier(.6,0,.2,1)` ; sceau runique (cercle, cercle pointillé, deux triangles) de 180 px, opacité 0 → 0,7, rotation 18 s linéaire ; image `scale(1.05)` en 1,4 s ; titre `translateY(-14px)` + halo `0 0 22px #d9b45acc` ; sous-titre puis bouton apparaissent (délais 0,15 s / 0,3 s) ; l'autre moitié `filter: sepia(.8) brightness(.4)` en 0,7 s.
- Particules (seulement pendant le survol) : Bibliothèque, 9 grains de poussière 3 px `#f5e6b0` (halo 6 px), montée de 460 px en 6 s avec dérive horizontale de −26 à +28 px, délais étalés de 0 à 5 s ; Taverne, 12 braises 4 px `#ffb04a` (halo `#ff7a1a`), montée en 3,0–3,9 s qui rapetissent jusqu'à 0,5, **et** oscillation latérale indépendante (élément interne, `ease-in-out` alterné, amplitude 7–14 px, demi-période 0,7–1,3 s, propres à chaque braise).
- `prefers-reduced-motion` : pas de particules ni de rotation ; le cadre apparaît sans tracé animé.
- Écran < 768 px : moitiés empilées (100 vw × 50 vh) ; le premier appui active l'effet, le second suit le lien.

- [ ] **Step 1 : tests rouges** : `renders the library on the left and the tavern on the right` ; `links to /library and /tavern` ; `activates a half on keyboard focus` (classe `is-active`) ; `particles render 9 dust motes and 12 embers` ; `particles render nothing when reduced motion is preferred` (simuler `matchMedia`). → FAIL.
- [ ] **Step 2 : implémenter** (CSS d'abord ; la seule logique TypeScript est l'état actif, le premier appui tactile et `matchMedia`).
- [ ] **Step 3 : tests** → PASS ; vérifier à l'œil avec `npm start` (survol des deux côtés, réduction des animations activée dans le système).
- [ ] **Step 4 : commit** `feat(web): split home page with illuminated hover`

### Task 8 : coque des pages intérieures

**Files :**
- Create : `apps/web/src/app/layout/shell/{shell,topbar,sidebar,breadcrumb}.ts` (+ `.scss`), `layout/nav.model.ts`, `layout/sidebar-state.service.ts`, `layout/breadcrumb.service.ts`, `apps/web/src/app/features/not-found.page.ts`
- Modify : `apps/web/src/app/app.routes.ts` (squelette des routes, voir ci-dessous)
- Test : `sidebar-state.service.spec.ts`, `breadcrumb.service.spec.ts`, `shell.spec.ts`

**Interfaces :**
- Consumes : `AuthService.account`, `SafeStorage`, `<dg-emblem>`, `<dg-icon>`.
- Produces :
  - `interface NavItem { label: string; icon: IconName; link: string; exact?: boolean }` ; `interface NavGroup { label: string; items: NavItem[] }`.
  - `<dg-shell title="BIBLIOTHÈQUE" [groups]="NavGroup[]">` avec `<ng-content select="[sidebarHeader]">` (sous le titre : y vient la liste des jeux de règles) et `<ng-content>` pour le contenu (habituellement `<router-outlet>`).
  - Barre du haut : 44 px, fond `#120d08`, filet d'or en dégradé dessous, `<dg-emblem>` + « DNDGATE » centrés, lien vers `/`.
  - Sommaire : titre de partie en Cinzel 700 centré, intertitres de groupe en capitales dorées 10 px, élément actif avec liseré or à gauche ; en bas : avatar (initiale du pseudo, cercle rouge sang liseré or) + pseudo + lien `/account`, ou « Se connecter » (`/login?returnUrl=…`).
  - `SidebarStateService.collapsed: Signal<boolean>`, `toggle(): void` ; mémorisé sous `dg.sidebar.collapsed` ; par défaut ouvert sur grand écran, réduit sous 768 px (CDK `BreakpointObserver`).
  - Sommaire ouvert 220 px ; réduit 56 px avec une icône par élément (infobulle = libellé), le titre de partie et la liste des jeux de règles masqués ; bouton de bascule (`chevron-left` / `chevron-right`, `aria-expanded`, `aria-controls`) en haut du sommaire ; transition de largeur 250 ms ; sous 768 px, ouvert = tiroir par-dessus le contenu avec voile, fermé au clic sur un lien.
  - `BreadcrumbService` : construit le fil depuis `route.data.breadcrumb` (chaîne) de la racine à la feuille ; `setLast(label: string): void` remplace le libellé du dernier élément (pour les noms chargés : sort, aventure…) ; `crumbs: Signal<{ label: string; link: string }[]>`. Le fil s'affiche en haut du contenu, séparateur « › », dernier élément non cliquable.
  - Routes (composants chargés à la demande ; pages encore absentes = `NotFoundPage` provisoire remplacée par les tâches suivantes) :
    - `''` accueil ; `login`, `signup`, `verify-email`, `confirm-email` ; `account` (`authGuard`) ; `players/:accountId` (`authGuard`) ;
    - `library` → `library.routes.ts` ; `tavern` (`authGuard`) → `tavern.routes.ts` ; `adventures/:id` → redirection `tavern/adventures/:id` ; `**` → `NotFoundPage` (« Cette page s'est perdue dans les brumes. » + lien accueil).

- [ ] **Step 1 : tests rouges** : `remembers the collapsed state` ; `works when storage throws` ; `starts collapsed on small screens` ; `builds crumbs from route data` ; `setLast replaces the last label` ; `shell shows the account or a login link` ; `collapsed shell shows icons with labels as tooltips`. → FAIL.
- [ ] **Step 2 : implémenter** (maquette de référence : `barre-haut-v2.html`).
- [ ] **Step 3 : tests** → PASS ; `lint`, `build`.
- [ ] **Step 4 : commit** `feat(web): app shell with centred emblem, collapsible sidebar and breadcrumb` ; ouvrir la PR du lot 1.

---

# Lot 2 — Compte (branche `feat/web-account`)

### Task 9 : connexion, inscription, confirmations d'e-mail

**Files :**
- Create : `apps/web/src/app/features/account/{login,signup,verify-email,confirm-email}.page.ts`, `account/account-layout.ts` (page centrée sur fond cuir, carte parchemin, emblème)
- Test : un `*.spec.ts` par page

**Interfaces :**
- Consumes : `AuthService.login`, client généré (`createAccount`, `verifyEmail`, `resendVerificationEmail`, `confirmEmailChange`), `applyFieldErrors`, `NotifierService`.
- Produces : routes `login`, `signup`, `verify-email`, `confirm-email`.

Règles (README racine, « Vérification de l'adresse e-mail ») :
- Connexion : pseudo + mot de passe ; succès → `returnUrl` (seulement s'il commence par `/`) sinon `/tavern`. `401` → « Pseudo ou mot de passe incorrect. » (pas de toast générique) ; `429` → délai `Retry-After`.
- Inscription : pseudo (`^\s*[\p{L}\p{N}_.\-]{3,30}\s*$`, flag `u`), e-mail (5–50), mot de passe (8 caractères min., 72 **octets** max. : `new TextEncoder().encode(pw).length <= 72`), confirmation du mot de passe. `202` → écran « Dernière étape : ouvre le lien reçu par e-mail pour valider ton compte. » + bouton « Renvoyer l'e-mail » (`resendVerificationEmail`, toujours un message neutre). `409` → erreur sur le champ pseudo « Ce pseudo est déjà pris. ».
- `/verify-email?email=&code=` : appelle `verifyEmail` au chargement ; `204` → « Ton compte est validé. » + lien Se connecter ; `400` → « Ce lien n'est plus valable. » + formulaire de renvoi ; `409` → « Ce pseudo a été pris entre-temps : inscris-toi avec un autre. » + lien inscription ; paramètres manquants → même message que `400`.
- `/confirm-email?email=&code=` : `confirmEmailChange` (sans jeton) ; `204` → « Ta nouvelle adresse est confirmée. » ; `400` → lien non valable ; `409` → « Cette adresse est déjà utilisée par un autre compte. ». Si connecté, `reloadAccount()` après succès.

- [ ] **Step 1 : tests rouges** : `login goes to returnUrl` ; `login ignores an external returnUrl` ; `login shows a wrong-credentials message on 401` ; `signup rejects a password over 72 bytes` (ex. 25 × « é » + …) ; `signup shows the check-your-mail step on 202` ; `signup marks the pseudo on 409` ; `verify-email handles 204, 400 and 409` ; `confirm-email handles 204, 400 and 409`. → FAIL.
- [ ] **Step 2 : implémenter** (formulaires réactifs typés, validateurs depuis les contraintes du contrat).
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): login, signup and e-mail confirmation pages`

### Task 10 : profil, page publique, annuaire des comptes

**Files :**
- Create : `apps/web/src/app/core/accounts/account-directory.service.ts`, `apps/web/src/app/features/account/{profile,public-profile}.page.ts`
- Test : `account-directory.service.spec.ts`, `profile.page.spec.ts`, `public-profile.page.spec.ts`

**Interfaces :**
- Consumes : `AuthService`, `ConfirmService.typeToConfirm`, client généré (`patchAccount`, `deleteMyAccount`, `getAccountById`, `getMeanRateOfAccount`, `getRatingsByAccount`).
- Produces :
  - `AccountDirectory.get(id: string): Observable<PublicAccountDto>` (cache mémoire par id, requêtes simultanées partagées) ; `AccountDirectory.pseudo(id: string): Signal<string>` (« … » pendant le chargement, « Compte supprimé » si `anonymized`).
  - Route `account` : sections « Identité » (pseudo, e-mail ; si `pendingEmail` : badge « en attente de confirmation : <adresse> »), « Mot de passe », « Sessions » (« Se déconnecter », « Se déconnecter de partout »), « Zone dangereuse » (supprimer le compte). Chaque modification demande le mot de passe actuel (`currentPassword`). Changer le mot de passe → toutes les sessions terminées : `forgetSession()` puis `/login` avec message.
  - Route `players/:accountId` : pseudo, note moyenne (`<dg-star-rating readonly>` + « N avis », « Pas encore d'avis » si `average` est `null`), et, si c'est mon compte, la liste paginée des notes reçues.
  - Suppression : `typeToConfirm({ inputType: 'password', … })`, corps `{ confirmation: <saisie> }` ; `204` → `forgetSession()`, accueil, toast « Ton compte a été supprimé. » ; `403` → « Mot de passe incorrect. » sans déconnexion.

- [ ] **Step 1 : tests rouges** : `directory caches and shares requests` ; `directory names anonymized accounts` ; `profile shows the pending e-mail` ; `profile sends currentPassword with every change` ; `changing the password ends the session` ; `delete account stays logged in on 403` ; `public profile shows no-rating state`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): profile, account deletion and public player page` ; ouvrir la PR du lot 2.

---

# Lot 3 — Bibliothèque (branche `feat/web-library`)

### Task 11 : abstraction des jeux de règles et pages génériques

**Files :**
- Create : `apps/web/src/app/features/library/rulesets/{ruleset.model,ruleset-registry,selected-ruleset.service}.ts`, `features/library/{library.routes,library-shell,ruleset-select}.ts`, `features/library/pages/{ruleset-home,section-list,entry-detail}.page.ts`, `pages/detail-blocks.component.ts`, `features/library/rulesets/fake.ruleset.ts` (pour les tests)
- Test : `selected-ruleset.service.spec.ts`, `section-list.page.spec.ts`, `entry-detail.page.spec.ts`, `detail-blocks.component.spec.ts`, `library-shell.spec.ts`

**Interfaces :**
- Produces (`ruleset.model.ts`) :
```ts
export type RulesetId = string; // ^[a-z0-9]+(-[a-z0-9]+)*$
export type SectionGroup = 'players' | 'master' | 'rules';
export interface ColumnDef { key: string; label: string; sortable: boolean }
export interface FilterDef { key: string; label: string; options: { value: string; label: string }[] }
export interface RulesetSection { id: string; label: string; group: SectionGroup; icon: IconName; columns: ColumnDef[]; filters: FilterDef[] }
export type FieldValue = string | number | boolean | string[];
export interface EntrySummary { index: string; name: string; fields: Record<string, FieldValue> }
export type DetailBlock =
  | { kind: 'text'; title?: string; paragraphs: string[] }            // markdown léger : **gras**, *italique*, listes
  | { kind: 'stats'; title?: string; items: { label: string; value: string }[] }
  | { kind: 'table'; title?: string; columns: string[]; rows: string[][] }
  | { kind: 'entries'; title?: string; items: { name: string; text: string; link?: { section: string; index: string } }[] };
export interface EntryDetail { index: string; name: string; subtitle?: string; tags: string[]; blocks: DetailBlock[] }
export interface Ruleset {
  readonly id: RulesetId; readonly label: string; readonly sections: readonly RulesetSection[];
  readonly spellSectionId: string | null;          // null : pas de grimoires pour ce jeu
  list(sectionId: string): Observable<EntrySummary[]>;
  detail(sectionId: string, index: string): Observable<EntryDetail>;
}
export const RULESETS = new InjectionToken<Ruleset[]>('RULESETS');
```
  - `RulesetRegistry.all(): Ruleset[]`, `get(id: RulesetId): Ruleset | undefined` (à partir de `RULESETS`, `multi`).
  - `SelectedRulesetService.current: Signal<Ruleset>` (suit le paramètre `:rulesetId` de l'URL ; repli `dnd5e-2014`), mémorisé sous `dg.ruleset`.
  - Routes (`library.routes.ts`, toutes sous `LibraryShell`, breadcrumb « Bibliothèque ») : `''` → redirection vers `<jeu mémorisé ou dnd5e-2014>` ; `grimoires` et `grimoires/:id` (`authGuard`, Task 14 ; déclarées **avant** les routes à paramètre) ; `:rulesetId` → `RulesetHomePage` ; `:rulesetId/:sectionId` → `SectionListPage` ; `:rulesetId/:sectionId/:index` → `EntryDetailPage`. Jeu ou rubrique inconnus → `NotFoundPage`.
  - `LibraryShell` : `<dg-shell title="BIBLIOTHÈQUE">` ; `<dg-ruleset-select>` (liste déroulante native stylée, libellés complets) dans `sidebarHeader` : changer de jeu navigue vers `/library/<id>` (ou la même rubrique si elle existe dans le nouveau jeu) ; groupes « POUR LES JOUEURS » / « POUR LE MJ » / « RÈGLES » construits depuis `sections` ; groupe « MES GRIMOIRES » (lien `/library/grimoires`) seulement si connecté et `spellSectionId !== null`.
  - `SectionListPage` : charge `list()` une fois par (jeu, rubrique) (mis en cache par l'adaptateur) ; recherche par nom (insensible à la casse et aux accents), un `<select>` par `FilterDef`, tri par colonne `sortable` (clic sur l'en-tête, flèche), pagination client 25 / 50 / 100 avec `<dg-paginator>` ; état d'URL `?q=&sort=&dir=&page=&<filtre>=` pour pouvoir partager le lien ; ligne cliquable vers le détail.
  - `EntryDetailPage` : titre, sous-titre italique, puces (`tags`), puis `<dg-detail-blocks>` ; `BreadcrumbService.setLast(name)` ; emplacement `<ng-content>`/slot pour « Ajouter au grimoire » (Task 15).
  - États communs : chargement (`<dg-spinner>`), **erreur avec bouton « Réessayer »**, liste vide « Aucune entrée ne correspond. ».

- [ ] **Step 1 : tests rouges** (avec `fake.ruleset.ts`) :
  - `selected ruleset follows the url and falls back to dnd5e-2014` ;
  - `list filters by name ignoring accents and case` ; `list applies a select filter` ; `list sorts by a column both ways` ; `list paginates and keeps state in the url` ;
  - **`shows a retry state when the ruleset fails`** (le `list()` échoue → bouton « Réessayer » relance `list()` ; le shell reste affiché) ;
  - `detail renders text, stats, table and entries blocks` ; `detail sets the breadcrumb to the entry name` ;
  - `sidebar hides spell books when logged out or without spells` ; `changing ruleset keeps the section when it exists`.
  → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): ruleset-agnostic library with search, filters and detail blocks`

### Task 12 : jeu de règles « DnD 5e — SRD 2014 »

**Files :**
- Create : `apps/web/src/app/features/library/rulesets/dnd5e/dnd5e-client.ts`, `dnd5e-2014.ruleset.ts`, `mappers/{spell,monster,class,race,background,personality,rule}.mapper.ts`, `__fixtures__/2014/*.json` (réponses réelles enregistrées une fois avec `curl`, réduites)
- Modify : `app.config.ts` (fournir `RULESETS` multi)
- Test : `dnd5e-2014.ruleset.spec.ts`, un `*.mapper.spec.ts` par mapper

**Interfaces :**
- Consumes : `Ruleset`, `EntrySummary`, `EntryDetail`.
- Produces :
  - `Dnd5eClient` (`baseUrl = 'https://www.dnd5eapi.co'`) : `rest<T>(version: '2014' | '2024', path: string): Observable<T>` (GET `/api/<version>/<path>`) et `graphql<T>(version, query: string, variables?: object): Observable<T>` (POST `/graphql/<version>`) ; cache mémoire par URL / requête (`shareReplay`), une erreur n'est pas mise en cache.
  - `Dnd5e2014Ruleset` : `id 'dnd5e-2014'`, `label 'DnD 5e — SRD 2014'`, `spellSectionId 'spells'`.
  - Rubriques et sources (listes : GraphQL quand il faut des colonnes / filtres, REST sinon ; détails : REST `/api/2014/<ressource>/<index>`) :

| id | libellé | groupe | colonnes / filtres | source liste |
|---|---|---|---|---|
| `classes` | Classes | players | — | REST `classes` |
| `races` | Races | players | — | REST `races` |
| `backgrounds` | Historiques | players | — | REST `backgrounds` |
| `personality` | Personnalité | players | (entrées : `alignments`, `languages`) | REST `alignments` + `languages` |
| `spells` | Sorts | players | niveau, école, classes ; filtres niveau (0 = « Tour de magie »), école, classe | GraphQL `spells { index name level school { name } classes { index name } ritual concentration }` |
| `monsters` | Monstres | master | type, taille, FP ; filtres type, FP | GraphQL `monsters { index name type size challenge_rating }` |
| `rules` | Règles | rules | — (137 entrées, détail `rules/<index>` : `desc` en markdown) | REST `rules` |

  - Mappers vers `EntryDetail` (ce que l'ancien front affichait, à reprendre) : sort (niveau + école en sous-titre ; puces temps d'incantation, portée, composantes, durée, concentration, rituel ; `desc[]` et `higher_level[]` en texte ; matériaux) ; monstre (CA, PV, vitesses, 6 caractéristiques avec modificateurs, sens, langues, FP / XP en `stats` ; capacités spéciales, actions, réactions, actions légendaires en `entries`) ; classe (dé de vie, maîtrises, jets de sauvegarde, équipement de départ, tableau des niveaux depuis `/classes/<i>/levels`, aptitudes par niveau) ; race (bonus de caractéristiques, âge, alignement, taille, vitesse, traits, langues, sous-races) ; historique (aptitude, traits / idéaux / liens / défauts en `table`) ; personnalité (alignements, langues) ; règle (texte markdown).
  - Le markdown des règles (tableaux, titres `##`) est rendu par le bloc `text` (sous-ensemble sûr, sans HTML brut).

- [ ] **Step 1 : enregistrer les fixtures** (`curl` vers dnd5eapi.co, garder 2–3 éléments par liste et un détail par rubrique).
- [ ] **Step 2 : tests rouges** : un test par mapper sur sa fixture (ex. `maps fireball: level 3 evocation, V S M, 8d6 in text`) ; `lists spells with level, school and classes filters` ; `monsters filter by challenge rating` ; `caches a list and does not cache errors`. → FAIL.
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : tests** → PASS ; vérifier à la main dans le navigateur chaque rubrique contre l'API réelle.
- [ ] **Step 5 : commit** `feat(web): D&D 5e SRD 2014 ruleset`

### Task 13 : jeu de règles « DnD 5e — SRD 2024 »

**Files :**
- Create : `apps/web/src/app/features/library/rulesets/dnd5e/dnd5e-2024.ruleset.ts`, `mappers/2024/*.mapper.ts` (ou mappers paramétrés quand les formes sont identiques), `__fixtures__/2024/*.json`
- Test : `dnd5e-2024.ruleset.spec.ts`, mappers 2024

**Interfaces :**
- Consumes : `Dnd5eClient`, mappers de la Task 12 réutilisables.
- Produces : `Dnd5e2024Ruleset` : `id 'dnd5e-2024'`, `label 'DnD 5e — SRD 2024'`, `spellSectionId 'spells'`. Rubriques : `classes`, `species` (« Espèces »), `backgrounds`, `feats` (« Dons »), `personality`, `spells`, `weapon-mastery-properties` (« Maîtrises d'armes »), `monsters`, `magic-items` (« Objets magiques », groupe master). **Pas** de `rules`.

Différences vérifiées le 2026-10-22 dont il faut tenir compte : `races` → `species` ; sort `description: string` (et non `desc[]`) ; le schéma GraphQL 2024 n'a pas les mêmes noms (ex. `monsters` n'existe pas, `monsters2024` oui) : **faire une introspection** (`{ __schema { queryType { fields { name } } } }`) au début de la tâche et utiliser REST pour toute liste dont la requête GraphQL n'est pas disponible (les listes REST 2024 de sorts donnent déjà `level`).

- [ ] **Step 1 : introspection + fixtures.**
- [ ] **Step 2 : tests rouges** : `maps a 2024 spell description` ; `exposes species and no rules` ; `lists 2024 monsters` ; un test par mapper nouveau. → FAIL.
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : tests** → PASS ; contrôle manuel de chaque rubrique.
- [ ] **Step 5 : commit** `feat(web): D&D 5e SRD 2024 ruleset`

### Task 14 : grimoires

**Files :**
- Create : `apps/web/src/app/features/library/spellbooks/{spellbook-list.page,spellbook-detail.page,spellbook-form,spellbook-file}.ts`
- Modify : `library.routes.ts` (pages réelles pour `grimoires`, `grimoires/:id`)
- Test : `spellbook-list.page.spec.ts`, `spellbook-detail.page.spec.ts`, `spellbook-file.spec.ts`

**Interfaces :**
- Consumes : client généré (`getMySpellBooks`, `createSpellBook`, `getSpellBookById`, `patchSpellBook`, `deleteSpellBookById`, `removeSpellFromBook`, `importSpellBookFile`, `exportSpellBookFile`), `RulesetRegistry`, `ConfirmService`.
- Produces :
  - Liste `/library/grimoires` : cartes parchemin (nom, jeu de règles en libellé complet, nombre de sorts) ; « Nouveau grimoire » (nom 1–255, description 1–5000, **jeu de règles** parmi ceux qui ont `spellSectionId`, défaut = jeu sélectionné) ; « Importer » : `<input type="file" accept="application/json,.json">` caché déclenché par le bouton, fichier envoyé tel quel en multipart ; erreurs `400` de l'import (champ par champ) affichées dans un encadré.
  - Détail `/library/grimoires/:id` : nom, description (modifiables, `409` → `NotifierService.conflict`), jeu de règles (non modifiable), sorts **résolus dans le jeu du grimoire** (nom, niveau, école, lien vers `/library/<ruleset>/spells/<index>`), triés par niveau puis nom ; retirer un sort ; « Exporter » ; « Supprimer » (`typeToConfirm` avec `expected` = nom).
  - `downloadSpellBook(dto: SpellBookImportExportDto, name: string): void` dans `spellbook-file.ts` : `Blob` JSON, nom de fichier `<nom-en-kebab>.json`.
  - Un grimoire dont le `ruleset` n'est pas dans le registre : sorts affichés par index, mention « Jeu de règles indisponible ».

- [ ] **Step 1 : tests rouges** : `creates a spell book with the selected ruleset` ; `import sends the chosen file as multipart` ; `import shows field errors from a 400` ; `resolves spells in the spell book ruleset, not the selected one` ; **`shows unknown spells and lets them be removed`** (détail 404 → ligne « Sort introuvable (<index>) » avec bouton retirer) ; `delete requires the exact name` ; `export downloads a json file named after the book`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): spell books bound to a ruleset, with import and export`

### Task 15 : « Ajouter au grimoire » depuis une fiche de sort

**Files :**
- Create : `apps/web/src/app/features/library/spellbooks/add-to-spellbook.component.ts`
- Modify : `features/library/pages/entry-detail.page.ts` (affiché quand `sectionId === ruleset.spellSectionId`)
- Test : `add-to-spellbook.component.spec.ts`

**Interfaces :**
- Consumes : `getMySpellBooks`, `addSpellToBook`, `removeSpellFromBook`.
- Produces : `<dg-add-to-spellbook [rulesetId] [spellIndex]>` : invisible si déconnecté ; menu des grimoires **du même jeu de règles** avec coche si le sort y est déjà (clic = ajouter / retirer) ; « Créer un grimoire » si aucun ; toast de succès.

- [ ] **Step 1 : tests rouges** : `offers only spell books of the same ruleset` ; `adds then removes a spell` ; `is hidden when logged out` ; `suggests creating a spell book when none fits`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): add a spell to a spell book from its page` ; ouvrir la PR du lot 3.

---

# Lot 4 — Taverne (branche `feat/web-tavern`)

### Task 16 : quêtes, code d'invitation, création, mes aventures

**Files :**
- Create : `apps/web/src/app/features/tavern/{tavern.routes,tavern-shell,adventure-card,adventure-status,adventure-form,quest-board.page,join-by-code.page,create-adventure.page,my-adventures.page}.ts`
- Test : `adventure-status.spec.ts`, `quest-board.page.spec.ts`, `my-adventures.page.spec.ts`, `create-adventure.page.spec.ts`, `join-by-code.page.spec.ts`

**Interfaces :**
- Consumes : client généré (`getAvailableAdventures`, `getAdventuresAsPlayer`, `getAdventuresAsMaster`, `createAdventure`, `joinAdventure`, `acceptPlayerInvitation`), `fetchAllPages`, `AccountDirectory`, `<dg-shell>`.
- Produces :
  - `adventure-status.ts` : `STATUS_LABEL: Record<Status, string>` = `NOT_STARTED` « Pas commencée », `ONGOING` « En cours », `FINISH` « Terminée », `ABORT` « Abandonnée » ; `allowedTransitions(s): Status[]` = `NOT_STARTED → [ONGOING, ABORT]`, `ONGOING → [FINISH, ABORT]`, `FINISH|ABORT → []` ; `isClosed(s)` = `FINISH|ABORT`.
  - `TavernShell` : `<dg-shell title="TAVERNE">`, groupes « QUÊTES » (Tableau des quêtes `/tavern/quests`, Rejoindre avec un code `/tavern/join`, Créer une aventure `/tavern/new`) et « MES AVENTURES » (En cours `/tavern/mine/ongoing`, Terminées `/tavern/mine/finished`) ; `''` → `quests`.
  - `<dg-adventure-card [adventure]>` : nom, extrait de description, ville + code postal, joueurs `players.length / maxPlayer`, statut, badge « MJ recherché » si `masterVacant`, visibilité.
  - Tableau des quêtes : `getAvailableAdventures` paginé côté serveur (20 / page) ; bouton « Rejoindre » (`joinAdventure` → aventure).
  - Rejoindre avec un code : champ code (≤ 50) → `acceptPlayerInvitation` → aventure ; `409` → « Cette aventure a déjà commencé ou est complète. ».
  - Créer : `<dg-adventure-form>` (nom 1–255, description 1–5000, joueurs max 1–100, visibilité publique / privée, code postal, ville) réutilisé en Task 17 ; `409` (50 aventures actives) → message dédié.
  - Mes aventures : `fetchAllPages` sur `as-player` et `as-master`, fusion sans doublon (par `id`), rôle affiché (« MJ » / « Joueur ») ; « En cours » = non `isClosed`, « Terminées » = `isClosed` ; tri par nom.

- [ ] **Step 1 : tests rouges** : `allowed transitions follow the README state machine` ; `quest board joins and opens the adventure` ; `my adventures merges player and master lists without duplicates` ; `finished tab shows FINISH and ABORT only` ; `create form enforces maxPlayer 1..100` ; `join by code shows the 409 message`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): tavern quest board, joining and my adventures`

### Task 17 : une aventure — coque, informations, joueurs

**Files :**
- Create : `apps/web/src/app/features/tavern/adventure/{adventure-shell,adventure-context.service,overview.page,players.page}.ts`
- Modify : `tavern.routes.ts` (`adventures/:id` + enfants)
- Test : `adventure-context.service.spec.ts`, `overview.page.spec.ts`, `players.page.spec.ts`

**Interfaces :**
- Consumes : `getAdventureById`, `updateAdventure`, `updateAdventureVisibility`, `updateAdventureStatus`, `deleteAdventureById`, `quitAdventure`, `invitePlayer`, `AuthService.account`, `AccountDirectory`, `ConfirmService`, `NotifierService.conflict`.
- Produces :
  - `AdventureContext` (fourni par `AdventureShell`) : `adventure: Signal<AdventureDto | null>`, `reload(): void`, `isMaster: Signal<boolean>` (= `creatorId === me` et non `masterVacant`), `isPlayer: Signal<boolean>` (`players` contient moi), `isParticipant: Signal<boolean>`, `update(dto: AdventureDto): void`.
  - Sous-navigation de l'aventure (onglets parchemin en haut du contenu, sous le fil d'Ariane « Taverne › <nom> › <onglet> ») : `overview` (Informations), `players` (Joueurs), `characters` (Personnages), `chronicle` (Chronique), `gm-notes` (Notes du MJ), `planning` (Planning), `messages` (Messagerie), `master` (MJ). Onglets réservés aux participants masqués pour un visiteur d'aventure publique (il ne voit que Informations + Planning + « Rejoindre »).
  - Informations : affichage ; pour le MJ, modifier (`<dg-adventure-form>`, `409` concurrence → `conflict(reload)` en gardant la saisie), visibilité, **statut** (boutons = `allowedTransitions` seulement, confirmation pour `FINISH` et `ABORT` : « irréversible »), supprimer (désactivé si `ONGOING` avec explication ; sinon `typeToConfirm` nom de l'aventure). Pour un joueur : « Quitter l'aventure » (confirmation).
  - Joueurs : MJ (pseudo, lien profil) puis joueurs ; pour le MJ si `NOT_STARTED` : « Inviter un joueur » par pseudo (`202` → toast ; `409`, `429` avec délai).
  - `adventures/:id` (lien des e-mails) arrive ici via la redirection de la Task 8.

- [ ] **Step 1 : tests rouges** : `context computes master, player and participant` ; `master sees only allowed status transitions` ; `delete is disabled while ongoing` ; `delete requires the adventure name` ; **`offers to reload on a 409 while editing`** (saisie conservée tant que « Recharger » n'est pas cliqué) ; `visitor of a public adventure sees info and planning only` ; `invite shows the retry delay on 429`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): adventure shell with information and players`

### Task 18 : personnages

**Files :**
- Create : `apps/web/src/app/features/tavern/adventure/characters/{character-list.page,character-form,character-detail.page,character-picture.service,character-picture.directive}.ts`
- Test : `character-picture.service.spec.ts`, `character-form.spec.ts`, `character-list.page.spec.ts`, `character-detail.page.spec.ts`

**Interfaces :**
- Consumes : `getGameCharacters` (`adventureId`), `createGameCharacter` (multipart : `picturePresentation` + `createGameCharacterRequest`), `getGameCharacterById`, `patchGameCharacter`, `deleteGameCharacterById`, `getGameCharacterPicture` (blob).
- Produces :
  - `CharacterPictureService.objectUrl(id: string): Observable<string>` (blob → `URL.createObjectURL`, mis en cache par id) ; `[dgCharacterPicture]="id"` sur un `<img>` : pose l'URL, la révoque à la destruction.
  - Routes `characters`, `characters/new`, `characters/:characterId`, `characters/:characterId/edit`.
  - Liste : cartes portrait (image, nom, race, classe, propriétaire) de **tout le groupe** ; « Créer un personnage » pour un participant.
  - Formulaire : nom 3–50, race 3–255, classe 1–255, personnalité 1–5000, histoire 1–80 000 (compteur), image obligatoire à la création : PNG ou JPEG, ≤ 5 Mo, vérifiée côté client par la **signature** (`89 50 4E 47` / `FF D8 FF`) avant l'envoi, aperçu ; `409` → « Tu as déjà 10 personnages dans cette aventure. ».
  - Détail : portrait, champs ; propriétaire : modifier, supprimer (`typeToConfirm` nom du personnage).

- [ ] **Step 1 : tests rouges** : `picture service fetches a blob and caches the url` ; `directive revokes the url on destroy` ; `form rejects a gif renamed png by its signature` ; `form rejects a picture over 5 MB` ; `create sends a multipart body with both parts` ; `list shows the whole party` ; `delete requires the character name`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): adventure characters with authenticated pictures`

### Task 19 : chronique et notes du MJ

**Files :**
- Create : `apps/web/src/app/features/tavern/adventure/{chronicle.page,gm-notes.page,note-editor}.ts`
- Test : `chronicle.page.spec.ts`, `gm-notes.page.spec.ts`

**Interfaces :**
- Consumes : `getHistoricalNotesByAdventureId`, `createHistoricalNote`, `patchHistoricalNote`, `deleteHistoricalNote` ; `getAdventureNotesByAdventureId`, `createAdventureNote`, `patchAdventureNote`, `deleteAdventureNote`.
- Produces :
  - `<dg-note-editor [withVisibility]>` : date (1900–2200, défaut aujourd'hui), contenu 1–5000 (compteur), visibilité publique / privée si demandé.
  - Chronique : frise verticale par date (plus récente en haut), paginée 20 ; tout participant crée ; modifier / supprimer selon les droits renvoyés par l'API (sur `403` : toast).
  - Notes du MJ : le MJ voit toutes ses notes (badge « Privée ») et les crée ; les joueurs ne voient que les publiques (filtré par l'API).
  - Suppression d'une note : `ConfirmService.confirm` puis corps `{ confirm: true }`.
  - `409` à la modification → `conflict(reload)`.

- [ ] **Step 1 : tests rouges** : `chronicle lists newest first and paginates` ; `deleting a note sends confirm true after confirmation` ; `gm notes marks private ones` ; `players cannot create gm notes`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): chronicle and game master notes`

### Task 20 : planning et disponibilités

**Files :**
- Create : `apps/web/src/app/shared/ui/date-picker/date-picker.ts`, `apps/web/src/app/features/tavern/adventure/planning.page.ts`
- Test : `date-picker.spec.ts`, `planning.page.spec.ts`

**Interfaces :**
- Consumes : `getPlanningByAdventureId`, `putPlanning`, `getAvailabilitiesByPlanningId`, `putAvailability`, `AccountDirectory`.
- Produces :
  - `<dg-date-picker mode="multiple" [selected] [allowed] (selectedChange)>` : calendrier mensuel parchemin, dates `YYYY-MM-DD`, `allowed` restreint les jours cliquables, 100 dates au plus.
  - Planning : pas de planning (404) → le MJ « Proposer des dates » (1–100) ; sinon dates proposées ; participant : cocher ses disponibilités → `putAvailability` (remplace son vote) ; tableau des votes (participants seulement) : une ligne par compte, une colonne par date, total par date, meilleure(s) date(s) en or. Le MJ peut modifier les dates (`putPlanning`). Visiteur d'une aventure publique : dates seulement.

- [ ] **Step 1 : tests rouges** : `date picker toggles dates and respects allowed days` ; `master proposes dates when there is no planning` ; `voting replaces my availability` ; `vote table totals each date and highlights the best`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): planning with availability votes`

### Task 21 : messagerie temps réel

**Files :**
- Create : `apps/web/src/app/core/realtime/stomp.service.ts`, `apps/web/src/app/features/tavern/adventure/messages.page.ts`
- Test : `stomp.service.spec.ts`, `messages.page.spec.ts`

**Interfaces :**
- Consumes : `@stomp/rx-stomp`, `AuthService.refreshAccessToken`, `TokenStore.accessToken`, `getMessageByAdventureId`, `sendMessage`, `AccountDirectory`.
- Produces :
  - `StompService.watchChat(adventureId: string): Observable<MessageDto>` : connexion unique et paresseuse à `ws(s)://<hôte><apiBaseUrl>/ws/websocket` (point SockJS en WebSocket brut, pas de dépendance `sockjs-client`), en-tête de `CONNECT` `Authorization: Bearer <jeton>` (jeton rafraîchi dans `beforeConnect` s'il manque), destination `/topic/adventure/chat/<uuid en minuscules>` ; identifiant d'abonnement unique à chaque abonnement ; reconnexion automatique (5 s) ; désabonnement à la fin de l'Observable ; déconnexion à `forgetSession()`.
  - Messagerie : historique paginé (20, du plus récent au plus ancien côté API) affiché du plus ancien au plus récent, « Messages plus anciens » en haut ; nouveaux messages reçus ajoutés en bas **sans doublon** (par `id`, le message envoyé arrive aussi par le sujet) ; pseudo + heure locale ; saisie 1–5000, Entrée envoie, Maj+Entrée saute une ligne ; `429` → « Tu envoies trop de messages, réessaie dans N s. » ; indicateur « connexion perdue » quand STOMP est déconnecté.

- [ ] **Step 1 : tests rouges** (client STOMP simulé) : `subscribes to the lowercase chat topic with the bearer header` ; `uses a fresh subscription id on resubscribe` ; `messages shows history oldest first and loads older pages` ; `a received message is appended once even if also returned by send` ; `send shows the retry delay on 429`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS ; essai manuel à deux navigateurs contre l'API locale.
- [ ] **Step 4 : commit** `feat(web): real-time adventure chat`

### Task 22 : rôle de MJ

**Files :**
- Create : `apps/web/src/app/features/tavern/adventure/master.page.ts`, `apps/web/src/app/features/tavern/accept-master.page.ts` (route `tavern/master-code`, lien dans le groupe « QUÊTES » : « Devenir MJ avec un code »)
- Test : `master.page.spec.ts`, `accept-master.page.spec.ts`

**Interfaces :**
- Consumes : `stepDownAsMaster`, `inviteMaster`, `revokeMasterInvitation`, `acceptMasterInvitation`, `getMasterClaim`, `claimMasterRole`, `cancelMasterClaim`, `voteOnMasterClaim`, `AdventureContext`.
- Produces (règles : README racine, « Changer de MJ ») ; rien n'est proposé si l'aventure est `isClosed` :
  - MJ : « Inviter un MJ » → affiche le code et son expiration (copier dans le presse-papiers), avertit qu'un nouveau code remplace l'ancien ; « Révoquer l'invitation » ; « Quitter le rôle de MJ » (`typeToConfirm` nom de l'aventure).
  - Joueur, `masterVacant` sans vote en cours : « Demander à devenir MJ ».
  - Vote en cours (`OPEN`) : candidat, échéance, état de chaque votant (`AccountDirectory`) ; votant : « Accepter » / « Refuser » (modifiable tant que le vote est ouvert) ; candidat : « Retirer ma demande ».
  - Accepter un code (n'importe quel compte connecté) : champ code → aventure.
  - Après chaque action qui change le rôle : `AdventureContext.reload()`.

- [ ] **Step 1 : tests rouges** : `master can create, show and revoke an invitation` ; `step down requires the adventure name` ; `a player can claim only when the seat is vacant` ; `voters see approve and refuse, the candidate can cancel` ; `nothing is offered on a closed adventure` ; `accepting a code opens the adventure`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): game master hand-over, invitation and vote`

### Task 23 : évaluer les autres participants

**Files :**
- Modify : `apps/web/src/app/features/tavern/adventure/players.page.ts`
- Test : `players.page.spec.ts`

**Interfaces :**
- Consumes : `getRatingBetween`, `putRating`, `getMeanRateOfAccount`, `<dg-star-rating>`.
- Produces : dans « Joueurs », si l'aventure est `ONGOING` ou `FINISH` et que je suis participant : pour chaque autre participant non anonymisé, ma note (`getRatingBetween` ; `404` = pas encore notée) modifiable par `<dg-star-rating>` → `putRating` ; à côté, la moyenne publique du compte.

- [ ] **Step 1 : tests rouges** : `rating is offered only on ongoing or finished adventures` ; `no rating for myself or anonymized accounts` ; `a click puts the rating` ; `a 404 means not rated yet`. → FAIL.
- [ ] **Step 2 : implémenter.**
- [ ] **Step 3 : tests** → PASS.
- [ ] **Step 4 : commit** `feat(web): rate fellow players` ; ouvrir la PR du lot 4.

---

# Lot 5 — Bout en bout (branche `feat/web-e2e`)

### Task 24 : parcours Playwright et finitions

**Files :**
- Create : `apps/web/playwright.config.ts`, `apps/web/e2e/{home,library,auth}.spec.ts`, `apps/web/e2e/fixtures/*`
- Modify : `.github/workflows/web-ci.yml` (job `e2e` : `npx playwright install --with-deps chromium`, `npm run e2e`), `apps/web/README.md`

**Interfaces :**
- Consumes : toute l'application ; API et dnd5eapi.co **simulées** par `page.route` (pas de backend en CI).

- [ ] **Step 1 : parcours** :
  - `home` : survol de la Bibliothèque → cadre et particules visibles ; clic → `/library/dnd5e-2014` ; Taverne déconnecté → `/login?returnUrl=%2Ftavern` ;
  - `library` : liste des sorts, recherche « fire », filtre niveau 3, fiche Fireball, bascule vers SRD 2024 garde la rubrique ; sommaire réduit puis rouvert, état conservé après rechargement ;
  - `auth` : connexion → Taverne ; deux appels en 401 simultanés → un seul refresh ; déconnexion.
- [ ] **Step 2 : lancer** `npm run e2e` → PASS (Chromium).
- [ ] **Step 3 : finitions** : `README` (lancer le front contre l'API locale, remplacer images et emblème), audit rapide d'accessibilité (navigation clavier de l'accueil, du sommaire, des dialogues ; contraste).
- [ ] **Step 4 : commit** `test(web): end-to-end journeys` ; ouvrir la PR du lot 5.
