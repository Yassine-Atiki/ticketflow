# Event Service : Conception technique

## Architecture interne
Couches : controller → service → repository.
DTO séparés des entités, mappers, gestion globale des exceptions (`@RestControllerAdvice`).

## Modèle de données
| Entité | Champs principaux |
|---|---|
| Event | id, title, description, categoryId, venueId, startDate, endDate, status, imageUrl, createdBy, createdAt, updatedAt |
| Category | id, name (unique), description |
| Venue | id, name, address, city, capacity |
| TicketTier | id, event_id, name, price, currency, totalCapacity, availableCapacity, version |

Relations : Event N—1 Category, Event N—1 Venue, Event 1—N TicketTier.

## Statuts et transitions
- DRAFT → PUBLISHED (publication)
- PUBLISHED → CLOSED (fermeture)
- PUBLISHED → CANCELLED (annulation)
- DRAFT → supprimé (seul cas de suppression)

Toute autre transition est refusée (409).

## Règles métier
- Publication possible seulement si les champs obligatoires sont renseignés et qu'au moins une catégorie de billet existe.
- startDate dans le futur, endDate postérieure à startDate.
- Prix ≥ 0, capacité > 0, availableCapacity jamais négative ni supérieure à totalCapacity.
- Un événement CLOSED ou CANCELLED n'est plus réservable.
- Seuls les événements PUBLISHED apparaissent dans la recherche publique.
- Création/modification réservées aux rôles ORGANIZER et ADMIN ; l'identité vient du JWT.
- Un événement avec des réservations ne peut pas être supprimé, seulement annulé.

## Interactions avec les autres services
**Appelé par OpenFeign (Booking Service)** :
- `GET /api/v1/events/{id}` : détail, statut, prix, disponibilité
- `POST /api/v1/internal/events/{id}/capacity/reservations` : réserve toutes les lignes
- `DELETE /api/v1/internal/events/{id}/capacity/reservations/{bookingId}` : libère toutes les lignes

**Kafka publié** : EventPublished, EventClosed, EventCancelled, EventUpdated.
**Kafka consommé** : aucun pour l'instant (à rediscuter selon le choix de gestion de capacité).

## Choix techniques et points à discuter
- Qui décrémente la capacité : proposition = l'Event Service (source de vérité), appelé par le Booking via `reserve`/`release`.
- Concurrence : verrouillage optimiste (`@Version` sur TicketTier) ou requête SQL atomique (`UPDATE ... WHERE available >= :qty`).
- Libération à l'expiration/annulation : appel Feign DELETE proposé par Booking ; pas de double restitution via Kafka.
- Format des événements Kafka (JSON, champs communs, clé de partition).
- Endpoints internes : comment les protéger (rôle de service, non exposés par la Gateway).
- Stockage des images : URL simple ou service dédié.

## Alignement du contrat avec Booking

Référence consultée : `feature/booking-service`, commit
`cb47e4a440b81d71a2af4a0ced2fad4b8d9078ae`, DESIGN §5.1 et Swagger.
Les deux services sont au stade squelette/contrat ; les endpoints métier ne sont
pas encore implémentés. Les choix de contrat ci-dessous (atomicité, clé de
déduplication, réponses et rejeux) sont confirmés avec Meriem, selon la confirmation
rapportée par Oumaima dans cet échange.

- Event et TicketTier : UUID dans les paramètres et réponses, comme les références
  `eventId` et `ticketTierId` de Booking. `bookingId` est aussi un UUID.
- Category (classification de l'événement) et Venue restent int64 dans ce contrat ;
  Category est distinct de TicketTier (catégorie de billet).
- POST sur le chemin indiqué plus haut, corps
  `{bookingId, lines: [{ticketTierId, quantity}]}` ; première réussite : 201.
- DELETE sur le chemin indiqué plus haut, sans corps ; les lignes et quantités
  proviennent de la réservation persistée ; première réussite : 200.
- Réponse proposée pour les deux :
  `{bookingId, eventId, status, lines: [{ticketTierId, quantity, availableCapacity}]}`,
  avec `status` RESERVED ou RELEASED. Les capacités sont un instantané enregistré,
  pas une disponibilité temps réel lors d'un rejeu.
- Chaque ligne doit avoir une quantité positive et un ticketTierId distinct (400
  sinon), appartenant au même événement (404 sinon).
- Réservation multi-catégories **globale atomique** dans Event : si une seule ligne
  manque de places, 409 INSUFFICIENT_CAPACITY et aucune modification du stock ni
  réservation persistée. Pas de compensation des catégories partiellement réservées.
- Événement non réservable : 422 EVENT_NOT_BOOKABLE. L'authentification et les droits
  sont documentés en 401/403 ; la sécurité reste à implémenter.
- La transaction Event ne couvre pas Booking : si Booking échoue après le POST,
  sa compensation prévue appelle DELETE pour libérer toutes les lignes.

## Idempotence des réservations

- Clé de déduplication persistante **globale : bookingId seul**, pour toutes les
  catégories du Booking ; pas une clé par catégorie. Un Booking concerne un événement.
- Empreinte comparée : eventId et ensemble des couples (ticketTierId, quantity),
  trié par identifiant ; l'ordre des lignes ne change pas leur signification.
- Premier POST réussi : 201, RESERVED. Rejeu identique à l'état RESERVED : 200 avec
  la réponse initiale persistée, sans nouvelle décrémentation, même si le stock ou
  le statut de l'événement a changé depuis. Rechercher le rejeu avant de revalider
  les règles de vente actuelles.
- Même bookingId avec un événement ou des lignes différents : 409 IDEMPOTENCY_CONFLICT.
- Premier DELETE : 200, RELEASED, restitution atomique de toutes les lignes, même
  si l'événement est fermé/annulé. Rejeu identique : 200 avec la réponse initiale de
  libération persistée, sans seconde restitution.
- DELETE avec bookingId inconnu : 404. bookingId connu associé à un autre événement :
  409 IDEMPOTENCY_CONFLICT (libération contradictoire). Aucune quantité fournie par
  l'appelant : une libération partielle n'est pas proposée.
- POST après libération : 409 ; utiliser un nouveau bookingId pour une nouvelle réservation.
- Un POST échoué ne consomme pas la clé. Conserver la clé et l'état après libération
  afin d'empêcher les rejeux tardifs de réserver à nouveau.
- À implémenter : contrainte unique sur bookingId, sérialisation des opérations sur
  cette clé, contrôle concurrent du stock et transaction locale unique pour modifier
  toutes les capacités, enregistrer l'état et la réponse. Aucun de ces comportements
  n'est encore exécutable dans ce squelette.

## Coordination Kafka avec Booking

La branche Booking consultée propose Kafka 3.9.0 annoncé en `kafka:9092` ; la
branche Event annonce actuellement Kafka 3.7.0 en `localhost:9092`. Une annonce
unique ne couvre pas les deux modes de lancement.

Principe confirmé avec Meriem, selon le retour d’Oumaima : un seul broker/bloc
Compose, deux listeners clients (hôte `localhost:9092`, conteneurs `kafka:29092`)
et un listener contrôleur séparé. La version Kafka retenue et la personne chargée
du fichier partagé ne sont pas précisées dans cet échange. Aligner ensuite les
bootstrap-servers dans la configuration centralisée selon le mode de lancement.

Le fichier partagé `infra/docker-compose.yml` et le dépôt de configuration ne sont
pas modifiés dans ce correctif limité à Event Service. La mise en œuvre de la
configuration Kafka commune et son démarrage restent à finaliser.

## Vérifications du correctif

- `mvnw.cmd -B -ntp -pl services/event-service -am verify` : BUILD SUCCESS,
  2 tests réussis (contexte H2 et accès HTTP Swagger UI / contrat OpenAPI),
  sans Config Server, PostgreSQL, Eureka ou Kafka externes.
- Contrat YAML chargé et références locales résolues ; `git diff --check` réussi.
- Tentative de démarrage du Config Server existant : échec du clonage du dépôt
  `ticketflow-config`, authentification Git requise par JGit sans CredentialsProvider.
  Aucun secret ni modification du Config Server ajouté dans ce correctif.
- Docker est arrêté sur le poste ; PostgreSQL Compose n'est donc pas disponible.
  Le démarrage normal Event, la récupération réelle des paramètres distants et
  Swagger dans ce mode restent à confirmer après résolution de ces prérequis.
- Les tests valident le squelette et la disponibilité de Swagger ; ils ne prouvent
  pas les garanties métier documentées, dont l'implémentation est prévue ultérieurement.
