# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Vue d'ensemble

DnDGate : référentiel des SRD de Donjons & Dragons et gestion de table de jeu (aventures, MJ et joueurs, personnages, grimoires, notes, messages, planning, évaluations).

Monorepo (voir `docs/adr/0001-monorepo.md`) :

- `apps/api/` : API Java 21 / Spring Boot 4, architecture hexagonale. **Seule application qui existe aujourd'hui.**
- `apps/web/` (Angular) et `apps/mobile/` : à créer. Ils ne dépendront que du contrat, jamais de l'API directement.
- `contracts/openapi.yaml` : contrat de l'API, source de vérité pour les clients.
- `docs/adr/` : décisions d'architecture. `docs/api-review.md` : backlog et décisions de l'audit de l'API (statut de chaque point, décisions prises par l'utilisateur).

Le `README.md` décrit en détail les règles métier et de sécurité (authentification, sessions, droits « participant », transfert de MJ, suppressions, pagination, limites d'entrée). Le lire avant de toucher à ces sujets plutôt que de deviner.

Une PR ne touche idéalement qu'une application, sauf changement de contrat. CI par chemin (`.github/workflows/api-ci.yml`).

## Commandes (depuis `apps/api`, `mvnw.cmd` sous Windows)

```
docker compose up -d                                   # (racine) PostgreSQL + MinIO locaux
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # lancer l'API
./mvnw clean verify                                    # build complet = ce que lance la CI
./mvnw test -Dtest=RatingEligibilityServiceTest            # une classe de test
./mvnw test -Dtest='RatingEligibilityServiceTest#method'   # une méthode
./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true   # régénérer contracts/openapi.yaml
```

- Les tests d'intégration (`src/test/.../integration`) utilisent Testcontainers (vrai PostgreSQL) : **Docker doit tourner**.
- `verify` échoue sous **90 % de lignes couvertes** (JaCoCo, `coverage.minimum` dans le `pom.xml`). La marge est faible : tout nouveau code vient avec ses tests.
- Après avoir supprimé ou renommé une migration Flyway, faire un `clean` : Maven laisse l'ancien script dans `target/classes` et Flyway refuse de démarrer.
- Secrets par variables d'environnement (`.env.example`) ; le profil `dev` fournit des valeurs de développement.

## Architecture de l'API

Paquet racine `fr.barlords.dndgateapi`, quatre couches dont les dépendances sont **vérifiées par ArchUnit** (`src/test/.../bootstrap/*DevelopmentRulesTest`) :

| Couche | Rôle | Peut être utilisée par |
|---|---|---|
| `domain` | Métier pur : `functional/model`, `functional/service`, `functional/exception`, ports `port/in` (`*Api`) et `port/out` (`*Spi`) | client, server, bootstrap |
| `client` | Entrée HTTP : `controller` (`*Controller`, `@RestController`), `dto`, `mapper` DTO↔modèle, `validator`, `error` | bootstrap seulement |
| `server` | Sortie : `adapter` (`*Adapter`, implémente un `*Spi`), `entity` JPA, `repository` (`*Repository`), `mapper` entité↔modèle, `mail`, `audit` | bootstrap seulement |
| `bootstrap` | Câblage Spring, sécurité, filtres, transactions | personne |

Flux d'une requête : `*Controller` → interface `*Api` (port in) → `*Service` du domaine → interface `*Spi` (port out) → `*Adapter` → repository JPA.

Points qui demandent de lire plusieurs fichiers :

- **Le domaine ne connaît pas Spring** : il ne dépend que de `java`, Lombok, Vavr et SLF4J. Les services sont instanciés à la main par des `@Bean` dans `bootstrap/config/domain/*ServiceConfiguration`. Un nouveau service = sa classe dans le domaine + son bean ici.
- **Transactions hors du domaine** : quand une opération doit être atomique, un décorateur dans `bootstrap/config` (`TransactionalAdventureDeleter`, `TransactionalAccountAnonymizer`, `TransactionalMasterTransfer`) enveloppe l'`*Api` du domaine. `RetryingTransaction` rejoue l'opération dans une nouvelle transaction sur `AdventureConcurrentModificationException` (verrou optimiste) : c'est ce qui protège jointures simultanées, votes et transfert de MJ.
- **Erreurs** : le domaine lève ses exceptions (`domain/functional/exception/...`) ; les `client/error/*ErrorHandler` (`@RestControllerAdvice`, `HIGHEST_PRECEDENCE`) les traduisent en `application/problem+json` (RFC 9457, avec `correlationId`). `FallbackErrorHandler` (`LOWEST_PRECEDENCE`) renvoie une 500 sans détail ; un nouveau handler doit garder une priorité plus haute que lui, sinon il est masqué.
- **Visibilité dans la requête** : les droits de lecture (aventures privées, notes privées du MJ) sont appliqués dans la requête en base, pour que la pagination ne compte que ce que l'appelant peut voir.

## Règles imposées par les tests

Ces règles font échouer `./mvnw verify` ; les respecter d'emblée :

- **Langue** : code, commentaires, logs et messages de l'API en anglais. Le seul français est le texte des e-mails, dans `src/main/resources/mail/emails_fr.properties` (`LanguagePolicyTest` refuse toute lettre accentuée dans `src/main/java`).
- **Aléatoire** : `SecureRandom` uniquement, `java.util.Random` est interdit (`RandomnessRulesTest`).
- **Routes** : noms en kebab-case, la méthode HTTP dit l'action, les filtres sont des paramètres de requête, « moi » se dit `mine` / `me` / `as-player` / `as-master` (`RouteNamingIntegrationTest`).
- **Contrat** : `contracts/openapi.yaml` est généré (springdoc, `OpenApiConfig`), jamais édité à la main. `OpenApiContractIntegrationTest` échoue si le code et le fichier divergent : régénérer et committer le contrat dans la même PR que le changement de route.
- **Base** : Flyway (`src/main/resources/db/migration`), Hibernate en `ddl-auto=validate`. Pour changer le modèle : modifier l'entité et ajouter `V<n>__<description>.sql`. Ne jamais modifier une migration existante.
- **Suppressions irréversibles** : tout `DELETE` irréversible exige une confirmation vérifiée par l'API dans le corps (mot de passe pour le compte, nom retapé pour aventure / personnage / grimoire, `confirm: true` pour les notes). Toute nouvelle suppression suit la même règle.
- Toutes les routes avec un corps JSON sont validées (`@Valid`).

## Tests

- Tests unitaires du domaine dans `src/test/.../domain/functional/service/...` (sans Spring).
- Tests d'intégration : étendre `ApiIntegrationTestSupport` (un conteneur PostgreSQL partagé par toutes les classes, profil `dev`, appels HTTP réels).
- Méthode suivie dans `docs/api-review.md` : chaque correctif vient avec un test, puis un **contrôle par mutation** (réintroduire le défaut, vérifier que le test devient rouge, restaurer). Mettre à jour le statut du point concerné dans `docs/api-review.md`.
