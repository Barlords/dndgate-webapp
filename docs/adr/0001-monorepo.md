# ADR 0001 : monorepo

Date : 2026-10-19. Statut : accepté.

## Contexte

Le projet était réparti sur plusieurs dépôts (api, front, mobile). Le contrat d'API
va évoluer et alimenter plusieurs clients, le front est réécrit en Angular, et le
projet est porté par une seule personne.

## Décision

Un seul dépôt, organisé en `apps/` (api, web, mobile), `contracts/` et `docs/`.
CI par chemin, sans outil de build monorepo au départ (Maven pour l'API, npm pour le web).

## Conséquences

- Un changement de contrat et ses adaptations tiennent dans une seule PR.
- Les anciens dépôts sont archivés, leur historique n'est pas importé (il contient des secrets).
- Si une partie doit être découplée plus tard (équipe, rythme de release), son dossier
  peut être extrait vers un dépôt dédié.
