# DnDGate

Référentiel des SRD de Donjons & Dragons et gestion de table de jeu.

## Structure

```
apps/
  api/       API Java / Spring Boot, architecture hexagonale
  web/       Front Angular (à créer)
  mobile/    Application mobile (à définir)
contracts/   Contrat d'API (OpenAPI), source de vérité pour les clients
docs/adr/    Décisions d'architecture
```

Chaque application a son propre build et sa propre CI (déclenchée par chemin).
Les applications ne dépendent pas les unes des autres : `web` et `mobile` ne
connaissent que le contrat dans `contracts/`.

## Démarrage

Infrastructure locale (PostgreSQL, MinIO) :

```
docker compose up -d
```

API :

```
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Les secrets se passent par variables d'environnement, voir `.env.example`.

Production : `docker compose -f docker-compose.prod.yml up -d` lance l'API et sa base (sans MinIO ni reverse proxy). Les variables obligatoires sont vérifiées au démarrage. L'API n'écoute que sur `127.0.0.1:4000` ; le port de gestion (4001, sondes et métriques) n'est jamais publié.

## Contrat et noms des routes

`contracts/openapi.yaml` est le contrat de l'API (voir `contracts/README.md`) : il est produit par l'API et vérifié par un test.

Les routes sont des **noms en minuscules séparés par des tirets** ; la méthode HTTP dit l'action. Ce qui filtre une liste est un paramètre de requête (`GET /game-characters?adventureId=`), ce qui désigne « moi » est `mine`, `me` ou `as-player` / `as-master`. Un test (`RouteNamingIntegrationTest`) refuse toute route qui n'est pas en kebab-case.

| Avant | Maintenant |
|---|---|
| `POST /accounts/log` | `POST /accounts/login` |
| `GET /adventures/getWhereImPlayer`, `getWhereImMaster` | `GET /adventures/as-player`, `/adventures/as-master` |
| `PATCH /adventures/{id}/setVisibility`, `/setStatus` | `PUT /adventures/{id}/visibility`, `PUT /adventures/{id}/status` |
| `POST /adventures/{id}/sendInvitationCode/{pseudo}` | `POST /adventures/{id}/invitations/{pseudo}` |
| `POST /adventures/joinByInvitationCode` | `POST /adventures/invitations/accept` |
| `DELETE /adventures/{id}/quit` | `DELETE /adventures/{id}/players/me` |
| `GET /adventure-notes/getByAdventureId/{id}`, idem `historical-notes` | `GET /adventure-notes?adventureId={id}`, `GET /historical-notes?adventureId={id}` |
| `GET /messages/getByAdventureId?adventureId=` | `GET /messages?adventureId=` |
| `GET /game-characters/getByAdventureId/{id}`, `getByOwnerId/{id}`, `getMyGameCharacters` | `GET /game-characters?adventureId=`, `?ownerId=`, `GET /game-characters/mine` |
| `GET /spellbooks/getByOwnerId/{id}`, `getMySpellBooks` | `GET /spellbooks?ownerId=`, `GET /spellbooks/mine` |
| `POST /spellbooks/{id}/addSpell`, `DELETE /spellbooks/{id}/removeSpell/{index}` | `POST /spellbooks/{id}/spells`, `DELETE /spellbooks/{id}/spells/{index}` |
| `GET /ratings/getByTargetedAccount`, `getByEmitterAndTargetedAccount`, `getMeanRateOfAccount` | `GET /ratings?targetedAccountId=`, `GET /ratings/between`, `GET /ratings/mean` |
| `GET /planning/adventure/{id}`, `GET /planning/availability/{planningId}` | `GET /planning?adventureId={id}`, `GET /planning/{planningId}/availability` |

Les anciens noms n'existent plus (pas d'alias : aucun client ne les utilisait).

## Conventions

- Branche principale : `main`. Travail par branches et pull requests.
- Une PR ne touche idéalement qu'une application, sauf changement de contrat.
- Décisions structurantes consignées dans `docs/adr/`.

## Base de données

Le schéma est géré par Flyway (`apps/api/src/main/resources/db/migration`).
Hibernate ne modifie jamais la base (`ddl-auto=validate`) : il vérifie seulement
que les entités correspondent au schéma et refuse de démarrer sinon.

Pour faire évoluer le modèle :

1. Modifier l'entité JPA.
2. Ajouter une migration `V<n>__<description>.sql` (numéro suivant, double underscore).
3. Ne jamais modifier une migration déjà appliquée : Flyway refuse de démarrer si son contenu a changé.

## Listes paginées

Les listes qui peuvent grossir sans limite sont paginées : `?page=0&size=20` (`page` à partir de 0, `size` de 1 à 100, 20 par défaut ; sinon `400`).
La réponse est `{ "content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }`.

| Route | Ordre |
|---|---|
| `GET /adventures`, `/adventures/available`, `/adventures/as-master`, `/adventures/as-player` | nom, puis identifiant |
| `GET /messages?adventureId=` | du plus récent au plus ancien |
| `GET /adventure-notes?adventureId=`, `GET /historical-notes?adventureId=` | du plus récent au plus ancien |
| `GET /ratings?targetedAccountId=` | identifiant |

La visibilité (aventures publiques ou auxquelles on participe, notes privées du MJ) est décidée **dans la requête** : le total ne compte que ce que la personne a le droit de voir.
`POST /planning/{planningId}/availability` (et `PUT`) est « créer ou remplacer » : un seul vote par compte et par planning (index unique), le vote enregistré est renvoyé. La date d'un message est fixée par le serveur à la réception (`Instant`, UTC) : `sendDate` n'existe plus dans la requête d'envoi (rupture de contrat), et la liste reste du plus récent au plus ancien.

Les listes bornées par nature (personnages d'une aventure ou d'un compte, grimoires, objets magiques, disponibilités) ne sont pas paginées.

## Limites et validation des entrées

- **Corps JSON** : 512 Ko au plus (`app.limits.max-body-bytes`), sinon `413`. Le plus long champ légitime (le fond d'un personnage, 80 000 caractères) y tient.
- **Images** : 5 Mo au plus (`413` au-delà). Seuls le **PNG** et le **JPEG** sont acceptés, reconnus par le **contenu** du fichier (signature) et non par le type annoncé par le client ; un autre contenu, un fichier vide ou tronqué répond `400`. L'extension du fichier stocké suit le format réel (`.png` ou `.jpg`).
- **Textes obligatoires** (noms, descriptions, contenus) : non vides une fois les espaces retirés. **Textes facultatifs d'une modification** (`PATCH`) : s'ils sont présents, non vides ; **absents, ils ne changent pas**.
- **Plannings et disponibilités** : de 1 à 100 dates. **Dates** (plannings, disponibilités, notes) : entre les années 1900 et 2200, sinon `400` (le format accepte des années que PostgreSQL ne sait pas stocker, ce qui finissait en `500`). **Signaux WebRTC** : type `offer`, `answer` ou `candidate`, contenu de 20 000 caractères au plus.
- **Quantités** : voir « Abonnements et paiement ». Au-delà d'un quota d'offre, la création répond `409` (`application/problem+json`, type `urn:dndgate:plan-limit-reached`). Les plafonds techniques sont les mêmes pour tous et se règlent dans `application-dev.properties` / `application-prod.properties` :
  - **1 000 sorts** par grimoire (`limits.spells-per-spell-book`), import compris ; ajouter un sort déjà présent n'est pas une erreur ;
  - **30 messages par compte et par minute** (`limits.messages.max`, `limits.messages.window`) : au-delà, `429` avec `Retry-After` (secondes). Les messages sont comptés en base sur la dernière minute (index `message(sender_id, send_date)`).
  Les comptages ne sont pas verrouillés : deux créations simultanées peuvent dépasser un quota d'une unité ou deux.
- **WebSocket** : un client n'envoie que `CONNECT`, `SUBSCRIBE` (et `UNSUBSCRIBE`) et des battements de cœur ; toute publication est refusée. Une trame de plus de 16 Ko coupe la connexion avant d'être lue. Seules les destinations exactes `/topic/adventure/chat/{id}` et `/topic/adventure/webrtc/{id}` (UUID en minuscules, rien avant ni après) sont acceptées au `SUBSCRIBE`. Une connexion ouvre au plus **20 abonnements** (`limits.websocket.subscriptions-per-connection`) ; un identifiant d'abonnement déjà utilisé sur la connexion est refusé, comme un second `CONNECT`. Limite connue : Spring ne garantit pas l'ordre de traitement des trames reçues d'une même connexion (pas d'option `preserveReceiveOrder` sur le canal entrant) ; un `UNSUBSCRIBE` suivi aussitôt d'un `SUBSCRIBE` avec le même identifiant peut donc être refusé si le second passe avant le premier. Le client doit attendre ou changer d'identifiant.
- Toutes les routes qui reçoivent un corps JSON sont validées (`@Valid`).
- **Modifications simultanées** : les comptes, aventures, grimoires, personnages, notes, notes d'historique, plannings et votes de disponibilité portent une version (pas les évaluations ni les messages). Une modification de l'une de ces lignes faite à partir d'une lecture devenue périmée (quelqu'un a modifié la même ligne entre-temps) répond `409` (`application/problem+json`) au lieu d'effacer l'autre modification : le client relit et renvoie. Sont rejoués automatiquement, sans `409`, l'ajout et le retrait d'un sort, le vote de disponibilité, la création simultanée d'un planning par `PUT /planning` et celle d'une évaluation par `PUT /ratings` : deux sorts ajoutés en même temps sont tous les deux gardés. Changer le mot de passe et terminer les sessions se font dans la même transaction : l'un ne va pas sans l'autre. Le mot de passe demandé pour modifier ou supprimer le compte est vérifié avant cette transaction, et un échec est toujours compté pour le verrouillage.
- **Erreurs** : chaque réponse d'erreur (`application/problem+json`) porte un `correlationId`, le même que l'en-tête `X-Request-Id` et que sur chaque ligne de log de la requête ; c'est l'identifiant à donner pour signaler un problème.

## Images des personnages

Le bucket MinIO est **privé** : au démarrage, l'API crée le bucket s'il manque et **retire toute politique publique** laissée par une ancienne version.
Une image se lit par `GET /game-characters/{id}/picture` (authentification comme le reste de l'API) : le propriétaire du personnage et les participants de l'aventure (s'il en a une), personne d'autre.
Le personnage renvoie `pictureUrl` (cette route) et non plus `picturePath` (le chemin interne). Réponse : `Content-Type` du format réel, `Cache-Control: private, max-age=3600`, `Content-Disposition: inline`.
Pour l'afficher, le front récupère l'image avec le jeton (`HttpClient`, `responseType: 'blob'`) puis l'affiche (`URL.createObjectURL`) : une balise `<img src>` seule n'envoie pas l'en-tête d'authentification.

## Abonnements et paiement

Le site propose quatre offres. Le paiement passe par **Stripe** (pages hébergées Checkout, portail client) : l'API ne manipule aucune carte. Le catalogue est le fichier `apps/api/src/main/resources/plans/plans.json` (montants en centimes, EUR) ; il ne contient aucun texte affiché.

### Les offres

| | Aventurier | Héros | Maître du Donjon | Légendaire |
|---|---|---|---|---|
| Identifiant | `adventurer` | `hero` | `dungeon-master` (mis en avant) | `legendary` |
| Mensuel / annuel | gratuit | 0,99 € / 9,90 € | 4,99 € / 49,90 € | 9,99 € / 99,90 € |
| Aventures actives en MJ (`activeAdventuresAsMaster`) | 1 | 2 | 10 | 25 |
| Personnages hors aventure (`charactersWithoutAdventure`) | 3 | 15 | 50 | 75 |
| Grimoires (`spellBooks`) | 2 | 10 | 50 | 100 |
| Objets magiques (`magicItems`) | 10 | 50 | 200 | 500 |
| Notes MJ et notes d'historique par aventure (chacune) | 50 | 100 | 500 | 500 |
| Personnages par joueur et par aventure | 1 | 1 | 5 | 10 |
| PNJ par aventure (`npcsPerAdventure`) | 5 | 10 | 50 | 100 |
| Messages conservés par aventure (`messagesPerAdventure`) | 200 | 200 | 1 000 | 2 000 |
| Images de personnage (`characterPictures`) | non | oui | oui | oui |
| Visio (`video`) | non | non | oui | oui |
| Table équipée (`equippedTable`) | non | non | oui | oui |
| Accès anticipé, support prioritaire (affichage seulement) | non | non | non | oui |
| Badge | aucun | aucun | `dungeon-master` | `legend` |

Rejoindre des aventures en joueur est illimité pour tous.

- **Au démarrage**, l'API refuse de démarrer si le catalogue est invalide : clé inconnue ou manquante, identifiant en double, `defaultPlan` inconnu ou avec des prix, limite inférieure à 1, offre payante sans prix. L'ordre du tableau `plans` est l'ordre d'affichage et « inférieur → supérieur ».
- **`PLANS_FILE`** (facultatif) : chemin d'un catalogue externe (`file:/chemin/plans.json`) qui remplace celui du jar, sans rebuild (une valeur vide n'est pas acceptée : laisser la variable absente, ou `classpath:plans/plans.json`, valeur par défaut de `docker-compose.prod.yml`). Changer un montant dans ce fichier ne change pas le prix Stripe (voir plus bas).
- **Quelle offre s'applique** : ce qui touche au compte (aventures actives en MJ, personnages hors aventure, grimoires, objets magiques) dépend de l'offre du **compte qui agit** ; ce qui touche à une aventure (notes, personnages par joueur, PNJ, messages conservés, visio) dépend de l'offre du **MJ** de l'aventure. Une aventure sans MJ (`masterVacant`) suit l'offre par défaut (`adventurer`). Après un transfert de MJ, l'aventure suit l'offre du nouveau MJ (le transfert ne vérifie pas son quota).
- **Images de personnage** : hors aventure, l'offre du compte ; dans une aventure, l'offre du compte **ou** celle du MJ si elle a `equippedTable`. L'image est facultative à la création (`picturePresentation` peut manquer) ; en envoyer une sans l'outil répond `403`.
- **Quand l'offre baisse** (résiliation, impayé, offre inférieure) : **rien n'est supprimé ni masqué**. Ce qui dépasse le nouveau quota reste lisible et modifiable ; seule la création est refusée. Un outil perdu ne sert plus pour la suite (visio, nouvelle image) mais les images déjà envoyées restent affichées. Seule exception : l'historique des messages (plus bas).
- **Refus** : `409` `urn:dndgate:plan-limit-reached` pour un quota (avec `limit`, `max`, `planId`, `upgradePlanId`) ; `403` `urn:dndgate:feature-not-included` (avec `feature`, `planId`, `upgradePlanId`) pour un outil absent de l'offre. `upgradePlanId` est l'offre la plus basse qui lèverait le refus (`null` si aucune). `decidedBy` dit quelle offre a décidé : `account` (celle de l'appelant : quotas du compte, outils du compte ou partageables) ou `master` (celle du MJ de l'aventure : quotas de l'aventure, outils du MJ) ; `planId` est alors l'offre du MJ.
- **Ce que voit le client** : `GET /plans` (public, le catalogue sans les clés Stripe), `GET /accounts/me/subscription` (offre effective, `interval`, `status`, `currentPeriodEnd`, `cancelAtPeriodEnd`, `billingEnabled` et `usage` de chaque limite de compte en `used` / `max`) et `GET /adventures/{id}/entitlements` (outils disponibles pour l'appelant dans cette aventure, et usage des limites de l'aventure ; l'offre du MJ n'est jamais nommée).
- **Badges** : les réponses qui montrent un compte portent `founder` (booléen) et `planBadge` (`dungeon-master`, `legend` ou rien, calculé à partir de l'offre effective). Le badge fondateur est gardé à vie, indépendant des offres, remis à `false` par l'anonymisation, et attribué à la main :

```sql
UPDATE accounts SET founder = true WHERE lower(pseudo) = lower('<pseudo>');
```

### Personnages : réserve, aventure, PNJ

- La **réserve** d'un compte, ce sont ses personnages hors aventure (quota `charactersWithoutAdventure`). `GET /game-characters/mine` liste les personnages du compte.
- **Un seul personnage actif** par joueur et par aventure (`active` dans la réponse). Le premier personnage d'un joueur dans l'aventure devient actif ; si l'actif sort ou est supprimé, aucun autre ne le devient. `PUT /adventures/{id}/players/me/active-character` `{ "characterId": "..." }` change de personnage actif.
- `PUT /game-characters/{id}/adventure` `{ "adventureId": "..." }` envoie un personnage de la réserve dans une aventure (quota « personnages par joueur » de l'offre du MJ ; le MJ n'a pas de personnage joueur). `DELETE /game-characters/{id}/adventure` le remet dans la réserve (sans confirmation : c'est réversible) ; `409` si la réserve est pleine.
- **Quitter une aventure** : `DELETE /adventures/{id}/players/me` avec un corps facultatif `{ "deletions": [{ "characterId": "...", "confirmation": "<nom du personnage>" }] }` (10 au plus). Les personnages du joueur passent dans sa réserve ; ceux qui n'y tiennent pas doivent être listés dans `deletions` avec leur nom retapé, sinon `409` et rien ne change (tout ou rien).
- **Suppression d'une aventure par le MJ** : les personnages des joueurs passent dans la réserve de leur propriétaire **même si elle est pleine** (la réserve peut alors dépasser son quota, seule la création reste bloquée) ; les PNJ sont supprimés avec l'aventure.
- **PNJ** : une fiche créée par le MJ dans son aventure (`POST /game-characters` avec son `adventureId`) est un PNJ (`npc: true`), **privé** par défaut (seul le MJ le lit, filtre appliqué dans la requête en base) ou **public** (toute la table le lit sans le modifier). `PUT /game-characters/{id}/visibility` `{ "visibility": "PUBLIC" | "PRIVATE" }` bascule d'un clic ; `409` sur un personnage qui n'est pas un PNJ. Un PNJ appartient à l'aventure : après un transfert de MJ, le nouveau MJ le gère. Un PNJ ne passe pas dans une réserve.

### Historique des messages

Une aventure garde au plus `messagesPerAdventure` messages, selon l'offre du MJ. Au-delà, chaque nouveau message **supprime définitivement les plus anciens**, dans la même transaction que l'envoi : c'est la seule suppression automatique du site. Si le MJ baisse d'offre, les messages en trop sont supprimés au premier envoi suivant. L'usage (`used` / `max`) est dans `GET /adventures/{id}/entitlements`. Le débit de 30 messages par minute s'ajoute.

### Configurer Stripe

Variables d'environnement : `STRIPE_SECRET_KEY` (`sk_test_…` / `sk_live_…`) et `STRIPE_WEBHOOK_SECRET` (`whsec_…`) ; `WEB_BASE_URL` sert aux URL de retour de Checkout et du portail. Sans clé secrète (dev par défaut), l'API démarre, tout le monde est sur l'offre gratuite et les routes de paiement répondent `503`. En production (`docker-compose.prod.yml`), les deux variables sont obligatoires et ne peuvent pas être vides.

1. **Produits** : un produit par offre payante (`hero`, `dungeon-master`, `legendary`) avec la métadonnée `plan_id` égale à l'identifiant du catalogue. C'est le produit qui dit l'offre d'un abonnement.
2. **Prix** : deux prix récurrents par produit (mensuel, annuel) avec les **lookup keys** du catalogue (`hero_monthly`, `hero_yearly`, `dungeon_master_monthly`, …). L'API vend par lookup key, car les identifiants de prix diffèrent entre test et live.
3. **Conditions générales** : Stripe refuse `consent_collection.terms_of_service` (utilisé par Checkout) tant qu'aucune URL de conditions n'est réglée dans les informations publiques du compte (Paramètres > Informations publiques) : y mettre `WEB_BASE_URL/terms`. Checkout affiche aussi que le client demande l'accès immédiat et renonce à son droit de rétractation de 14 jours (`billing.checkout.terms-acceptance-message`). Une session Checkout expire au bout de 31 minutes.
4. **Portail client** : autoriser le changement d'offre entre ces prix, la résiliation et la mise à jour de la carte.
5. **Webhook** : un endpoint `<api>/dndgate-api/billing/stripe-webhook` avec les quatre événements `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated` et `customer.subscription.deleted`. Son secret de signature est `STRIPE_WEBHOOK_SECRET`.
6. **Test en local** : `stripe listen --forward-to localhost:4000/dndgate-api/billing/stripe-webhook` affiche le `whsec_…` à mettre dans `STRIPE_WEBHOOK_SECRET`.

**Changer un prix** : un prix Stripe ne change jamais de montant. Créer un nouveau prix sur le même produit avec `transfer_lookup_key` (la lookup key passe du vieux prix au nouveau), puis mettre le montant à jour dans le catalogue. Les abonnés existants gardent leur ancien prix, et leur offre (reconnue par le produit).

### Routes de paiement et règles

- `POST /billing/checkout-session` `{ "planId": "hero", "interval": "monthly" | "yearly" }` renvoie `{ url }` ; `409` si le compte a déjà un abonnement non terminé chez Stripe (`active`, `trialing`, `past_due`, `incomplete` : il passe par le portail). `POST /billing/portal-session` renvoie `{ url }` ; `409` sans client Stripe. Les deux répondent `503` si le paiement est désactivé.
- `POST /billing/stripe-webhook` est public, mais la **signature est vérifiée sur le corps brut** : absente ou fausse, `400` et rien n'est fait. Les autres événements répondent `200` sans effet.
- **Offre effective selon le statut** : `active`, `trialing` et `past_due` (le temps des relances) donnent l'offre du produit ; `canceled`, `unpaid`, `incomplete`, `incomplete_expired`, `paused` ou aucun abonnement donnent l'offre par défaut. Une résiliation demandée au portail garde l'offre jusqu'à `currentPeriodEnd`. Un `plan_id` inconnu du catalogue retombe sur l'offre par défaut (log d'erreur).
- **Synchronisation** : à chaque événement d'abonnement, l'API **relit tous les abonnements du client chez Stripe** et retient le meilleur actif (offre la plus haute, puis fin de période la plus lointaine) : l'ordre et les doublons des événements n'ont pas d'importance. L'écriture est un compare-and-set sur la ligne lue ; après plusieurs conflits de suite, la réponse est `503` pour que Stripe réessaie. Un doublon actif est journalisé en erreur pour un remboursement à la main. Seul le webhook donne une offre, jamais le retour de Checkout.
- **Suppression de compte** : voir « Suppression de compte ».

## Changer de MJ

Un seul MJ par aventure, et lui seul a les droits de MJ (modifier, changer le statut, supprimer, inviter, lire ses notes privées). Le MJ peut changer de trois façons, tant que l'aventure n'est ni terminée ni abandonnée.

| Cas | Route | Qui |
|---|---|---|
| **Quitter le rôle de MJ** | `POST /adventures/{id}/master/step-down` `{ "confirmation": "<nom de l'aventure>" }` | le MJ (retaper le nom : difficile à défaire) |
| **Inviter un MJ** : code spécial, à usage unique, valable 7 jours (`game-master.invitation.ttl`), un seul en attente (le nouveau remplace l'ancien) ; seul son hash est stocké | `POST /adventures/{id}/master/invitation` (renvoie `{code, expiresAt}`), `DELETE` pour le révoquer | le MJ |
| Accepter une invitation | `POST /adventures/master/accept` `{ "code": "..." }` | tout compte connecté (même hors de l'aventure) |
| **Récupérer le rôle de MJ** | `POST /adventures/{id}/master/claim` | un **joueur**, seulement si l'aventure n'a plus de MJ (`masterVacant`) |
| Voter | `PUT /adventures/{id}/master/claim/vote` `{ "approve": true }` | les **autres joueurs** |
| Voir le vote en cours / l'abandonner | `GET` / `DELETE /adventures/{id}/master/claim` | les participants / le candidat |

- **L'aventure n'a plus de MJ** (`masterVacant: true`) quand le MJ a quitté le rôle ou **supprimé son compte** (aventure en cours). Personne n'a alors les droits de MJ, pas même l'ancien. Une aventure qui n'a pas commencé dont le MJ supprime le compte est toujours abandonnée.
- **Vote** : **tous** les autres joueurs doivent approuver ; **un refus** met fin au vote ; une réponse peut être changée tant que le vote est en cours ; un vote sans réponse **expire au bout de 7 jours** (`game-master.claim.ttl`) et rien ne change. Les joueurs dont le compte est supprimé ne votent pas ; si plus personne n'a à voter, le candidat prend le rôle aussitôt. Un seul vote à la fois par aventure ; le vote est annulé si le candidat quitte l'aventure ou si son compte est supprimé (la suppression annule ses votes ouverts et prévient les votants ; un vote resté ouvert pour un compte anonymisé est annulé au premier vote au lieu de donner le rôle).
- **Au changement** : le nouveau MJ n'est plus joueur ; l'**ancien MJ devient joueur** s'il y a une place et que son compte existe, sinon il quitte l'aventure (ses personnages restent). Les **notes privées du MJ passent au nouveau MJ** (elles n'ont pas d'auteur : elles appartiennent au rôle). L'invitation en attente est annulée.
- **E-mails** (jamais envoyés à un compte supprimé) :

| Événement | Qui est prévenu |
|---|---|
| Le MJ quitte le rôle | les joueurs (de la part de qui) |
| Le MJ supprime son compte (aventure en cours) | les joueurs (sans dire qui) |
| Un joueur demande le rôle | les joueurs qui votent |
| Un joueur refuse | le candidat |
| Le candidat abandonne | les joueurs qui votaient |
| Le rôle change de mains (vote ou invitation) | le nouveau MJ, l'ancien MJ et les autres joueurs, chacun avec son message |

  Les e-mails partent **après la validation** de la transaction qui change le rôle (rien ne part pour un changement annulé ou rejoué après un conflit) ; ils contiennent un lien vers `WEB_BASE_URL/adventures/{id}`. Aucun e-mail pour une requête refusée.
- Tout se fait en une transaction, rejouée en cas de conflit : deux personnes qui utilisent le même code, ou qui votent en même temps, ne produisent jamais deux MJ ni un vote bloqué.

## Sécurité

### Authentification

- `POST /accounts/login` renvoie un **jeton d'accès** (JWT, 15 min) et un **jeton de rafraîchissement** (opaque, 60 jours).
  Le jeton d'accès s'envoie dans `Authorization: Bearer <jeton>`.
- `POST /accounts/refresh` `{ "refreshToken": "..." }` échange le jeton de rafraîchissement contre une nouvelle paire.
  Chaque jeton de rafraîchissement **n'est utilisable qu'une fois** (rotation). Présenter un jeton déjà utilisé signale un vol
  et **révoque toute la session**.
- Chaque connexion crée une **session** ; le jeton d'accès porte son identifiant (`sid`) et n'est accepté que tant que la session
  est active, vérifié à chaque requête. La révocation est donc immédiate.
- Une session se termine par : `POST /accounts/logout` (session courante), `POST /accounts/logout-all` (toutes les sessions),
  un changement de mot de passe, la suppression du compte, l'expiration (180 jours maximum, quel que soit le nombre de rafraîchissements)
  ou la détection d'un jeton rejoué.
- Les jetons de rafraîchissement sont stockés hachés (SHA-256) ; les mots de passe avec BCrypt.
- Les sessions expirées sont purgées chaque nuit (`security.session.cleanup-cron`).

### Mots de passe

- 8 caractères au minimum, 72 **octets** au maximum (limite de BCrypt : au-delà, la fin du mot de passe serait ignorée sans le dire).
  Il n'y a pas de règle de composition : la longueur compte plus que les symboles.
- Le mot de passe n'est jamais modifié (pas de `trim()`) : les espaces en début et en fin en font partie.
  Le pseudo, lui, est rogné de la même façon à l'inscription et à la connexion.
- Les anciens comptes ne sont pas concernés par la règle de longueur, qui s'applique à l'inscription et au changement de mot de passe.

### Statut d'une aventure

- Le statut ne change que par `PUT /adventures/{id}/status` (le `PATCH /adventures/{id}` n'a plus de champ `status` : s'il est envoyé, il est ignoré).
- Passages permis : `NOT_STARTED` vers `ONGOING`, `ONGOING` vers `FINISH`, `NOT_STARTED` ou `ONGOING` vers `ABORT`. Redemander le statut courant est accepté sans effet. Tout le reste répond `409` en nommant le statut courant et le statut demandé : rien ne sort de `FINISH` / `ABORT`, rien ne revient à `NOT_STARTED`, on ne termine pas une aventure qui n'a pas démarré.
- Le démarrage automatique quand la dernière place est prise (`NOT_STARTED` vers `ONGOING`) et l'abandon des aventures non commencées à l'anonymisation du MJ suivent la même machine.
- `maxPlayer` ne peut pas descendre sous le nombre de joueurs déjà inscrits : `PATCH` en dessous répond `409`.

### Rejoindre une aventure

- Par identifiant : réservé aux aventures **publiques**. Une aventure privée est invisible pour un étranger (404) ; on la rejoint avec son code d'invitation.
- Dans les deux cas : l'aventure doit être `NOT_STARTED` et avoir encore de la place (409 sinon) ; on ne la rejoint pas deux fois, et le propriétaire en est déjà participant.
- Quand la dernière place est prise, l'aventure démarre (`ONGOING`) et devient privée.
- Les codes d'invitation et de vérification sont générés avec `SecureRandom` (une règle ArchUnit interdit `java.util.Random`).

### Inviter un joueur par e-mail

`POST /adventures/{id}/invitations/{pseudo}` (le MJ seulement) envoie le code d'invitation par e-mail. Il est refusé :

- `409` si l'aventure n'est plus `NOT_STARTED` (démarrée, terminée ou abandonnée) ou si le destinataire y participe déjà (joueur ou MJ) ;
- `429` avec `Retry-After` (secondes) et un `application/problem+json` si une limite est atteinte :
  - **par expéditeur** : 20 invitations par heure (`invitation.rate-limit.sender.max`, `invitation.rate-limit.sender.window`) ;
  - **par destinataire** : une invitation par aventure et par 24 h (`invitation.rate-limit.recipient.window`).

Contrairement aux limites par adresse IP, celles-ci sont comptées en base (table `sent_invitations`) : elles survivent à un redémarrage et valent pour toutes les instances.
Une invitation refusée n'est pas comptée. Les lignes plus anciennes que la plus longue fenêtre sont purgées chaque nuit (même cron que les sessions, `security.session.cleanup-cron`).

### Suppression de compte

`DELETE /accounts` (avec le mot de passe, voir plus haut) **anonymise** le compte au lieu de le supprimer :

- Ce qui identifie la personne disparaît : pseudo, e-mail, mot de passe, sessions, spellbooks, objets magiques, et les personnages hors aventure (avec leur image) : personne d'autre ne les lit. Le compte ne peut plus se connecter.
- Ce sur quoi les autres joueurs s'appuient reste : personnages d'une aventure, messages, votes et évaluations pointent vers le même identifiant, devenu anonyme
  (`anonymized: true` dans la réponse publique, pseudo `deleted-…`).
- Les aventures du compte qui n'ont pas démarré sont annulées et masquées ; celles qui ont démarré restent consultables par leurs joueurs.
  Sa place est libérée dans les aventures qui n'ont pas démarré, conservée dans les autres.
- L'ancien pseudo et l'ancien e-mail peuvent être réutilisés par une nouvelle inscription.
- **Abonnement** : avant l'anonymisation, l'API **fait expirer les pages Checkout encore ouvertes** du client (une page ouverte juste avant la suppression ne peut plus être payée), puis **résilie immédiatement** les abonnements Stripe du compte (sans remboursement au prorata) et efface la ligne d'abonnement ; le client Stripe et ses factures restent chez Stripe. Si l'appel à Stripe échoue, la suppression répond `503` et rien n'est anonymisé. Fenêtre d'échec partiel acceptée : Stripe résilie, puis la transaction d'anonymisation échoue ; le compte reste, son abonnement est terminé, et **relancer la suppression est sans danger**.

### Vérification de l'adresse e-mail

Après l'inscription, un e-mail contient un lien vers **le site web** (`WEB_BASE_URL/verify-email?email=…&code=…`, avec l'adresse et le code encodés).
La page appelle l'API :

- `POST /accounts/verify` `{ "email": "...", "code": "..." }` : **crée le compte, déjà activé**, à partir de l'inscription en attente (pseudo et mot de passe haché de l'inscription), supprime l'inscription et répond `204`. Tout est fait dans une seule transaction (`TransactionalAccountVerifier`).
  Un code est **à usage unique**, valable **24 heures** (`security.verification.ttl`), de 24 caractères aléatoires, comparé en temps constant (SHA-256 du code reçu contre celui de la base).
  Un code faux, expiré ou déjà utilisé, ou une adresse inconnue, donnent **la même réponse `400`** : on ne peut pas savoir quelles adresses sont inscrites. Deviner un code ne détruit pas l'inscription du propriétaire.
  - Deux confirmations simultanées du même lien : une seule crée le compte (`204`), l'autre voit un code invalide (`400`) : utiliser l'inscription, c'est la supprimer, et une seule requête y parvient.
  - Pseudo pris entre-temps (par un compte créé depuis, y compris par une confirmation simultanée) : `409` « pseudo déjà utilisé », l'inscription est supprimée (la personne se réinscrit avec un autre pseudo).
  - Adresse devenue celle d'un compte entre-temps (changement d'adresse confirmé) : même réponse qu'un code invalide (`400`), l'inscription est supprimée.
  - Aucune erreur `500` dans ces courses : les violations des index uniques de `accounts` (adresse, pseudo) sont traduites.
- `POST /accounts/verification-email` `{ "email": "..." }` : pour une inscription en attente, génère un **nouveau code** (l'ancien cesse de fonctionner), repart pour 24 h et renvoie le mail. Répond **toujours `202`** avec le même corps, que l'adresse soit en attente, déjà inscrite ou inconnue (dans ces deux derniers cas rien n'est envoyé). Au plus un mail par minute et par adresse (`security.verification.resend-cooldown`), en silence au-delà.
- `POST /accounts` **ne crée plus de compte** : il enregistre une **inscription en attente** (table `pending_signups`, une par adresse, adresse en minuscules) et envoie le lien de confirmation. Il répond **toujours `202`** avec le même corps fixe `{"message": "One last step before your account is validated: check your e-mail address"}`, que l'adresse soit déjà inscrite ou non, jamais de donnée de compte : l'inscription ne révèle pas les adresses connues.
  - Le pseudo est examiné en premier, **contre les comptes seulement** : un pseudo pris par un compte répond `409` quelle que soit l'adresse (les pseudos sont publics). Le pseudo d'une inscription en attente n'est **pas réservé** : sinon on pourrait sonder une adresse en deux requêtes (un pseudo neuf avec l'adresse visée, puis un sondage de ce pseudo). Le pseudo sera revérifié à la confirmation.
  - Le mot de passe est haché avant que l'adresse soit regardée (les deux branches coûtent pareil).
  - Adresse déjà inscrite : aucune inscription, et son propriétaire reçoit un mail l'informant de la tentative (sans code), **au plus un par adresse et par 24 h** (`security.signup-notice.window`, compté en base sous forme d'empreinte de l'adresse).
  - Adresse libre : l'inscription en attente est créée, ou **remplacée** (nouveau pseudo, mot de passe, code, l'ancien lien cesse de fonctionner) si l'ancienne a plus que `security.verification.resend-cooldown` (1 minute) ; sinon rien ne change et aucun mail ne part, en silence. L'écriture est une seule requête SQL atomique : deux inscriptions simultanées pour la même adresse laissent une seule ligne et un seul mail, sans erreur.
  - Le code (24 caractères aléatoires, valable `security.verification.ttl`, 24 h) n'est **jamais stocké en clair** : la base garde son SHA-256 (hexadécimal) ; seul le mail contient le code.
  - Les inscriptions expirées sont purgées chaque nuit (même tâche planifiée que les sessions).
- Une validation n'est plus un `GET` : suivre un lien (analyseurs de courrier, aperçus) ne modifie rien.
- Si le mail ne peut pas être envoyé, l'erreur est journalisée et **la réponse reste la même** ; la personne peut se réinscrire après le délai d'une minute.
- Il n'existe pas de compte « créé mais non activé » : un compte existe toujours activé (ou anonymisé). `accounts` n'a pas de colonne de code de vérification (le changement d'adresse a ses propres colonnes `pending_email_*`).
- **Front** : la page `/verify-email` appelle `POST /accounts/verify` ; après `POST /accounts`, afficher le message « vérifiez votre adresse e-mail » (le compte n'existe pas encore, la connexion répond `401` jusqu'à la confirmation) ; la page peut proposer `POST /accounts/verification-email` si le mail n'arrive pas. Un `409` sur `/verify` signifie « pseudo pris entre-temps : se réinscrire avec un autre ».

### Pseudo et contenu des e-mails

- Un pseudo est affiché aux autres joueurs et écrit dans les e-mails : 3 à 30 caractères, **lettres (toutes langues), chiffres, `_`, `-` et `.`**, sans espace ni balise.
- Dans les e-mails, tout ce qui vient d'une personne est **échappé** (HTML) ou **encodé** (liens) ; le sujet ne peut pas contenir de saut de ligne.

### Actions sensibles et suppressions irréversibles

Une suppression irréversible demande toujours une confirmation, **vérifiée par l'API** : un `DELETE` sans elle ne supprime rien.
Elle se passe dans le corps JSON de la requête :

| Suppression | Corps de la requête |
|---|---|
| `DELETE /accounts` | `{ "confirmation": "<mot de passe du compte>" }` |
| `DELETE /adventures/{id}` | `{ "confirmation": "<nom de l'aventure>" }` |
| `DELETE /game-characters/{id}` | `{ "confirmation": "<nom du personnage>" }` |
| `DELETE /spellbooks/{id}` | `{ "confirmation": "<nom du spellbook>" }` |
| `DELETE /magic-items/{id}` | `{ "confirmation": "<nom de l'objet magique>" }` |
| `DELETE /adventure-notes/{id}`, `DELETE /historical-notes/{id}` | `{ "confirm": true }` (une note n'a pas de nom) |

- Le nom est comparé tel qu'il est saisi : même casse, mêmes lettres. Les espaces autour et la façon d'encoder un accent sont sans effet.
- Réponses : `400` si la confirmation manque ou ne correspond pas, `403` si le mot de passe est faux ou si l'on n'est pas propriétaire,
  `429` si le compte est verrouillé. Un mot de passe faux donne 403 et pas 401, pour que la page ne déconnecte pas l'utilisateur.
- **Un mauvais mot de passe saisi pour confirmer compte comme un échec de connexion** (même verrouillage) : un jeton d'accès volé ne permet pas de deviner le mot de passe.
- Supprimer une aventure supprime avec elle ses personnages (et leurs images), notes, notes historiques, messages, planning et votes.
  Une aventure en cours (`ONGOING`) ne peut pas être supprimée (409) : il faut la terminer ou l'abandonner d'abord.

`PATCH /accounts` change l'e-mail, le pseudo et/ou le mot de passe. Le mot de passe **actuel** est obligatoire :

```json
{ "currentPassword": "...", "email": "...", "pseudo": "...", "newPassword": "..." }
```

Seuls les champs fournis changent. Changer le mot de passe termine toutes les sessions.

**Changer d'adresse e-mail est une demande, pas un ordre.** Le compte garde son ancienne adresse (connexion, notifications) tant que la nouvelle n'est pas confirmée :

- `PATCH /accounts` avec un `email` différent enregistre l'adresse en attente (`pendingEmail` dans la réponse, `email` reste l'ancienne) et envoie un mail **à la nouvelle adresse**,
  avec un lien vers le site (`WEB_BASE_URL/confirm-email?email=…&code=…`, adresse et code encodés). Une adresse déjà utilisée est refusée tout de suite (`409`).
  Les autres champs de la même requête (pseudo, mot de passe) s'appliquent immédiatement.
- `POST /accounts/email/confirm` `{ "email": "<nouvelle adresse>", "code": "..." }` : `204`, l'adresse du compte devient la nouvelle. **Sans jeton** : le lien peut être ouvert sur un autre appareil.
  Le code est **propre à ce changement** (le code d'inscription ne peut pas le confirmer), à usage unique, valable 24 heures (`security.verification.ttl`), comparé en temps constant.
  Un code faux, expiré, déjà utilisé ou une adresse que personne n'attend donnent **la même réponse `400`**. Si l'adresse a été prise entre-temps : `409`, l'ancienne adresse reste.
- Redemander un changement remplace la demande précédente (l'ancien lien cesse de fonctionner). Rien ne réserve l'adresse en attente : deux comptes peuvent l'attendre, le code décide.
- Supprimer le compte (anonymisation) efface la demande en attente.
- **À faire côté front** : la page `/confirm-email` qui lit `email` et `code` dans l'adresse et appelle `POST /accounts/email/confirm`, et l'affichage de `pendingEmail` (« en attente de confirmation »).

### Protection contre la force brute

- **Verrouillage du compte, par couple (compte, adresse IP du client)** : après 5 échecs consécutifs depuis la même adresse (`security.login.max-failed-attempts`),
  le compte est verrouillé 15 min **pour cette adresse** (`security.login.lock-duration`) et répond `429` avec `Retry-After`, même avec le bon mot de passe.
  Les autres adresses ne sont pas touchées et peuvent se connecter avec le bon mot de passe. Un succès remet à zéro le compteur de cette adresse.
  Le compteur est incrémenté de façon atomique en base, mais la vérification du verrou puis l'enregistrement de l'échec ne le sont pas :
  une rafale de requêtes simultanées peut obtenir quelques essais de plus que 5 avant que le verrou ne s'applique (borné par la limite par IP).
  Les trois points d'entrée qui vérifient un mot de passe (connexion, `DELETE /accounts`, `PATCH /accounts`) partagent le même compteur, avec la même règle.
  - **Pourquoi** : les pseudos sont publics, donc un verrou partagé par toutes les adresses permettait à n'importe qui d'empêcher le titulaire de se connecter,
    en recommençant toutes les 15 minutes (point 27 de l'audit v2).
  - **La limite du compromis** : un attaquant qui dispose de nombreuses adresses n'est plus bloqué par le verrou (5 essais par adresse et par 15 min), il est seulement
    ralenti par la limitation par adresse IP ci-dessous. Une défense contre la force brute distribuée repose sur la robustesse du mot de passe, pas sur ce verrou.
  - **Alerte, sans blocage** : quand le nombre d'adresses différentes ayant échoué contre le même compte dans la fenêtre de verrouillage atteint 50
    (`security.login.distinct-clients-warning-threshold`), l'API écrit un `WARN`, une fois au franchissement du seuil (identifiant du compte et nombre d'adresses, ni adresse ni mot de passe).
  - **Quelle adresse** : l'adresse de la connexion (`getRemoteAddr`), jamais un en-tête lu directement. Derrière le reverse proxy, `server.forward-headers-strategy=native`
    la remplace par celle du client, seulement si l'appel vient d'un proxy de confiance. Sans cela, toutes les requêtes auraient l'adresse du proxy et partageraient le même verrou.
    En production, le reverse proxy doit **ajouter à** ou **écraser** `X-Forwarded-For` (jamais le transmettre tel quel), et Tomcat fait confiance par défaut à tout pair situé dans une plage interne
    (loopback, 10.x, 172.16-31.x, 192.168.x) : `docker-compose.prod` ne publie le port de l'API que sur 127.0.0.1, ce qui doit rester vrai.
  - **IPv6** : un client IPv6 possède en général un /64 entier et peut y changer d'adresse gratuitement. Une adresse IPv6 est donc comptée par son préfixe /64
    (verrou et limitation par IP), et une adresse IPv4-mappée (`::ffff:a.b.c.d`) est traitée comme l'IPv4. Un attaquant qui change de /64 (ou d'adresse IPv4) échappe quand même au verrou.
  - **Donnée personnelle** : une adresse IP en est une. Elle n'est stockée que sous forme d'un SHA-256 non salé (pseudonymisation, pas anonymisation : l'espace IPv4 se parcourt en entier)
    dans la table `login_failures`, avec le nombre d'échecs et la fin du verrou. Elle est gardée au plus la durée de la fenêtre de verrouillage :
    la purge nocturne supprime les lignes dont le dernier échec est plus ancien, et la suppression du compte efface celles du compte.
- **Limitation par adresse IP** (`429` + `Retry-After`), en mémoire donc par instance de l'API :

| Endpoint | Limite par défaut |
|---|---|
| `POST /accounts/login` | 10 par minute |
| `POST /accounts/refresh` | 30 par minute |
| `POST /accounts` (envoie un e-mail) | 5 par heure |
| `POST /accounts/verify` | 20 par minute |
| `POST /accounts/email/confirm` | 20 par minute |
| `POST /accounts/verification-email` | 5 par heure |

  Derrière un reverse proxy, `server.forward-headers-strategy=native` (activé en prod) fait lire l'adresse du client dans `X-Forwarded-For` ;
  le proxy doit écraser cet en-tête et ne pas le relayer tel quel.
- Un identifiant inconnu et un mauvais mot de passe donnent la même réponse, avec le même temps de calcul.

| Variable | Rôle |
|---|---|
| `JWT_SECRET` | Clé de signature des jetons, 32 octets minimum. **Obligatoire en production**, une valeur de développement existe dans `application-dev.properties` uniquement. |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées (HTTP et WebSocket), séparées par des virgules. |
| `WEB_BASE_URL` | Adresse du site web (par défaut `http://localhost:4200` en dev) : les liens des e-mails y mènent. |

Règles d'accès :

- Seuls `POST /accounts` (inscription), `POST /accounts/login` (connexion), `POST /accounts/refresh`, `POST /accounts/verify`, `POST /accounts/email/confirm` et `POST /accounts/verification-email` sont publics.
- Une aventure privée n'est visible que par son propriétaire et ses joueurs. Seul le propriétaire peut la supprimer.
- **Règle « participant »** (propriétaire ou joueur d'une aventure) : messages, signalisation WebRTC, abonnements WebSocket, notes historiques, notes du MJ et personnages ne sont accessibles qu'aux participants. Créer un personnage dans une aventure exige aussi d'être participant.
- Les notes du MJ de visibilité `PRIVATE` ne sont lisibles que par le propriétaire de l'aventure.
- Un personnage est lisible par son propriétaire et par les participants de son aventure ; on ne peut lister que ses propres personnages.
- **Personnage hors aventure** : `adventureId` est facultatif à la création (`POST /game-characters`). Sans lui, le personnage n'appartient à aucune aventure de l'application (un personnage joué à une table « réelle », simplement numérisé) : seul son propriétaire le lit, ainsi que son image (`403` pour les autres). `GameCharacterDto.adventureId` vaut alors `null`.
- Les spellbooks sont personnels : seul leur propriétaire peut les lire, les exporter ou les modifier.
- **Objets magiques** (`/magic-items`) : personnels comme les spellbooks (`403` pour un autre compte). Nom, type (`ARMOR`, `POTION`, `RING`, `ROD`, `SCROLL`, `STAFF`, `WAND`, `WEAPON`, `WONDROUS_ITEM`), rareté (`COMMON` à `ARTIFACT`), harmonisation requise, description. `GET /magic-items/mine` les liste triés par nom ; un `PATCH` ne change que les champs donnés.
- **Jeu de règles d'un grimoire** : un grimoire appartient à un jeu de règles (`ruleset`, par exemple `dnd5e-2014` ou `dnd5e-2024`), choisi à la création, obligatoire. L'API ne connaît pas le contenu des règles : elle vérifie seulement le format de l'identifiant (minuscules, chiffres et tirets, 50 caractères au plus, `^[a-z0-9]+(-[a-z0-9]+)*$`), sinon `400`. Un `PATCH` ne le change jamais. Le fichier d'import peut le porter ; absent, il vaut `dnd5e-2014` (comme les grimoires existants avant l'ajout du champ). L'export l'écrit toujours.
- **Évaluations (ratings)** : on ne peut noter un compte que si l'on a joué une aventure avec lui, c'est-à-dire une aventure démarrée (`ONGOING` ou `FINISH`) dont les deux comptes sont participants. On ne peut pas se noter soi-même, ni noter deux fois le même compte (`POST` répond 409, `PUT` met à jour). La note est comprise entre 1 et 5. La moyenne d'un compte est publique ; le détail des notes reçues n'est lisible que par le compte noté.
- Planning : lisible tant que l'aventure est visible (publique, ou participant), pour que des candidats puissent voter avant de rejoindre ; créé ou modifié par le propriétaire seulement. Les votes de tous ne sont lisibles que par les participants, et chacun ne peut voter que pour soi.
- WebSocket : le client envoie le jeton dans l'en-tête STOMP `Authorization` au `CONNECT` ; il ne peut pas publier lui-même, seul le serveur diffuse.
- **Fin d'accès sur une connexion WebSocket ouverte** : la règle « participant » n'est pas jugée qu'au `SUBSCRIBE`. Dès que l'opération est validée en base (rien n'est coupé pour une opération annulée) :
  - un joueur qui **quitte l'aventure**, un **ancien MJ qui n'a pas de place** de joueur après un changement de MJ, et tous les abonnés d'une **aventure supprimée** perdent leurs abonnements à cette aventure (chat et WebRTC) ; la connexion reste ouverte pour leurs autres aventures ;
  - une **session révoquée** (`logout`, jeton de rafraîchissement rejoué) ferme les connexions ouvertes avec ses jetons ; `logout-all`, un **changement de mot de passe** et la **suppression du compte** ferment toutes les connexions du compte (code de fermeture 1008). Les autres sessions du même compte ne sont pas touchées par un simple `logout`.
  - En plus, le serveur ne livre une diffusion qu'aux abonnements qu'il sait encore autorisés, et seulement si elle concerne l'aventure pour laquelle l'abonnement a été accepté ; une connexion révoquée ne peut plus s'abonner.
  - **Une seule instance de l'API** : les connexions et abonnements sont suivis en mémoire, avec le broker simple de Spring. Avec plusieurs instances, une révocation n'atteindrait que les connexions de l'instance qui la traite : il faudrait alors un broker externe et propager les révocations.
  - L'expiration du jeton d'accès (15 min) ne ferme pas une connexion déjà ouverte : seules la révocation de la session et la fin de la participation coupent. Quitter le rôle de MJ (`step-down`) ne coupe rien : l'ancien MJ reste participant tant que le siège est vacant.

Les tests d'intégration (`apps/api/src/test/.../integration`) démarrent l'application contre un vrai
PostgreSQL via Testcontainers : Docker doit être lancé pour exécuter `./mvnw verify`.
