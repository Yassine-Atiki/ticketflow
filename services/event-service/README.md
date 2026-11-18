# Event Service

## Rôle
Gérer les événements : catégories, lieux, dates, tarifs et capacités.
Permettre la création, la modification, la publication, la fermeture et la recherche d'événements.


## État d'avancement

Cette PR contient le squelette du Event Service
et une proposition de contrat API OpenAPI.

### Réalisé
- Création du module Event Service
- Configuration Maven et Java 21
- Intégration Spring Cloud Config
- Configuration PostgreSQL
- Préparation Kafka et Eureka
- Documentation OpenAPI et Swagger UI
- Configuration des tests avec H2

### À développer
- Entités JPA et repositories
- Controllers et services métier
- Gestion des événements
- Gestion des catégories et des lieux
- Gestion des billets et des capacités
- Réservation et libération idempotentes
- Publication des messages Kafka
- Migrations Flyway

## Important

Les endpoints décrits dans Swagger constituent
un contrat API proposé.

Ils ne sont pas encore implémentés et ne peuvent
pas être utilisés pour effectuer des opérations métier.

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