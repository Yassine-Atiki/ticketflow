# Notification Service

Socle Java 21 / Spring Boot du service de notifications TicketFlow. Le document [DESIGN.md](DESIGN.md) décrit le flux métier prévu par FR-014 et les contrats à valider.

## Lancement local

Démarrer Eureka sur le port 8761 et Config Server sur le port 8888. La configuration centrale doit contenir `notification-service.yml`. Depuis IntelliJ, lancer `NotificationServiceApplication` avec le JDK 21. Depuis la racine du dépôt, l'équivalent sous PowerShell est le suivant.

```powershell
.\mvnw.cmd -pl services/notification-service spring-boot:run
```

`CONFIG_SERVER_URL` permet de changer l'adresse de Config Server. L'import est obligatoire et utilise `http://localhost:8888` par défaut. Avant intégration de la configuration dans `main` de `ticketflow-config`, définir `SPRING_CLOUD_CONFIG_LABEL=feature(_)notification-service` dans l'environnement du service pour charger la branche de configuration. Vérifier au préalable que Config Server peut lire ce label.

## Docker Desktop

Avec Config Server et Eureka démarrés sur l'hôte, lancer le conteneur depuis la racine. Compose passe par `host.docker.internal` pour joindre les deux services.

```powershell
docker compose -f infra/docker-compose.yml up --build notification-service
```

Pour tester la branche de configuration en Docker, définir la même variable `SPRING_CLOUD_CONFIG_LABEL` dans le terminal qui lance Compose.

## Vérification

- Config Server fournit `notification-service.yml` sur `http://localhost:8888/notification-service/default` après intégration dans `main`.
- Santé locale sur `http://localhost:8088/actuator/health`.
- Service `NOTIFICATION-SERVICE` au statut `UP` dans Eureka sur `http://localhost:8761/`.

```powershell
.\mvnw.cmd -B -ntp -pl services/notification-service -am verify
```

Le test HTTP embarque son propre serveur et désactive Config Server et Eureka. Ce socle expose la santé technique. Le service métier sera piloté par Kafka, avec une route Gateway et un contrat OpenAPI uniquement si une API HTTP métier est définie.
