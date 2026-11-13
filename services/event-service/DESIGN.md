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
- `POST /api/v1/internal/events/{id}/tiers/{tierId}/reserve` : réserve N places
- `POST /api/v1/internal/events/{id}/tiers/{tierId}/release` : libère N places

**Kafka publié** : EventPublished, EventClosed, EventCancelled, EventUpdated.
**Kafka consommé** : aucun pour l'instant (à rediscuter selon le choix de gestion de capacité).

## Choix techniques et points à discuter
- Qui décrémente la capacité : proposition = l'Event Service (source de vérité), appelé par le Booking via `reserve`/`release`.
- Concurrence : verrouillage optimiste (`@Version` sur TicketTier) ou requête SQL atomique (`UPDATE ... WHERE available >= :qty`).
- Libération de la capacité à l'expiration/annulation d'une réservation : appel Feign ou événement Kafka (BookingExpired) ?
- Format des événements Kafka (JSON, champs communs, clé de partition).
- Endpoints internes : comment les protéger (rôle de service, non exposés par la Gateway).
- Stockage des images : URL simple ou service dédié.

## Idempotence des réservations

- Chaque réservation est identifiée par un bookingId.
- Event Service conserve les réservations de capacité traitées.
- Une réservation répétée avec les mêmes données ne modifie pas le stock une deuxième fois.
- Une réservation répétée avec des données différentes retourne HTTP 409.
- Une libération doit correspondre à une réservation existante.
- Une libération répétée ne restitue pas les places une deuxième fois.
- Une libération contradictoire retourne HTTP 409.
- Les modifications de capacité et l'enregistrement de l'opération seront effectués dans une même transaction.