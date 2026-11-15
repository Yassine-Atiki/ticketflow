
# Payment Service — Technical Design

## 1. Objectif

Le Payment Service gère le cycle de vie des tentatives de paiement associées aux réservations TicketFlow.

Il sépare les règles métier de paiement des fournisseurs externes.

Ses responsabilités principales sont :

- Initier une tentative de paiement.
- Suivre le statut d'un paiement.
- Simuler un paiement dans un environnement de démonstration.
- Gérer les annulations et les remboursements.
- Recevoir les notifications des fournisseurs de paiement.
- Publier les résultats métier via Kafka.

Le service ne stocke jamais les numéros de cartes bancaires.

## 2. Architecture interne

Architecture en couches :

```text
Payment Service
│
├── controller/
│   ├── PaymentController
│   └── PaymentWebhookController
│
├── service/
│   ├── PaymentService
│   └── RefundService
│
├── repository/
│   ├── PaymentRepository
│   └── RefundRepository
│
├── entity/
│   ├── PaymentAttempt
│   └── Refund
│
├── dto/
│   ├── PaymentInitiationRequest
│   ├── PaymentResponse
│   ├── PaymentSimulationRequest
│   ├── PaymentCancellationRequest
│   ├── RefundRequest
│   └── RefundResponse
│
├── adapter/
│   └── PaymentProviderAdapter
│
├── client/
│   └── BookingClient
│
├── messaging/
│   └── PaymentEventPublisher
│
├── config/
│
└── exception/
    └── GlobalExceptionHandler
```

Cette structure constitue une proposition d'organisation.
Les classes seront créées progressivement selon les besoins de l'implémentation.

## 3. Modèle de données

### Entité : PaymentAttempt

| Attribut | Type | Description |
|---|---|---|
| id | UUID | Identifiant du paiement |
| bookingId | UUID | Référence de réservation |
| amount | BigDecimal | Montant du paiement |
| currency | String | Devise |
| status | Enum | Statut du paiement |
| provider | String | Fournisseur ou SIMULATED |
| externalReference | String | Référence externe facultative |
| idempotencyKey | String | Clé d'idempotence |
| createdAt | LocalDateTime | Date de création |
| updatedAt | LocalDateTime | Dernière modification |

### Entité : Refund

Une entité Refund distincte pourra être utilisée pour gérer les remboursements partiels ou totaux.

| Attribut | Type | Description |
|---|---|---|
| id | UUID | Identifiant du remboursement |
| paymentId | UUID | Référence du paiement |
| amount | BigDecimal | Montant remboursé |
| status | Enum | Statut du remboursement |
| reason | String | Motif du remboursement |
| createdAt | LocalDateTime | Date de création |

La structure définit une proposition initiale.
Les types et contraintes devront être validés avec Booking Service et l'équipe.

### Statuts proposés

Statuts de paiement :

- PENDING
- SUCCEEDED
- FAILED
- CANCELLED
- PARTIALLY_REFUNDED
- REFUNDED

Statuts de remboursement :

- PENDING
- SUCCEEDED
- FAILED

Les transitions doivent être contrôlées par le service métier.

## 4. Règles métier

1. Un paiement doit être associé à une réservation valide.
2. Le montant doit être strictement positif.
3. Le montant et la devise doivent être cohérents avec les informations validées de la réservation.
4. Le montant définitif ne doit pas être fourni librement par le client : il doit être vérifié auprès de Booking Service.
5. Une même demande idempotente ne doit pas créer plusieurs effets de paiement.
6. Un paiement réussi ne doit pas être marqué comme échoué par une nouvelle requête.
7. La simulation est autorisée uniquement dans un environnement de démonstration.
8. Les callbacks externes doivent être authentifiés et vérifiés.
9. Un paiement n'entraîne pas directement la création d'un billet : Booking Service coordonne la confirmation de réservation.
10. Un remboursement ne peut pas dépasser le montant restant remboursable.
11. Les remboursements partiels et multiples doivent être contrôlés.

## 5. Communication interservices

### Communication synchrone

OpenFeign peut être utilisé pour communiquer avec Booking Service afin de :

- Vérifier l'existence de la réservation.
- Vérifier que la réservation est payable.
- Récupérer le montant définitif.
- Récupérer la devise.

Les contrats exacts doivent être validés avec le responsable de Booking Service.

Payment Service ne doit pas accéder directement à la base de données de Booking Service.

### Communication asynchrone

Kafka permet de publier les résultats métier.

Événements prévus :

- PaymentSucceeded
- PaymentFailed

Chaque événement doit contenir au minimum :

- eventId
- eventType
- schemaVersion
- occurredAt
- paymentId
- bookingId
- correlationId

Les événements doivent être versionnés et les consommateurs doivent être idempotents.

Booking Service reste responsable de la confirmation de la réservation après réception et validation de PaymentSucceeded.

Le Ticket Service pourra ensuite traiter l'événement de confirmation de réservation selon le contrat défini par l'équipe.

## 6. API REST

Les endpoints prévus sont :

| Méthode | Endpoint | Description |
|---|---|---|
| POST | /api/payments | Initier un paiement |
| GET | /api/payments | Lister les paiements |
| GET | /api/payments/booking/{bookingId} | Consulter les paiements d'une réservation |
| GET | /api/payments/{id} | Consulter un paiement |
| POST | /api/payments/{id}/simulate | Simuler un paiement |
| POST | /api/payments/{id}/cancel | Annuler un paiement |
| POST | /api/payments/{id}/refund | Demander un remboursement |
| POST | /api/payments/webhooks/{provider} | Recevoir une notification fournisseur |

La simulation est réservée aux environnements de démonstration et doit être désactivée en production.

La spécification détaillée est définie dans `openapi.yaml`.

## 7. Sécurité

Les mécanismes de sécurité seront intégrés dans une prochaine étape avec l'équipe.

Les exigences prévues sont :

- Authentification OIDC/JWT.
- Autorisation vérifiée côté API.
- Accès aux paiements limité au propriétaire ou aux rôles autorisés.
- Simulation désactivée en production.
- Webhooks vérifiés par signature ou mécanisme d'authentification du fournisseur.
- Aucun numéro de carte bancaire stocké.
- Aucun secret dans Git ou dans Config Server.

## 8. Gestion des erreurs

| Situation | HTTP |
|---|---|
| Paiement trouvé | 200 |
| Paiement créé | 201 |
| Demande acceptée pour traitement | 202 |
| Identifiant inexistant | 404 |
| Données invalides | 400 |
| Utilisateur non authentifié | 401 |
| Accès interdit | 403 |
| Conflit de statut | 409 |
| Erreur interne | 500 |

Les codes 401 et 403 représentent les réponses prévues après intégration de la sécurité.

## 9. Résilience

- Timeouts pour les appels externes.
- Circuit Breaker avec Resilience4j.
- Retries limités et uniquement lorsque l'opération est sûre.
- Idempotence des requêtes.
- Corrélation des logs.
- Gestion des événements Kafka en échec.
- Prévention des traitements multiples des webhooks.

## 10. Points à discuter en réunion

1. Quelle est la responsabilité exacte de Booking Service dans la création du paiement ?
2. Quel service fournit le montant définitif et la devise ?
3. Quel mécanisme commun d'authentification JWT sera utilisé ?
4. Quel format commun adopter pour les événements Kafka ?
5. Quel scénario de simulation doit être présenté ?
6. Quel fournisseur de paiement pourrait être intégré ultérieurement ?
7. Comment gérer les remboursements partiels et leurs transitions ?
8. Quel contrat utiliser entre Payment Service et Booking Service ?

## 11. État actuel

### Infrastructure

- Spring Boot : opérationnel.
- PostgreSQL : opérationnel.
- Config Server : opérationnel.
- Eureka : opérationnel.
- API Gateway : route configurée.

### Métier

- Contrôleur : à développer.
- Entité PaymentAttempt : à développer.
- Entité Refund : à développer.
- Repository : à développer.
- Service métier : à développer.
- Intégration OpenFeign : à développer.
- Intégration Kafka : à développer.
- Sécurité : à intégrer avec l'équipe.
- OpenAPI : spécification initiale préparée, à valider.

Cette conception constitue une première proposition technique à valider collectivement.
