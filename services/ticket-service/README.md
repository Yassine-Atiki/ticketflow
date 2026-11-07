# Ticket Service

## Présentation

Le Ticket Service est le service propriétaire du domaine de billetterie de TicketFlow. Sa cible est d’émettre les billets après confirmation d’une réservation, de permettre au client authentifié de retrouver ses billets et de fournir une vérification de leur code et de leur statut. Il ne traite pas les paiements et ne réserve pas de capacité.

> **État du dépôt :** le module ne contient actuellement qu’une classe Spring Boot, une configuration minimale et un test de démarrage. Aucun endpoint, modèle de billet, repository, consommateur Kafka, configuration de base de données ou contrat OpenAPI n’est implémenté. Ce document décrit la cible et distingue les choix proposés des exigences du cahier des charges.

## Responsabilités et frontières

- Posséder les données et règles de cycle de vie des billets.
- Émettre un billet ou plusieurs billets uniques à partir d’une réservation confirmée.
- Permettre au client de consulter ses billets et permettre aux opérateurs autorisés de contrôler leur statut et leur utilisation.
- Publier `TicketIssued` après émission, selon le contrat d’événement à convenir.
- Ne pas accéder aux bases de Customer, Booking, Payment ou Event. Les identifiants et informations utiles sont reçus par événements ou contrats API.
- Ne stocker aucune donnée bancaire. Le client propriétaire est déterminé par le contexte JWT validé et/ou par la donnée de réservation fiable; il n’est pas choisi par un paramètre fourni par le client.

## Technologies

Le POM du module déclare Java 21, Spring Boot hérité du parent (4.0.8 dans le POM racine examiné), Spring Cloud 2025.1.2, Spring MVC, validation, Spring Data JPA, Kafka, OpenFeign, Eureka, Config Client, PostgreSQL au runtime et les starters de test Spring Boot. La présence d’une dépendance ne signifie pas que la fonctionnalité correspondante est configurée ou utilisée. Les versions restent pilotées par les POM du projet.

## Architecture et dépendances

Flux cible principal :

1. Booking Service confirme une réservation après validation du paiement.
2. Il publie `BookingConfirmed` sur Kafka.
3. Ticket Service consomme l’événement de manière idempotente et crée les billets concernés dans sa propre base.
4. Ticket Service publie `TicketIssued` et le client retrouve les billets via l’API.

Les événements indicatifs et conventions générales sont définis aux sections 6.2 et 7.3 du cahier des charges. Le nom du topic, le schéma détaillé, la clé de partition et les garanties de livraison restent à convenir. L’émission ne doit pas dépendre d’un accès direct à une base d’un autre service. OpenFeign n’est pas requis pour le flux nominal si `BookingConfirmed` contient les références minimales nécessaires; tout appel synchrone éventuel doit avoir un contrat, des délais et un comportement d’indisponibilité explicites.

## Configuration et ports

La configuration suivie contient `spring.application.name=ticket-service`, l’import du Config Server à `http://localhost:8888`, le label `main` et `spring.jpa.open-in-view=false`. Les valeurs chargées du Config Server `main` ont été vérifiées pendant `verify` : port applicatif `8083`, datasource `jdbc:postgresql://localhost:5433/ticket_db` et Eureka `http://localhost:8761/eureka/`. Elles ne sont pas codées dans les fichiers locaux du module. Aucune configuration Kafka n’a été observée dans le module; les topics et paramètres du broker restent à confirmer.

Les mots de passe, secrets de signature ou jetons doivent venir de la configuration runtime/secrets et ne doivent pas être commités. La cible PostgreSQL est recommandée par le cahier des charges; le détail du schéma et des migrations reste à définir.

## Installation et lancement

Depuis la racine, avec un JDK 21 :

```powershell
.\mvnw.cmd -B -ntp -pl services/ticket-service -am verify
```

Le service importe actuellement le Config Server au démarrage. Il faut donc rendre disponible la configuration `ticket-service` sur le label `main` (ou fournir un profil local approprié) avant de lancer l’application. Une fois cette dépendance satisfaite :

```powershell
.\mvnw.cmd -pl services/ticket-service spring-boot:run
```

Le démarrage nécessite le Config Server et les dépendances locales définies par sa configuration; le test exécuté a notamment pu joindre Config Server, PostgreSQL et Eureka. La configuration d’un broker Kafka n’est pas établie par les fichiers examinés.

## Communication avec les autres services

| Service | Interaction cible | Frontière |
|---|---|---|
| Booking Service | `BookingConfirmed` vers Ticket Service; corrélation de réservation | Événement Kafka versionné, consommateur idempotent |
| Client / Gateway | Consultation des propres billets via REST | JWT OIDC validé côté API; propriétaire tiré du sujet/claims convenus |
| Contrôle d’accès | Vérification et consommation du billet par un opérateur autorisé | API proposée, autorisation à préciser sans inventer de rôle |
| Notification Service | `TicketIssued` peut alimenter une notification | Kafka; échec de notification sans annuler le billet |

## Documentation API

Le contrat proposé OpenAPI 3.1 se trouve dans [`src/main/resources/static/openapi.yaml`](src/main/resources/static/openapi.yaml). Ses routes `/api/v1` sont des propositions de conception, pas des routes actuellement exposées. Le contrat définit la consultation des billets du client, la consultation d’un billet autorisée, la vérification d’un code et le contrôle d’utilisation. L’émission asynchrone est décrite par le flux Kafka et n’est pas exposée comme une commande publique permettant de choisir librement une réservation ou un propriétaire.

Swagger UI est fourni par `springdoc-openapi-starter-webmvc-ui` en version `3.0.3`, compatible avec la ligne Spring Boot 4.0.x. La configuration dans `application.properties` lui fait charger le contrat statique `/openapi.yaml` et désactive l’URL de démonstration par défaut. L’interface est servie sur `/swagger-ui/index.html`; sa disponibilité dépend du démarrage du service et du port configuré.

## Tests

Le dépôt contient un test Spring `contextLoads` uniquement. Les vérifications de contrat et règles à ajouter avec l’implémentation devraient couvrir au minimum :

- l’idempotence de `BookingConfirmed` et l’absence de billets en double après redélivrance;
- la création attendue de billets et la publication fiable de `TicketIssued` (outbox ou garantie équivalente);
- l’accès d’un client à ses seuls billets, ainsi que les refus sans jeton, avec jeton invalide et pour un autre propriétaire;
- code invalide, billet déjà utilisé et consommation concurrente;
- compatibilité du schéma d’événement et comportement sur message invalide/épuisé.

Ces cas ne sont pas couverts par le code présent. La commande Maven ci-dessus valide le réacteur jusqu’au module; elle ne remplace pas des tests métier ou d’intégration.
