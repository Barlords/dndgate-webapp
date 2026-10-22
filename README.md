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

Les listes bornées par nature (personnages d'une aventure ou d'un compte, grimoires, disponibilités) ne sont pas paginées.

## Limites et validation des entrées

- **Corps JSON** : 512 Ko au plus (`app.limits.max-body-bytes`), sinon `413`. Le plus long champ légitime (le fond d'un personnage, 80 000 caractères) y tient.
- **Images** : 5 Mo au plus (`413` au-delà). Seuls le **PNG** et le **JPEG** sont acceptés, reconnus par le **contenu** du fichier (signature) et non par le type annoncé par le client ; un autre contenu, un fichier vide ou tronqué répond `400`. L'extension du fichier stocké suit le format réel (`.png` ou `.jpg`).
- **Textes obligatoires** (noms, descriptions, contenus) : non vides une fois les espaces retirés. **Textes facultatifs d'une modification** (`PATCH`) : s'ils sont présents, non vides ; **absents, ils ne changent pas**.
- **Plannings et disponibilités** : de 1 à 100 dates. **Dates** (plannings, disponibilités, notes) : entre les années 1900 et 2200, sinon `400` (le format accepte des années que PostgreSQL ne sait pas stocker, ce qui finissait en `500`). **Signaux WebRTC** : type `offer`, `answer` ou `candidate`, contenu de 20 000 caractères au plus.
- **Quantités** : au-delà d'un plafond, la création répond `409` (`application/problem+json`) ; les plafonds sont comptés en base par des requêtes `COUNT` et appliqués par le domaine (clés `limits.*` de `application-dev.properties` / `application-prod.properties`) :
  - **10 personnages** par joueur et par aventure (`limits.characters-per-player-and-adventure`), vérifié avant le stockage de l'image ;
  - **50 aventures actives** par compte comme MJ (`limits.active-adventures-per-master`) : celles dont le compte est le MJ (`creator_id`, sans MJ vacant) et dont le statut est `NOT_STARTED` ou `ONGOING` ; une aventure terminée ou abandonnée ne compte plus ;
  - **100 grimoires** par compte (`limits.spell-books-per-account`), import compris ;
  - **1 000 sorts** par grimoire (`limits.spells-per-spell-book`), import compris ; ajouter un sort déjà présent n'est pas une erreur ;
  - **500 notes d'aventure** et **500 notes d'historique** par aventure (`limits.adventure-notes-per-adventure`, `limits.historical-notes-per-adventure`) ;
  - **30 messages par compte et par minute** (`limits.messages.max`, `limits.messages.window`) : au-delà, `429` avec `Retry-After` (secondes). Les messages sont comptés en base sur la dernière minute (index `message(sender_id, send_date)`).
  Une aventure terminée ou abandonnée ne peut pas être rouverte (machine à états du statut, voir « Statut d'une aventure ») : le plafond d'aventures actives ne se contourne donc pas ainsi. Le transfert de MJ (cession, réclamation) ne vérifie volontairement pas le plafond du nouveau MJ.
  Les comptages ne sont pas verrouillés : deux créations simultanées peuvent dépasser un plafond d'une unité ou deux. Les limites de taille des images ne changent pas.
- **WebSocket** : un client n'envoie que `CONNECT`, `SUBSCRIBE` (et `UNSUBSCRIBE`) et des battements de cœur ; toute publication est refusée. Une trame de plus de 16 Ko coupe la connexion avant d'être lue. Seules les destinations exactes `/topic/adventure/chat/{id}` et `/topic/adventure/webrtc/{id}` (UUID en minuscules, rien avant ni après) sont acceptées au `SUBSCRIBE`. Une connexion ouvre au plus **20 abonnements** (`limits.websocket.subscriptions-per-connection`) ; un identifiant d'abonnement déjà utilisé sur la connexion est refusé, comme un second `CONNECT`. Limite connue : Spring ne garantit pas l'ordre de traitement des trames reçues d'une même connexion (pas d'option `preserveReceiveOrder` sur le canal entrant) ; un `UNSUBSCRIBE` suivi aussitôt d'un `SUBSCRIBE` avec le même identifiant peut donc être refusé si le second passe avant le premier. Le client doit attendre ou changer d'identifiant.
- Toutes les routes qui reçoivent un corps JSON sont validées (`@Valid`).
- **Erreurs** : chaque réponse d'erreur (`application/problem+json`) porte un `correlationId`, le même que l'en-tête `X-Request-Id` et que sur chaque ligne de log de la requête ; c'est l'identifiant à donner pour signaler un problème.

## Images des personnages

Le bucket MinIO est **privé** : au démarrage, l'API crée le bucket s'il manque et **retire toute politique publique** laissée par une ancienne version.
Une image se lit par `GET /game-characters/{id}/picture` (authentification comme le reste de l'API) : le propriétaire du personnage et les participants de l'aventure, personne d'autre.
Le personnage renvoie `pictureUrl` (cette route) et non plus `picturePath` (le chemin interne). Réponse : `Content-Type` du format réel, `Cache-Control: private, max-age=3600`, `Content-Disposition: inline`.
Pour l'afficher, le front récupère l'image avec le jeton (`HttpClient`, `responseType: 'blob'`) puis l'affiche (`URL.createObjectURL`) : une balise `<img src>` seule n'envoie pas l'en-tête d'authentification.

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
- **Vote** : **tous** les autres joueurs doivent approuver ; **un refus** met fin au vote ; une réponse peut être changée tant que le vote est en cours ; un vote sans réponse **expire au bout de 7 jours** (`game-master.claim.ttl`) et rien ne change. Les joueurs dont le compte est supprimé ne votent pas ; si plus personne n'a à voter, le candidat prend le rôle aussitôt. Un seul vote à la fois par aventure ; le vote est annulé si le candidat quitte l'aventure.
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

- Ce qui identifie la personne disparaît : pseudo, e-mail, mot de passe, sessions, spellbooks. Le compte ne peut plus se connecter.
- Ce sur quoi les autres joueurs s'appuient reste : personnages, messages, votes et évaluations pointent vers le même identifiant, devenu anonyme
  (`anonymized: true` dans la réponse publique, pseudo `deleted-…`).
- Les aventures du compte qui n'ont pas démarré sont annulées et masquées ; celles qui ont démarré restent consultables par leurs joueurs.
  Sa place est libérée dans les aventures qui n'ont pas démarré, conservée dans les autres.
- L'ancien pseudo et l'ancien e-mail peuvent être réutilisés par une nouvelle inscription.

### Vérification de l'adresse e-mail

À l'inscription, un e-mail contient un lien vers **le site web** (`WEB_BASE_URL/verify-email?email=…&code=…`, avec l'adresse et le code encodés).
La page appelle l'API :

- `POST /accounts/verify` `{ "email": "...", "code": "..." }` : `204` si le code est bon.
  Un code est **à usage unique**, valable **24 heures** (`security.verification.ttl`), de 24 caractères aléatoires, comparé en temps constant.
  Un code faux, expiré ou déjà utilisé, ou une adresse inconnue, donnent **la même réponse `400`** : on ne peut pas savoir quelles adresses sont inscrites.
- `POST /accounts/verification-email` `{ "email": "..." }` : renvoie un nouveau mail (l'ancien code cesse de fonctionner). Répond **toujours `202`**,
  que l'adresse existe ou non, soit déjà vérifiée ou non. Au plus un mail par minute et par adresse (`security.verification.resend-cooldown`).
- Une validation n'est plus un `GET` : suivre un lien (analyseurs de courrier, aperçus) ne modifie rien.
- Si le mail ne peut pas être envoyé, **l'inscription réussit quand même** (sinon le pseudo et l'adresse resteraient pris par un compte inutilisable) ;
  la personne demande un nouveau mail. Après un envoi raté, il faut attendre la minute de délai.

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
    dans la table `login_failures` (migration V10), avec le nombre d'échecs et la fin du verrou. Elle est gardée au plus la durée de la fenêtre de verrouillage :
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
- **Règle « participant »** (propriétaire ou joueur d'une aventure) : messages, signalisation WebRTC, abonnements WebSocket, notes historiques, notes du MJ et personnages ne sont accessibles qu'aux participants. Créer un personnage exige aussi d'être participant.
- Les notes du MJ de visibilité `PRIVATE` ne sont lisibles que par le propriétaire de l'aventure.
- Un personnage est lisible par son propriétaire et par les participants de son aventure ; on ne peut lister que ses propres personnages.
- Les spellbooks sont personnels : seul leur propriétaire peut les lire, les exporter ou les modifier.
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
