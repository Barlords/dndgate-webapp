# Front web Angular de DnDGate : conception

Date : 2026-10-23. Statut : soumis à validation.

## Intention

DnDGate a un ancien front Vue (dépôt `Barlords/dndgate-front`, archivé, non importé) qui ne suit plus l'API
réécrite. On le remplace par un front **Angular** dans `apps/web/`, qui ne dépend que du contrat
`contracts/openapi.yaml` pour notre API (ADR 0001) et de sources publiques de règles pour le référentiel.

Le nouveau front reprend le contenu de l'ancien, mais doit être **nettement plus beau et plus attirant** :
on doit avoir envie de rester sur le site dès la page d'accueil.

Le site a deux parties distinctes :

- **Bibliothèque** (publique) : le référentiel des règles de JDR. Aujourd'hui le SRD de D&D 5e, demain
  d'autres jeux (Dragonlance, Cyberpunk…). C'est aussi là que vivent les **grimoires** de l'utilisateur
  connecté.
- **Taverne** (connecté) : la gestion de table de jeu (aventures, MJ et joueurs, personnages, chronique,
  notes du MJ, planning, messagerie, évaluations).

## Objectifs

1. Une page d'accueil coupée verticalement en deux moitiés (50 % de largeur, 100 % de hauteur chacune) :
   Bibliothèque à gauche, Taverne à droite, avec une animation de survol marquante (décrite plus bas).
2. Une Bibliothèque publique qui lit les règles depuis une **source de règles interchangeable**, avec un
   sélecteur du jeu de règles (D&D 5e SRD 2014 et 2024 dès cette version).
3. Une Taverne qui couvre toutes les routes de table de jeu du contrat (hors WebRTC).
4. Les pages de compte imposées par l'API (inscription, connexion, confirmation d'e-mail, profil, suppression).
5. Une identité visuelle unique, « Grimoire enluminé », appliquée partout.

## Hors périmètre (pour plus tard)

- **La Forge** (lanceur de dés 3D, générateur de cartes, calculateur de caractéristiques, fiche de personnage
  de l'ancien front). Rien ne doit l'empêcher d'être ajoutée ensuite, mais elle n'apparaît pas (pas de
  troisième entrée sur l'accueil, pas de lien mort).
- **L'appel vidéo** (routes `/webrtc/*`, sujet WebSocket `/topic/adventure/webrtc/{id}`).
- D'autres jeux de règles que D&D 5e : seule l'**architecture** qui les permettra est attendue maintenant.
- La traduction du contenu des règles (dnd5eapi.co ne fournit que de l'anglais) et l'internationalisation
  de l'interface (l'interface est en français seulement).
- Le déploiement continu du front (la CI oui, la CD non).
- L'application mobile.

## Anti-objectifs (ce qui ferait échouer le projet même si « ça marche »)

- Un rendu générique de framework (Bootstrap, Material par défaut) : l'ambiance est le cœur de la demande.
- Une Bibliothèque câblée en dur sur D&D (types, noms de sections, appels à dnd5eapi.co éparpillés dans les
  composants) qui obligerait à tout réécrire pour un deuxième jeu.
- Un client d'API écrit à la main qui dérive du contrat.
- Une suppression irréversible sans la confirmation exigée par l'API, ou un message d'erreur qui avale le
  `correlationId`.

## Contraintes

### Venues du dépôt et de l'API

- Monorepo : le front vit dans `apps/web/`, ne lit jamais le code de `apps/api/`, seulement
  `contracts/openapi.yaml`. Une PR ne touche idéalement que `apps/web/` (CLAUDE.md).
- CI par chemin : un workflow `web-ci.yml` sur `apps/web/**` et `contracts/**`, à côté de `api-ci.yml`.
- Toutes les règles du `README.md` racine qui visent le front s'appliquent ; les principales :
  - **Authentification** : jeton d'accès JWT (15 min) en `Authorization: Bearer`, jeton de rafraîchissement
    opaque (60 jours) **à usage unique** (rotation : présenter un jeton déjà utilisé révoque la session).
    Deux requêtes qui expirent en même temps ne doivent donc déclencher **qu'un seul** `POST /accounts/refresh`.
  - **Inscription** : `POST /accounts` répond toujours `202` ; afficher « vérifiez votre adresse e-mail »,
    proposer `POST /accounts/verification-email`. `409` = pseudo déjà pris.
  - **Liens des e-mails** (chemins fixés par l'API, à servir tels quels) : `/verify-email?email=&code=`,
    `/confirm-email?email=&code=`, `/login`, `/adventures/{id}`.
  - `/verify-email` appelle `POST /accounts/verify` (`409` = pseudo pris entre-temps, se réinscrire) ;
    `/confirm-email` appelle `POST /accounts/email/confirm` (sans jeton : peut s'ouvrir sur un autre appareil).
  - Afficher `pendingEmail` (« en attente de confirmation ») sur le profil.
  - **Suppressions irréversibles** : le corps porte la confirmation vérifiée par l'API — mot de passe pour le
    compte, nom retapé pour aventure / personnage / grimoire (et pour quitter le rôle de MJ),
    `{ "confirm": true }` pour les notes. L'interface demande cette saisie avant d'envoyer.
    Un `403` sur mot de passe faux **ne déconnecte pas**.
  - **Erreurs** : toute erreur est un `application/problem+json` (RFC 9457) ; afficher `detail` (en anglais,
    le message vient de l'API) **et** le `correlationId` à citer pour signaler un problème ; afficher les
    erreurs de champ (`errors[]`) sous les champs du formulaire. `429` : afficher le délai de `Retry-After`.
    `409` de concurrence (version périmée) : proposer de recharger.
  - **Images de personnages** : chargées avec le jeton (`HttpClient`, `responseType: 'blob'`, puis
    `URL.createObjectURL`), jamais par `<img src>` direct. PNG/JPEG, 5 Mo au plus : vérifier côté client avant
    l'envoi pour un message clair.
  - **Pagination** : `?page=&size=` (size 1 à 100), réponse `{content, page, size, totalElements, totalPages}`.
  - **Messagerie** : STOMP sur WebSocket, jeton dans l'en-tête STOMP `Authorization` au `CONNECT`, abonnement
    en lecture seule à `/topic/adventure/chat/{id}` (UUID en minuscules), envoi par `POST /messages`.
    Réabonnement : changer d'identifiant d'abonnement (le README explique pourquoi). `429` au-delà de
    30 messages / minute.
  - **Statut d'une aventure** : `NOT_STARTED → ONGOING → FINISH`, `NOT_STARTED|ONGOING → ABORT`, rien d'autre.
    N'offrir que les transitions permises. Une aventure `ONGOING` ne se supprime pas.
  - **Transfert de MJ** : quitter le rôle, inviter un MJ par code (7 jours), accepter un code, réclamer le
    rôle quand il est vacant (`masterVacant`), voter. Le README racine (« Changer de MJ ») fait foi.
  - **Évaluations** : seulement entre comptes ayant joué ensemble une aventure démarrée ; 1 à 5 ; la moyenne
    est publique, le détail n'est lisible que par le compte noté.
  - Limites de saisie : reprendre les `minLength` / `maxLength` / `pattern` du contrat dans les validateurs
    de formulaire (pseudo, mot de passe 8 caractères minimum, etc.).

### Source des règles : dnd5eapi.co (vérifié le 2026-10-22)

- `https://www.dnd5eapi.co/api` redirige vers `/api/2014`. Deux jeux de données sont en ligne :
  - **2014** : `/api/2014/…` et `/graphql/2014` ; ressources `ability-scores, alignments, backgrounds,
    classes, conditions, damage-types, equipment, equipment-categories, feats, features, languages,
    magic-items, magic-schools, monsters, proficiencies, races, rules, skills, spells, subclasses, subraces,
    traits, weapon-properties`.
  - **2024** : `/api/2024/…` et `/graphql/2024` ; mêmes ressources **sauf** `races`/`subraces`
    (devenus `species`/`subspecies`) et `rules` (absent), **plus** `poisons` et `weapon-mastery-properties`.
    Exemples de volume : 339 sorts, 341 monstres, 12 classes, 9 espèces, 4 historiques.
  - Les formes diffèrent entre versions (ex. sort : `desc: string[]` en 2014, `description: string` en 2024).
- Contenu en anglais, sans authentification. Pas de limite documentée : mettre en cache les listes.
- Les grimoires de l'API stockent des `spellIndex` (ex. `fireball`) sans dire de quel jeu de règles ils
  viennent : réglé par le lot préalable côté API ci-dessous (un grimoire appartient à un jeu de règles).

### Points vérifiés dans le code de l'API (absents du contrat ou du README)

- **Messagerie** : point d'entrée STOMP `/ws` sous le chemin de base de l'API (`/dndgate-api/ws`), **SockJS**
  activé (`withSockJS()`) ; chaque message diffusé sur `/topic/adventure/chat/{id}` a la forme d'un
  `MessageDto`.
- **Personnages d'une aventure** : `GET /game-characters?adventureId=` est ouvert à **tout participant** ;
  l'onglet « personnages » montre donc tout le groupe. `?ownerId=` est une route distincte.

## Lot préalable côté API (une PR `apps/api` + contrat, avant le front)

Décidé avec l'utilisateur : on corrige l'API et le contrat plutôt que de contourner dans le front. Cette PR
suit les règles de `.claude/CLAUDE.md` (tests, contrôle par mutation, contrat régénéré et commité, couverture).

1. **Contrat des personnages** : `GET /game-characters?ownerId=` et `GET /game-characters?adventureId=` sont
   deux routes distinctes côté serveur, mais le contrat n'en décrit qu'une qui exige les deux paramètres.
   Corriger (annotations springdoc, ou deux opérations distinctes) pour qu'un client généré sache appeler
   chacune ; `OpenApiContractIntegrationTest` doit rester vert.
2. **Import de grimoire** : `POST /spellbooks/import-file` attend un fichier `multipart/form-data` (partie
   `file`), le contrat le déclare en JSON. Corriger le contrat. Usage visé : un bouton « Importer » ouvre
   l'explorateur de fichiers, l'utilisateur choisit un fichier JSON (celui que produit l'export), il est
   envoyé tel quel.
3. **Un grimoire appartient à un jeu de règles** (décision de l'utilisateur) :
   - nouveau champ obligatoire à la création (identifiant de jeu de règles, ex. `dnd5e-2014`,
     `dnd5e-2024`), renvoyé dans `SpellBookDto`, présent dans le fichier d'export et lu à l'import ;
   - non modifiable ensuite (*choix du constructeur*, un grimoire ne change pas de jeu) ;
   - migration Flyway : les grimoires existants passent en `dnd5e-2014` (l'ancien front n'utilisait que
     2014) ; un fichier d'import sans ce champ est lu comme `dnd5e-2014` (*choix du constructeur*, pour
     relire les anciens exports) ;
   - l'API ne connaît pas le contenu des règles : elle ne vérifie que le format de l'identifiant (longueur,
     caractères). C'est le front qui ne propose d'ajouter à un grimoire que des sorts de son jeu.

## Ce qui a été décidé avec l'utilisateur

### Identité visuelle : « Grimoire enluminé »

- Fond cuir sombre (dégradés brun-noir), contenu sur **parchemin** (dégradé crème, ombre intérieure,
  liseré or), accents **or** (`~#d9b45a`) et **rouge sang** (`~#7a1f12`).
- Titres en capitales gravées (type *Cinzel*), texte courant à empattements lisible (type *Spectral*).
- Maquettes de référence : `.superpowers/brainstorm/767-1792704011/content/` (`direction-visuelle.html`,
  option A ; `accueil-hover-v3.html` ; `barre-haut-v2.html`). Ces fichiers ne sont pas commités : les
  valeurs importantes sont reprises ici.

### Page d'accueil

- Deux moitiés verticales, 50 % × 100 % de la fenêtre, **Bibliothèque à gauche**, **Taverne à droite**.
- **Images fournies par l'utilisateur** (il les génère lui-même). En attendant : dégradés de remplacement
  (Bibliothèque brun-or à rayures de rayonnages, Taverne foyer orangé), et un emplacement d'image documenté
  pour qu'il n'ait qu'à déposer ses fichiers.
- Animation au survol de la moitié survolée, validée sur maquette (« cadre enluminé + particules ») :
  - un **filet d'or se dessine** tout autour de la moitié (≈1,1 s) ;
  - un **sceau runique** tourne lentement derrière le titre ;
  - l'image zoome légèrement ; le titre monte et s'illumine ; un sous-titre et un bouton apparaissent
    (« Entrer » / « S'attabler ») ;
  - **Bibliothèque** : poussière dorée fine qui monte lentement en dérivant ;
  - **Taverne** : braises orangées qui montent vite **en oscillant latéralement** (amplitude et rythme
    propres à chaque braise, 7–14 px), et rapetissent en montant ;
  - l'autre moitié passe en **sépia sombre**.
- `prefers-reduced-motion` : pas de particules ni de rotation.
- Cliquer sur la Taverne sans être connecté mène à la connexion, puis à la Taverne.

### Pages intérieures (Bibliothèque et Taverne)

- **Barre du haut** fine et sombre, filet d'or en dessous, **emblème DnDGate au centre** (retour à l'accueil).
  Emblème **provisoire** dessiné par nous, remplaçable par l'utilisateur (un seul fichier).
- **Sommaire à gauche sur toute la hauteur** :
  - en tête, le **nom de la partie** (« BIBLIOTHÈQUE » ou « TAVERNE ») ;
  - dans la Bibliothèque, juste dessous, une **liste déroulante du jeu de règles** au nom complet
    (« DnD 5e — SRD 2014 », « DnD 5e — SRD 2024 », d'autres JDR plus tard) ;
  - rubriques groupées sous des intertitres dorés en capitales ;
  - en bas, le **compte** (avatar, pseudo, accès au profil) ou « Se connecter » ;
  - **dépliant** (demande de l'utilisateur) : un bouton permet de l'ouvrir et de le réduire à tout moment ;
    le contenu reprend la place libérée, avec une transition douce.
    *Choix du constructeur* : réduit, il devient une fine colonne d'icônes (une par rubrique, avec infobulle,
    plus le bouton pour le rouvrir) ; l'état ouvert / réduit est mémorisé dans le navigateur ; sur petit
    écran il est réduit par défaut et s'ouvre par-dessus le contenu.
- **Fil d'Ariane en haut du contenu** (ex. Bibliothèque › Sorts › Fireball).
- Contenu sur parchemin.

### Bibliothèque

- Publique. Rubriques reprises de l'ancien front, réparties selon le jeu de règles choisi :
  - **Pour les joueurs** : classes, races (2014) / espèces (2024), historiques, personnalité
    (alignements, langues), sorts ;
  - **Pour le MJ** : monstres ;
  - **Règles** (2014 seulement, puisque 2024 n'en a pas).
  Une rubrique qui n'existe pas dans le jeu choisi n'apparaît pas.
- **Listes** (*choix du constructeur*, à partir de l'ancien front qui avait recherche, tri et pagination) :
  recherche par nom, tri par colonne, pagination côté client ; filtres par niveau et école pour les sorts,
  par type et facteur de puissance pour les monstres.
- **Mes grimoires** (connecté seulement) dans la Bibliothèque : lister, créer (en choisissant son jeu de
  règles), renommer, supprimer (nom retapé), ajouter / retirer un sort, **importer** (bouton qui ouvre
  l'explorateur de fichiers, choix d'un fichier JSON), **exporter** (téléchargement du fichier JSON).
  Un grimoire appartient à un jeu de règles : ses sorts s'affichent avec les fiches de ce jeu, quel que
  soit le jeu sélectionné dans la liste déroulante. Depuis la fiche d'un sort : « Ajouter au grimoire »,
  qui ne propose que les grimoires du jeu de règles de la fiche.
- Le sommaire de la Bibliothèque ne montre « Mes grimoires » que pour un jeu de règles qui a des sorts.
- **Architecture des jeux de règles** : la Bibliothèque ne parle qu'à une abstraction (un jeu de règles
  déclare son identifiant stable (celui stocké dans les grimoires, ex. `dnd5e-2014`), son nom complet, ses
  rubriques et sait lister / détailler leurs entrées, et dit s'il a des sorts) ; dnd5eapi 2014 et 2024 en
  sont deux implémentations. Ajouter un JDR = ajouter une implémentation et l'enregistrer.

### Taverne

Connectée. Sommaire : **Tableau des quêtes** (aventures publiques à rejoindre), **Rejoindre avec un code**,
**Créer une aventure**, **Mes aventures** en cours / terminées (en tant que joueur et MJ).

Dans une aventure : informations (modifier, visibilité, statut, supprimer), **joueurs** (inviter par pseudo,
quitter), **personnages** (créer avec image, voir, modifier, supprimer), **chronique** (notes d'historique),
**notes du MJ** (publiques / privées), **planning** (le MJ propose des dates, chacun vote ses
disponibilités, voir les votes), **messagerie** temps réel, **MJ** (quitter le rôle, inviter un MJ par code,
accepter un code, réclamer le rôle vacant, voter), **évaluer** les autres participants.

### Compte

Connexion, inscription, `/verify-email`, `/confirm-email`, profil (pseudo, e-mail avec `pendingEmail`,
mot de passe, déconnexion, déconnexion de partout, suppression avec mot de passe), page publique d'un
joueur (pseudo, note moyenne).

## Laissé au constructeur (choix par défaut proposés, à confirmer dans le plan)

- **Angular 22** (dernière version stable), composants autonomes, signaux, nouveau flux de contrôle,
  SCSS ; pas de bibliothèque de composants visuels, **Angular CDK** autorisé pour les fenêtres, focus et
  accessibilité.
- **Client d'API généré** depuis `contracts/openapi.yaml` (ex. `ng-openapi-gen`) par un script npm, non
  commité, régénéré en CI : le build casse si le contrat change de façon incompatible.
- Stockage des jetons (proposition : jeton d'accès en mémoire, jeton de rafraîchissement en
  `localStorage` puisque l'API le renvoie dans le corps), intercepteur avec rafraîchissement unique partagé.
- Client STOMP (ex. `@stomp/rx-stomp`).
- Choix REST ou GraphQL pour dnd5eapi.co, cache des réponses.
- Chemins des routes (les chemins imposés par les e-mails sont fixes ; `/adventures/{id}` doit mener à
  l'aventure dans la Taverne).
- « Mes aventures terminées » : l'API n'a pas de filtre par statut ; filtrer côté client.
- Comportement sur petit écran : moitiés de l'accueil empilées, survol remplacé par l'appui ; sommaire
  repliable en tiroir.
- Accessibilité : le focus clavier déclenche les mêmes effets que le survol ; contrastes AA sur parchemin.
- Tests : unitaires (Vitest, seuil de couverture dans la CI), quelques parcours de bout en bout ; un
  `Dockerfile` (nginx, repli SPA) sans CD.
