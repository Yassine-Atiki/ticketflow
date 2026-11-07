
# Payment Service — TicketFlow

## 1. Présentation

Payment Service est le microservice responsable de la gestion des paiements dans la plateforme TicketFlow.

Il permet de simuler les paiements dans l'environnement de démonstration et prépare l'intégration future avec un fournisseur de paiement externe.

Il constitue la source d'autorité pour les tentatives de paiement, leurs statuts et les remboursements associés.

## 2. Responsabilités

- Gérer les tentatives de paiement.
- Associer chaque paiement à une réservation.
- Vérifier les informations de réservation auprès de Booking Service.
- Simuler les paiements réussis, échoués et annulés.
- Gérer les statuts des paiements.
- Gérer les remboursements totaux et partiels.
- Garantir l'idempotence des opérations.
- Vérifier les callbacks des fournisseurs externes.
- Publier les événements métier sur Kafka.
- Préparer l'intégration d'un prestataire externe.

**Important :** TicketFlow ne stocke ni ne traite directement les numéros de carte bancaire.

Le Payment Service ne confirme pas directement les réservations et ne génère pas les billets. Booking Service reste responsable de la confirmation des réservations.

## 3. Fonctionnalités prévues

| Fonctionnalité | Description |
|---|---|
| Création | Initialiser une tentative de paiement |
| Simulation | Simuler succès, échec ou annulation |
| Consultation | Consulter un paiement |
| Statut | Suivre l'état du paiement |
| Idempotence | Éviter les opérations en double |
| Annulation | Annuler un paiement selon son statut |
| Remboursement | Gérer les remboursements totaux ou partiels |
| Webhooks | Recevoir les notifications des fournisseurs |
| Intégration externe | Préparer un adaptateur de paiement |
| Événements | Publier les résultats sur Kafka |

## 4. Technologies

- Java 21
- Spring Boot 4
- Spring Cloud
- Spring Data JPA
- Spring Security / OAuth2 Resource Server (intégration prévue)
- Spring Cloud Config
- Netflix Eureka
- Spring Cloud Gateway
- Apache Kafka
- OpenFeign
- PostgreSQL 17
- Docker / Docker Compose
- OpenAPI / Swagger UI

## 5. Architecture et dépendances

- API Gateway : point d'entrée des requêtes externes.
- Config Server : configuration centralisée.
- Eureka : découverte des microservices.
- Booking Service : vérification des réservations et récupération du montant et de la devise.
- Kafka : communication événementielle.
- PostgreSQL : stockage des paiements et remboursements.

Le Payment Service conserve la propriété exclusive de ses données.

Aucun accès direct à la base de données des autres microservices n'est autorisé.

Les communications interservices se font via API REST/OpenFeign ou événements Kafka.

## 6. Configuration locale

**Port :** `8082`

**Base de données :** `payment_db`

**URL PostgreSQL :**

`jdbc:postgresql://localhost:5433/payment_db`

**Nom du service Eureka :**

`payment-service`

**Route Gateway :**

`/api/payments/**`

**Base URL via Gateway :**

`http://localhost:8080`

Les mots de passe et autres secrets doivent être fournis par l'environnement d'exécution et ne doivent pas être publiés dans Git.

## 7. Endpoints API prévus

| Méthode | Endpoint | Description |
|---|---|---|
| POST | `/api/payments` | Initier un paiement |
| GET | `/api/payments` | Lister les paiements |
| GET | `/api/payments/booking/{bookingId}` | Consulter les paiements d'une réservation |
| GET | `/api/payments/{id}` | Consulter un paiement |
| POST | `/api/payments/{id}/simulate` | Simuler un paiement |
| POST | `/api/payments/{id}/cancel` | Annuler un paiement |
| POST | `/api/payments/{id}/refund` | Demander un remboursement |
| POST | `/api/payments/webhooks/{provider}` | Recevoir une notification fournisseur |

La simulation est réservée à l'environnement de démonstration et doit être désactivée en production.

La spécification détaillée est disponible dans `src/main/resources/static/openapi.yaml`.

## 8. Lancement

Depuis le dossier du projet :

```bash
cd services/payment-service
mvn spring-boot:run
```

### Prérequis

- JDK 21
- Maven
- PostgreSQL disponible
- Config Server disponible sur le port 8888
- Discovery Server disponible sur le port 8761

## 9. Vérification

### Health check

```http
GET http://localhost:8082/actuator/health
```

### Eureka

`http://localhost:8761`

### API Gateway

`http://localhost:8080`

### Swagger UI

`http://localhost:8082/swagger-ui/index.html`

## 10. État d'avancement

### Déjà réalisé

- Module Spring Boot.
- Intégration Maven au projet parent.
- Configuration centralisée.
- Connexion PostgreSQL.
- Enregistrement Eureka.
- Route API Gateway.
- Première spécification OpenAPI.

### À développer

- Entité PaymentAttempt.
- Entité Refund.
- Repository.
- Service métier.
- Contrôleurs REST.
- DTO et validations.
- Intégration de la sécurité JWT avec l'équipe.
- Intégration OpenFeign.
- Intégration Kafka.
- Tests unitaires et d'intégration.
- Implémentation des endpoints documentés.

## 11. Évolution future

L'architecture doit permettre de remplacer le paiement simulé par un adaptateur vers un fournisseur externe sans modifier les règles métier centrales.

L'intégration future devra respecter les exigences de sécurité, d'idempotence et de traçabilité des transactions.
