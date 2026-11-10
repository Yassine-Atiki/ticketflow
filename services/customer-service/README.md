# customer-service

> Customer profiles: the first business microservice of TicketFlow.

## Concept illustrated
A standalone Spring Boot REST service with its own database. It owns customer
data (name, email, phone). Authentication will be delegated to Keycloak later;
this service only manages the business profile.

## Overview
- **Port:** 8081
- **Database:** H2 in-memory (recreated on every start)
- **Depends on:** nothing

| Method | Path | Description |
|---|---|---|
| POST | `/api/customers` | Create a customer (201 + Location) |
| GET | `/api/customers/{id}` | Get one customer |
| GET | `/api/customers` | List customers |
| PUT | `/api/customers/{id}` | Update a customer |
| DELETE | `/api/customers/{id}` | Delete a customer |

Errors follow RFC 9457 (`ProblemDetail`): 400 validation, 404 not found, 409 duplicate email.

## Package layout
    controllers/   REST endpoints
    service/       business logic
    repos/         Spring Data repositories
    entities/      JPA entities
    dto/           request/response records (the API contract)
    exceptions/    domain exceptions and their HTTP mapping

## Run locally
    ./mvnw -pl services/customer-service spring-boot:run

    curl -i -X POST localhost:8081/api/customers \
      -H 'Content-Type: application/json' \
      -d '{"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com"}'

## Run with Docker
    docker compose -f infra/docker-compose.yml up --build customer-service

## Run the tests
    ./mvnw -pl services/customer-service test

## Exercise
1. Add a `GET /api/customers?email=...` search endpoint with a test.
2. Restart the service and list customers. Why is the list empty? What would
   you change to keep the data?

## Common pitfalls
- Exposing JPA entities directly in the API couples your contract to your
  database schema: that is why we use `CustomerResponse`.
- `open-in-view` is disabled on purpose to avoid lazy-loading surprises.
- An in-memory database is fine for learning but never shared between
  services: each service owns its data.



## Rôle dans TicketFlow
Gère les comptes clients, les profils et les préférences de notification.
Source de vérité des clients pour Booking, Notification et AI Assistant.
L'authentification est déléguée à Keycloak (intégration prévue).

## Fonctionnalités
- CRUD client (`/api/customers`), email unique
- Préférences : langue (fr/en/ar), canal (EMAIL/TELEGRAM), opt-in marketing
- Prévu : liaison du compte Telegram (FR-017)

## Technologies
Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL 17 (`customer_db`), Spring Cloud Config, Eureka, springdoc-openapi.

## Dépendances avec les autres services
| Service | Relation |
|---|---|
| Config Server | fournit la configuration (`customer-service.yml`, dépôt `ticketflow-config`) |
| Discovery Server | enregistrement Eureka |
| Booking Service | appellera `GET /api/customers/{id}` pour vérifier le client |
| Notification Service | appellera `GET /api/customers/{id}/preferences` |
| AI Assistant | liaison Telegram (prévue) |

## Configuration et lancement
1. `docker compose -f infra/docker-compose.yml up -d postgres`
2. Démarrer `config-server` (8888), puis `discovery-server` (8761)
3. `./mvnw -pl services/customer-service spring-boot:run`
4. Swagger UI : http://localhost:8081/swagger-ui.html
5. Contrat : `src/main/resources/static/openapi.yaml`

Les tests utilisent H2 et ne nécessitent ni Config Server ni Eureka.