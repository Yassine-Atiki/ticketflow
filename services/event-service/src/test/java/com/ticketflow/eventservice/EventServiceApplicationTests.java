package com.ticketflow.eventservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=classpath:/application-test.properties")
@ActiveProfiles("test")
class EventServiceApplicationTests {

    @Value("${local.server.port}")
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void swaggerServesTheEventContractWithoutExternalServices() throws Exception {
        try (var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL).build()) {
            var ui = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/swagger-ui.html")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(ui.statusCode()).isEqualTo(200);
            assertThat(ui.body()).contains("Swagger UI");

            var contract = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/openapi.yml")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(contract.statusCode()).isEqualTo(200);
            assertThat(contract.body()).contains("operationId: reserveCapacity",
                    "operationId: releaseCapacity", "ticketTierId");
        }
    }
}
