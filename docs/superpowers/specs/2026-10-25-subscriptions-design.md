# Abonnements et paiement Stripe — conception

Date : 2026-10-25

## Intention

DnDGate devient payant par abonnement, sans chercher à « dépouiller » les utilisateurs : le site n'est pas fini, les
prix sont volontairement bas et monteront à mesure que la Forge s'enrichit (premier outil prévu, après ce chantier :
le lanceur de dés 2D / 3D). L'abonnement délimite deux choses :

- des **quantités** (aventures actives en MJ, personnages, grimoires, objets magiques, notes) ;
- des **outils** accessibles ou non (images de personnage, visio, puis les outils de la Forge).

Le paiement passe par **Stripe** (l'utilisateur a un compte). Les offres et ce qu'elles contiennent sont décrits dans
**un fichier JSON à part**, pour les faire évoluer sans toucher au code des services.

## Les offres

| | Aventurier | Héros | Maître du Donjon ⭐ | Légendaire |
|---|---|---|---|---|
| Identifiant | `adventurer` | `hero` | `dungeon-master` | `legendary` |
| Mensuel | 0 € | 0,99 € | 4,99 € | 9,99 € |
| Annuel (2 mois offerts) | — | 9,90 € | 49,90 € | 99,90 € |
| Aventures actives en MJ | 1 | 2 | 10 | 25 |
| Rejoindre des aventures en joueur | illimité | illimité | illimité | illimité |
| Personnages hors aventure | 3 | 15 | 50 | 75 |
| Grimoires | 2 | 10 | 50 | 100 |
| Objets magiques | 10 | 50 | 200 | 500 |
| Notes MJ + chronique par aventure | 50 | 100 | 500 | 500 |
| Personnages par joueur dans ses aventures | 1 | 1 | 5 | 10 |
| PNJ par aventure | 5 | 10 | 50 | 100 |
| Messages conservés par aventure | 200 | 200 | 1 000 | 2 000 |
| Bibliothèque SRD, messagerie, planning | ✔ | ✔ | ✔ | ✔ |
| Import / export de grimoire | ✔ | ✔ | ✔ | ✔ |
| Images de personnage | — | ✔ | ✔ | ✔ |
| Visio dans ses aventures | — | — | ✔ | ✔ |
| Table équipée | — | — | ✔ | ✔ |
| Badge | — | — | MJ | Légende |
| Accès anticipé (Forge), support prioritaire | — | — | — | ✔ |

Les prix sont TTC, tels que payés. « Maître du Donjon » est l'offre mise en avant (`highlighted`), Légendaire sert
d'ancre de prix. Le badge **Fondateur** ne fait partie d'aucune offre : il est réservé aux bêta-testeurs (voir
« Badges »).

Hors offre, des **plafonds techniques** identiques pour tous restent dans `QuantityLimits` (anti-abus, pas un levier
commercial) : 1 000 sorts par grimoire, 30 messages par minute. L'ancien plafond de 10 personnages par joueur et par
aventure disparaît, remplacé par la limite d'offre « personnages par joueur dans ses aventures ».

*Mon choix* : « Notes MJ + chronique par aventure : N » veut dire N notes MJ **et** N notes de chronique (les deux
compteurs existants, chacun plafonné à N).

## Quelle offre s'applique

Règle : **ce qui touche au compte dépend de l'offre du compte ; ce qui touche à une aventure dépend de l'offre de son
MJ.**

| Limite / outil | Offre qui compte |
|---|---|
| Aventures actives en MJ, personnages hors aventure, grimoires, objets magiques | celle du compte qui crée |
| Notes MJ et notes de chronique d'une aventure | celle du MJ de l'aventure (`creator_id`) |
| Personnages par joueur, PNJ, messages conservés d'une aventure | celle du MJ de l'aventure |
| Visio dans une aventure (signaux `POST /webrtc/*`) | celle du MJ de l'aventure |
| Images de personnage | hors aventure : celle du compte ; dans une aventure : celle du compte **ou** celle du MJ si elle a `equippedTable` |
| Futurs outils de Forge marqués « partageables » (dés 3D) | même règle que les images |

- **Table équipée** (`equippedTable`) : dans les aventures d'un MJ qui l'a, chaque participant bénéficie des outils
  partageables de l'offre du MJ. Aujourd'hui : images de personnage ; demain : dés 3D. La liste des outils
  partageables est fixée dans le code (une propriété de la clé d'outil), pas dans le JSON.
- **Ce que voit un participant** (*mon choix*) : la réponse d'une aventure porte, pour l'appelant, la liste des outils
  qui marchent pour lui dans cette aventure (`availableFeatures`, ex. `["video", "characterPictures"]`), sans dire
  quelle offre les fournit : l'offre du MJ n'est jamais exposée aux autres. Le front s'en sert pour griser les boutons.
- **Aventure sans MJ** (`master_vacant_since` non nul) : l'offre gratuite s'applique à ce qui dépend du MJ.
- **Transfert de MJ** : ne vérifie toujours pas le plafond du nouveau MJ (règle actuelle conservée) ; à partir du
  transfert, l'aventure suit l'offre du nouveau MJ.

## Quand l'offre baisse (résiliation, impayé, passage à une offre inférieure)

**Rien n'est jamais supprimé ni masqué**, sauf l'historique des messages (seule suppression automatique du site, voir
« Historique des messages »). Ce qui dépasse le nouveau quota reste lisible et modifiable ; seule la **création** (ou
l'entrée d'un personnage dans une aventure) au-delà du quota est refusée. Un outil perdu ne sert plus pour la suite (visio refusée, nouvelle image
refusée), mais les images déjà envoyées restent affichées.

Refus d'un quota d'offre : `409` `application/problem+json` avec un type distinct de l'actuel dépassement de plafond
technique, pour que le front propose l'offre supérieure. Le problème porte au moins : la clé de la limite
(`limit`), la valeur atteinte (`max`) et l'offre qui l'a imposée (`planId`). Refus d'un outil : `403` problem+json avec
la clé de l'outil (`feature`) et `planId`.

## Personnages : réserve, sortie d'aventure, PNJ

La **réserve** d'un compte, ce sont ses personnages hors aventure (quota « personnages hors aventure » de son offre).

### Personnages des joueurs

- Un joueur a au plus *N* personnages dans une aventure, *N* fixé par l'offre du MJ (1 / 1 / 5 / 10). Le MJ n'est pas
  concerné : ses fiches dans son aventure sont des PNJ (plus bas).
- **Un seul personnage actif** par joueur et par aventure ; les autres attendent « sur le banc » (personnage mort,
  remplaçant…). Le joueur change de personnage actif en un clic. *Mon choix* : le premier personnage d'un joueur dans
  l'aventure devient actif ; si l'actif sort de l'aventure ou est supprimé, aucun autre ne le devient automatiquement,
  le joueur choisit (et le prochain qui entre devient actif s'il n'y en a aucun). Les autres participants voient quel
  personnage est actif.
- **Sortir un personnage de l'aventure** (nouvelle action, par son propriétaire) : le personnage passe dans la réserve
  avec toutes ses données, et libère sa place. Possible **seulement s'il reste de la place dans la réserve**. Sinon
  l'API refuse (`409`, quota de la réserve) et le front ouvre une boîte de dialogue : annuler, ou continuer et
  **supprimer définitivement** le personnage. La suppression suit la règle du dépôt (le nom retapé, vérifié par
  l'API).
- **Quitter une aventure** (`DELETE /adventures/{id}/players/me`) : les personnages du joueur dans l'aventure passent
  automatiquement dans sa réserve. S'ils n'y tiennent pas tous, même boîte de dialogue : annuler, ou continuer en
  supprimant définitivement ceux qui ne tiennent pas (chacun confirmé par son nom). *Mon choix* : l'opération est
  atomique (tout ou rien) ; la requête de départ porte la liste des personnages à supprimer avec leurs confirmations,
  et l'API vérifie que le reste tient dans la réserve.
- **Envoyer un personnage de la réserve dans une aventure** (*mon choix*, l'inverse de la sortie) : soumis à la limite
  de personnages par joueur de l'aventure, comme une création.
- **Suppression d'une aventure par le MJ** : les personnages des joueurs passent dans la réserve de leur propriétaire,
  **même si elle est pleine** (ce n'est pas le joueur qui agit ; la réserve peut alors dépasser son quota, seule la
  création reste bloquée). Les PNJ sont supprimés avec l'aventure. Même transaction que la suppression
  (`TransactionalAdventureDeleter`).

### PNJ

- Les fiches que le MJ crée dans son aventure sont des **PNJ** (même contenu qu'un personnage : nom, race, classe,
  personnalité, histoire, image). Limite **par aventure**, fixée par l'offre du MJ : 5 / 10 / 50 / 100.
- Visibilité **privée** (par défaut, *mon choix*) : seul le MJ les lit, c'est sa réserve de PNJ. **Publique** : toute
  la table les lit, sans pouvoir les modifier. Le filtre est appliqué dans la requête en base, comme les notes privées.
- **Action rapide** pour rendre public / privé un PNJ en un clic, depuis la liste des PNJ du MJ, sans formulaire ni
  confirmation.
- Les PNJ appartiennent à l'aventure : après un transfert de MJ, c'est le nouveau MJ qui les gère (*mon choix*). Ils
  ne passent pas dans une réserve personnelle.

## Historique des messages

- Une aventure garde au plus *M* messages, *M* fixé par l'offre du MJ (200 / 200 / 1 000 / 2 000). Au-delà, chaque
  nouveau message **supprime définitivement les plus anciens**, dans la même transaction que l'envoi. C'est la seule
  suppression automatique du site ; elle est annoncée dans l'interface et dans les CGV.
- La liste des messages renvoie l'usage (`used` / `max`). De **80 % à 99 %**, toute la table voit un bandeau
  « historique bientôt plein » ; à **100 %**, « chaque nouveau message efface le plus ancien ». Le MJ voit en plus
  l'offre qui augmente la limite.
- Si le MJ baisse d'offre, les messages en trop sont supprimés au premier nouvel envoi ; le bandeau l'annonce avant.
- Le débit de 30 messages par minute et par compte reste en plus.

## Le fichier des offres

Fichier JSON lu par l'API au démarrage : `apps/api/src/main/resources/plans/plans.json`, remplaçable sans rebuild par
un fichier externe via `PLANS_FILE` (chemin). Il ne contient **aucun texte affiché** : les noms et descriptions des
offres sont traduits côté web (`core/i18n/fr|en`), à partir de l'identifiant.

```json
{
  "currency": "EUR",
  "defaultPlan": "adventurer",
  "plans": [
    {
      "id": "adventurer",
      "highlighted": false,
      "prices": {},
      "limits": {
        "activeAdventuresAsMaster": 1,
        "charactersWithoutAdventure": 3,
        "spellBooks": 2,
        "magicItems": 10,
        "masterNotesPerAdventure": 50,
        "historicalNotesPerAdventure": 50,
        "charactersPerPlayerAndAdventure": 1,
        "npcsPerAdventure": 5,
        "messagesPerAdventure": 200
      },
      "features": {
        "characterPictures": false,
        "video": false,
        "equippedTable": false,
        "earlyAccess": false,
        "prioritySupport": false
      },
      "badge": null
    },
    {
      "id": "hero",
      "highlighted": false,
      "prices": {
        "monthly": { "amount": 99, "stripeLookupKey": "hero_monthly" },
        "yearly": { "amount": 990, "stripeLookupKey": "hero_yearly" }
      },
      "limits": { "...": "..." },
      "features": { "...": "..." },
      "badge": null
    },
    { "id": "dungeon-master", "...": "...", "badge": "dungeon-master" },
    { "id": "legendary", "...": "...", "badge": "legend" }
  ]
}
```

- Montants en centimes. L'ordre du tableau `plans` est l'ordre d'affichage et l'ordre « inférieur → supérieur »
  (sert à dire quelle offre débloque une limite).
- Les **clés** de `limits` et `features` sont connues du code (énumérations). Au démarrage, l'API **refuse de
  démarrer** si : une clé est inconnue, une clé manque dans une offre, un identifiant est dupliqué, `defaultPlan`
  n'existe pas ou a des prix, une limite est < 1, une offre payante n'a pas de prix. Ajouter un outil = une clé dans
  l'énumération + sa valeur dans chaque offre.
- `earlyAccess` et `prioritySupport` n'ont pas d'effet technique dans ce chantier (affichage seulement) ;
  `earlyAccess` servira à ouvrir les outils de Forge en avant-première.
- `badge` : badge d'offre affiché tant que l'offre est effective (`null`, ou une clé connue du code :
  `dungeon-master`, `legend`).
- Le futur lanceur de dés ajoutera ses clés (prévu : 2D pour tous, 3D à partir de Maître du Donjon, partageable).
  Il n'est **pas** dans ce chantier.

## Stripe

Principe : l'API ne manipule pas de carte. Le paiement se fait sur les pages hébergées par Stripe (**Checkout**), la
gestion de l'abonnement (changer d'offre, de carte, résilier, factures) sur le **portail client** Stripe. La
résiliation simple qu'impose la loi française est donc assurée par le portail.

### Côté Stripe (configuration manuelle, documentée dans le README)

- Un **produit** par offre payante, avec la métadonnée `plan_id` = identifiant du JSON (`hero`, …).
- Deux **prix** récurrents par produit (mensuel, annuel), avec les `lookup_key` du JSON (`hero_monthly`, …).
- Le portail client configuré pour permettre changement d'offre (entre ces prix), résiliation, mise à jour de la carte.
- Un endpoint de webhook pointant sur l'API, avec les événements listés plus bas.

Pourquoi `lookup_key` pour vendre et `plan_id` du produit pour reconnaître : les identifiants de prix diffèrent entre
mode test et mode live, et un prix Stripe ne change jamais de montant. Changer un prix = créer un nouveau prix sur le
même produit et lui transférer la `lookup_key` ; les abonnés existants gardent leur ancien prix (et donc leur offre,
reconnue par le produit).

### Variables d'environnement (à ajouter à `.env.example`)

| Variable | Rôle |
|---|---|
| `STRIPE_SECRET_KEY` | clé secrète (`sk_test_…` / `sk_live_…`) |
| `STRIPE_WEBHOOK_SECRET` | secret de signature du webhook (`whsec_…`) |
| `PLANS_FILE` | facultatif : chemin d'un `plans.json` externe |

Les URL de retour de Checkout et du portail sont construites à partir de `WEB_BASE_URL` (déjà existante). Pas de clé
publiable : avec Checkout, le front ne fait que rediriger vers l'URL renvoyée par l'API.

Sans `STRIPE_SECRET_KEY` (dev par défaut), l'API démarre, les offres et quotas fonctionnent (tout le monde est sur
l'offre gratuite), et les routes de paiement répondent `503` problem+json. En production, les deux variables Stripe
sont obligatoires et vérifiées au démarrage comme les autres.

### Routes (noms à confirmer par `RouteNamingIntegrationTest`)

| Route | Accès | Rôle |
|---|---|---|
| `GET /plans` | public | le catalogue (contenu du JSON, sans les `stripeLookupKey`) |
| `GET /accounts/me/subscription` | connecté | offre effective, intervalle, statut, fin de période, résiliation programmée, et l'usage de chaque limite de compte (`used` / `max`) |
| `POST /billing/checkout-session` | connecté | corps `{ planId, interval }` → `{ url }` d'une session Checkout ; refusé (`409`) si le compte a déjà un abonnement actif (il passe par le portail) |
| `POST /billing/portal-session` | connecté | → `{ url }` du portail client ; `409` si le compte n'a pas de client Stripe |
| `POST /billing/stripe-webhook` | public, signature Stripe | reçoit les événements |

### Le webhook

- Signature vérifiée sur le **corps brut** avec `STRIPE_WEBHOOK_SECRET` ; signature absente ou fausse → `400`, rien
  n'est fait. La route est exclue de l'authentification JWT ; la limite de 512 Ko sur les corps reste valable (les
  événements Stripe sont bien plus petits).
- Événements traités : `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated`,
  `customer.subscription.deleted`. Les autres → `200` sans effet.
- Pour chaque événement d'abonnement, l'API **relit l'abonnement chez Stripe** et enregistre son état actuel : l'ordre
  d'arrivée des événements et leurs doublons n'ont alors pas d'importance (traitement idempotent).
- Le compte est retrouvé par le client Stripe (`stripe_customer_id`), créé par l'API au premier checkout avec la
  métadonnée `account_id` (et `client_reference_id` sur la session).
- Une erreur interne → `500`, Stripe réessaie.

### Un seul abonnement par compte (*mon choix*)

La ligne `subscription` n'est remplie qu'à l'arrivée du webhook : deux checkouts dans deux onglets pourraient créer deux
abonnements payants. Donc :

- avant d'ouvrir un checkout, l'API demande **à Stripe** (pas seulement à la table) les abonnements du client qui ne
  sont pas terminés (`active`, `trialing`, `past_due`, `incomplete`) ; s'il y en a un → `409`, passer par le portail ;
- la session Checkout expire vite (30 min, le minimum Stripe) pour réduire la fenêtre restante ;
- à chaque événement, l'API relit **tous** les abonnements du client et retient le meilleur actif (offre la plus haute,
  puis la fin de période la plus lointaine) : un `deleted` sur un doublon n'efface pas l'abonnement qui reste. Un
  doublon actif est journalisé en erreur pour être remboursé à la main ; pas de résiliation automatique.

### Statut Stripe → offre effective

| Statut de l'abonnement | Offre effective |
|---|---|
| `active`, `trialing` | l'offre du produit |
| `past_due` (Stripe relance le paiement) | l'offre du produit, le temps des relances |
| `canceled`, `unpaid`, `incomplete`, `incomplete_expired`, `paused` ; pas d'abonnement | `defaultPlan` |

Une résiliation demandée dans le portail garde l'offre jusqu'à la fin de la période payée (`cancel_at_period_end`),
puis Stripe envoie `customer.subscription.deleted`.

### Données

Nouvelle migration Flyway, une table `subscription` (une ligne par compte au plus) : `account_id` (unique),
`stripe_customer_id` (unique), `stripe_subscription_id` (unique, nullable), `plan_id`, `billing_interval`, `status`,
`current_period_end`, `cancel_at_period_end`, `updated_at`. L'offre effective est calculée à partir de cette ligne et du
catalogue ; un `plan_id` qui n'existe plus dans le JSON retombe sur `defaultPlan` (avec un log d'erreur).

### Badges

Deux sortes de badges, cumulables, **visibles par les autres** : ils figurent dans toutes les réponses qui montrent un
autre compte (profil public, participants d'une aventure, auteur d'un message, évaluations…) et dans
`GET /accounts/me`.

- **Badge d'offre** (`dungeon-master` → « MJ », `legend` → « Légende ») : déclaré par offre dans le JSON (`badge`),
  calculé à partir de l'offre effective ; il disparaît quand l'offre n'est plus effective. Il révèle donc l'offre en
  cours, c'est voulu.
- **Badge Fondateur** : réservé aux bêta-testeurs recrutés par l'utilisateur, indépendant de toute offre. Colonne
  `founder boolean NOT NULL DEFAULT false` sur `accounts` (même migration), gardé à vie. L'anonymisation le remet à
  `false`. Attribué **à la main en base** : la commande SQL est donnée dans le README ; pas d'écran
  d'administration. L'offre éventuellement offerte aux bêta-testeurs sera décidée plus tard (hors chantier).

### Conditions générales de vente et rétractation

Il n'existe pas encore de CGV : ce chantier crée la page.

- Le front a une page publique **CGV** (fr / en), liée depuis la page Offres, le pied de page et Checkout. Son contenu
  juridique (identité de l'éditeur, prix, durée, renouvellement, résiliation, rétractation, médiateur…) est une
  **structure à compléter et faire valider par l'utilisateur** : le builder ne l'invente pas comme un texte définitif.
- Checkout demande l'acceptation des CGV (`consent_collection.terms_of_service = required` ; l'URL des CGV se règle dans
  les informations publiques du compte Stripe, documenté dans le README) et affiche, par un texte personnalisé, que le
  client demande l'accès immédiat au service et renonce ainsi à son droit de rétractation de 14 jours.

### Mise en ligne

Aucune donnée de production n'existe (le site n'est pas encore ouvert) : pas de transition. Tous les comptes
commencent sur `defaultPlan`.

### Suppression de compte

`DELETE /accounts` (anonymisation) **résilie immédiatement** l'abonnement Stripe du compte (sans remboursement au
prorata — *mon choix*, à revoir si besoin) et efface la ligne `subscription`. Le client Stripe et ses factures restent
chez Stripe (obligations comptables). Si l'appel Stripe échoue, la suppression du compte échoue aussi (`503`) : on ne
laisse pas un compte anonymisé qui continue de payer.

## Architecture (API)

Selon l'architecture hexagonale existante :

- **domaine** : modèle du catalogue (`Plan`, `PlanLimit`, `Feature`), `Subscription`, un service qui donne l'offre
  effective d'un compte et d'une aventure (port in), et les ports out : lecture du catalogue, persistance des
  abonnements, prestataire de paiement (création de session Checkout / portail, lecture d'un abonnement, résiliation).
  Le domaine ne connaît ni Stripe ni Spring.
- **server** : adaptateur Stripe (SDK officiel `stripe-java`), adaptateur JPA `subscription`, lecteur du `plans.json`.
- **client** : contrôleurs `PlanController`, `BillingController`, `GET /accounts/me/subscription`, handlers d'erreurs
  (priorité plus haute que `FallbackErrorHandler`).
- **bootstrap** : beans, configuration Stripe, sécurité (routes publiques), vérification du JSON au démarrage.

Les services de création existants (aventures, personnages, grimoires, objets magiques, notes, chronique) et les
signaux WebRTC demandent l'offre effective au lieu de lire `QuantityLimits` pour les limites qui passent dans l'offre.
`QuantityLimits` ne garde que les plafonds techniques ; les clés `limits.*` correspondantes disparaissent des
`application-*.properties` (le README est mis à jour).

**Image de personnage sans l'outil** : aujourd'hui l'image est obligatoire à la création (`picturePresentation`,
`picture_path NOT NULL`). Elle devient facultative : sans image, le personnage n'a pas d'image stockée et le front
affiche une image par défaut. Avec une image mais sans l'outil → `403`. (La façon de représenter « pas d'image » en
base est laissée au builder.)

## Front (web)

PR séparée, après celle de l'API et du contrat (règle du dépôt : une application par PR, sauf contrat).

- Page **Offres** (publique) : les 4 offres côte à côte à partir de `GET /plans`, bascule mensuel / annuel (annuel
  présenté comme « 2 mois offerts »), badge « le plus choisi » sur l'offre `highlighted`. Bouton : se connecter /
  s'abonner (Checkout) / gérer mon abonnement (portail) selon l'état.
- Profil : « Mon abonnement » avec l'offre, la date de renouvellement ou de fin, et l'usage (`1 / 2 aventures`).
- Retour de Checkout : page de confirmation qui relit `GET /accounts/me/subscription` (le webhook peut arriver
  quelques secondes après la redirection : réessayer brièvement).
- Sur un `409` de quota d'offre ou un `403` d'outil : message qui nomme l'offre minimale qui débloque, avec un lien
  vers la page Offres. Les boutons d'outils non inclus (envoi d'image, visio) sont visibles mais désactivés avec la
  même invitation.
- Badge Fondateur à côté du pseudo partout où un autre compte est montré.
- Page CGV publique (voir « Conditions générales de vente »).
- Textes fr / en dans `core/i18n`.

## Hors chantier

- Le lanceur de dés (2D / 3D) et tout autre outil de Forge.
- Essai gratuit, tarif Fondateur (prix bloqué), codes promo, offres attribuées à la main par un admin.
- Le texte juridique définitif des CGV (seule la structure de la page est dans ce chantier).
- Quota d'espace de stockage des images, recompression des images (sujets relevés, traités à part).
- TVA automatique (Stripe Tax), facturation entreprise.
- Application mobile.

## Anti-objectifs

- Supprimer ou cacher du contenu d'un utilisateur parce que son offre a baissé (seule exception : l'historique des
  messages, annoncé).
- Rendre la résiliation difficile.
- Mettre du texte affiché ou des identifiants Stripe propres à un environnement dans le code Java.
- Faire confiance au retour de Checkout côté navigateur pour donner une offre : seul le webhook (relu chez Stripe) le
  fait.

## Tests

Règles habituelles du dépôt (90 % de lignes couvertes, contrôle par mutation de chaque règle). En particulier :

- tests unitaires du domaine : offre effective (compte, aventure, table équipée, MJ vacant, statuts Stripe), chaque
  quota d'offre et chaque refus d'outil ;
- validation du `plans.json` au démarrage (chaque cas de refus) ;
- sortie d'aventure et départ d'un joueur (réserve pleine, suppression confirmée, atomicité), PNJ privés invisibles
  aux joueurs (y compris dans les listes paginées), purge des messages au-delà de la limite et après baisse d'offre ;
- webhook : signature valide / absente / fausse, doublon, ordre inversé (avec des événements signés par le test) ;
- l'adaptateur Stripe est testé sans appel réel à Stripe (serveur simulé ; le choix de l'outil est laissé au
  builder) ;
- contrat `contracts/openapi.yaml` régénéré dans la PR de l'API.

## Laissé au builder

Noms exacts des classes et des types de problème, représentation d'une image absente, version du SDK Stripe, outil de
simulation de Stripe dans les tests, découpage en commits.
