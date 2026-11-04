package com.marketsentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Starts the real application on a random port against PostgreSQL in Docker (Testcontainers) and
 * calls it over HTTP, the way a broker's systems and the dashboard do.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "sentinel.demo.seed=false")
class ApiIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper json;

    record Response(int status, JsonNode body, HttpResponse<String> raw) {
    }

    @Test
    void firmsCannotSeeEachOthersData() throws Exception {
        String alice = signup("Alpha Securities", "admin@alpha.test");
        String bob = signup("Beta Brokerage", "admin@beta.test");

        Response sim = call("POST", "/api/v1/simulator/run?date=2026-10-19", alice, null, Map.of());
        assertThat(sim.status()).isEqualTo(200);
        assertThat(sim.body().path("detection").path("newAlerts").asInt()).isEqualTo(5);

        Response aliceAlerts = call("GET", "/api/v1/alerts", alice, null, Map.of());
        Response bobAlerts = call("GET", "/api/v1/alerts", bob, null, Map.of());
        assertThat(aliceAlerts.body().path("total").asInt()).isEqualTo(5);
        assertThat(bobAlerts.body().path("total").asInt()).isZero();

        long aliceAlertId = aliceAlerts.body().path("items").path(0).path("id").asLong();
        assertThat(call("GET", "/api/v1/alerts/" + aliceAlertId, bob, null, Map.of()).status()).isEqualTo(404);
    }

    @Test
    void ingestIsIdempotentAndValidated() throws Exception {
        String admin = signup("Gamma Capital", "admin@gamma.test");
        String key = call("POST", "/api/v1/admin/api-keys", admin, Map.of("name", "OMS"), Map.of()).body().path("apiKey")
                .asString();
        Map<String, Object> trade = Map.of("tradeRef", "T-1", "symbol", "ACME", "buyAccountRef", "C1", "sellAccountRef", "C2",
                "price", "10.25", "quantity", 100, "executedAt", "2026-10-21T15:00:00Z");
        Map<String, String> headers = Map.of("X-API-Key", key, "Idempotency-Key", "abc-1");

        Response first = call("POST", "/api/v1/ingest/trades", null, trade, headers);
        Response retry = call("POST", "/api/v1/ingest/trades", null, trade, headers);
        assertThat(first.status()).isEqualTo(201);
        assertThat(retry.status()).isEqualTo(200);
        assertThat(retry.raw().headers().firstValue("Idempotent-Replayed")).contains("true");

        Map<String, Object> other = new java.util.HashMap<>(trade);
        other.put("tradeRef", "T-2");
        assertThat(call("POST", "/api/v1/ingest/trades", null, other, headers).status()).isEqualTo(422);

        other.put("price", "-5");
        Response invalid = call("POST", "/api/v1/ingest/trades", null, other, Map.of("X-API-Key", key, "Idempotency-Key", "abc-2"));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("fields").has("price")).isTrue();

        assertThat(call("POST", "/api/v1/ingest/trades", null, trade, Map.of("X-API-Key", "msk_nope", "Idempotency-Key", "x"))
                .status()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/billing", admin, null, Map.of()).body().path("tradesThisMonth").asLong()).isEqualTo(1);

        // Traded value is exact NUMERIC math, sent as a string so JavaScript never parses it into a float.
        JsonNode day = call("GET", "/api/v1/alerts/stats", admin, null, Map.of()).body().path("daily").path(0);
        assertThat(day.path("day").asString()).isEqualTo("2026-10-21");
        assertThat(day.path("trades").asLong()).isEqualTo(1);
        assertThat(day.path("tradedValue").isString()).isTrue();
        assertThat(day.path("tradedValue").asString()).isEqualTo("1025.0000");
    }

    private String signup(String firm, String email) throws Exception {
        Response r = call("POST", "/api/v1/auth/signup", null,
                Map.of("firmName", firm, "email", email, "password", "correct-horse-battery"), Map.of());
        assertThat(r.status()).isEqualTo(201);
        return r.body().path("token").asString();
    }

    private Response call(String method, String path, String token, Object body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        headers.forEach(b::header);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode parsed = res.body() == null || res.body().isBlank() ? json.nullNode() : json.readTree(res.body());
        return new Response(res.statusCode(), parsed, res);
    }
}
