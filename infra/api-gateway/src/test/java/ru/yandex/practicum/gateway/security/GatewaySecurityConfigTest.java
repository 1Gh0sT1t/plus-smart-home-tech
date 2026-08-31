package ru.yandex.practicum.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(GatewaySecurityConfigTest.TestBackendConfiguration.class)
class GatewaySecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowPublicProductRequest() {
        webTestClient.get()
                .uri("/api/products/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectAnonymousOrderCreation() {
        webTestClient.post()
                .uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowIvanToCreateOrder() {
        webTestClient.post()
                .uri("/api/orders")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldForbidIvanToChangeProducts() {
        webTestClient.post()
                .uri("/api/products")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldAllowAnnaToChangeProducts() {
        webTestClient.post()
                .uri("/api/products")
                .headers(headers -> headers.setBasicAuth("anna", "anna"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldForbidIvanToGetAllOrders() {
        webTestClient.get()
                .uri("/api/orders")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldAllowAnnaToGetAllOrders() {
        webTestClient.get()
                .uri("/api/orders")
                .headers(headers -> headers.setBasicAuth("anna", "anna"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldDenyUnknownRouteForAnna() {
        webTestClient.get()
                .uri("/api/unknown")
                .headers(headers -> headers.setBasicAuth("anna", "anna"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldAllowPreflightRequest() {
        webTestClient.method(HttpMethod.OPTIONS)
                .uri("/api/orders")
                .exchange()
                .expectStatus().isOk();
    }

    @TestConfiguration
    static class TestBackendConfiguration {
        @Bean
        RouterFunction<ServerResponse> testBackendRoutes() {
            return RouterFunctions.route(all(), request -> ServerResponse.ok().build());
        }
    }
}
