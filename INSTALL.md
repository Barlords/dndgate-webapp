# Installation

Ce guide explique comment lancer DnDGate en local (développement) et en production. Les règles métier et de sécurité sont dans le [README](README.md).

Le dépôt contient deux applications :

| Application | Dossier | Technologie | Port local |
|---|---|---|---|
| API | `apps/api` | Java 21, Spring Boot 4 | 4000 |
| Front web | `apps/web` | Angular 22 | 4200 |

L'application mobile (`apps/mobile`) n'existe pas encore.

## Prérequis

| Outil | Version | Pour quoi faire |
|---|---|---|
| Git | récent | récupérer le dépôt |
| Docker (avec Compose) | récent | PostgreSQL et MinIO en local, tests de l'API, production |
| JDK | 21 | API (build et lancement hors Docker) |
| Node.js | 24, au moins 24.15 | front web (exigé par Angular CLI 22) |

Maven n'est pas à installer : le wrapper `mvnw` (`mvnw.cmd` sous Windows) est fourni.

## Développement local

### 1. Récupérer le dépôt

```
git clone https://github.com/Barlords/dndgate-webapp.git
cd dndgate-webapp
```

### 2. Lancer l'infrastructure (PostgreSQL et MinIO)

Depuis la racine du dépôt :

```
docker compose up -d
```

| Service | Adresse | Identifiants par défaut |
|---|---|---|
| PostgreSQL 17 | `localhost:5432`, base `dndgate` | `dndgate` / `dndgate` |
| MinIO (API S3) | `http://localhost:9000` | `dndgate` / `dndgate2024` |
| MinIO (console) | `http://localhost:9001` | idem |

Ces valeurs correspondent à `application-dev.properties` : rien à configurer. Pour en changer, copier `.env.example` en `.env` et renseigner `DB_USER`, `DB_PASSWORD`, `MINIO_ACCESS_KEY` et `MINIO_SECRET_KEY`.

### 3. Lancer l'API

```
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev        # Linux, macOS, Git Bash
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"    # Windows (cmd ou PowerShell)
```

L'API écoute sur `http://localhost:4000/dndgate-api`. Au démarrage :

- Flyway applique les migrations (`src/main/resources/db/migration`) ;
- le bucket MinIO `dndgate` est créé s'il manque.

Le profil `dev` fournit une clé JWT de développement et des valeurs par défaut pour la base et MinIO. Quelques fonctions demandent une configuration en plus :

- **E-mails** (inscription, invitations) : renseigner `MAIL_USERNAME` et `MAIL_PASSWORD`. Sans eux, l'envoi échoue, l'erreur est journalisée et l'API continue de répondre.
- **Paiement** : sans `STRIPE_SECRET_KEY`, l'API démarre, tous les comptes sont sur l'offre gratuite et les routes de paiement répondent `503`. Pour tester Stripe, voir la section « Abonnements et paiement » du README (`stripe listen --forward-to localhost:4000/dndgate-api/billing/stripe-webhook`).

### 4. Lancer le front web

Dans un second terminal :

```
cd apps/web
npm ci
npm start
```

Ouvrir `http://localhost:4200`. Le serveur de développement envoie `/dndgate-api` vers `http://localhost:4000` (voir `apps/web/proxy.conf.json`). `npm start` régénère d'abord le client d'API à partir de `contracts/openapi.yaml`.

Le référentiel des règles est lu directement sur `https://www.dnd5eapi.co` (aucune clé requise).

### 5. Vérifier

| Commande | Dossier | Rôle |
|---|---|---|
| `./mvnw clean verify` | `apps/api` | build complet, tests et seuil de couverture de 90 % (Docker doit tourner : les tests utilisent Testcontainers) |
| `npm run lint` | `apps/web` | ESLint |
| `npm run test:ci` | `apps/web` | tests unitaires et couverture |
| `npm run e2e` | `apps/web` | parcours Playwright (première fois : `npx playwright install chromium`) |

### Arrêter et remettre à zéro

```
docker compose down        # arrête, garde les données
docker compose down -v     # arrête et efface la base et le stockage MinIO
```

Après avoir supprimé ou renommé une migration Flyway, faire un `./mvnw clean` avant de relancer l'API.

## Production

La production repose sur trois éléments :

1. l'**API** et sa base PostgreSQL, lancées par `docker-compose.prod.yml` ;
2. le **front web**, une image nginx qui sert le build statique ;
3. un **reverse proxy** (TLS) devant les deux.

`docker-compose.prod.yml` ne contient que l'API et sa base. MinIO, le serveur de mail et le reverse proxy sont à fournir à côté.

### Ce qu'il faut avoir

- Un serveur avec Docker et Compose.
- Un nom de domaine et un certificat TLS (le reverse proxy s'en charge).
- Une instance **MinIO** accessible par l'API. Son adresse est fixée dans `application-prod.properties` (`minio.url`, aujourd'hui `https://dndgate-minio.barlords.fr`) ; pour une autre instance, modifier cette propriété et reconstruire l'image de l'API.
- Un compte **SMTP** : l'hôte et le port sont dans `application-prod.properties` (`ssl0.ovh.net`, port 587) ; seuls l'identifiant et le mot de passe sont des variables.
- Un compte **Stripe** configuré selon la section « Abonnements et paiement » du README (produits, prix, portail, webhook).
- Une image de l'API publiée sur Docker Hub (voir « Publication de l'API »).

### 1. Configurer les variables

Sur le serveur, copier `.env.example` en `.env` (fichier ignoré par git) et renseigner :

| Variable | Rôle |
|---|---|
| `DOCKER_USERNAME` | compte Docker Hub qui publie l'image `dndgate-api` |
| `API_VERSION` | étiquette de l'image à lancer (`latest` par défaut) |
| `DB_PASSWORD` | mot de passe PostgreSQL (`DB_USER` et `DB_NAME` sont facultatifs : `dndgate` par défaut) |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | identifiants de l'instance MinIO |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | compte SMTP |
| `JWT_SECRET` | clé de signature des jetons, 32 octets minimum |
| `CORS_ALLOWED_ORIGINS` | origines autorisées, séparées par des virgules (par exemple `https://dndgate.barlords.fr`) |
| `WEB_BASE_URL` | adresse publique du site : les liens des e-mails et les retours Stripe y mènent |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | clés Stripe (`sk_live_…`, `whsec_…`) |
| `PLANS_FILE` | facultatif : catalogue des offres externe (`file:/chemin/plans.json`) |

Toutes les variables sans valeur par défaut sont obligatoires : si l'une manque, `docker compose` refuse de démarrer plutôt que de lancer l'API avec un secret vide. Pour générer un `JWT_SECRET` :

```
openssl rand -base64 48
```

### 2. Lancer l'API et la base

```
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps        # l'API doit passer à « healthy » (jusqu'à ~90 s)
docker compose -f docker-compose.prod.yml logs -f dndgate-api
```

- La base n'est pas exposée : seule l'API lui parle, par le réseau interne. Les données sont dans le volume `db_storage`.
- L'API n'écoute que sur `127.0.0.1:4000`, pour que seul le reverse proxy y accède. Ne pas publier ce port sur d'autres interfaces (le README explique pourquoi pour la limitation de débit par adresse).
- Le port de gestion `4001` (sondes et métriques) n'est jamais publié.
- Au premier démarrage, Flyway crée le schéma.

### 3. Construire et lancer le front

Aucune image du front n'est publiée par la CI pour l'instant. Il faut la construire depuis la racine du dépôt :

```
docker build -f apps/web/Dockerfile -t dndgate-web .
docker run -d --name dndgate-web --restart unless-stopped -p 127.0.0.1:8080:80 dndgate-web
```

L'image sert le build statique avec nginx et renvoie `index.html` pour toutes les routes de l'application. Elle **ne relaie pas** l'API : c'est le reverse proxy qui le fait.

### 4. Configurer le reverse proxy

Le front appelle l'API par le chemin relatif `/dndgate-api` : le site et l'API doivent donc être servis **sur le même domaine**. Le reverse proxy fait trois choses :

- terminer le TLS ;
- envoyer `/dndgate-api` vers `127.0.0.1:4000`, **WebSocket compris** (le chat et la visio passent par là) ;
- envoyer tout le reste vers `127.0.0.1:8080` (le front).

Exemple nginx :

```nginx
server {
  listen 443 ssl;
  server_name dndgate.example.com;
  # ssl_certificate / ssl_certificate_key : à renseigner

  location /dndgate-api {
    proxy_pass http://127.0.0.1:4000;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;   # écraser, jamais transmettre tel quel
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location / {
    proxy_pass http://127.0.0.1:8080;
  }
}
```

`X-Forwarded-For` doit être **ajouté ou écrasé**, jamais transmis tel que le client l'envoie : l'API s'en sert pour limiter le débit et le verrouillage de connexion par adresse.

### 5. Brancher Stripe

Créer dans Stripe un endpoint webhook vers `https://<votre-domaine>/dndgate-api/billing/stripe-webhook` avec les événements `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated` et `customer.subscription.deleted`. Son secret de signature est `STRIPE_WEBHOOK_SECRET`. Le détail est dans le README.

### Publication de l'API (CI/CD)

À chaque push sur `main` qui touche `apps/api`, le workflow `.github/workflows/api-cd.yml` lance `./mvnw clean verify`, construit l'image et la pousse sur Docker Hub sous deux étiquettes : `latest` et l'identifiant du run. Il demande deux secrets de dépôt GitHub : `DOCKER_USERNAME` et `DOCKER_PASSWORD`.

Pour déployer une version précise, renseigner `API_VERSION` avec l'identifiant du run, puis relancer `docker compose -f docker-compose.prod.yml up -d`.

### Mettre à jour

```
docker compose -f docker-compose.prod.yml pull dndgate-api
docker compose -f docker-compose.prod.yml up -d
```

Pour le front : reconstruire l'image, puis relancer le conteneur.

### Sauvegarde et restauration de la base

```
docker compose -f docker-compose.prod.yml exec -T dndgate-db pg_dump -U dndgate dndgate > dndgate.sql
docker compose -f docker-compose.prod.yml exec -T dndgate-db psql -U dndgate dndgate < dndgate.sql
```

Remplacer `dndgate` par `DB_USER` et `DB_NAME` si vous les avez changés. Les images des personnages sont dans MinIO : à sauvegarder séparément.

## Dépannage

| Symptôme | Piste |
|---|---|
| `docker compose` répond `... is required` | une variable obligatoire manque dans `.env` |
| L'API refuse de démarrer sur Flyway après avoir supprimé ou renommé une migration | lancer `./mvnw clean` (l'ancien script reste dans `target/classes`) |
| Les tests d'intégration échouent | Docker doit tourner (Testcontainers) |
| `npm start` : version de Node refusée | installer Node 24.15 ou plus |
| Erreurs CORS en production | `CORS_ALLOWED_ORIGINS` doit contenir l'origine exacte du site (schéma et domaine, sans `/` final) |
| La connexion échoue en boucle derrière le reverse proxy | vérifier que `X-Forwarded-For` est écrasé et que le port 4000 n'est pas publié ailleurs que sur `127.0.0.1` |
| Pas d'e-mail d'inscription | vérifier `MAIL_USERNAME` et `MAIL_PASSWORD` et les logs de l'API (l'erreur y est journalisée) |
