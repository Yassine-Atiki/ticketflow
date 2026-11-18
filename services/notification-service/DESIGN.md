# Conception de Notification Service

FR-014 prévoit les confirmations, rappels et changements importants via les canaux configurés, en respectant les préférences et désabonnements. Customer Service possède les coordonnées et préférences. Notification Service possède les futures demandes de notification, leur canal, les tentatives et le résultat.

## Flux cible

1. Consommer un événement dont le producteur et le schéma versionné sont confirmés, avec un identifiant d'événement stable.
2. Résoudre le destinataire et ses préférences via un contrat Customer Service. En cas d'échec de résolution, différer l'envoi selon une politique bornée.
3. Persister une demande dédupliquée avant d'acquitter l'événement Kafka. Lors d'une redélivrance, retrouver la demande existante.
4. Traiter la demande avec un adaptateur de canal. Consigner le résultat, borner les reprises et prévoir un traitement des messages invalides ou épuisés. Définir avec le fournisseur la stratégie d'idempotence pour les cas où une réponse d'envoi est perdue.

## Contrats à valider

- Événement de confirmation initial, producteur, topic, version et identifiant permettant de retrouver le destinataire. Le cahier des charges cite notamment `BookingConfirmed`, `TicketIssued` et `NotificationRequested` à titre indicatif.
- API Customer Service pour les préférences et les désabonnements.
- Canaux retenus et moyen de vérifier les envois en développement sans contacter de vrais destinataires.

Le broker partagé est disponible sur `localhost:9092` depuis l'hôte et `kafka:29092` depuis Docker. Le raccordement du consommateur et les migrations métier dépendent de la validation des contrats ci-dessus.
