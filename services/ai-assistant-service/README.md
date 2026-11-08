# ai-assistant-service

> Conception initiale pour la prochaine réunion — documentation uniquement, aucune API implémentée.

## Sources et statut

Ce document s'appuie sur le [README racine](../../README.md), les [consignes du dépôt](../../AGENTS.md), le [POM parent](../../pom.xml), le [service client existant](../customer-service/README.md) et l'extrait du cahier des charges partagé dans la conversation « Résumé cahier des charges » (tableau des huit microservices, lignes 7 et 8).

L'extrait décrit le rôle de l'assistant et du serveur MCP ; le cahier des charges intégral et un compte rendu formel de réunion ne sont pas disponibles dans le dépôt. La conversation confirme le périmètre des trois livrables, mais ses suggestions de fonctionnalités et de priorités ne constituent pas des exigences validées.

- **Périmètre de cette contribution :** ce README, [DESIGN.md](DESIGN.md) et [docs/openapi.yaml](docs/openapi.yaml).
- **Rôle décrit dans l'extrait :** gérer le bot Telegram, comprendre les demandes grâce au LLM, orchestrer les outils MCP et produire des réponses adaptées.
- **Propositions à valider :** fonctionnalités détaillées, chemins HTTP, DTO propres à l'assistant, sécurité, limites et choix techniques d'intégration.

## Rôle et responsabilités

L'AI Assistant Service porte le dialogue avec l'utilisateur. Il reçoit les messages Telegram, prépare les échanges avec le modèle de langage (LLM), sélectionne parmi les outils autorisés ceux nécessaires à la demande et restitue une réponse à partir des résultats obtenus.

Le MCP Tool Server expose et exécute les outils sécurisés. Il valide leurs paramètres et les droits sur les ressources, puis appelle les API métier. L'assistant ne possède ni les données clients, ni les événements, réservations, paiements ou billets ; il n'accède pas directement à leurs bases.

Les confirmations, rappels et notifications métier relèvent de Notification Service dans l'extrait fourni. La coordination d'un éventuel bot Telegram partagé reste à décider.

## Fonctions envisagées

| Fonction | Statut et dépendances |
|---|---|
| Recevoir un message Telegram et retourner une réponse conversationnelle | Rôle prévu ; réception par webhook proposée, traitement et envoi à concevoir. |
| Comprendre une demande avec un LLM et orchestrer les outils MCP autorisés | Rôle prévu ; fournisseur, transport et catalogue MCP à valider. |
| Aider à rechercher des événements et consulter leurs détails | Capacités prévues côté MCP dans l'extrait ; contrats Event/MCP absents de cette branche. |
| Consulter les réservations autorisées | Capacité prévue côté MCP ; dépend des contrats Booking/MCP, de l'identité et des autorisations à définir. |
| Recommandations et recherche augmentée par récupération de documents (RAG) | Mentionnées dans le README racine ; données, pertinence et périmètre à confirmer. |
| Dialogue par HTTP indépendant de Telegram | Proposition facultative pour les échanges techniques et les essais ; besoin produit à valider. |
| Liaison compte Telegram–TicketFlow et mémoire conversationnelle | Questions ouvertes ; aucun stockage, schéma de données ou endpoint dédié retenu ici. |

La création de réservations, le paiement et l'émission de billets ne sont pas définis comme outils de l'assistant dans l'extrait disponible. Aucun contrat d'écriture métier n'est proposé.

## Technologies et dépendances

| Élément | État vérifié ou choix à confirmer |
|---|---|
| Java / Spring Boot / Spring Cloud | Le POM parent fixe Java 21, Boot 4.0.8 et Cloud 2025.1.2. Aucun nouveau module Maven n'est ajouté. |
| Spring AI | Prévu dans la feuille de route ; version et compatibilité LLM/MCP avec le socle à vérifier avant toute implémentation. |
| Telegram Bot API | Dépendance externe envisagée ; webhook HTTPS et secret d'origine proposés. Aucune configuration ni clé ajoutée. |
| Fournisseur LLM | Non choisi ; modèle, coût, délais et politique de données à valider. |
| MCP Tool Server | Dépendance prévue par le sujet, absente de la branche ; outils, schémas, transport et sécurité à convenir avec son responsable. |
| API métier | Accès via MCP envisagé. Seul Customer Service possède une API métier observable ici ; aucune intégration n'est réalisée. |
| Keycloak / Gateway / Eureka / Config Server | Keycloak figure dans la feuille de route ; les trois modules d'infrastructure existent. Identité, routage et configuration de l'assistant restent à définir. |
| OpenFeign / Resilience4J / Kafka | Présents dans la feuille de route globale ; leur emploi par l'assistant n'est pas une décision acquise. Aucun client, topic ou événement ajouté. |
| Base de données | Aucun besoin de base propre à l'assistant validé ; aucun schéma, migration ou stockage vectoriel ajouté. |

## Contrat prévisionnel

[docs/openapi.yaml](docs/openapi.yaml) est une proposition OpenAPI 3.0.3 autonome, destinée à être relue avant développement. Toutes ses opérations portent le marqueur `x-statut: a-valider`.

| Méthode | Chemin proposé | Usage |
|---|---|---|
| POST | `/api/ai-assistant/telegram/webhook` | Recevoir un `Update` Telegram ; acquittement distinct de la réponse conversationnelle. |
| POST | `/api/ai-assistant/chat` | Interface HTTP de dialogue facultative, synchrone, protégée par jeton selon une proposition à valider. |

Ces chemins ne sont ni déployés ni routés. Aucun hôte, port, rôle Keycloak, nom d'outil MCP ou endpoint métier futur n'est fixé. Le format d'erreur `application/problem+json` est proposé pour rester cohérent avec le `ProblemDetail` de Customer Service.

## État du dossier

Ce dossier ne contient ni POM, ni application Spring Boot, ni configuration de lancement, ni contrôleur. L'assistant n'est pas déclaré dans le réacteur Maven ni dans Docker Compose ; aucune commande de démarrage ne s'applique à ce stade.

Avant développement, l'équipe doit trancher les [questions ouvertes du DESIGN](DESIGN.md#questions-ouvertes-pour-la-réunion) et valider le contrat. La validité d'une spécification ne démontre pas le fonctionnement d'une API.
