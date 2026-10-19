package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(AuthTestResource.class)
class HealthResourceTest {
    @Test
    void reportsHealthWithoutAuthenticationOrRequestBody() {
        given().accept("application/json")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .header("Cache-Control", equalTo("no-store"))
                .body("$", equalTo(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void emptyChecksDoNotImplyAnImplementedDatabaseProbe() {
        given().when().get("/q/health").then()
                .statusCode(200).body("status", equalTo("UP")).body("checks", empty());
    }

    @Test
    void keepsJsonEvenWhenAcceptRequestsPlainText() {
        given().accept("text/plain")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .body("$", equalTo(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void rejectsAnUnapprovedOriginWithAnEmptyBody() {
        given().header("Origin", "https://unapproved.example")
                .when().get("/q/health").then()
                .statusCode(403).body(isEmptyString())
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
