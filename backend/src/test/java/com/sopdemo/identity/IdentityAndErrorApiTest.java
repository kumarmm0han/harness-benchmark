package com.sopdemo.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sopdemo.AbstractPostgresSpringTest;

/**
 * TASK-003 verification: identity resolution (401), role authorization (403),
 * normalized error shape, generic 500 without internals, and CORS confining (FR-001, IR-001, NFR-020).
 */
class IdentityAndErrorApiTest extends AbstractPostgresSpringTest {

    private static final HttpHeaders AUTHOR = new HttpHeaders();
    private static final HttpHeaders CONSUMER = new HttpHeaders();

    static {
        AUTHOR.set("X-Demo-User", "demo-author");
        CONSUMER.set("X-Demo-User", "demo-consumer");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("missing identity -> 401 with normalized body")
    void missingIdentityReturns401() {
        ResponseEntity<Map> res = rest.getForEntity("/api/v1/probe/open", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("code", "unauthorized");
        assertThat(res.getBody()).containsKey("message");
        assertThat(res.getBody().get("issues")).as("issues must be [] (IR-001)").isEqualTo(List.of());
    }

    @Test
    @DisplayName("unknown identity -> 401")
    void unknownIdentityReturns401() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Demo-User", "mallory");
        ResponseEntity<Map> res = rest.exchange("/api/v1/probe/open", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("code", "unauthorized");
    }

    @Test
    @DisplayName("consumer can access an open API")
    void consumerAccessesOpenApi() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/probe/open", HttpMethod.GET, new HttpEntity<>(CONSUMER), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("consumer on author-only API -> 403 forbidden")
    void consumerOnAuthorApiGets403() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/probe/author", HttpMethod.GET, new HttpEntity<>(CONSUMER), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).containsEntry("code", "forbidden");
    }

    @Test
    @DisplayName("author on author-only API -> 200")
    void authorAccessesAuthorApi() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/probe/author", HttpMethod.GET, new HttpEntity<>(AUTHOR), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("who", "demo-author");
    }

    @Test
    @DisplayName("unexpected error -> generic 500, no internals leaked")
    void unexpectedErrorDoesNotLeakInternals() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/probe/error", HttpMethod.GET, new HttpEntity<>(AUTHOR), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).containsEntry("code", "internal-error");
        assertThat(res.getBody().toString()).doesNotContain("secret-internal-pg-password-xyz");
        assertThat(res.getBody().get("issues")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("unknown /api path -> 404 normalized body")
    void unknownApiPathIs404() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/nope", HttpMethod.GET, new HttpEntity<>(CONSUMER), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).containsEntry("code", "not-found");
    }

    @Test
    @DisplayName("preflight from the configured UI origin is allowed")
    void corsAllowsConfiguredOrigin() {
        HttpHeaders h = new HttpHeaders();
        h.set("Origin", "http://localhost:3010");
        h.set("Access-Control-Request-Method", "GET");
        ResponseEntity<String> res = rest.exchange("/api/v1/probe/open", HttpMethod.OPTIONS, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3010");
    }

    @Test
    @DisplayName("preflight from another origin is not granted")
    void corsRejectsOtherOrigins() {
        HttpHeaders h = new HttpHeaders();
        h.set("Origin", "http://attacker.example");
        h.set("Access-Control-Request-Method", "GET");
        ResponseEntity<String> res = rest.exchange("/api/v1/probe/open", HttpMethod.OPTIONS, new HttpEntity<>(h), String.class);
        assertThat(res.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
    }
}
