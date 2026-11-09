# ai-assistant-service

> Socle Spring Boot démarrable et contrat API proposé pour revue d’équipe ; aucune logique métier ni intégration Telegram, LLM ou MCP implémentée.

## Sources et statut

Ce document s'appuie sur le [README racine](../../README.md), les [consignes du dépôt](../../AGENTS.md), le [POM parent](../../pom.xml), le [service client existant](../customer-service/README.md) et l'extrait du cahier des charges partagé dans la conversation « Résumé cahier des charges » (tableau des huit microservices, lignes 7 et 8).

L'extrait décrit le rôle de l'assistant et du serveur MCP ; le cahier des charges intégral et un compte rendu formel de réunion ne sont pas disponibles dans le dépôt. La conversation confirme le périmètre des trois livrables, mais ses suggestions de fonctionnalités et de priorités ne constituent pas des exigences validées.

- **État implémenté :** module Maven déclaré dans le réacteur parent, application Spring Boot, configuration Config Client/Eureka, test de contexte isolé et Swagger UI affichant [docs/openapi.yaml](docs/openapi.yaml).
- **Conception à valider :** ce README, [DESIGN.md](DESIGN.md) et les opérations proposées dans le contrat OpenAPI.
- **Rôle décrit dans l'extrait :** gérer le bot Telegram, comprendre les demandes grâce au LLM, orchestrer les outils MCP et produire des réponses adaptées.
- **Propositions à valider :** fonctionnalités détaillées, chemins HTTP, DTO propres à l'assistant, sécurité, limites et choix techniques d'intégration.

## Rôle et responsabilités

Dans l’architecture visée, l’AI Assistant Service portera le dialogue avec l’utilisateur : réception des messages Telegram, préparation des échanges avec le modèle de langage (LLM), orchestration des outils autorisés et restitution d’une réponse fondée sur leurs résultats. Ces fonctions ne sont pas encore implémentées.

Le MCP Tool Server prévu par le sujet devra exposer et exécuter les outils sécurisés. Il valide leurs paramètres et les droits sur les ressources, puis appelle les API métier. L'assistant ne possède ni les données clients, ni les événements, réservations, paiements ou billets ; il n'accède pas directement à leurs bases.

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
| Java / Spring Boot / Spring Cloud | Le [POM parent](../../pom.xml) fixe Java 21, Boot 4.0.8 et Cloud 2025.1.2 et déclare le module `ai-assistant-service`. |
| Spring Web MVC / Validation / Actuator | Dépendances présentes ; classe de démarrage et santé technique disponibles, sans contrôleur métier. |
| Springdoc OpenAPI | Version 3.0.1 présente ; Swagger UI charge le contrat proposé, avec exécution des opérations désactivée. |
| Spring AI | Prévu dans la feuille de route ; version et compatibilité LLM/MCP avec le socle à vérifier avant toute implémentation. |
| Telegram Bot API | Dépendance externe envisagée ; webhook HTTPS et secret d'origine proposés. Aucune configuration ni clé ajoutée. |
| Fournisseur LLM | Non choisi ; modèle, coût, délais et politique de données à valider. |
| MCP Tool Server | Dépendance prévue par le sujet, absente de la branche ; outils, schémas, transport et sécurité à convenir avec son responsable. |
| API métier | API Java/README de Customer et contrats OpenAPI de [Payment](../payment-service/src/main/resources/static/openapi.yaml) et [Ticket](../ticket-service/src/main/resources/static/openapi.yaml) disponibles dans cette branche. Aucun appel métier ni outil MCP intégré à l’assistant. |
| Config Server / Eureka | Starters clients et import Config Server présents ; la configuration distante fournit le port et la configuration Eureka. Le service est configuré pour s’enregistrer auprès d’Eureka. |
| Gateway / Keycloak | Gateway existe mais sa configuration partagée ne comporte pas encore de route assistant. Keycloak et l’identité de l’utilisateur restent à définir pour les futures fonctions métier. |
| OpenFeign / Resilience4J / Kafka | Présents dans la feuille de route globale ; leur emploi par l'assistant n'est pas une décision acquise. Aucun client, topic ou événement ajouté. |
| Base de données | Aucun besoin de base propre à l'assistant validé ; aucun schéma, migration ou stockage vectoriel ajouté. |

## Contrat prévisionnel

[docs/openapi.yaml](docs/openapi.yaml) est une proposition OpenAPI 3.0.3 autonome, destinée à être relue avant développement. Toutes ses opérations portent le marqueur `x-statut: a-valider`.

| Méthode | Chemin proposé | Usage |
|---|---|---|
| POST | `/api/ai-assistant/telegram/webhook` | Recevoir un `Update` Telegram ; acquittement distinct de la réponse conversationnelle. |
| POST | `/api/ai-assistant/chat` | Interface HTTP de dialogue facultative, synchrone, protégée par jeton selon une proposition à valider. |

Ces deux chemins sont documentés mais aucun contrôleur ne les implémente : ils ne sont pas exécutables et le Gateway ne les route pas encore. Swagger UI affiche une proposition, pas des opérations métier disponibles. Aucun rôle Keycloak, nom d’outil MCP ou endpoint métier supplémentaire n’est fixé. Le format d’erreur `application/problem+json` est proposé pour rester cohérent avec le `ProblemDetail` de Customer Service.

## Configuration et lancement

Prérequis :

- JDK 21 pour le projet, le lanceur Maven et `JAVA_HOME` dans le terminal ; utiliser le wrapper Maven fourni à la racine.
- Accès aux dépendances Maven et au dépôt Git de configuration utilisé par Config Server.
- Config Server démarré sur `http://localhost:8888`, servant le dépôt `Yassine-Atiki/ticketflow-config`, label `main`. L’import `configserver:` est obligatoire : sans ce serveur, le démarrage normal échoue.
- Eureka sur `http://localhost:8761` pour l’enregistrement et la découverte du service. Son adresse provient de la configuration commune distante.

La configuration locale [application.yaml](src/main/resources/application.yaml) conserve le nom du service, l’import Config Server et les réglages Swagger. La [configuration distante actuelle](https://github.com/Yassine-Atiki/ticketflow-config/blob/main/ai-assistant-service.yml) définit le port **8087** et active l’enregistrement Eureka ; le [fichier commun](https://github.com/Yassine-Atiki/ticketflow-config/blob/main/application.yml) fournit l’adresse du registre. Cette configuration externe peut évoluer indépendamment de cette PR.

Depuis la racine `ticketflow`, dans PowerShell :

```powershell
.\mvnw.cmd -pl services/ai-assistant-service spring-boot:run
```

Après démarrage, avec la configuration distante actuelle :

- Swagger UI : [http://localhost:8087/swagger-ui/index.html](http://localhost:8087/swagger-ui/index.html).
- Contrat proposé : [http://localhost:8087/openapi.yaml](http://localhost:8087/openapi.yaml).
- Santé technique : [http://localhost:8087/actuator/health](http://localhost:8087/actuator/health).

Le [POM du service](pom.xml) copie `docs/openapi.yaml` dans les ressources `static` lors de la construction ; Swagger charge `/openapi.yaml`. Le fichier de `docs/` reste la source unique et `supported-submit-methods: []` désactive l’exécution depuis l’interface.

La [configuration Gateway actuelle](https://github.com/Yassine-Atiki/ticketflow-config/blob/main/api-gateway.yml) ne contient pas de route assistant et désactive la création automatique des routes. Gateway n’est donc pas requis pour consulter directement Swagger sur le port 8087. L’assistant n’est pas déclaré dans Docker Compose.

## Test de contexte isolé

Le [test existant](src/test/java/com/ticketflow/aiassistantservice/AiAssistantServiceApplicationTests.java) active le profil `test` et sélectionne explicitement sa [configuration dédiée](src/test/resources/application-test.yaml) avec `spring.config.location=classpath:/application-test.yaml`. Ce choix charge une configuration de test sans l’import distant obligatoire de la configuration normale, qui serait sinon traité avant le profil. Config Client, la découverte Eureka et l’enregistrement automatique y sont désactivés. Aucune base de données ni exclusion JPA n’est nécessaire : le service n’utilise pas JPA.

Depuis la racine, avec Java 21 :

```powershell
.\mvnw.cmd -B -ntp -pl services/ai-assistant-service -am verify
```

Ce test vérifie le chargement du contexte Spring sans Config Server ni Eureka locaux ; il ne valide pas les communications réelles avec l’infrastructure ni les futures fonctions métier. Le profil de test n’affecte pas le lancement normal.

Avant d’implémenter le dialogue, l’équipe doit trancher les [questions ouvertes du DESIGN](DESIGN.md#questions-ouvertes-pour-la-réunion) et valider le contrat.
