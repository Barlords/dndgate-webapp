# Audit v2 de l'API : corrections et améliorations

Audit du 21/10/2026 de `apps/api` (tout le code de `src/main`, la configuration, les migrations, la CI et la livraison), fait **après** l'audit v1 (`docs/api-review.md`).
Les points déjà traités ou déjà notés dans la v1 ne sont pas repris, sauf quand ce rapport y ajoute un fait nouveau.
La numérotation continue celle de la v1 (points 1 à 24) : les points de ce rapport vont de **25** à **58**.

Statuts : `[ ]` à faire, `[~]` en cours, `[x]` fait.

**Preuve** : *vérifié* = confirmé par un appel réel (tests d'intégration jetables lancés contre PostgreSQL le 21/10/2026, puis supprimés : voir l'annexe) ; *lu* = constaté dans le code, sans appel.

---

## Synthèse

| # | Sujet | Priorité | Preuve |
|---|---|---|---|
| 25 | Limite par IP contournée en encodant une lettre du chemin | P1 | vérifié |
| 26 | Invitations par e-mail illimitées : spam et saturation de la boîte d'envoi | P1 | lu |
| 27 | N'importe qui peut verrouiller le compte de n'importe qui | P1 | lu |
| 28 | La date d'une note du MJ ne change jamais, alors que l'API répond 201 | P1 | vérifié |
| 29 | Un compte peut voter plusieurs fois au planning | P1 | vérifié |
| 30 | La date d'un message est choisie par le client | P1 | vérifié |
| 31 | L'inscription révèle si une adresse est inscrite | P1 | vérifié |
| 32 | Adresses e-mail sensibles à la casse | P1 | lu |
| 33 | Le chat reste reçu après avoir quitté l'aventure ou s'être déconnecté | P1 | lu |
| 34 | Statut et capacité d'une aventure modifiables sans règle | P2 | vérifié |
| 35 | Erreurs de base de données : 500 sans cause ou cause perdue | P2 | vérifié |
| 36 | Import de grimoire non validé | P2 | vérifié |
| 37 | Ajout d'un sort sans identifiant | P2 | vérifié |
| 38 | Un candidat dont le compte est supprimé peut devenir MJ | P2 | lu |
| 39 | Images supprimées avant que la base ait validé | P2 | lu |
| 40 | Aucune limite de quantité (personnages et images, aventures, grimoires…) | P2 | lu |
| 41 | Jetons de rafraîchissement accumulés pendant 180 jours | P2 | lu |
| 42 | Écritures concurrentes perdues, opérations en plusieurs étapes non atomiques | P2 | lu |
| 43 | Requêtes N+1 restantes, domaine qui transporte des comptes entiers | P2 | lu |
| 44 | Contraintes manquantes en base | P2 | lu |
| 45 | WebRTC répond du texte là où le contrat annonce du JSON | P3 | vérifié |
| 46 | Codes de statut HTTP incohérents | P3 | lu |
| 47 | Champs ignorés et routes en double dans le contrat | P3 | lu |
| 48 | Ce qui passe par WebSocket n'est ni typé ni documenté | P3 | lu |
| 49 | CORS : en-têtes de réponse illisibles par le front | P3 | lu |
| 50 | Deux textes en français ont échappé à `LanguagePolicyTest` | P3 | vérifié |
| 51 | Code mort | P3 | lu |
| 52 | Doublons et petites incohérences de code | P3 | lu |
| 53 | Dépendances, images et actions non épinglées | P4 | lu |
| 54 | Configuration : profils dupliqués, e-mails réels en dev | P4 | lu |
| 55 | Tests manquants | P4 | lu |
| 56 | L'ancien MJ reste « participant » pendant la vacance | Décision | lu |
| 57 | Un inconnu peut rejoindre une aventure publique sans MJ et prendre le rôle | Décision | lu |
| 58 | Autres questions fonctionnelles | Décision | — |

Ordre conseillé : **lot 1** (petits correctifs sûrs : 25, 28, 29, 30, 37, 45, 50), **lot 2** (abus : 26, 27, 40, 33), **lot 3** (intégrité : 34, 35, 36, 38, 39, 42, 44), **lot 4** (contrat, avec le premier client Angular généré : 31, 32, 46, 47, 48, 49), puis le reste.

---

## P1 — Sécurité et exactitude

### 25. La limite par IP se contourne en encodant une lettre du chemin — *vérifié*
- [x] `RateLimitFilter` (ligne 67) compare `request.getRequestURI()`, c'est-à-dire le chemin **encore encodé**, à `/accounts/login`. Spring MVC, lui, décode le chemin avant de choisir la route.
  `POST /dndgate-api/accounts/%6Cogin` arrive donc sur la connexion **sans jamais être compté**.
- Mesuré avec une limite de 2 connexions : chemin normal `404 404 429 429`, chemin encodé `404 404 404 404` (404 = identifiant inconnu, donc la connexion a bien été traitée).
- Concerne les six règles : connexion (deviner des mots de passe sur plusieurs comptes), inscription et renvoi de mail (envoyer des e-mails à volonté), vérification et confirmation d'adresse.
- Correctif : comparer le chemin décodé et normalisé (`UrlPathHelper.getPathWithinApplication`, ou `request.getServletPath()`), ou mieux, appliquer la limite après le choix de la route (un `HandlerInterceptor` et une annotation `@RateLimited` sur les méthodes des contrôleurs), ce qui suit automatiquement tout renommage de route.
- Test : la même boucle que ci-dessus doit donner `429` sur le chemin encodé.
- Corrigé : `RateLimitFilter` compare désormais `UrlPathHelper.getPathWithinApplication` (chemin décodé et normalisé) ; `RateLimitEncodedPathIntegrationTest` couvre les six règles, contrôle par mutation fait (6 tests rouges avec l'ancien code).

### 26. Invitations par e-mail illimitées — *lu*
- [x] `POST /adventures/{id}/invitations/{pseudo}` (`AdventureUpdaterService.sendInvitationCode`, ligne 131) envoie un e-mail à chaque appel, sans aucune limite. Créer une aventure n'est pas limité non plus.
  Un compte peut donc écrire des centaines de fois au même joueur (les pseudos sont publics), ou à tous les joueurs.
- Effets de bord :
  - la boîte d'envoi (`MailExecutorConfig` : file de 100) se remplit, et les e-mails suivants sont **abandonnés**, y compris les e-mails de vérification et de changement d'adresse des autres personnes ;
  - le compte SMTP (OVH) risque d'être bloqué pour envoi massif.
- L'invitation part aussi pour une aventure terminée ou abandonnée, et vers quelqu'un qui est déjà participant.
- Correctif : limite par expéditeur (par exemple 20 par heure) et par destinataire (une invitation par aventure et par 24 h) ; refuser si l'aventure n'est pas `NOT_STARTED` ou si le destinataire participe déjà (`409`) ; à plus long terme, une file d'attente séparée par priorité (les e-mails de sécurité ne doivent jamais attendre derrière des invitations).
- Corrigé (hors file prioritaire, qui reste à faire) : `409` si l'aventure n'est pas `NOT_STARTED` ou si le destinataire participe déjà ; `429` + `Retry-After` à 20 invitations par heure et par expéditeur (`invitation.rate-limit.sender.*`) et à une invitation par aventure et par 24 h et par destinataire (`invitation.rate-limit.recipient.window`). Comptage en base (`sent_invitations`, migration V8, purge nocturne), horloge injectée. Tests : `AdventureUpdaterServiceInvitationTest`, `InvitationLimitIntegrationTest` ; contrôle par mutation fait.

### 27. N'importe qui peut verrouiller le compte de n'importe qui — *lu*
- [ ] Le verrouillage (5 échecs, 15 minutes, `AccountPasswordVerifierService`) compte par **compte**, quelle que soit l'adresse IP. Les pseudos sont publics (`GET /accounts/pseudo/{pseudo}`) et 5 essais tiennent sous la limite par IP (10 par minute).
  Recommencer toutes les 15 minutes suffit à empêcher un joueur ou un MJ de se connecter indéfiniment.
- Ce n'est pas un bug d'implémentation, c'est le compromis du verrouillage strict : **décision à prendre**. Pistes, de la plus simple à la plus complète :
  1. compter les échecs par couple (compte, IP) pour le verrouillage, et garder un compteur global par compte seulement pour journaliser ou alerter ;
  2. remplacer le verrou par un délai croissant (1 s, 2 s, 4 s…) qui ralentit sans bloquer ;
  3. ne pas appliquer le verrou à un appareil qui s'est déjà connecté avec succès (cookie ou jeton d'appareil) ;
  4. prévenir le titulaire par e-mail quand son compte est verrouillé.
- Ne pas oublier les deux autres points d'entrée qui partagent le compteur (suppression du compte, `PATCH /accounts`).

### 28. La date d'une note du MJ ne change jamais — *vérifié*
- [x] Corrigé : `updatable = false` retiré, test `the_date_of_an_adventure_note_can_be_patched` (rouge avant, vert après). `AdventureNoteEntity.date` était déclarée `updatable = false` (ligne 33). Hibernate retire la colonne de l'`UPDATE`, mais le `merge` recopie la valeur dans l'objet renvoyé.
- Mesuré : `PATCH /adventure-notes/{id}` `{"date":"2027-05-05"}` répond **201 avec la nouvelle date** ; la base garde `2026-01-01`, et un `GET` juste après renvoie l'ancienne date. La perte est silencieuse.
- `PartialUpdateIntegrationTest` ne modifie que le contenu, d'où le trou. Les notes historiques n'ont pas ce défaut.
- Correctif : retirer `updatable = false` (aucune migration nécessaire) ; ajouter au test un `PATCH` de la date, relu en base.

### 29. Un compte peut voter plusieurs fois au planning — *vérifié*
- [x] *Corrigé : `POST` devient « créer ou remplacer » (délègue à l'updater), `PUT` renvoie le vote enregistré, migration `V6` qui dédoublonne puis index unique `(planning_id, account_id)`, opération rejouée si deux premiers votes simultanés se heurtent à l'index. `V6` garde un doublon arbitraire (le plus grand UUID) faute d'horodatage en base. Contrôle par mutation fait (retry désactivé : tests rouges).*
  `POST /planning/availability` (`AvailabilityCreatorService.create`) créait un nouveau vote à chaque appel. Mesuré : 3 appels du même compte, **3 votes** en base.
  Rien en base ne l'empêche (pas d'unicité sur `(planning_id, account_id)`). Tout compte qui voit une aventure publique peut ainsi fausser son planning.
- `PUT /planning/availability`, lui, met à jour, mais renvoie l'objet reçu (`AvailabilityUpdaterService`, ligne 39) et non le vote enregistré : l'`id` de la réponse est un identifiant neuf qui n'existe pas en base.
- Correctif : faire de `POST` un alias de « créer ou remplacer », ou répondre `409` s'il existe déjà un vote ; migration qui dédoublonne puis crée l'index unique `(planning_id, account_id)` ; renvoyer le vote enregistré.

### 30. La date d'un message est choisie par le client — *vérifié*
- [x] **Corrigé** (date fixée par le serveur via `Clock` en `Instant`, `message.send_date` en `timestamptz` + index `(adventure_id, send_date desc)` en V7 ; **rupture de contrat** : `sendDate` retiré de `MessageCreationRequest`). `MessageCreationRequest.sendDate` vient du client, et la liste est triée par cette date. Mesuré : un message daté du `2199-12-31` reste **en tête du chat pour toujours**. Contrôle par mutation fait (date du client conservée au lieu de celle du `Clock` : tests rouges).
  On peut aussi antidater un message pour le glisser dans l'historique.
- La colonne est un `timestamp without time zone` (`LocalDateTime`) : deux clients dans deux fuseaux horaires produisent un ordre faux, même de bonne foi.
- Correctif : date fixée par le serveur (`Clock`), en `Instant` ; migration de `message.send_date` en `timestamptz` ; retirer `sendDate` de la requête (**rupture de contrat**) ; ajouter un index `(adventure_id, send_date desc)`.

### 31. L'inscription révèle si une adresse est inscrite — *vérifié*
- [ ] `POST /accounts` avec une adresse déjà utilisée répond `409 "Email is already in use"`. La v1 a fermé les autres fuites (points 5 et 13 : `/verify`, `/verification-email`, suppression de `GET /accounts/email/{email}`), mais celle-ci reste, à 5 essais par heure et par IP (et sans limite réelle, voir le point 25).
- **Décision à prendre**, car l'inscription devient moins claire pour une personne de bonne foi :
  - fermer : répondre `202` dans tous les cas, et écrire à l'adresse déjà inscrite (« quelqu'un a tenté de créer un compte avec votre adresse ; si c'est vous, connectez-vous ou réinitialisez votre mot de passe ») ;
  - assumer : documenter que l'inscription révèle les adresses, et compter sur la limite par IP (une fois le point 25 corrigé).
- `PATCH /accounts` répond aussi `409` pour une adresse prise ; ce cas demande le mot de passe actuel et peut rester tel quel.

### 32. Adresses e-mail sensibles à la casse — *lu*
- [ ] L'adresse est seulement rognée (`AccountDtoMapper`), puis comparée exactement (`AccountRepository.findByEmail`, unicité `accounts_email_key`).
  `Bob@Mail.com` et `bob@mail.com` peuvent ouvrir deux comptes ; une personne qui retape son adresse avec une autre casse ne la retrouve pas (`/verify`, `/verification-email`, `/email/confirm`).
- Correctif : comme pour le pseudo (v1, point 13) : mettre l'adresse en minuscules à l'entrée, index unique sur `lower(email)` (et recherche de `pending_email` sur la même forme), avec une migration qui s'arrête si la base contient déjà deux adresses identiques à la casse près.

### 33. Le chat et les signaux WebRTC restent reçus après avoir quitté l'aventure — *lu*
- [ ] `WebSocketSecurityInterceptor` vérifie la règle « participant » **une seule fois**, au `SUBSCRIBE`. Ensuite, rien ne coupe l'abonnement :
  quitter l'aventure, passer la main de MJ, se déconnecter, changer de mot de passe ou supprimer son compte laissent la connexion ouverte, qui continue de recevoir tous les messages et toutes les offres WebRTC.
  Le jeton d'accès expire au bout de 15 minutes, mais la connexion WebSocket n'est jamais rejugée.
- La v1 notait déjà « fermer les WebSockets quand leur session est révoquée » ; le cas « quitte l'aventure » est nouveau et touche directement la confidentialité du chat.
- Correctif possible :
  - garder pour chaque connexion son compte et sa session (`SessionConnectEvent`, `SessionDisconnectEvent`) ;
  - à la révocation d'une session, au départ d'une aventure ou au changement de MJ, fermer les connexions concernées, ou au moins leurs abonnements ;
  - autre approche, plus robuste : ne plus diffuser sur un sujet commun, mais envoyer à chaque participant **actuel** (`convertAndSendToUser`).
- Au passage : le contrôle d'abonnement prend le **dernier segment** de la destination comme identifiant d'aventure (`/topic/adventure/n-importe-quoi/<id>` est accepté). Ce n'est pas exploitable aujourd'hui, mais une liste blanche exacte (`/topic/adventure/(chat|webrtc)/{uuid}`) serait plus sûre.

---

## P2 — Intégrité des données et robustesse

### 34. Statut et capacité d'une aventure modifiables sans règle — *vérifié*
- [ ] `PATCH /adventures/{id}` et `PUT /adventures/{id}/status` acceptent n'importe quel passage. Mesuré : une aventure passe de `FINISH` à `NOT_STARTED` et `maxPlayer` descend à 1 alors qu'un joueur est déjà inscrit (201).
- Conséquences :
  - une aventure terminée redevient « disponible » et joignable ;
  - un MJ peut passer une aventure à `ONGOING` dès qu'un joueur la rejoint, ce qui ouvre les évaluations sans avoir joué (règle « avoir joué ensemble ») ;
  - `maxPlayer` peut être inférieur au nombre de joueurs.
- Correctif : une machine à états dans le domaine (`NOT_STARTED → ONGOING → FINISH`, `NOT_STARTED|ONGOING → ABORT`, rien après `FINISH`/`ABORT`), `409` sinon ; `maxPlayer >= players.size()` ; retirer `status` de `AdventurePatchRequest` (la route `PUT …/status` existe déjà).

### 35. Erreurs de base de données : 500 sans cause, ou cause perdue — *vérifié*
- [ ] Les adaptateurs enveloppent `repository.save` dans un `Try` de Vavr, mais l'`INSERT` n'est envoyé qu'au commit, **après** la sortie du `Try`. Une contrainte violée devient donc une 500 générique.
  Mesuré : un deuxième `POST /planning` pour la même aventure donne `500 "An unexpected error occurred"` au lieu d'un `409`.
- Quand le `Try` attrape bien l'erreur (écriture immédiate, comme `saveAndFlush`), les 37 appels `getOrElseThrow(DataNotSaveException::new)` jettent l'`ApplicationError` et sa cause : `DataNotSaveException` n'a pas de cause, et `ErrorHandler` ne journalise rien. Une erreur réelle ne laisse **aucune trace** dans les logs.
- Correctif :
  - supprimer `Either`/`Try` des adaptateurs et laisser remonter les exceptions ;
  - traduire les violations connues en exceptions du domaine (planning déjà créé, vote déjà enregistré, adresse prise entre-temps) ;
  - ajouter un handler pour `DataIntegrityViolationException` qui journalise et répond `409`.

  Option minimale : `DataNotSaveException(Throwable cause)` et un `log.error` dans son handler.

### 36. Import de grimoire non validé — *vérifié*
- [ ] `POST /spellbooks/import-file` lit le fichier avec son propre `ObjectMapper`, sans aucune validation. Mesuré : un fichier sans `name` donne une **500** (`NullPointerException` sur `trim()`).
  Un nom de plus de 255 caractères, ou un sort de plus de 255 caractères, finit aussi en 500 (colonne trop courte). Le nombre de sorts n'est pas borné (le fichier peut faire 5 Mo).
- Statuts inversés : `SpellBookImportException` (fichier illisible) → **500** au lieu de `400` ; `SpellBookExportException` → **404** au lieu de `500`.
- L'export envoie `Content-Disposition: form-data; name="attachment"; filename="book.json"` (`setContentDispositionFormData`, ligne 103), qui est l'en-tête d'un formulaire : il faut `ContentDisposition.attachment().filename("book.json")`.
- Correctif : valider le DTO importé avec les mêmes contraintes que la création (via `Validator`, `@NotBlank`, `@Size`, liste ≤ N sorts de 255 caractères au plus), et répondre `400` avec la liste des erreurs.

### 37. Ajout d'un sort sans identifiant — *corrigé*
- [x] Corrigé : `@NotBlank` sur `spellIndex` (400 sur `{}` ou texte blanc), pas de validation contre le SRD ; test + contrôle par mutation.
- Correctif : `@NotBlank`. À décider : valider l'identifiant contre la liste des sorts du SRD (voir le point 58).

### 38. Un candidat dont le compte est supprimé peut devenir MJ — *lu*
- [ ] `AccountAnonymizerService` ne touche pas aux votes ouverts. Le candidat reste dans les joueurs d'une aventure en cours (historique), et `MasterClaimService.isStillValid` (ligne 169) ne vérifie pas que son compte existe encore.
  Si les autres approuvent, le rôle est donné à un **compte anonymisé** : l'aventure a de nouveau un MJ (plus de « vacance »), que personne ne peut plus jouer ni remplacer.
- Même sans aller au vote, le vote ouvert bloque toute autre demande pendant 7 jours.
- Correctif : l'anonymisation annule les votes où le compte est candidat ; `isStillValid` exclut un candidat anonymisé ; test d'intégration du scénario.

### 39. Images supprimées avant que la base ait validé — *lu*
- [ ] `GameCharacterDeleterService` supprime l'image dans MinIO, **puis** la ligne en base. Si MinIO est en panne, le personnage ne peut plus être supprimé (500). Si la base refuse, le personnage reste mais sans image.
- `AdventureDeleterService` supprime les images à la fin de la méthode, mais **dans** la transaction (`TransactionalAdventureDeleter`), donc avant le commit : un commit qui échoue laisse des personnages sans image. Le commentaire « after the data » est trompeur.
- À la création, l'image est envoyée avant l'enregistrement du personnage : un enregistrement raté laisse une image orpheline.
- Correctif : supprimer les images après le commit (le même `AfterCommit` que l'audit), en ignorant les échecs, qui sont journalisés ; supprimer l'image envoyée si l'enregistrement échoue ; à terme, un nettoyage périodique des objets orphelins.

### 40. Aucune limite de quantité — *lu*
- [x] Rien ne borne le nombre de personnages (chacun avec une image de 5 Mo), d'aventures, de grimoires, de sorts par grimoire, de notes ou de messages par compte.
  Un participant peut remplir le stockage MinIO ; un compte peut créer des milliers d'aventures (et alimenter le point 26).
- La v1 justifiait l'absence de pagination de ces listes par « bornées par nature (à revoir si on plafonne leur création) » : elles ne le sont pas.
- Correctif : des plafonds dans le domaine (par exemple 10 personnages par joueur et par aventure, 50 aventures actives par compte, 100 grimoires, 1 000 sorts), `409` au-delà, et une limite d'écriture par compte pour les messages.
- Corrigé : plafonds dans le domaine (`QuantityLimits`, clés `limits.*`), `409` via `QuantityLimitExceededException` et `429` + `Retry-After` pour les messages (`MessageRateLimitException`, comptés en base sur la minute écoulée, index V9 sur `message(sender_id, send_date)`). Aventures actives = celles dont le compte est le MJ (`creator_id`, MJ non vacant) en `NOT_STARTED` ou `ONGOING`. Tests unitaires (`QuantityLimitsServicesTest`) et d'intégration (`QuantityLimitIntegrationTest`, plafonds bas), contrôle par mutation fait sur chaque plafond. Contournement connu : rouvrir une aventure terminée ou abandonnée dépasse le plafond d'aventures actives tant que le point 34 n'interdit pas de quitter `FINISH` / `ABORT` (décision : pas de contrôle ici). Le transfert de MJ (cession, réclamation) ne vérifie volontairement pas le plafond du nouveau MJ. Non traité : pas de verrou, un dépassement d'une ou deux unités est possible en cas de créations simultanées.

### 41. Jetons de rafraîchissement accumulés pendant 180 jours — *lu*
- [ ] Chaque rafraîchissement ajoute une ligne à `refresh_tokens` ; les jetons utilisés sont gardés (pour détecter le rejeu) jusqu'à la suppression de la **session**, soit 180 jours.
  Avec un jeton d'accès de 15 minutes, un client ouvert en continu ajoute environ 96 lignes par jour, soit environ 17 000 par session.
- Les sessions révoquées restent aussi jusqu'à leur expiration.
- Correctif : la tâche de nuit supprime aussi les jetons expirés (un jeton expiré ne peut plus être rejoué : inutile de le garder) et les sessions révoquées depuis plus de quelques jours ; index sur `refresh_tokens(expires_at)`.

### 42. Écritures concurrentes perdues, opérations en plusieurs étapes non atomiques — *lu*
- [ ] Seuls l'aventure et le vote de MJ ont un verrou optimiste (`@Version`). Ailleurs, le dernier qui écrit gagne, et il écrit **toutes** les colonnes (le mapper reconstruit l'entité entière) :
  - deux ajouts de sorts simultanés dans un grimoire : l'un est perdu ;
  - `PATCH /accounts` en même temps qu'une confirmation d'adresse : l'un efface l'autre.
- Opérations en plusieurs écritures, sans transaction :
  - `AccountUpdaterService.update` : le nouveau mot de passe est enregistré, puis les sessions sont révoquées dans un second temps (si la seconde étape échoue, les anciennes sessions survivent au changement de mot de passe) ;
  - `AvailabilityUpdaterService` : une boucle d'écritures ;
  - `PlanningUpdaterService` : lecture puis écriture, avec une course possible à la création.
- Deux re-votes simultanés d'un même compte peuvent laisser les dates des deux votes : `AvailabilityEntity` n'a pas de `@Version` et la mise à jour ne réécrit que `availability_dates`. Correctif : `@Version` + rejeu, ou `SELECT ... FOR UPDATE`.
- Effet secondaire des identifiants fixés par le domaine : sans `@Version`, Spring Data ne sait pas qu'une entité est nouvelle et fait un `merge`, donc **un `SELECT` avant chaque `INSERT`**.
- Correctif : `@Version` sur les entités modifiables (compte, grimoire, personnage, notes, planning) ; un décorateur transactionnel pour `AccountUpdaterApi` (comme `TransactionalAccountAnonymizer`) ; `Persistable<UUID>` ou `@Version` pour supprimer les `SELECT` inutiles.

### 43. Requêtes N+1 restantes, domaine qui transporte des comptes entiers — *lu*
- [ ] `GET /spellbooks/mine` : une requête par grimoire pour sa liste de sorts.
- [ ] `GET /planning/{id}/availability` : une requête par vote pour ses dates.
- [ ] `GET /game-characters?adventureId=` : le `@EntityGraph` charge l'aventure mais pas le **propriétaire**, chargé une fois par joueur différent (le test N+1 de la v1 portait sur un seul propriétaire).
- Plus profond : `GameCharacter.owner` et `SpellBook.owner` sont des `Account` complets (hash du mot de passe, codes de vérification, adresse), et `GameCharacter.adventure` une `Adventure` complète (code d'invitation compris).
  Le DTO filtre aujourd'hui, mais un objet du domaine renvoyé tel quel un jour exposerait tout ; le `MessageController` diffuse déjà un objet du domaine (point 48).
- Correctif : `@BatchSize` sur les collections, `owner` dans l'`@EntityGraph`, et à terme référencer le propriétaire et l'aventure par leur identifiant dans ces modèles.
  Étendre `QueryCountIntegrationTest` à ces trois routes.

### 44. Contraintes manquantes en base — *lu*
- [ ] **Clés étrangères absentes** : `adventure.creator_id`, `adventure_players.player_id`, `message.adventure_id` et `sender_id`, `rating.emitter_id` et `target_id`, `availability.planning_id` et `account_id`, `planning.adventure_id`, `master_claims.candidate_id`, `master_claim_votes.voter_id`.
  Aujourd'hui, la cohérence ne tient que si le code supprime tout dans le bon ordre (la v1 a déjà trouvé une méthode de suppression vide).
- [ ] **Unicités absentes** : `(adventure_id, player_id)`, `rating(emitter_id, target_id)` (le contrôle « déjà noté » est une lecture puis une écriture : deux requêtes simultanées créent deux notes), `availability(planning_id, account_id)` (fait par `V6`, point 29), `adventure.invitation_code`.
- [ ] **NOT NULL absents** : `adventure_players.player_id`, `adventure.visibility`.
- Les tables de collection (`adventure_players`, `propositions`…) n'ont pas de clé primaire : Hibernate efface et réécrit toute la liste à chaque modification.
- Nom hérité : `spell_book_entity_spell_list(spell_book_entity_id, spell_list)`.
- Correctif : une migration `V8` (`V6` et `V7` sont pris) qui vérifie d'abord les données (comme `V2` pour les pseudos), puis ajoute les contraintes ; `FlywayMigrationIntegrationTest` couvre le cas « données incohérentes : la migration s'arrête ».

---

## P3 — Contrat de l'API et qualité du code

### 45. WebRTC répond du texte là où le contrat annonce du JSON — *corrigé*
- [x] Corrigé : les trois routes répondent `204 No Content` (`@ResponseStatus`), contrat régénéré, diffusion WebSocket couverte par un test (mutation vérifiée).
  Mesuré : le corps n'est pas du JSON valide. Le client Angular généré échouera à le lire.
- Correctif : `204 No Content`, et régénérer le contrat.

### 46. Codes de statut HTTP incohérents — *lu*
- [ ] Les `PATCH` répondent `201 Created` (aventure, personnage, notes, notes historiques, grimoire) : il faut `200`.
- [ ] Les `PUT` répondent toujours `201`, même pour une mise à jour (planning, disponibilité, évaluation) : `200` pour une mise à jour, `201` pour une création.
- [ ] `POST /messages` répond `200` sans corps : il faut `201` et le `MessageDto` créé.
- [ ] `POST /adventures/{id}/invitations/{pseudo}` répond `200` sans corps : il faut `202` (l'e-mail part plus tard) ou `204`.
- [ ] Un échec de connexion (pseudo inconnu ou mauvais mot de passe) répond **404** (`AccountCredentialNotFoundException`) : il faut `401`.
- [ ] Points 36 (import, export) et 35 (`409` plutôt que 500).
- À faire en une fois, avant de générer le client Angular : ce sont des ruptures de contrat.

### 47. Champs ignorés et routes en double dans le contrat — *lu*
- [ ] `adventureId` dans `AdventureNotePatchRequest` et `HistoricalNotePatchRequest` est ignoré depuis la v1 (le droit vient de la note) : à retirer. Idem pour `accountId` dans `WebRTCMessage`.
- [ ] Trois façons d'écrire une évaluation : `POST`, `PUT /ratings` et `PATCH /ratings/{id}` (le `PATCH` ne revérifie pas l'éligibilité). En garder une ou deux.
- [ ] `PATCH /spellbooks/{id}` exige le nom **et** la description : ce n'est pas une modification partielle, contrairement aux autres `PATCH`.
- [ ] `POST /planning` et `PUT /planning` font presque la même chose (le `POST` finit en 500 si le planning existe, point 35).
- [ ] `GET /ratings/mean` renvoie un nombre nu, `0.0` quand il n'y a aucune note (impossible de distinguer « aucune note » d'une moyenne nulle) : renvoyer `{ "average": 4.2, "count": 7 }`.
- [ ] `GET /ratings?targetedAccountId=` est trié par `id` (un UUID aléatoire, donc un ordre sans sens) ; les évaluations n'ont pas de date de création.
- [ ] `MessageCreationRequest.adventureId` est une `String` validée à la main, alors que les autres requêtes utilisent `UUID`.
- [ ] Le nom d'un personnage fait au moins 3 caractères à la création, mais 1 suffit en modification.

### 48. Ce qui passe par WebSocket n'est ni typé ni documenté — *lu*
- [ ] `MessageController` diffuse l'objet du domaine `Message`, pas le `MessageDto` (ligne 57). Les sujets `/topic/adventure/chat/{id}` et `/topic/adventure/webrtc/{id}`, et la forme de leurs messages, n'apparaissent nulle part dans le contrat. Le front devra les deviner.
- [ ] Les signaux WebRTC (offres SDP, candidats ICE qui contiennent les adresses IP) sont diffusés à **tous** les participants, sans destinataire : voir le point 58.
- Correctif : diffuser des DTO, et documenter les sujets (un `contracts/asyncapi.yaml`, ou au moins une section de `contracts/README.md`).

### 49. CORS : en-têtes de réponse illisibles par le front — *lu*
- [ ] `SecurityConfig.corsConfigurationSource` n'expose aucun en-tête. Depuis une autre origine (le front en dev sur `localhost:4200`), le JavaScript ne peut pas lire `X-Request-Id`, `Retry-After` (429) ni `Content-Disposition` (export).
- Correctif : `setExposedHeaders(List.of("X-Request-Id", "Retry-After", "Content-Disposition"))`. Remplacer aussi `setAllowedHeaders("*")` par la liste réellement utilisée.

### 50. Deux textes en français ont échappé à `LanguagePolicyTest` — *corrigé*
- [x] Corrigé : les deux textes sont en anglais et `LanguagePolicyTest` refuse aussi une liste de mots français fréquents (test rouge avant la traduction ; `pom.xml` non touché).
- [x] `SecurityConfig` (ligne 76) : commentaire « Sinon toute erreur serveur serait masquee en 401… ».
- Le test ne cherche que les lettres accentuées ; ces deux textes n'en ont pas. Le renforcer, par exemple en refusant aussi une courte liste de mots français fréquents (` les `, ` des `, ` une `, ` pour `, ` sinon `…) dans les chaînes et les commentaires.
- Les commentaires du `pom.xml` sont aussi en français (hors du périmètre du test) : à traduire si la règle doit valoir pour tout le code.

### 51. Code mort — *lu*
- [ ] `AccountPersistenceSpi.deleteById` et `update`, `AdventurePersistenceSpi.update` : jamais appelés. Le premier est **dangereux** : un compte ne doit jamais être supprimé, seulement anonymisé (v1, point 4).
- [ ] `AccountPersistenceSpi.findByVerificationCode`, `MessageFinderApi.findById` et `findBySenderId`, `AvailabilityFinderApi.findByAccountIdAndPlanningId` (sans contrôle d'accès), `AvailabilityRepository.findAllByAccountId`.
- [ ] `SendInvitationCodeRequest`, `RemoveSpellToSpellBookRequest`, `DateFormatNotValidException`, `RandomStringGenerator.generateAlphabeticString`.
- [ ] `AccountEntity.spellBookList` (`@OneToMany` avec `cascade = ALL`, jamais rempli) : à retirer avant qu'une cascade inattendue ne se déclenche.

### 52. Doublons et petites incohérences de code — *lu*
- [ ] Deux classes `AfterCommit` identiques (`server/audit` et `server/mail`).
- [ ] Deux mécanismes de rejeu : `RetryingTransaction` (bootstrap) et `retryOnConcurrentChange` (dans `AdventureUpdaterService`). Garder le premier.
- [ ] `@EnableJpaRepositories` deux fois (`DndGateApiApplication` et `DomainConfiguration`).
- [ ] `HistoricalNoteUpdaterService` lève `AdventureNoteNotFoundException` (« adventure note not found ») pour une note historique.
- [ ] 49 constantes `EXCEPTION_MESSAGE` déclarées `public static String`, donc modifiables par n'importe quel code : `public static final`.
- [ ] `UuidValidator.validate(id)` appelé à la main dans chaque contrôleur : `@PathVariable UUID id` et un handler pour `MethodArgumentTypeMismatchException` feraient la même chose.
- [ ] `MinioAdapter.uploadFile` passe `inputStream.available()` comme taille (exact pour un `ByteArrayInputStream`, faux pour tout autre flux) et son message d'erreur dit « Only PNG ». Passer un `byte[]`.
- [ ] `AccountErrorHandler` calcule `Retry-After` avec `Instant.now()` au lieu de l'horloge injectée.
- [ ] Les contrôles d'accès appellent `CurrentAccountService.getAccountById` (une requête) uniquement pour vérifier que le compte existe, alors que la session l'a déjà prouvé.

---

## P4 — Exploitation et livraison

### 53. Dépendances, images et actions non épinglées — *lu*
- [ ] `docker-compose.yml` : `minio/minio` sans version (`latest`). MinIO a changé la distribution de son édition communautaire en 2025 : **vérifier** que l'image publiée reçoit encore des correctifs ; sinon, épingler une version connue, ou choisir un autre stockage compatible S3.
- [ ] Actions GitHub épinglées par étiquette (`@v4`) et non par empreinte (SHA) ; images de base du `Dockerfile` sans empreinte (`@sha256:`). Dependabot sait mettre à jour les deux formes.
- [ ] Le `Dockerfile` construit avec `mvn` (l'image `maven:3.9`), pas avec `./mvnw` : la version de Maven peut différer de la CI.
- [ ] Aucune analyse de l'image construite (par exemple Trivy) avant le `push` sur Docker Hub.

### 54. Configuration — *lu*
- [ ] `application-dev.properties` et `application-prod.properties` répètent presque tout (durées, limites, cron). Une valeur changée d'un côté seulement divergera. Mettre le commun dans `application.properties` et ne garder dans les profils que ce qui diffère.
- [ ] En dev, les e-mails partent vers le vrai serveur OVH avec des identifiants vides (échec journalisé). Ajouter **Mailpit** au `docker-compose.yml` (interface web pour lire les e-mails de dev) et le pointer dans le profil `dev`.
- [ ] En prod, `minio.url` est écrit en dur : `MINIO_URL` est ignoré.
- [ ] À garder en tête pour plusieurs instances : la limite par IP est en mémoire (déjà noté) et la purge de nuit tourne sur chaque instance (sans danger, mais en double).

### 55. Tests manquants — *lu*
- Aucun test unitaire pour : services des grimoires (sauf la création), planning et disponibilités, évaluations (création, mise à jour, `PATCH`), modification, suppression et lecture des personnages, services des notes.
- Aucun test d'intégration pour : import et export de grimoire, (faits : vote multiple (29), modification de la date d'une note (28), chemin encodé et limite par IP (25), date d'un message (30)) abonnement WebSocket après départ de l'aventure (33), statut d'aventure à rebours (34).
- La marge de couverture est de 0,5 point (v1) : chaque correctif de ce rapport doit arriver avec son test, et suivre la méthode habituelle (réintroduire le défaut, vérifier que le test devient rouge, restaurer).

---

## Décisions à prendre

### 56. L'ancien MJ reste « participant » pendant la vacance — *lu*
Quand le MJ quitte le rôle, `ownerId` garde son identifiant et `Adventure.isParticipant` le compte toujours comme participant.
Il continue de lire le chat, les notes publiques et les personnages, et peut créer des personnages, mais il n'est pas joueur, ne peut pas quitter l'aventure (`PlayerNotInAdventureException`) et apparaît encore dans `GET /adventures/as-master`.
Est-ce voulu ? Sinon : il devient joueur au moment où il quitte le rôle (s'il y a une place), ou il sort de l'aventure.

### 57. Un inconnu peut rejoindre une aventure publique sans MJ et prendre le rôle — *lu*
Une aventure `NOT_STARTED` publique dont le MJ a quitté le rôle reste joignable. Le nouveau venu peut aussitôt demander le rôle ; s'il est le seul joueur, il l'obtient **sans vote**.
Pistes : refuser les nouveaux joueurs tant qu'il n'y a pas de MJ ; ou réserver la demande aux joueurs présents avant la vacance.

### 58. Autres questions fonctionnelles
- **Verrouillage** (point 27) et **énumération par l'inscription** (point 31) : choisir le compromis.
- **Code d'invitation permanent** : le même code de 10 caractères sert pour toute la vie de l'aventure, n'expire pas et ne peut pas être révoqué (stocké en clair). Toute personne qui l'a reçu une fois peut revenir. Ajouter une rotation (`POST /adventures/{id}/invitation-code`), voire des codes par invitation.
- **Retirer un joueur** : le MJ n'a aucun moyen d'exclure un joueur.
- **Évaluations** : la personne notée voit qui l'a noté et combien (`emitterId`). Est-ce voulu, ou les évaluations reçues doivent-elles être anonymes ?
- **Signaux WebRTC** : diffusés à tous (y compris les adresses IP des candidats ICE) au lieu du seul destinataire. Ajouter un `targetAccountId` et un envoi par utilisateur.
- **Référentiel SRD** : le projet se décrit comme « référentiel des SRD », mais l'API n'expose aucune donnée du SRD et n'en vérifie aucune (un sort est une chaîne libre). Est-ce porté par le front, ou à faire côté API ?
- **Messages** : ni modification ni suppression, même pour leur auteur ou le MJ (modération).
- **Image d'un personnage** : impossible de la changer après la création.

---

## Annexe — sondes du 21/10/2026

Deux classes de test jetables (`ZzReviewV2ProbeIntegrationTest`, `ZzReviewV2RateLimitProbeIntegrationTest`, supprimées après usage) ont appelé l'API réelle contre PostgreSQL (Testcontainers, profil `dev`, JDK 21). Résultats bruts :

| Sonde | Résultat |
|---|---|
| `PATCH /adventure-notes/{id}` `{"date":"2027-05-05"}` | `201`, corps avec `2027-05-05` ; en base `2026-01-01` ; `GET` suivant : `2026-01-01` |
| Second `POST /planning` pour la même aventure | `500 "An unexpected error occurred"` |
| 3 × `POST /planning/availability` par le même compte | 3 lignes en base |
| `POST /webrtc/offer` avec `Accept: application/json` | `application/json`, corps `Offer received and broadcasted`, illisible en JSON |
| Message daté `2199-12-31T23:59` puis message daté du jour | le premier de la liste est celui de 2199 |
| `POST /spellbooks/{id}/spells` `{}` | `201`, `"spellList":[null]`, aucune ligne en base |
| `POST /spellbooks/import-file` sans `name` | `500` |
| `POST /accounts` avec une adresse déjà inscrite | `409 "Email is already in use"` |
| `PATCH` aventure `FINISH`, puis `NOT_STARTED` + `maxPlayer: 1` avec un joueur | `201` / `201` |
| Limite de connexion à 2 : chemin normal, puis `/accounts/%6Cogin` | `404 404 429 429`, puis `404 404 404 404` |
