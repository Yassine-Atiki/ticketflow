# MCP Tool Server — conception

## Statut

Ce module est l'adaptateur MCP de TicketFlow. Il valide les arguments, applique une confirmation pour les opérations sensibles, puis délègue aux API des autres services. Il ne possède pas de données métier et n'applique pas les règles de stock, de prix ou de paiement.

Seul `GET /api/customers/{id}` est implémenté aujourd'hui par Customer Service et activé ici (`contract-confirmed: true`). Les contrats Event, Ticket, Booking et Payment sont publiés sur les branches de l'équipe, mais leurs contrôleurs ne sont pas encore là. Leurs outils restent donc désactivés.

Il n'existe pas de Notification Service. Les outils de notification ont été retirés.

## Frontière

| Élément | Responsabilité de ce service | Limite |
|---|---|---|
| Outils MCP | Schéma, validation, confirmation, en-têtes | Pas de décision métier |
| Customer | `GET /api/customers/{id}` | Le profil n'authentifie pas l'appelant |
| Booking | `POST /api/v1/bookings`, `GET /api/v1/bookings/{id}`, `POST /api/v1/bookings/{id}/cancel` | Le propriétaire est le sujet du jeton Bearer. `eventId`, `ticketTierId` et `bookingId` sont des UUID. `Idempotency-Key` de création : 100 caractères |
| Ticket | `GET /api/v1/me/tickets` | Même règle d'identité. Pas d'endpoint de disponibilité publié |
| Payment | `POST /api/payments`, `GET /api/payments/{id}` | Corps `bookingId` UUID et `provider` optionnel. `Idempotency-Key` obligatoire, 255 caractères |
| Event | `GET /api/v1/events`, `GET /api/v1/events/{id}` | Paramètres `q`, `categoryId`, `city`, `dateFrom`, `dateTo`, `page`, `size`, `sort`. L'identifiant d'événement du contrat Event est un entier |

`create_booking`, `cancel_booking` et `initiate_payment` ne partent pas tant que `userConfirmed` n'est pas `true`.

## Identité

Booking et Ticket exigent un en-tête `Authorization: Bearer ...` avant l'appel. Ce serveur ne vérifie ni la signature, ni l'émetteur, ni l'audience : Keycloak n'est pas encore branché. Il refuse seulement l'appel si le jeton est absent. Customer Service n'a pas cette exigence sur le profil.

## Activation

`contract-confirmed` reste `false` tant que le service cible n'implémente pas le chemin configuré. Un contrat OpenAPI seul ne suffit pas.

## Hôte MCP

Les hôtes acceptés sont `localhost`, `127.0.0.1` et `mcp-tool-server`, pour qu'un assistant dans Compose puisse appeler `http://mcp-tool-server:8090/mcp`. Un autre hôte reçoit HTTP 421.

## Documentation HTTP

Le contrat statique est `src/main/resources/static/openapi.yaml`. Swagger UI le charge. `GET /api/tools` reste le catalogue exécutable. `/mcp` est du JSON-RPC Streamable HTTP, pas une API REST des services métier.
