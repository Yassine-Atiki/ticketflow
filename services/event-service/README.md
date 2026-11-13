# Event Service

## Rôle
Gérer les événements : catégories, lieux, dates, tarifs et capacités.
Permettre la création, la modification, la publication, la fermeture et la recherche d'événements.

## Fonctionnalités
- Créer, modifier, supprimer (brouillon) un événement
- Publier / fermer / annuler un événement
- Gérer les catégories et les lieux
- Gérer les catégories de billets (tarif + capacité par catégorie)
- Rechercher et filtrer les événements publiés (mot-clé, catégorie, ville, date) avec pagination
- Consulter la disponibilité et le prix d'un événement (utilisé par le Booking Service)
- Réserver / libérer de la capacité de façon atomique (endpoints internes)
- Publier des événements Kafka (EventPublished, EventClosed, EventCancelled)

## Technologies
Java 21, Spring Boot, Spring Data JPA, PostgreSQL 17, Flyway, Kafka,
Eureka Client, Spring Cloud Config, springdoc-openapi (Swagger UI).

## Dépendances avec les autres services
| Service | Interaction |
|---|---|
| Booking Service | Appelle l'Event Service (OpenFeign) pour lire l'événement, le prix et réserver/libérer la capacité |
| Customer Service | Fournit l'identité/les rôles via le JWT (organisateur, admin) |
| Notification Service | Peut consommer les événements Kafka (annulation, modification) |
| AI Assistant / MCP Tool Server | Consultent les événements via l'API de recherche |

## Configuration et lancement
- Port : 8082
- Base de données : `event_db` (PostgreSQL, 127.0.0.1:5433)
- Configuration : `ticketflow-config/event-service.yml`
- Identifiants DB : `event_user` / `event_dev` (créés par `infra/postgres/init/01-init-databases.sql`)

Prérequis : PostgreSQL, Config Server et Eureka démarrés.

```bash
docker compose -f infra/docker-compose.yml up -d postgres
cd services/event-service
mvn spring-boot:run
```

Swagger UI : http://localhost:8082/swagger-ui.html