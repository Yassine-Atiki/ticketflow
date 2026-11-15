# Ticket Service — conception cible

## Références et statut des décisions

Le cahier des charges TicketFlow est la référence fonctionnelle. Les mentions **Exigence** ci-dessous reprennent son intention; les mentions **Proposition** sont des choix d’architecture à faire valider. Le code actuel ne réalise pas encore ces éléments : il contient une classe de démarrage Spring Boot, `application.properties` minimal et un test de contexte.

## Rôle, responsabilités et limites

**Exigences (sections 3.5, 6.1, 7.1; FR-010, FR-011, FR-013)**

- Un billet unique est émis après confirmation de la réservation et rattaché à la commande.
- Le client peut le consulter dans son espace.
- Un code de billet ne doit pas exposer de données personnelles; le code de contrôle est non prédictible et le statut du billet doit être vérifiable.
- Ticket Service est le propriétaire du domaine Ticket et de ses données.

**Limites** : Booking reste propriétaire de la réservation et de sa confirmation; Payment reste propriétaire du paiement; Customer reste propriétaire du profil. Ticket Service ne confirme ni paiement ni réservation, ne gère pas l’inventaire, n’effectue pas de jointures inter-bases et ne stocke pas de données bancaires.

## Architecture interne proposée

Découpage logique recommandé, à matérialiser lorsque l’implémentation Java commencera :

- adaptateur Kafka entrant pour `BookingConfirmed`;
- cas d’usage d’émission, consultation et vérification; la consommation est reportée au futur check-in;
- modèle et règles métier Ticket;
- ports de persistance, d’horloge et de génération aléatoire;
- adaptateur JPA/PostgreSQL et adaptateurs Kafka sortants;
- contrôleurs REST, DTO et validation pour les parcours synchrones.

Les contrôleurs REST ne doivent pas permettre à un utilisateur externe d’émettre un ticket pour un booking arbitraire. L’émission est déclenchée par un événement de domaine fiable; si une API interne administrative est jugée nécessaire, elle doit être restreinte et idempotente.

## Modèle de données proposé

Une ligne `Ticket` représente une unité d’accès. Si une ligne confirmée a une quantité supérieure à un, l’émission produit le nombre de billets convenu par unité. Proposition d’attributs :

| Attribut | Rôle |
|---|---|
| `id` | Identifiant interne, UUID |
| `bookingId` | Référence opaque vers Booking, sans relation JPA inter-service |
| `bookingLineId` | Référence opaque facultative pour rattacher le billet à la ligne confirmée |
| `ownerSubject` | Sujet stable de l’identité client, selon le claim OIDC retenu |
| `eventId` | Référence opaque facultative pour les informations d’affichage nécessaires |
| `controlCodeCiphertext` | Code de contrôle chiffré avec chiffrement authentifié; jamais conservé en clair |
| `controlCodeKeyVersion` | Identifiant/version de la clé de chiffrement nécessaire à la restitution et à la rotation |
| `status` | État du billet selon le cycle de vie défini ci-dessous |
| `issuedAt` | Horodatage UTC d’émission |
| `usedAt` | Champ futur si le check-in et l’état `USED` sont ajoutés |
| `version` | Version optimiste éventuelle pour les transitions concurrentes |

L’identifiant d’événement et le libellé affiché ne doivent être stockés que si nécessaires au billet et à sa consultation. Pas de relation ORM vers les entités d’autres services. La base dédiée et PostgreSQL sont la cible; aucun datasource ni migration n’est présent à ce jour.

## États et transitions proposés

Le cahier des charges impose une vérification de statut, mais ne fixe pas la liste des états ni les règles d’annulation. Proposition minimale :

| État courant | Événement/commande | État suivant |
|---|---|---|
| absent | réservation confirmée admissible | `ISSUED` |
| `ISSUED` | annulation confirmée par le domaine propriétaire | `CANCELLED` (à confirmer) |
| `ISSUED`, `CANCELLED` | vérification du code | même état (opération sans mutation) |

Le check-in et l’état `USED` sont reportés à une évolution future; aucun endpoint décrit ici ne consomme un billet. Ne pas traiter un événement de paiement réussi comme équivalent à `BookingConfirmed`. Les transitions terminales, l’impact d’un remboursement et les cas de réémission doivent être arbitrés avec Booking/Payment.

## Émission, code et idempotence

**Exigences** : émission après confirmation; unicité; code non prédictible; confirmation unique (FR-010); événements identifiés et consommés de façon idempotente (sections 7.3 et 8).

**Propositions** :

1. Dédupliquer chaque réservation confirmée avec une contrainte unique persistée sur la clé métier retenue, par exemple `(bookingId, bookingLineId, unitIndex)`. Cette clé doit être cohérente avec l’identifiant d’événement et le contrat de réservation.
2. Générer un secret aléatoire cryptographiquement sûr avec suffisamment d’entropie; ne pas dériver le code d’un UUID séquentiel, de l’identifiant client ou du booking.
3. Le contrat OpenAPI restitue le même code lors des consultations autorisées du billet. Le stocker sous forme chiffrée avec chiffrement authentifié (par exemple AES-256-GCM); le champ chiffré comprend le nonce et le tag d’authentification, et `controlCodeKeyVersion` identifie la clé utilisée. Le code brut et les clés ne sont jamais journalisés.
4. Garder les clés hors de la base et du dépôt, dans un gestionnaire de secrets approprié à l’environnement. Pour la rotation, chiffrer les nouvelles émissions avec la nouvelle version, relire temporairement les anciennes versions et rechiffrer les enregistrements existants avant de retirer l’ancienne clé. La plateforme de gestion des clés et la politique de rétention doivent être confirmées avant l’implémentation.
5. Publier `TicketIssued` avec un identifiant d’événement unique, version, horodatage UTC, identifiant d’agrégat et corrélation; ne jamais y inclure le code brut ni des données personnelles non nécessaires.
6. Persister le ticket et l’intention de publication dans une outbox transactionnelle, ou démontrer une garantie équivalente. Sans cela, une panne après commit DB mais avant Kafka perdrait `TicketIssued`.

La contrainte de déduplication, l’émission et l’outbox doivent être transactionnelles localement. Une redélivrance identique doit être acquittée sans créer un second billet ni deuxième effet externe. La politique de retry et de dead-letter doit être bornée et documentée.

## Flux `BookingConfirmed` → émission → `TicketIssued`

1. Booking Service publie un événement contenant au minimum un `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `aggregateId`, `correlationId` et les références minimales de réservation confirmée. Champs et topic exacts à valider.
2. Ticket Service valide la version et les champs; un événement invalide ne crée pas de billet.
3. Le consommateur vérifie l’idempotence persistante; il n’interroge pas la base Booking.
4. Ticket Service génère les billets pour les unités confirmées, persiste les billets et les messages d’outbox.
5. Un relay publie `TicketIssued`, avec clé de partition stable liée au booking ou ticket si l’ordre est nécessaire. La clé précise reste à convenir.
6. Les notifications peuvent consommer l’événement; leur indisponibilité ne doit ni annuler la réservation ni supprimer les billets.

Kafka est déclaré dans le POM, mais aucun broker, topic, sérialiseur, listener, stratégie retry/DLQ ni outbox n’est actuellement configuré dans ce module.

## API REST proposée

Le préfixe `/api/v1` suit la convention du cahier des charges (section 7.2). Les opérations détaillées sont dans `src/main/resources/static/openapi.yaml` et demeurent des propositions, car aucun endpoint de Ticket Service n’est actuellement implémenté.

- `GET /api/v1/me/tickets` : l’identité propriétaire provient du jeton vérifié; aucun `customerId` n’est accepté comme autorité.
- `GET /api/v1/me/tickets/{ticketId}` : consultation réservée au propriétaire authentifié; le service vérifie l’appartenance au sujet JWT et ne permet pas à un autre client de consulter le billet.
- `POST /api/v1/tickets/verify` : vérifie le code et le statut sans mutation; cette opération ne réalise pas le check-in.

Le check-in, l’opération de consommation et l’état `USED` ne font pas partie du contrat actuel; ils nécessitent une décision produit et une mise à jour coordonnée du contrat et du Gateway avant leur implémentation.

Réponses d’erreur structurées et statuts HTTP cohérents sont décrits dans OpenAPI. Ne pas révéler si un ticket appartenant à un autre client existe; les politiques 403/404 doivent être choisies en cohérence avec le modèle de menace.

## Sécurité et autorisations

OIDC/JWT est prévu par le cahier des charges. Le service doit valider lui-même signature, expiration, issuer et audience selon le realm et le contrat convenus, même si le Gateway filtre les appels. Il doit dériver le propriétaire d’un claim stable validé. L’autorisation opérateur pour vérifier un billet doit reprendre les rôles/scopes établis par le produit; une autorisation distincte pour le futur check-in restera à définir. Le PDF ne nomme pas ces rôles, donc aucun nom n’est imposé ici.

Ne jamais journaliser le bearer token ni le code de contrôle. Limiter les données de réponse aux champs nécessaires. La vérification d’un code ne doit pas constituer une autorisation de consulter un profil ou une réservation complète.

## Gestion des erreurs

- événement inconnu/invalide : rejet contrôlé, observabilité sans contenu sensible, politique de quarantaine/DLQ à définir;
- rejeu déjà traité : succès idempotent sans émission supplémentaire;
- dépendance Kafka/base indisponible : ne pas acquitter prématurément; reprise bornée avec traitement d’échec;
- code inconnu : réponse générique de vérification invalide;
- ticket annulé : le résultat de vérification restitue `CANCELLED` sans mutation. L’état `USED` et les conflits de consommation ne s’appliquent qu’après l’ajout futur du check-in;
- erreur interne : réponse structurée sans stack trace ni secret.

## Vérifications à ajouter à l’implémentation

Tests de contrat HTTP et validation, accès propriétaire et refus sans/avec jeton invalide, redélivrance Kafka, contrainte concurrente d’émission, outbox lors d’une panne entre commit et publication, et traitement des messages invalides/épuisés. L’atomicité de consommation ne s’ajoutera qu’avec le futur check-in. Aucun de ces scénarios métier n’est actuellement testé par le seul `contextLoads`; celui-ci utilise le profil `test` et n’exige pas Config Server, Eureka ou PostgreSQL.

## Points à confirmer

- Schéma/version et topic de `BookingConfirmed`, nombre de tickets par ligne, clé de partition et contrat exact de `TicketIssued`.
- Identifiant client stable dans JWT, audience/issuer, realm et rôles/scopes d’accès au contrôle.
- États, politique d’annulation/remboursement, réémission, et sémantique exacte de la vérification versus consommation.
- Base et migrations; gestionnaire de clés, politique de rotation et rétention du code chiffré.
- Configuration centralisée, ports, Gateway et disponibilité des services connexes. Le dossier `payment-service` et son README ne sont pas présents dans les sources suivies examinées.
