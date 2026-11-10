# DESIGN - Customer Service

## 1. Architecture interne
controllers -> service -> repos -> PostgreSQL (`customer_db`).
DTO (records) séparés des entités JPA. Erreurs en ProblemDetail (RFC 9457) via `ApiExceptionHandler`.

## 2. Entités et modèle de données
| Table | Colonnes principales |
|---|---|
| `customers` | id, firstName, lastName, email (unique), phone, createdAt |
| `customer_preferences` | id, customerId (unique), language, notificationChannel, marketingOptIn |

Le lien préférences -> client passe par `customerId` (pas de relation JPA) pour ne pas modifier l'entité `Customer` existante.

## 3. Règles métier
- Email normalisé (trim, minuscules) et unique : 409 sinon.
- Préférences absentes : valeurs par défaut `fr`, `EMAIL`, `false` (non persistées).
- Langue limitée à fr, en, ar. Client inconnu : 404.

## 4. Interactions avec les autres services
- Config Server : configuration non secrète (`customer-service.yml`).
- Eureka : enregistrement du service.
- OpenFeign (entrant, prévu) : Booking vérifie le client, Notification lit les préférences.
- Kafka : aucun usage à ce stade.

## 5. Choix techniques
- PostgreSQL, `ddl-auto: update` (convention de l'équipe).
- Tests sur H2 avec un `application.properties` de test isolé du Config Server.

## 6. Points à discuter
- Sécurité Keycloak : rôles et noms exacts à reprendre du realm, endpoints à protéger, `customerId` à dériver du JWT plutôt que du chemin.
- Suppression d'un client : les préférences restent orphelines (à traiter).
- Endpoints internes `/internal/...` non exposés par le Gateway ?
- Migrations versionnées (Flyway) à la place de `ddl-auto: update` ?
- Liaison Telegram (code à usage unique, révocable) : emplacement et sécurité de la confirmation.
- Contrat OpenAPI : `static/openapi.yaml` (Ticket, Payment) ou `docs/openapi.yaml` (AI Assistant) ?