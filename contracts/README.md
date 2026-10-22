# contracts

`openapi.yaml` décrit l'API et fait foi pour les clients `web` et `mobile` : le client Angular en est généré.
Toute modification d'endpoint passe par ce fichier, dans la même PR que le code de l'API.

Le fichier est **produit par l'API**, pas écrit à la main (springdoc, configuration dans `OpenApiConfig`) :

```
cd apps/api
./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true
```

réécrit `contracts/openapi.yaml` ; la différence se relit comme du code. Sans `-Dopenapi.update=true`, le même test **échoue** si le fichier et le code divergent, donc la CI (`./mvnw verify`) refuse une PR qui change une route sans mettre le contrat à jour.

Ce que le test garantit en plus :

- chaque opération a un `operationId` unique et lisible (c'est le nom de la méthode du client généré), un résumé et un groupe (`tags`) ;
- toute opération renvoie, en cas d'erreur, le même schéma `Problem` (RFC 9457, avec `correlationId`) ;
- une opération est déclarée **sans jeton** exactement quand l'API n'en demande pas (le test appelle chaque route sans jeton) ;
- les réponses sont du JSON ;
- aucun couple de routes ne peut désigner la même URL.

## `asyncapi.yaml` : les sujets WebSocket

`asyncapi.yaml` décrit ce que l'API diffuse en WebSocket (STOMP) : `/topic/adventure/chat/{adventureId}` (un `MessageDto`) et `/topic/adventure/webrtc/{adventureId}` (un `WebRTCMessage`), ainsi que la connexion, les droits d'abonnement et leurs limites. Contrairement à `openapi.yaml`, il est **écrit à la main** ; ses charges utiles pointent vers les schémas de `openapi.yaml`, un message n'a donc qu'une définition. `AsyncApiContractTest` vérifie que les deux sujets y figurent et que chaque schéma référencé existe ; `BroadcastPayloadTest` vérifie que les contrôleurs diffusent bien ces types. Un nouveau sujet se déclare dans ce fichier.

L'API ne sert pas ce contrat en production (`springdoc.api-docs.enabled=false`) ; en développement : `GET /dndgate-api/v3/api-docs` (ou `.yaml`).

Limites connues : une création de personnage est un `multipart/form-data` dont la partie JSON n'a pas d'`encoding` explicite, et les `DELETE` portent un corps de confirmation ; certains générateurs demandent un réglage pour ces deux cas.
