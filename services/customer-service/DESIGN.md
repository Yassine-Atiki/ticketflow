# DESIGN - Customer Service

## 1. Architecture interne
controllers -> service -> repos -> base de données.
DTO (records) séparés des entités JPA. Erreurs en ProblemDetail (RFC 9457) via `ApiExceptionHandler`.

## 2. Entités et modèle de données
| Table | Colonnes principales |
|---|---|
| `customers` | id, firstName, lastName, email (unique), phone, createdAt |
| `customer_preferences` | id, customer_id (FK vers customers, unique, ON DELETE CASCADE), language, notificationChannel, marketingOptIn |

Le lien préférences -> client est une relation JPA `@OneToOne` côté `CustomerPreferences` uniquement : l'entité `Customer` existante n'est pas modifiée. La colonne `customer_id` et le contrat JSON (`customerId`) sont inchangés.

## 3. Règles métier
- Email normalisé (trim, minuscules) et unique : 409 sinon.
- Préférences absentes : valeurs par défaut `fr`, `EMAIL`, `false` (non persistées).
- Langue limitée à fr, en, ar. Client inconnu : 404.
- Suppression d'un client : ses préférences sont supprimées par la base (cascade).
- Création concurrente des préférences : verrou pessimiste sur la ligne du client (`SELECT ... FOR UPDATE`), donc une seule ligne et jamais de 500.

## 4. Interactions avec les autres services
- Config Server : configuration non secrète (`customer-service.yml`).
- Eureka : enregistrement du service.
- OpenFeign (entrant, prévu) : Booking vérifie le client, Notification lit les préférences.
- Kafka : aucun usage à ce stade.

## 5. Choix techniques
- Par défaut (`application.properties`) : H2 en mémoire, `ddl-auto=create-drop`. Les tests utilisent H2 avec un `application.properties` de test isolé du Config Server.
- PostgreSQL (`customer_db`, `ddl-auto: update`, convention de l'équipe) : fourni par `customer-service.yml` du dépôt `ticketflow-config`, une fois la PR de configuration fusionnée sur `main`. Identifiants de développement uniquement.
- Tests d'intégration sur H2 (contrainte unique, FK, verrou) ; pas de Testcontainers à ce stade.

## 6. Points à discuter
- Sécurité Keycloak : rôles et noms exacts à reprendre du realm, endpoints à protéger, `customerId` à dériver du JWT plutôt que du chemin.
- Endpoints internes `/internal/...` non exposés par le Gateway ?
- Migrations versionnées (Flyway) à la place de `ddl-auto: update` ?
- Tests sur PostgreSQL réel (Testcontainers) pour valider le verrou `FOR UPDATE`.
- Externalisation des mots de passe, même pour le développement.
- Liaison Telegram (code à usage unique, révocable) : emplacement et sécurité de la confirmation.
- Contrat OpenAPI : `static/openapi.yaml` (Ticket, Payment) ou `docs/openapi.yaml` (AI Assistant) ?