# DnDGate - Front web

Front Angular 22 (composants standalone, signaux, SCSS, Vitest). Il ne parle à l'API que par le client généré depuis `contracts/openapi.yaml`.

## Prérequis

- Node 24 (au moins 24.15, exigé par Angular CLI 22) et npm
- L'API lancée en local sur `http://localhost:4000` pour `npm start` (voir le README racine)

## Commandes (depuis `apps/web`)

```
npm ci              # installer les dépendances
npm start           # serveur de dev (proxy /dndgate-api -> http://localhost:4000)
npm run build       # build de production (dist/web)
npm test            # tests en mode watch
npm run test:ci     # tests + couverture (seuil lignes 80 %)
npm run lint        # ESLint (angular-eslint)
npm run e2e         # tests Playwright
```

## Client d'API généré

Le client est produit par `ng-openapi-gen` dans `src/app/core/api/generated/` (non versionné, ignoré par git). `start`, `build`, `test` et `test:ci` le régénèrent automatiquement ; pour le forcer :

```
npm run generate:api
```

Après un changement de `contracts/openapi.yaml`, relancer cette commande. Aucun appel HTTP vers l'API ne doit passer ailleurs que par ce client.

## Images à remplacer

Les visuels de l'accueil et l'emblème sont des emplacements provisoires, à remplacer par les images définitives (emplacements précisés au fil des tâches dans `src/assets/`).

## Docker

```
docker build -f apps/web/Dockerfile -t dndgate-web .   # depuis la racine du dépôt
```

L'image sert le build statique avec nginx (repli SPA vers `index.html`).
