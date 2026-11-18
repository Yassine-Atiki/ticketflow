# Booking Service

Microservice de **réservation** de la plateforme TicketFlow.
Il gère le cycle de vie des réservations : création, contrôle de disponibilité, retenue temporaire, expiration, confirmation et annulation.

> Statut : conception initiale (voir [DESIGN.md](DESIGN.md) et [openapi.yml](openapi.yml)).

---

## 1. Rôle et responsabilités

- Gérer les réservations des clients.
- Vérifier la disponibilité des places et **empêcher la surréservation**.
- Gérer les **réservations temporaires** (statut `PENDING_PAYMENT`), leur **expiration** et leur **confirmation**.
- Publier les événements métier de réservation sur Kafka (`BookingCreated`, `BookingConfirmed`, `BookingExpired`).
- Réagir aux résultats de paiement (`PaymentSucceeded`, `PaymentFailed`).

**Ce que le service ne fait pas** : il ne gère pas les événements/capacités du catalogue (Event Service), ni les paiements (Payment Service), ni l'émission des billets (Ticket Service), ni l'envoi des notifications (Notification Service).

## 2. Fonctionnalités

| # | Fonctionnalité | Priorité | État |
|---|---|---|---|
| 1 | Créer une réservation (`PENDING_PAYMENT`, durée limitée) | P1 | À développer |
| 2 | Vérifier la capacité de façon atomique | P1 | À développer |
| 3 | Calculer et figer le prix (snapshot) | P1 | À développer |
| 4 | Expirer automatiquement les réservations non payées | P1 | À développer |
| 5 | Confirmer une réservation après paiement réussi (une seule fois) | P1 | À développer |
| 6 | Annuler une réservation selon la politique | P2 | À développer |
| 7 | Lister mes réservations (`/api/v1/me/bookings`) | P1 | À développer |
| 8 | Consulter le détail d'une réservation (propriétaire uniquement) | P1 | À développer |
| 9 | Idempotence de la création via l'en-tête `Idempotency-Key` | P1 | À développer |
| 10 | Publication / consommation d'événements Kafka | P1 | À développer |

## 3. Technologies

| Domaine | Technologie |
|---|---|
| Langage | Java 21 |
| Framework | Spring Boot 4.x, Spring Cloud |
| Persistance | Spring Data JPA, PostgreSQL 17 (`booking_db`) |
| Migrations | Flyway |
| Appels synchrones | OpenFeign |
| Résilience | Resilience4j (timeouts, circuit breaker, retry borné) |
| Asynchrone | Apache Kafka (Spring for Apache Kafka) |
| Découverte / config | Eureka Client, Spring Cloud Config Client |
| Documentation API | springdoc-openapi (Swagger UI) |
| Observabilité | Spring Boot Actuator |
| Tests | JUnit 5, Spring Boot Test, Testcontainers |

## 4. Dépendances avec les autres microservices

| Service | Type | Utilisation |
|---|---|---|
| **Event Service** | OpenFeign (sortant) | Lire l'événement, les catégories de billets et les prix ; réserver / libérer la capacité |
| **Customer Service** | OpenFeign (sortant) | Valider l'existence et l'éligibilité du client |
| **Payment Service** | Kafka (entrant) | Consommation de `PaymentSucceeded` et `PaymentFailed` |
| **Ticket Service** | Kafka (sortant) | Consomme `BookingConfirmed` pour émettre les billets |
| **Notification Service** | Kafka (sortant) | Consomme `BookingConfirmed`, `BookingExpired`, etc. |
| **API Gateway** | Routage | Expose `/api/v1/bookings/**` et `/api/v1/me/bookings/**` |
| **Eureka / Config Server** | Infrastructure | Enregistrement et configuration |

## 5. Configuration et lancement

### Prérequis

Depuis la racine du dépôt, démarrer PostgreSQL et Kafka avec Docker Desktop.

```powershell
docker compose -f infra/docker-compose.yml up -d --wait postgres kafka
```

Démarrer Eureka et Config Server depuis IntelliJ, ou lancer les commandes suivantes dans deux terminaux séparés avec le JDK 21.

```powershell
.\mvnw.cmd -pl services/discovery-server spring-boot:run
.\mvnw.cmd -pl services/config-server spring-boot:run
```

Si le dépôt de configuration requiert une authentification, fournir au processus Config Server les variables `SPRING_CLOUD_CONFIG_SERVER_GIT_USERNAME` et `SPRING_CLOUD_CONFIG_SERVER_GIT_PASSWORD` avec des identifiants GitHub autorisés.

Config Server charge les paramètres de Booking depuis `booking-service.yml`, sur la branche `main` de `ticketflow-config`. Vérifier que `http://localhost:8888/booking-service/default` renvoie ce fichier dans `propertySources` avant de lancer Booking.

### Lancement depuis IntelliJ ou Maven

Configurer `BOOKING_DB_USER` et `BOOKING_DB_PASSWORD` avec les identifiants de la base de développement créée par Compose. Depuis IntelliJ, exécuter `BookingServiceApplication` avec ces variables et le JDK 21. En terminal, définir les mêmes variables dans la session puis lancer le service depuis la racine.

```powershell
.\mvnw.cmd -pl services/booking-service spring-boot:run
```

L'import Config Server est obligatoire. `CONFIG_SERVER_URL` vaut par défaut `http://localhost:8888`. La configuration distante utilise PostgreSQL sur `127.0.0.1:5433`, Kafka sur `localhost:9092` et Eureka sur `localhost:8761`.

### Lancement de Booking dans Docker

Laisser Config Server et Eureka démarrés sur l'hôte. Compose les joint via `host.docker.internal` et utilise les noms de services `postgres` et `kafka` pour les connexions internes.

```powershell
docker compose -f infra/docker-compose.yml up -d --build booking-service
```

### Vérifications

- Santé de Booking sur `http://localhost:8083/actuator/health`.
- Instance `BOOKING-SERVICE` au statut `UP` sur `http://localhost:8761/eureka/apps/BOOKING-SERVICE`.
- Swagger UI sur `http://localhost:8083/swagger-ui/index.html` et contrat statique sur `http://localhost:8083/openapi.yml`.

Le test de démarrage tourne avec H2 en mémoire et sans Config Server, Eureka ou Kafka.

```powershell
.\mvnw.cmd -B -ntp -pl services/booking-service -am verify
```

## 6. API (résumé)

| Méthode | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/bookings` | Créer une réservation |
| GET | `/api/v1/bookings/{id}` | Détail (propriétaire uniquement) |
| GET | `/api/v1/me/bookings` | Mes réservations (paginé) |
| POST | `/api/v1/bookings/{id}/cancel` | Annuler une réservation |

Contrat complet : [openapi.yml](openapi.yml).

## 7. Documentation associée

- [DESIGN.md](DESIGN.md) : conception technique détaillée
- [openapi.yml](openapi.yml) : contrat OpenAPI

## 8. Règles de contribution

- Branche de travail : `feature/booking-service` (depuis `develop`), Pull Request vers `develop`.
- Aucun mot de passe, token ou fichier `.env` dans Git.
- Aucun accès direct aux tables d'un autre service.
