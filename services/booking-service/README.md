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
| Langage | Java (17 actuellement dans le `pom.xml`, cible 21 selon le cahier des charges) |
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
- JDK 17 ou 21, Maven
- Docker Desktop (pour PostgreSQL)
- Config Server et Eureka démarrés (sinon voir le profil `dev`)

### Base de données
```bash
# depuis la racine du dépôt ticketflow
docker compose -f infra/docker-compose.yml up -d postgres
```
- Hôte : `127.0.0.1`, port : `5433`
- Base : `booking_db`
- Utilisateur / mot de passe : identifiants dédiés au service (fournis par l'infra, **jamais committés**)

### Variables d'environnement

| Variable | Description | Exemple |
|---|---|---|
| `BOOKING_DB_USER` | Utilisateur PostgreSQL du service | *(à définir)* |
| `BOOKING_DB_PASSWORD` | Mot de passe PostgreSQL | *(secret, hors Git)* |
| `KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |
| `EUREKA_URL` | Adresse Eureka | `http://localhost:8761/eureka` |

### Configuration externalisée
Les paramètres non secrets sont dans le dépôt `ticketflow-config`, fichier `booking-service.yml` (port, URL de base, durée de retenue, limite par commande, etc.).

| Paramètre | Valeur par défaut proposée |
|---|---|
| `server.port` | À confirmer avec l'équipe |
| `booking.hold-duration` | `PT15M` (15 minutes) |
| `booking.max-quantity-per-order` | `10` |

### Lancement
```bash
cd services/booking-service
mvn spring-boot:run
```

### Accès
- Swagger UI : `http://localhost:<port>/swagger-ui.html`
- Spécification : `http://localhost:<port>/v3/api-docs`
- Santé : `http://localhost:<port>/actuator/health`
- Via le Gateway : `http://localhost:<port-gateway>/api/v1/bookings`

## 6. API (résumé)

| Méthode | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/bookings` | Créer une réservation |
| GET | `/api/v1/bookings/{id}` | Détail (propriétaire uniquement) |
| GET | `/api/v1/me/bookings` | Mes réservations (paginé) |
| POST | `/api/v1/bookings/{id}/cancel` | Annuler une réservation |

Contrat complet : [openapi.yml](openapi.yml).

## 7. Documentation associée

- [DESIGN.md](DESIGN.md) : conception technique
- [openapi.yml](openapi.yml) : contrat OpenAPI

## 8. Règles de contribution

- Branche de travail : `feature/booking-service` (depuis `develop`), Pull Request vers `develop`.
- Aucun mot de passe, token ou fichier `.env` dans Git.
- Aucun accès direct aux tables d'un autre service.