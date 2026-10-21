# Suivi des points à revoir sur l'API

Audit du 20/10/2026 de `apps/api`. Chaque point a un statut :

- `[ ]` à faire
- `[~]` en cours
- `[x]` fait (avec un renvoi vers le commit ou la section « Réalisé »)

**Preuve** : *vérifié* = confirmé par un appel réel (test d'intégration jetable), *lu* = constaté dans le code.

## Déjà traité avant cet audit

- [x] Secrets sortis du code (variables d'environnement)
- [x] Mots de passe hachés avec BCrypt (au lieu de SHA-256 sans sel)
- [x] Spring Security 7 sans état, JWT, session vérifiée à chaque requête (révocation immédiate)
- [x] Refresh tokens à usage unique avec rotation et détection de rejeu
- [x] Verrouillage du compte après 5 échecs, limitation par IP (connexion, refresh, inscription, vérification)
- [x] Règle « participant » : messages, WebRTC, WebSocket, notes, personnages
- [x] Spellbooks personnels, plannings et votes, évaluations (ratings) : règle « avoir joué ensemble »
- [x] PostgreSQL, Flyway, Spring Boot 4, Java 21, renommage `user` → `account`

---

## P1 — Sécurité et exactitude (à corriger d'abord)

### 1. Rejoindre une aventure ne vérifie presque rien — *vérifié* — **fait**
- [x] Une aventure **privée** peut être rejointe par son id (`POST /adventures/{id}/join`) par n'importe quel compte connecté.
- [x] La **capacité** n'est pas respectée : 2 joueurs sur une aventure `maxPlayer=1`.
- [x] On peut rejoindre une aventure `ONGOING`, `FINISH` ou `ABORT`.
- [x] Le propriétaire peut se rejoindre lui-même comme joueur.
- [x] Le domaine modifie directement la liste Hibernate des joueurs (`getPlayers().add`).
- Deux jointures **simultanées** ne dépassent plus la capacité : verrou optimiste et rejeu (point 8).
- Fichier : `AdventureUpdaterService` (`joinAdventure`, `joinAdventureByCode`, `quitAdventure`).

### 2. Codes aléatoires prévisibles — *lu* — **fait**
- [x] Le code d'invitation (accès à une aventure privée) et le code de vérification d'e-mail utilisent `java.util.Random`.
- [x] Les passer à `SecureRandom` et garantir par une règle ArchUnit que `java.util.Random` n'est plus utilisé.
- Fichier : `RandomStringGenerator`.

### 3. Mots de passe — *vérifié* — **fait**
- [x] Le mot de passe est `trim()`é à l'inscription mais pas à la connexion : `" secret123 "` ne peut plus se connecter.
- [x] Règle trop faible : 3 à 30 caractères. BCrypt accepte 72 **octets** au maximum.
- [x] La connexion n'est pas validée (`@Valid` absent) ni bornée en taille.
- Voir aussi « À mettre au point plus tard » : vérification des mots de passe compromis.
- Fichiers : `AccountDtoMapper`, `AccountCreationRequest`, `AccountController.login`, `BcryptPasswordHasherAdapter`.

### 4. Suppression de compte — *vérifié* — **fait**
- [x] `DELETE /accounts` renvoie une **500** si le compte possède un personnage (clé étrangère).
- [x] Les aventures d'un compte supprimé restent, avec un `creator_id` qui n'existe plus (4 constatées).
- [x] Messages, notes, évaluations et disponibilités ne sont pas traités.
- [x] **Décision prise : anonymiser** (le compte reste, la personne disparaît), pour que les autres joueurs gardent les informations des personnages.
- Reste (voir « À mettre au point plus tard ») : une aventure en cours dont le MJ est parti n'a plus personne pour la gérer.

### 5. Parcours de vérification d'e-mail — *vérifié / lu* — **fait**
- [x] Le lien n'encodait pas l'adresse : un e-mail contenant `+` ne pouvait pas être validé (404). Le lien encode adresse et code (`+` → `%2B`).
- [x] Si l'envoi du mail échoue, le compte est créé mais l'inscription répondait 500 : elle réussit désormais, la personne redemande un mail.
- [x] Route pour renvoyer le mail de vérification : `POST /accounts/verification-email` (toujours 202, délai d'une minute entre deux envois, limite par IP).
- [x] Le code de vérification n'expirait jamais : 24 h, usage unique, 24 caractères, comparaison en temps constant ; un code antérieur à l'expiration n'est plus accepté.
- [x] La validation se faisait par un `GET` qui modifie l'état : `POST /accounts/verify`, le lien du mail mène à la page du site (`WEB_BASE_URL`).
- [x] Une adresse inconnue répondait 404 (énumération des adresses inscrites) : même réponse `400` qu'un mauvais code.
- [x] `@Autowired` sur champ `final` de `EmailingAdapter` supprimé (constructeur).
- Reste : l'envoi est asynchrone (point 12), un échec n'apparaît donc que dans les logs ; après un envoi raté il faut attendre la minute de délai avant de redemander.
- Le changement d'e-mail via `PATCH /accounts` est maintenant vérifié par un lien envoyé à la nouvelle adresse (point 22).
- À faire côté front : la page `/verify-email` qui lit `email` et `code` dans l'adresse et appelle `POST /accounts/verify`, et un bouton « renvoyer le mail ».

### 6. Injection HTML dans les e-mails — *lu* — **fait**
- [x] Tout ce qui vient d'une personne (pseudos, code) est échappé dans le HTML des e-mails ; les liens sont encodés ; le sujet ne peut pas contenir de saut de ligne.
- [x] Le pseudo est restreint (lettres, chiffres, `_`, `-`, `.`, 3 à 30 caractères une fois rogné, à l'inscription **et** au changement) — `@ValidPseudo`.
- [x] Le texte de l'invitation est corrigé (« vous a invité »).
- Les e-mails sont construits par `EmailContentBuilder` (testé sans serveur d'envoi) ; `EmailingAdapter` ne fait plus que l'envoi.

---

## P1 — Ajouts décidés le 20/10/2026

### 21. Confirmation des suppressions irréversibles — règle fixée par l'utilisateur — **fait**
Règle : **toute suppression irréversible demande une validation**, vérifiée par l'API (pas seulement par la boîte de dialogue du front).
- [x] Compte (`DELETE /accounts`) : retaper le **mot de passe**.
- [x] Aventure, personnage, spellbook : retaper le **nom**.
- [x] Notes du MJ et notes historiques (pas de nom) : confirmation explicite (`confirm: true`).
- [x] Contrat : `DELETE` avec un corps JSON `{ "confirmation": "..." }` (ou `{ "confirm": true }` pour les notes) ; 400 si la valeur ne correspond pas ou manque.
  Comparaison du nom exacte, sensible à la casse, après normalisation Unicode (NFC) et suppression des espaces autour.
- [x] Un mauvais mot de passe de confirmation compte dans le **verrouillage du compte** (même compteur que la connexion) et la limite par IP ;
  réponse 403 (pas 401, pour ne pas déconnecter le front), puis 429 une fois verrouillé.
- [x] La confirmation est vérifiée dans le **domaine** (`DeletionConfirmation`, `AccountPasswordVerifierService`), après le contrôle de propriété :
  un tiers reçoit 403 quelle que soit la confirmation.
- [x] **Trace d'audit de chaque suppression confirmée** (`AuditTrail`, logger dédié `AUDIT`) : compte, aventure, personnage, spellbook, note du MJ, note historique.
  - Champs : `event=deletion`, `subject` (`account`, `adventure`, `character`, `spellbook`, `adventure-note`, `historical-note`), `actorId`, `subjectId` ; l'heure est celle de la ligne et le `correlationId` de la requête s'y ajoute (point 19). Vérifié dans la vraie sortie JSON de production (format `logstash`).
  - **Identifiants seulement** : jamais le nom, le pseudo ni l'e-mail (les logs ne doivent pas garder une copie des données qu'on vient de supprimer).
  - Écrite **seulement si la base a validé la suppression** (décision du 20/10/2026) : le domaine appelle le port `AuditSpi`, et `AuditAdapter` attend le `commit` de la transaction en cours (`AfterCommit`) ; une transaction annulée ou pas encore validée ne laisse aucune ligne. Hors transaction (personnage, spellbook, notes), la suppression est déjà définitive à l'appel.
  - Jamais pour une tentative refusée (mauvaise confirmation, tiers, aventure en cours) ; une aventure supprimée donne une ligne (pas une par note ou personnage emporté).
  - Limite inverse : si le processus s'arrête entre la validation et l'écriture de la ligne, la suppression a eu lieu sans trace (la trace est volontairement après, jamais avant).
  - Le logger `AUDIT` peut être conservé plus longtemps ou envoyé ailleurs que le reste des logs (à régler côté collecteur).
  - Tests : `DeletionAuditIntegrationTest` (8), `DeletionAuditCommitIntegrationTest` (4 : transaction annulée = pas de ligne, ligne seulement après le commit, pour le compte et l'aventure), `AuditLogFormatIntegrationTest` (1) ; vérifié par mutation (trace retirée, trace avant la confirmation, trace écrite sans attendre le commit : tests rouges, code restauré).
- Repli prévu si un proxy supprime le corps d'un `DELETE` : `POST /…/deletion`.

### 22. Ré-authentification pour les actions sensibles — *vérifié* — **fait**
- [x] `PATCH /accounts` change le mot de passe ou l'e-mail avec un simple jeton d'accès (200, sans demander l'ancien mot de passe).
  Le mot de passe **actuel** est maintenant obligatoire (`currentPassword`), avec le même compteur de tentatives que la connexion.
- [x] La mise à jour est **partielle** : seuls les champs fournis changent (`email`, `pseudo`, `newPassword`).
- [x] Corrigé au passage : envoyer son propre pseudo ou e-mail était un conflit « déjà utilisé » ; changer le seul mot de passe obligeait à changer aussi le pseudo et l'e-mail.
- [x] Changer l'e-mail est **vérifié** par un lien envoyé à la nouvelle adresse (20/10/2026) :
  - `PATCH /accounts` avec un autre `email` enregistre l'adresse **en attente** (`pendingEmail`, code propre de 24 caractères, 24 h) et écrit à la nouvelle adresse ; le compte garde l'ancienne jusqu'à la confirmation. Adresse déjà prise : `409` tout de suite.
  - `POST /accounts/email/confirm` `{email, code}` (public, limité par IP comme `/verify`) : `204` et l'adresse change. Code faux, expiré, déjà utilisé ou adresse que personne n'attend : même `400`. Adresse prise entre-temps : `409`.
  - Le code d'inscription ne peut pas confirmer un changement. Redemander remplace la demande précédente. Deux comptes peuvent attendre la même adresse (rien ne la réserve) : le code désigne le bon, l'un ne bloque pas l'autre.
  - L'anonymisation efface la demande en attente. Migration `V5__pending_email_change.sql`.
  - **Rupture de contrat** : `AccountDto` gagne `pendingEmail`. Front : page `/confirm-email` qui lit `email` et `code` et appelle la route ; afficher « en attente de confirmation ».
  - Reste : l'ancienne adresse n'est pas prévenue de la demande ; pas de délai minimal entre deux demandes (le mot de passe actuel est exigé et compte dans le verrouillage).

### 23. Supprimer une aventure qui a des personnages — *vérifié* — **fait**
- [x] `DELETE /adventures/{id}` renvoyait une **500** (clé étrangère des notes) quand l'aventure avait des personnages ou des notes.
  Cause : `AdventureEntity` déclare ses personnages et notes à la fois par une table de jointure et par la clé étrangère de l'enfant (voir point 8).
- [x] La suppression emporte désormais tout ce qui appartient à l'aventure : personnages (et leurs images), notes, notes historiques, messages, planning, votes.
  Une seule transaction (`TransactionalAdventureDeleter`) ; les images sont retirées après les données, une image qui résiste n'annule pas la suppression.
- [x] **Décision prise par défaut (réversible)** : une aventure `ONGOING` ne peut pas être supprimée (409) ; il faut la terminer ou l'abandonner d'abord.
- [x] Trouvé grâce au test : `PlanningDatabaseAdapter.deleteById` était une méthode **vide** (un « delete » qui ne faisait rien).
- Les tables de jointure `adventure_*` inutilisées et les mappers récursifs ont été supprimés avec la refonte de l'agrégat (point 8) ;
  `AdventureDto` n'a plus `gameCharacters` / `adventureNotes` / `historicalNotes`.

### 24. Transfert du rôle de MJ (nouvelle fonctionnalité) — **fait**
Remplace la « limite connue » du point 4 (aventure en cours dont le MJ est parti). **Décisions de l'utilisateur (20/10/2026) :** « Inviter un MJ » = code spécial ; « Récupérer le rôle de MJ » seulement si le MJ est parti ou son compte supprimé ; vote de tous les joueurs.
- [x] **Quitter le rôle de MJ** : action explicite (`step-down`), avec retypage du nom de l'aventure ; l'aventure devient « sans MJ » (`masterVacant`) et plus personne n'a les droits de MJ.
- [x] **Inviter un MJ** : code spécial distinct du code joueur, usage unique (consommation atomique), expiration 7 jours, un seul en attente, révocable, accepté par un compte connecté ; seul le hash est stocké.
- [x] **Récupérer le rôle** : seulement si pas de MJ (parti ou compte supprimé) ; le candidat est un joueur ; vote à l'unanimité des autres joueurs (comptes supprimés exclus), un refus clôt, expiration à 7 jours, un vote à la fois (index unique), annulable par le candidat.
- [x] **À l'échange** : le nouveau MJ sort des joueurs ; l'ancien devient joueur s'il y a une place (toujours le cas après un vote) et si son compte existe, sinon il quitte (personnages conservés).
- [x] **Décisions prises (à confirmer)** : (a) les notes privées du MJ **passent au nouveau MJ** (elles n'ont pas d'auteur) ; (b) quorum = **tous** les joueurs, pas seulement les « actifs » (aucune trace d'activité n'existe) ; un joueur qui ne répond pas fait expirer le vote au bout de 7 jours, le candidat peut relancer ; (c) le transfert n'est possible que pour une aventure non commencée ou en cours.
- [x] Dépendance au point 8 : opérations rejouées dans une transaction chacune (`RetryingTransaction`), verrou optimiste sur l'aventure et sur le vote.
- [x] **Notifications par e-mail** (20/10/2026) : le MJ quitte / supprime son compte (joueurs), demande du rôle (votants), refus (candidat), abandon (votants), changement de MJ (nouveau MJ, ancien MJ, autres joueurs) ; envoyées après la validation de la transaction (`AfterCommit` dans `EmailingAdapter`), jamais à un compte supprimé, texte échappé.
- Reste : pas de message système dans le chat ni de rappel avant l'expiration d'un vote ; la trace d'audit ne couvre que les suppressions (pas les changements de MJ) ; un ancien MJ qui a quitté ne peut pas se représenter directement (il n'est plus joueur : il doit être réinvité) ; l'acceptation d'un code n'a pas de limite de débit dédiée (24 caractères aléatoires, compte connecté requis).

---

## P2 — Performance et robustesse

### 7. Listes : requêtes et pagination — *lu / testé* — **fait (listes non bornées)**
- [x] `findVisibleTo` ne fait plus `findAll()` + filtre : la visibilité est dans la requête (`public OR créateur OR joueur`), de même pour « disponibles » (public, non commencée, ni la mienne ni déjà rejointe).
- [x] Pagination (`page`, `size` ≤ 100, 20 par défaut, `400` sinon) et réponse `{content, page, size, totalElements, totalPages}` sur : les 4 listes d'aventures, les messages, les notes d'aventure, les notes historiques, les notes reçues (ratings).
- [x] Ordre stable (sinon les pages se chevauchent) : aventures par nom puis id ; messages et notes du plus récent au plus ancien.
- [x] Les notes privées du MJ sont exclues **dans la requête** pour les joueurs : le total ne les compte pas.
- [x] N+1 sur les joueurs d'une page d'aventures supprimé (`@BatchSize`).
- Non paginées volontairement : personnages (par aventure / par compte), grimoires, disponibilités — bornées par nature (à revoir si on plafonne leur création).
- Les mappers des personnages chargeaient l'aventure de chacun (N+1, mesuré : 2 requêtes de plus par aventure) ; corrigé par un `@EntityGraph` (voir « Réalisé », lot B).
- Les `findAll()` non bornés du `PersistenceSpi` générique et de ses adaptateurs, jamais utilisés, sont supprimés (lot A du tri des « Reste »).

### 8. Concurrence — *lu / testé* — **fait**
- [x] Verrou optimiste : `@Version` sur `AdventureEntity` (colonne `version`). Une écriture faite à partir d'une lecture périmée est refusée (`AdventureConcurrentModificationException` → `409`).
- [x] Rejoindre et quitter une aventure sont rejoués (10 tentatives) à partir d'une lecture fraîche : personne n'est perdu, la capacité n'est jamais dépassée (8 joueurs simultanés sur 3 places → exactement 3).
- [x] Modifier statut/visibilité/nom (MJ) n'est pas rejoué : le MJ reçoit un `409`.
- [x] L'agrégat `Adventure` est réduit à ses propres données : plus de listes de personnages/notes dans le domaine, l'entité et le DTO (elles avaient leurs routes dédiées) ; mappers plus récursifs.
- [x] Tables de jointure `adventure_game_characters`, `adventure_historical_notes`, `adventure_adventure_notes` supprimées (jamais remplies).
- Reste : la version n'est pas exposée dans l'API, donc deux **modifications manuelles** simultanées d'un même écran restent « dernier arrivé gagne » (si besoin : `ETag`/`If-Match`).
- N+1 des mappers : corrigé (point 7).

### 9. Configuration JPA — *lu / testé* — **fait**
- [x] `spring.jpa.open-in-view=false` (dans `application.properties`, pour tous les profils) : plus de session ouverte pendant l'écriture de la réponse, et plus d'avertissement au démarrage.
- [x] Deux fuites trouvées en le désactivant (500 « Cannot lazily initialize collection ») : les mappers des grimoires (`spellList`) et des plannings (`propositionDates`) passaient la collection paresseuse de l'entité au domaine. Ils la copient maintenant, comme les autres mappers.
- Garde-fou : `PersistenceConfigurationIntegrationTest` vérifie la propriété ; les tests des grimoires et plannings (`ParticipantRuleIntegrationTest`) échouent si une collection paresseuse sort à nouveau des adaptateurs.

### 10. Validation et uploads — *lu / testé* — **fait**
- [x] `@Valid` ajouté : `PUT /planning`, `PUT /planning/availability`, `POST /adventures/invitations/accept`, les trois routes WebRTC.
- [x] Limites ajoutées : code d'invitation (50), jeton de rafraîchissement (200), identifiants (36), dates d'un planning/disponibilité (1 à 100, sans élément nul), signaux WebRTC (type parmi `offer|answer|candidate`, contenu 20 000, `adventureId` obligatoire).
- [x] Textes obligatoires vides d'espaces refusés (`@NotBlank`) ; en modification, un texte présent ne peut pas être vide d'espaces (`Patterns.NOT_BLANK`).
- [x] Image : le format est reconnu par le contenu (signature PNG/JPEG), plus par le type annoncé ; fichier vide, tronqué, GIF, HTML… → `400` (c'était un `500` ou, pire, accepté) ; l'extension suit le format (un JPEG était stocké en `.png`) ; taille lue bornée à 5 Mo ; la taille envoyée à MinIO est celle du contenu lu (et non `available()`).
- [x] Limites explicites : fichiers 5 Mo (`413`), requête multipart 6 Mo, corps JSON 512 Ko (`RequestBodyLimitFilter`, y compris corps en `chunked`, `413`).
- **Trouvé en testant** : (a) `PATCH` partiel d'aventure, de personnage, de note : un champ absent écrasait la valeur par `null` → `500` ou valeurs perdues ; désormais un champ absent ne change rien (et le code postal d'une aventure est enfin modifiable) ; (b) **faille** : le MJ d'une aventure A pouvait modifier les notes (`adventure-notes`, `historical-notes`) d'une aventure B en nommant A dans la requête ; le droit vient désormais de l'aventure de la note.
- Messages WebSocket (STOMP) : un client ne peut rien publier (`SEND` refusé par l'intercepteur) et une trame de plus de 16 Ko coupe la connexion. Dates bornées aux années 1900 à 2200 (`@ValidDate`) : une année comme `+999999999` finissait en `500` à l'écriture en base.
- Reste : pas de recompression/redimensionnement des images ; le contenu d'une image valide n'est pas analysé en profondeur (pas d'antivirus).

### 11. MinIO — *lu / testé* — **fait (décision : bucket privé, images servies par l'API)**
- [x] **Décision prise** : le bucket n'est plus jamais public. Les images sont servies par `GET /game-characters/{id}/picture` avec la règle « propriétaire du personnage ou participant de l'aventure ». Une image de personnage d'une aventure privée n'est plus lisible par quiconque connaît l'adresse.
- [x] Au démarrage : le bucket est créé s'il manque et toute politique publique existante est supprimée (`deleteBucketPolicy`) ; si MinIO est injoignable, l'erreur est journalisée (`ERROR`) et l'application démarre.
- [x] `picturePath` (chemin interne `account/<id>/…`) n'est plus exposé : le personnage renvoie `pictureUrl`.
- [x] Lecture de l'image : type du format réel, `Cache-Control: private`, `inline`, `nosniff` ; une panne du stockage répond `404` sans détail ; la réponse MinIO est fermée après lecture (fuite de connexion corrigée).
- Pourquoi pas des URLs signées : elles auraient demandé que MinIO reste joignable depuis le navigateur et laissé un lien valable pour quiconque le reçoit pendant sa durée de vie. Passer par l'API coûte de la bande passante (images de profil : négligeable) ; à revoir si les images deviennent lourdes (alors URLs signées de quelques minutes).
- Reste : le front doit charger l'image avec le jeton (voir README) ; les anciens objets stockés en `.png` pour un JPEG (avant le point 10) sont servis comme PNG.

### 12. Envoi de mail synchrone — *lu / testé* — **fait**
- [x] `EmailingAdapter` rédige le mail dans la requête mais l'envoie dans une **boîte d'envoi** (`mailExecutor` : 2 threads, file de 100) : un SMTP lent ne bloque plus l'inscription ni l'invitation.
- [x] Une panne SMTP est journalisée, jamais remontée à l'appelant ; file pleine : le mail est abandonné (avertissement) sans bloquer ni échouer la requête.
- [x] Délais SMTP bornés (connexion 5 s, lecture/écriture 10 s) pour qu'un serveur muet ne garde pas un thread indéfiniment.
- Conséquence : une invitation dont l'envoi échoue ne renvoie plus d'erreur à l'appelant (avant : 500) ; le mail est perdu au redémarrage (pas de boîte d'envoi persistante).
- Tests : `EmailingAdapterTest` (3) ; vérifié en remettant l'envoi synchrone : 2 tests échouent, puis le code est restauré.

## P2 — Vie privée

### 13. Énumération d'adresses e-mail — *lu / testé* — **fait**
- [x] `GET /accounts/email/{email}` supprimée : plus aucun moyen de savoir si une adresse est inscrite ni de retrouver un pseudo à partir d'elle.
- [x] **Décision prise : inviter par pseudo uniquement.** `POST /adventures/{id}/invitations/{pseudo}` (avant : `/sendInvitationCode/{accountId}`) ; pseudo inconnu ou compte anonymisé : 404 ; seul le MJ peut inviter.
  Rupture de contrat : le front doit passer le pseudo dans l'URL (l'identifiant n'est plus accepté).
- [x] **Le pseudo désigne un seul compte, casse comprise** : index unique sur `lower(pseudo)` (migration `V2__pseudo_unique_ignoring_case.sql`),
  recherche par pseudo insensible à la casse (inscription, changement de pseudo, connexion, invitation) ; « Gandalf » et « gandalf » sont le même compte (409 à l'inscription).
  Changer uniquement la casse de son propre pseudo reste permis.
- Attention au déploiement : la migration échoue si la base contient déjà deux pseudos qui ne diffèrent que par la casse (à vérifier avant de la passer).
- Reste : `GET /accounts/pseudo/{pseudo}` permet toujours de savoir si un pseudo existe (voulu : le pseudo est public).
- Tests : `PseudoInvitationIntegrationTest` (10).

---

## P3 — Architecture et qualité

- [x] 14. `@Autowired` sur champ `final` (`EmailingAdapter`) : retiré (point 5). `printStackTrace` dans `MinioConfig` : retiré (point 11).
  Identifiants : **décision de conserver** `UUID.randomUUID()` comme valeur par défaut des modèles du domaine (pas de service `IdGenerator` : aucun bug constaté, aucun test n'a besoin d'identifiants prévisibles, et ce serait 14 modèles + 10 mappers + la plupart des tests à toucher).
  Retirée seulement la **double génération** : les 10 mappers DTO de création (`.id(UUID.randomUUID())`) refaisaient ce que le modèle fait déjà. Si l'on veut un jour des UUID v7, on changera la valeur par défaut des modèles.
- [x] 15. Code mort supprimé :
  - `PlanningRepository.findAllByPropositionDate` (requête native sur une table `planning_table` qui n'existe pas) et `findAllById(UUID)` ;
  - `PlanningPersistenceSpi.findAll(UUID)`, dont l'implémentation renvoyait `null` ;
  - `AccountFinderApi.findAll` et `MessageFinderApi.findAll` (et leur test), exposés par aucune route ;
  - `AccountRole` et `Account.roles` (jamais renseignés) : `GET /accounts/me`, `POST /accounts` et `PATCH /accounts` ne renvoient plus `roles: null`. Garde-fou : `ProfileShapeIntegrationTest`.
  - Les `findAll()` génériques restants ont été supprimés ensuite (voir point 7).
- [x] 16. Format d'erreur unique : `ProblemDetail` (RFC 9457, successeur de la 7807), `Content-Type: application/problem+json`.
  - Corps : `{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "<explication>", "instance": "/dndgate-api/..." }`. `detail` remplace l'ancien `message` (rupture de contrat ; aucun client n'existe encore).
  - Validation (400) : `detail` générique et `errors: [{ "field", "message" }]` (avant : une liste nue).
  - Même format pour tout : handlers métier, erreurs du framework (route inconnue 404, méthode 405, corps illisible 400…, via `ResponseEntityExceptionHandler`), filtres (429 limite par IP, 413 corps trop gros), sécurité (401 avec l'en-tête `WWW-Authenticate` conservé, 403).
  - **Handler générique** (`FallbackErrorHandler`) : toute exception imprévue donne une 500 sans détail (« An unexpected error occurred ») ; la cause est dans les logs, jamais dans la réponse. Les exceptions de sécurité lui échappent (relancées).
  - **Ordre des advices** : les handlers par domaine sont en `HIGHEST_PRECEDENCE`, le générique en `LOWEST_PRECEDENCE` ; sinon le générique masquerait les spécifiques (mutation vérifiée : un 429 devenait 500).
  - Trouvé au passage : `AvailabilityErrorHandler` n'avait pas `@RestControllerAdvice` (jamais enregistré) ; corrigé.
  - Trouvé au passage : avec un corps chunké, Tomcat coupait la connexion et tronquait le 413 d'une image trop grande ; `server.tomcat.max-swallow-size=8MB` (au-dessus des 6 Mo acceptés).
  - Tests : `ProblemDetailIntegrationTest` (9). Reporté dans `contracts/openapi.yaml` (schéma `Problem`, point 18).
- [x] 17. Nommage et langue — **fait** (20/10/2026) :
  - **`functionnal` → `functional`** (tous les paquets du domaine, en `main` et en `test`) ; **`Disponibility` → `Availability`** partout (classes, services, ports, entités, DTO, erreurs). **Rupture de contrat** : la route `/planning/disponibility…` devient `/planning/availability…`. Base : migration `V4__availability_naming.sql` renomme les deux tables, les colonnes, la clé primaire, la clé étrangère et l'index (V1 n'est pas modifié).
  - **Politique de langue** : code, commentaires, journaux et messages de l'API en **anglais** ; le seul français est ce qu'une personne lit dans un **e-mail**. Les derniers textes français du code (message d'erreur d'écriture en base, validation du mot de passe, confirmation de suppression, deux commentaires) sont traduits ; `LanguagePolicyTest` échoue si une lettre accentuée réapparaît dans `src/main/java`.
  - **E-mails** : les textes ne sont plus codés en dur dans `EmailContentBuilder` mais dans `src/main/resources/mail/emails_fr.properties` (UTF-8, un texte par clé, `{valeur}` remplacée en une seule passe par `EmailTemplates` : une valeur qui ressemble à un `{repère}` n'est jamais remplacée une seconde fois). L'échappement HTML reste fait par le builder. Pour ajouter une langue : un fichier `emails_<langue>.properties` avec les mêmes clés (et choisir la langue par compte, qui n'a pas encore de préférence).
  - Tests ajoutés : `EmailTemplatesTest` (5), `LanguagePolicyTest` (1), `NamingIntegrationTest` (2). Total : 568.
  - Routes renommées ensuite (lot B, voir le tableau du README) ; reste : messages de validation (`@Size`…) laissés aux textes par défaut de Hibernate Validator (anglais).

## P3 — Livraison et exploitation

- [x] 18. **OpenAPI** (springdoc 3.1.1) exporté dans `contracts/openapi.yaml` pour générer le client Angular (voir `contracts/README.md`).
  - Le fichier est produit par l'API et **vérifié par un test** (`OpenApiContractIntegrationTest`) : la CI échoue si une route change sans que le contrat soit mis à jour (`-Dopenapi.update=true` le réécrit).
  - Le test garantit aussi : `operationId` uniques et lisibles (80 opérations), résumé et groupe par opération, schéma d'erreur `Problem` partout, réponses en JSON, aucune route ambiguë, et **une opération est déclarée publique exactement quand l'API ne demande pas de jeton** (chaque route est appelée sans jeton).
  - `OpenApiConfig` : jeton `bearerAuth` (JWT), routes publiques lues dans `SecurityConfig.PUBLIC_POST_ROUTES` (une seule liste), serveur relatif (le chemin de contexte).
  - **Non servi en production** (`springdoc.api-docs.enabled=false`, vérifié par `ProductionConfigurationTest`) ; en développement `GET /dndgate-api/v3/api-docs`. Pas d'interface Swagger.
  - Validé par un linter indépendant (Redocly) : 0 erreur ; restent des avertissements de style (`4XX` explicites, licence).
  - Reste : la partie JSON d'un `multipart/form-data` n'a pas d'`encoding`, les `DELETE` portent un corps, les identifiants sont des `string` (pas `format: uuid`) : à régler au premier essai de génération du client.
- [x] 19. Exploitation et livraison :
  - **Sondes et métriques** (actuator) sur un **port de gestion séparé** (`MANAGEMENT_PORT`, 4001 par défaut) que le reverse proxy ne doit **pas** publier : `/actuator/health` (+ `liveness`, `readiness`) et `/actuator/prometheus`, sans jeton (chaîne de sécurité dédiée, limitée aux endpoints exposés). Rien d'autre n'est exposé (`env`, `beans`, `heapdump`… refusés) ; la santé ne détaille rien. Sur le port public, ces routes n'existent pas.
  - `readiness` = base de données joignable ; le serveur de mail n'en fait pas partie (`management.health.mail.enabled=false`) : une panne de mail ne doit pas sortir l'API du service.
  - **Identifiant de corrélation** : `CorrelationIdFilter` (premier filtre) donne à chaque requête un `correlationId` présent sur chaque ligne de log (MDC) et dans l'en-tête `X-Request-Id` de la réponse, erreurs et refus compris. L'identifiant envoyé par le front est conservé s'il est inoffensif (`[A-Za-z0-9._-]{1,64}`), sinon remplacé (pas de saut de ligne ni de balise dans les logs).
  - **Logs structurés** : un objet JSON par ligne en production (`logging.structured.format.console=logstash`), identifiant inclus ; en dev, l'identifiant s'affiche entre crochets.
  - **Dockerfile** : utilisateur non root (`app`), `HEALTHCHECK` sur la sonde de vie (jamais la readiness : une panne de base ne doit pas redémarrer le conteneur), `MaxRAMPercentage=75`. Vérifié en vrai : image construite, conteneur `healthy` avec une base, logs JSON.
  - **CI** : JaCoCo (rapport pendant `verify`, couverture mesurée **90,5 % des lignes**, 89,9 % des branches), totaux dans le résumé de l'exécution et rapport en artefact ; `.github/dependabot.yml` (Maven, Docker, GitHub Actions, hebdomadaire) pour l'analyse continue des dépendances.
  - Tests : `CorrelationIdFilterTest` (5), `ObservabilityIntegrationTest` (9) ; filtre vérifié par mutation (validation et nettoyage du MDC retirés : tests rouges).
  - **Seuil de couverture : 90 % des lignes** (décision du 20/10/2026), appliqué par `jacoco:check` pendant `verify` : le build échoue en dessous (vérifié avec un seuil à 0,99). Mesuré : 90,5 %, donc **marge de 0,5 point** : tout nouveau code doit venir avec ses tests. Modifiable par `-Dcoverage.minimum=0.95` ou la propriété `coverage.minimum` du `pom.xml`.
  - Fait ensuite (lot A du tri des « Reste ») : `correlationId` dans le corps de chaque erreur (même valeur que `X-Request-Id`, y compris pour les erreurs écrites par un filtre ou par la sécurité) ; `docker-compose.prod.yml` (API + base, variables obligatoires vérifiées, port de gestion jamais publié, `healthcheck` et attente de la base) ; revue des dépendances sur les pull requests (`.github/workflows/dependency-review.yml`, échoue à partir de la gravité haute ; demande le graphe de dépendances du dépôt, et sur un dépôt privé GitHub Advanced Security).
  - Reste : journal d'accès HTTP.
- [x] 20. Tests manquants ajoutés (31 tests) :
  - `AdventureUpdaterServiceQuitTest` (5) : départ d'un joueur, liste d'origine non modifiée, non-joueur, aventure inconnue, échec d'enregistrement. (`join` avait déjà `AdventureUpdaterServiceJoinTest`.)
  - `AdventureUpdaterServiceUpdateTest` (8) : modification partielle, tous les champs, identité et joueurs jamais pris de la requête, MJ seul pour `update`, `updateVisibility` et `updateStatus`.
  - `GameCharacterCreatorServiceTest` (10) : propriétaire, aventure et chemin de l'image, type réel (PNG/JPEG), fichier qui n'est pas une image, limite de 5 Mo (5 Mo accepté, au-delà refusé), flux illisible, non-participant, panne de stockage (aucun personnage créé), échec d'enregistrement.
  - `SpellBookCreatorServiceTest` (3) : le grimoire appartient à l'appelant quoi que dise la requête, appelant inconnu, échec d'enregistrement.
  - `FlywayMigrationIntegrationTest` (5, vraie base PostgreSQL, sans Spring) : base vide migrée dans l'ordre sans trou, deuxième passage sans effet, **base à la version 1 avec des données** mise à jour sans perte, unicité du pseudo après `V2`, et **deux pseudos ne différant que par la casse : la migration s'arrête, rien n'est perdu, la base reste à la version 1** (le risque noté au point 13).
  - Vérifié par mutation : 8 défauts injectés dans les services (suppression du retrait du joueur, du contrôle d'appartenance, du `withCity` partiel, de la vérification du MJ, de la détection du format, du propriétaire, de la limite de taille…) et 3 dans les migrations (migration qui efface des données, index oublié, trou de version) : les tests concernés échouent à chaque fois, puis le code est restauré.
  - Couverture après ces tests : **91,0 % des lignes** (seuil 90 %), 90,2 % des branches.
  - Piège noté : Maven ne supprime pas de `target/classes` un script de migration retiré ; un `V4` périmé fait échouer Flyway (« more than one migration with version 4 ») jusqu'à un `clean`.

## À mettre au point plus tard

- Une évaluation visant un compte anonymisé reste possible si le compte a joué avec le noteur ; à interdire si cela gêne.
- Cache court (5 s) avec éviction sur la vérification de session, si la charge le justifie.
- Vérification des mots de passe compromis (API HIBP en k-anonymat).
- Liste des sessions actives exposée à l'utilisateur (révoquer un appareil oublié).
- Fermer les WebSockets ouverts quand leur session est révoquée.

---

## Réalisé dans cet audit

### 20/10/2026 — points 1, 2 et 3

- **Point 1** : `AdventureUpdaterService` — `join` unique pour les deux chemins ; aventure privée invisible par id (404), statut `NOT_STARTED` exigé, capacité respectée,
  propriétaire déjà participant ; listes de joueurs copiées au lieu d'être modifiées en place (jointure et départ).
  Nouvelles erreurs 409 : `AdventureFullException`, `AdventureNotJoinableException`. Règles portées par `Adventure` (`isFull`, `isOpenToNewPlayers`).
- **Point 2** : `RandomStringGenerator` utilise `SecureRandom` sur un alphabet explicite ; règle ArchUnit `RandomnessRulesTest`.
- **Point 3** : contrainte `@ValidPassword` (8 caractères minimum, 72 octets maximum, pas que des espaces), plus de `trim()` du mot de passe,
  connexion validée (`@Valid`, tailles bornées) et pseudo rogné à la connexion, `BcryptPasswordHasherAdapter` sans exception sur un mot de passe trop long.
- **Tests ajoutés** : `AdventureUpdaterServiceJoinTest` (10), `PasswordValidatorTest` (7), `BcryptPasswordHasherAdapterTest` (5), `RandomStringGeneratorTest` (5),
  `RandomnessRulesTest` (1), `AdventureJoinIntegrationTest` (9), `PasswordPolicyIntegrationTest` (9).
  Chaque correction a été vérifiée en remettant le défaut d'origine : 6 tests échouent, puis le code est restauré.

### 20/10/2026 — point 4 (suppression de compte = anonymisation)

- `DELETE /accounts` appelle `AccountAnonymizerApi` (l'ancien `AccountDeleterApi` est supprimé). Toute l'opération est **une seule transaction**
  (décorateur `TransactionalAccountAnonymizer` dans `bootstrap`, le domaine ne connaît pas les transactions) : un compte n'est jamais à moitié anonymisé.
- Effacé : pseudo (`deleted-<12 caractères de l'id>`), e-mail (`<id>@anonymized.invalid`), mot de passe (remplacé par un hash d'un secret aléatoire que personne ne connaît),
  activation, code de vérification, verrouillage, **sessions**, **spellbooks**.
- Conservé, rattaché au même identifiant désormais anonyme : personnages, messages, notes, disponibilités, évaluations données et reçues.
- Aventures du compte : celles qui n'ont pas démarré sont **annulées et masquées** ; les autres ne changent pas.
  Dans les aventures où il est joueur : sa place est libérée si l'aventure n'a pas démarré, conservée sinon (historique).
- `PublicAccountDto` expose `anonymized` pour que les pages affichent « compte supprimé » ; plus d'invitation possible vers un compte anonyme.
- `AccountVerifierService` ne plante plus quand le code de vérification est absent (NPE).
- Colonne `anonymized_at`.
- **Tests ajoutés** : `AccountAnonymizerServiceTest` (10), `AccountAnonymizationIntegrationTest` (16), `AccountAnonymizationAtomicityIntegrationTest` (1).
  Vérifié en retirant les protections (transaction, annulation des aventures, libération des places, suppression des spellbooks, e-mail anonymisé,
  filtre d'invitation) : les tests concernés échouent, puis le code est restauré.

### 20/10/2026 — étape 1 : points 21, 22 et 23

- `DeletionConfirmation` (domaine) : comparaison du nom (espaces, casse, accents) et confirmation explicite ; erreurs `DeletionNotConfirmedException` (400), `WrongPasswordException` (403), `AdventureInProgressException` (409).
- `AccountPasswordVerifierService` : une seule vérification du mot de passe pour la connexion, la suppression du compte et la modification du compte,
  avec verrouillage partagé. Les compteurs d'échecs sont écrits dans **leur propre transaction** (`REQUIRES_NEW`) : le refus d'une opération qui annule sa transaction ne les efface pas.
- `AccountAnonymizerApi.anonymize(accountId, password)`, `AccountUpdaterApi.update(accountId, currentPassword, AccountChanges)`.
- Dépôts : suppressions groupées avec `flushAutomatically` et `clearAutomatically` (sinon Hibernate garde en mémoire des enfants qui pointent vers un parent supprimé).
- **Tests ajoutés** : `DeletionConfirmationTest` (8), `DeletersConfirmationTest` (8), `AccountPasswordVerifierServiceTest` (5), `AdventureDeleterServiceTest` (8, réécrit),
  `AccountUpdaterServiceTest` (9, réécrit), `DeletionConfirmationIntegrationTest` (15), `AccountUpdateIntegrationTest` (11).
  Mutations vérifiées (confirmation par nom, par drapeau, mot de passe de suppression et de modification, `REQUIRES_NEW`, nettoyage des enfants, méthode vide du planning, statut `ONGOING`) :
  20 tests échouent, puis le code est restauré.

### 20/10/2026 — points 5 et 6 (vérification d'e-mail et contenu des e-mails)

- Domaine : `Account.verificationExpiresAt` et `hasValidVerificationCode` (expiration + comparaison en temps constant) ; `AccountVerifierService` (void, `InvalidVerificationCodeException` → 400) ;
  `AccountVerificationResenderService` (délai entre deux envois déduit de la date d'expiration) ; `AccountCreatorService` tolère l'échec d'envoi.
- Serveur : colonne `verification_expires_at` ; `EmailContentBuilder` ; `EmailingAdapter` réduit à l'envoi, l'exception garde sa cause.
- API : `POST /accounts/verify`, `POST /accounts/verification-email` ; l'ancien `GET /accounts/verify` est supprimé ; limites par IP pour les deux.
- **Tests ajoutés** : `AccountVerificationTest` (22, en 4 groupes), `EmailContentBuilderTest` (9), `ValidPseudoTest` (8), `EmailVerificationIntegrationTest` (22) ;
  `RateLimitIntegrationTest` couvre aussi le renvoi. Mutations vérifiées (expiration, code quelconque, panne de mail, délai de renvoi, compte vérifié, échappement HTML,
  encodage d'URL, saut de ligne du sujet, format du pseudo) : 27 tests échouent, puis le code est restauré.
- Deux défauts de ma première version trouvés à la relecture : `UriUtils.encodeQueryParam` laisse passer le `+` (il aurait été lu comme une espace),
  et la taille du pseudo était comptée avant le rognage.

### 20/10/2026 — point 8 (concurrence et forme de l'agrégat)

- `Adventure.version` + `@Version` ; `AdventureDatabaseAdapter` écrit avec `saveAndFlush` pour détecter le conflit dans l'appel et le traduire en exception du domaine.
- `AdventureUpdaterService` rejoue `join`/`quit` (`retryOnConcurrentChange`, 10 tentatives).
- `AdventureDto` n'a plus `gameCharacters`, `historicalNotes`, `adventureNotes` : **changement de contrat** pour le front (utiliser les routes par aventure).
- **Tests ajoutés** : `AdventureUpdaterServiceConcurrencyTest` (6), `AdventureConcurrencyIntegrationTest` (9, vraies requêtes parallèles contre PostgreSQL). Total : 348.
- Mutations : sans rejeu, 8 tests échouent ; sans `@Version`, la création échoue (colonne `NOT NULL`) — le test d'écriture périmée couvre le reste.

### 20/10/2026 — migrations compactées

- Les sept migrations ont été fusionnées en un seul `V1__init.sql` (aucune donnée à conserver). Schéma vérifié identique à celui des sept migrations par comparaison de `pg_dump`.
- **Conséquence** : toute base déjà créée avec les anciennes migrations doit être recréée (l'historique Flyway ne correspond plus). Les futures évolutions repartent de `V2`.

### 20/10/2026 — point 7 (requêtes et pagination)

- Domaine : `Page`, `PageRequest` (bornes), `InvalidPageRequestException` → 400 ; serveur : `PageMapper` ; client : `PageDto`.
- **Changement de contrat** : les 8 routes ci-dessus renvoient désormais l'enveloppe paginée au lieu d'un tableau.
- **Tests ajoutés** : `PaginationIntegrationTest` (11), `PageTest` (4), `AdventureFinderServiceTest` réécrit. Total : 364.
- Mutations vérifiées (ordre supprimé, filtre des notes privées supprimé, exclusion des aventures déjà rejointes supprimée) : 3 tests échouent, code restauré.
- Deux tests existants lisaient la première page d'une liste partagée entre tests : ils comparent maintenant des totaux avec la base.

### 20/10/2026 — point 9 (open-in-view)

- Propriété ajoutée, deux mappers corrigés (`SpellBookEntityMapper`, `PlanningEntityMapper`). Total : 365 tests.

### 20/10/2026 — point 10 (validation et uploads)

- Domaine : `PictureFormat`, `InvalidPictureException` (→ 400), `GameCharacterCreatorApi.create` ne reçoit plus le type annoncé. Client : `Patterns`, `Text.trimmed`, `WebRTCMessage` validé, `RequestBodyLimitFilter`, réponses `413`.
- **Tests ajoutés** : `InputValidationIntegrationTest` (14), `PartialUpdateIntegrationTest` (6). Total : 385. Les images de test sont de vrais en-têtes PNG/JPEG.
- Mutations vérifiées (détection d'image, droit des notes, `@Valid` WebRTC, patch partiel) : 6 tests échouent, code restauré. La limite de corps est testée par les deux chemins (taille annoncée, corps en `chunked`) : retirer l'un des deux ne casse pas le test, l'autre prend le relais.

### 20/10/2026 — point 11 (MinIO)

- Domaine : `GameCharacterPicture`, `GameCharacterFinderApi.findPicture`, `PictureFormat.fromPath`. Client : route `/game-characters/{id}/picture`, `pictureUrl`. Bootstrap : `MinioConfig` rend le bucket privé.
- **Changement de contrat** : `picturePath` remplacé par `pictureUrl` ; l'image doit être chargée avec le jeton.
- **Tests ajoutés** : `PictureAccessIntegrationTest` (8), `MinioConfigTest` (3).
- Mutations vérifiées (plus de contrôle d'accès sur l'image, politique de nouveau publique) : 4 tests échouent, code restauré.

### 20/10/2026 — point 24 (transfert du rôle de MJ)

- Migration `V3__game_master_transfer.sql` (colonne `master_vacant_since`, tables `master_claims`, `master_claim_votes`, `master_invitations`) ; `V2` appartient à un autre changement (pseudo unique sans casse).
- Domaine : `Adventure.masterVacantSince`, `isMaster`/`isMasterVacant`/`isTransferable` ; `MasterStepDownService`, `MasterInvitationService`, `MasterClaimService`, `MasterHandOverService` ; ports `MasterStepDownApi`, `MasterInvitationApi`, `MasterClaimApi`, `MasterClaimPersistenceSpi`, `MasterInvitationPersistenceSpi`. Anonymisation : une aventure en cours devient « sans MJ ».
- Bootstrap : `RetryingTransaction`, `TransactionalMasterTransfer`. Client : `AdventureMasterController` ; `AdventureDto.masterVacant`.
- **Tests ajoutés** : 36 d'intégration (`MasterTransferIntegrationTest`, dont deux scénarios parallèles) et 36 unitaires. Total : 482.
- Mutations vérifiées (droits conservés sans MJ, unanimité, usage unique du code, mise « sans MJ » à la suppression du compte, rejeu) : 17 tests échouent, code restauré.

### 20/10/2026 — notifications du transfert de MJ

- Domaine : `MasterTransferNotifier` (qui est prévenu de quoi), `MasterTransferEvent`, `MasterTransferMessage`, `EmailingSpi.sendMasterTransferEmail`. Serveur : `EmailContentBuilder.masterTransfer`, `AfterCommit` ; `EmailingAdapter` attend la validation de la transaction en cours (cela vaut aussi pour les autres e-mails envoyés dans une transaction).
- L'anonymisation prévient les joueurs d'une aventure en cours dont le MJ supprime son compte.
- **Tests ajoutés** : `MasterTransferNotifierTest` (8), `EmailingAdapterTransactionTest` (3), `EmailContentBuilderTest` (+4), `MasterTransferNotificationIntegrationTest` (8) et assertions dans les tests des services. Total : 506.
- Mutations vérifiées (envoi hors transaction, ancien MJ non prévenu, échappement, notification du changement supprimée) : 7 tests échouent, code restauré.

### 20/10/2026 — points 22 et 5 (changement d'e-mail vérifié)

- Domaine : `Account.pendingEmail` / `pendingEmailCode` / `pendingEmailExpiresAt` et `hasValidPendingEmailCode` ; `AccountUpdaterService` (demande + mail) ; `AccountEmailChangeConfirmerService` / `AccountEmailChangeConfirmerApi` ; `AccountPersistenceSpi.findAllByPendingEmail` ; `EmailingSpi.sendEmailChangeEmail`.
- Serveur : colonnes `pending_email*` (V5), `EmailContentBuilder.emailChange`, texte `email-change.*` dans `emails_fr.properties`. Client : `POST /accounts/email/confirm` (public), règle de limite `confirm-email`, `AccountDto.pendingEmail`.
- **Tests ajoutés** : `AccountEmailChangeTest` (17), `EmailChangeIntegrationTest` (14), `EmailContentBuilderTest` (+2), `AccountAnonymizerServiceTest` (+1), `RateLimitIntegrationTest` (+1) ; `AccountUpdateIntegrationTest` et `ProfileShapeIntegrationTest` adaptés. Total : 616.
- Mutations vérifiées (pas de contrôle de disponibilité à la confirmation, adresse appliquée tout de suite, code sans expiration, demande en attente conservée à l'anonymisation) : 4 défauts attrapés, code restauré.

### 20/10/2026 — lot B du tri des « Reste » et point 18

- **Routes** : 19 routes renommées en kebab-case, sans verbe (tableau dans le README). `RouteNamingIntegrationTest` : toute route en kebab-case, aucune ne commence par un verbe, et les anciens chemins n'existent plus. Une ambiguïté repérée par le contrat (`/planning/adventure/{id}` et `/planning/{planningId}/availability` pouvaient désigner la même URL) est levée en passant à `GET /planning?adventureId=`. Une route morte (`@GetMapping` en commentaire) supprimée. **Rupture de contrat** pour le front.
- **Noms des opérations** : méthodes mal nommées corrigées pour que le client généré soit lisible (`changeStatus` devient `updateAdventureStatus`, `invite` devient `inviteMaster`, `getMyProfil` devient `getMyProfile`…) ; deux méthodes avaient le même nom (`deleteSpellBookById` pour supprimer un personnage, `createAdventure` pour modifier une aventure).
- **N+1** : `QueryCountIntegrationTest` mesure les requêtes SQL (statistiques Hibernate) pour 2 puis 6 aventures : 7 puis 15 avant, identique après `@EntityGraph(attributePaths = "adventure")` sur `findByOwner_Id` et `findByAdventure_Id`. Les listes de notes et de messages ne sont pas concernées (une seule aventure par requête).
- **OpenAPI** : voir le point 18. Dépendance `springdoc-openapi-starter-webmvc-api`, `OpenApiConfig`, `@Tag` sur les 11 contrôleurs.
- **Tests ajoutés** : `RouteNamingIntegrationTest` (3), `QueryCountIntegrationTest` (2), `OpenApiContractIntegrationTest` (10), `ProductionConfigurationTest` (1) ; les appels des tests existants migrés vers les nouvelles routes.
- Mutations vérifiées (routes publiques déclarées sécurisées, réponse d'erreur retirée, `operationId` en double, contrat servi en production, N+1 rétabli) : à chaque fois le test attendu échoue, code restauré.

### 20/10/2026 — lot A du tri des « Reste »

- **Dates** : `@ValidDate` (années 1900 à 2200, `LocalDate` et `LocalDateTime`) sur les dates des plannings, disponibilités, notes (création et modification) et la date d'envoi d'un message. Avant : `+999999999-12-31` passait la validation et finissait en `500` à l'écriture.
- **WebSocket** : `setMessageSizeLimit(16 Ko)` ; le transport coupe la connexion avant que la trame soit lue. L'audit du `SEND` n'était pas à faire : l'intercepteur refuse déjà toute publication d'un client.
- **Erreurs** : `CorrelationIdProblemAdvice` (réponses des contrôleurs et du framework) et `ProblemJson` (filtres et sécurité) ajoutent `correlationId` au corps.
- **Code mort** : `findAll()` retiré de `PersistenceSpi`, `AvailabilityPersistenceSpi` et des 10 adaptateurs (aucun appelant, aucune route).
- **Livraison** : `docker-compose.prod.yml`, `dependency-review.yml`, `.env.example` (`DOCKER_USERNAME`), README.
- **Tests ajoutés** : `DateBoundsIntegrationTest` (6), `WebSocketConfigTest` (1, vérifie la limite elle-même), `WebSocketSecurityIntegrationTest` (+1, comportement : un client ne voit pas de différence entre une trame coupée par le transport et une publication refusée, d'où le test de configuration), `ProblemDetailIntegrationTest` (+1 et le `correlationId` vérifié dans chacun des cas d'erreur existants). `EmailVerificationIntegrationTest` compare maintenant les corps d'erreur sans le `correlationId`, qui change à chaque requête.

### 20/10/2026 — point 17 (nommage et langue)

- Renommage mécanique `functionnal` → `functional`, `Disponibility` → `Availability` (332 fichiers modifiés, 20 renommés) ; sauvegarde des sources faite avant (hors dépôt).
- Les quatre tests ajoutés par un autre travail pendant le renommage (sous l'ancien paquet) ont été déplacés dans `functional`.
- Mutations vérifiées : texte français réintroduit → `LanguagePolicyTest` échoue ; échappement du remplacement retiré (`quoteReplacement`) → `EmailTemplatesTest` échoue ; code restauré.

