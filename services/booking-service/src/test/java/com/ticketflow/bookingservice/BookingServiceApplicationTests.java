package com.ticketflow.bookingservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vérifie que le contexte Spring démarre correctement.
 *
 * La configuration de test est dans src/test/resources/application.yaml :
 * Config Server désactivé, H2 en mémoire, Kafka exclu, Eureka désactivé.
 * Aucune dépendance externe n'est requise pour exécuter ce test.
 */
@SpringBootTest
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {
        // Vérifie uniquement que le contexte Spring se charge sans erreur.
        // Les tests métier sont dans des classes dédiées.
    }

}
