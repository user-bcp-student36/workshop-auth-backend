package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(HealthDownHttpTest.DownProfile.class)
@QuarkusTestResource(AuthTestResource.class)
class HealthDownHttpTest {
    @Test
    void returnsServiceUnavailableWhenTheProviderReportsDown() {
        given().accept("application/json")
                .when().get("/q/health").then()
                .statusCode(503).contentType("application/json")
                .header("Cache-Control", equalTo("no-store"))
                .body("$", equalTo(Map.of("status", "DOWN", "checks", List.of())));
    }

    public static class DownProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("io.smallrye.health.emptyChecksOutcome", "DOWN");
        }
    }
}
