package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusIntegrationTest
@QuarkusTestResource(TokenTestResource.class)
class AuthFrameworkIT {
    @Test
    void rejectsMalformedJsonAndIncompatibleFieldTypes() {
        for (String body : new String[]{"{", "{\"username\":{},\"password\":\"Workshop123!\"}"}) {
            assertEmptyFrameworkError(given().contentType("application/json").body(body)
                    .post("/auth/login").then(), 400);
        }
    }

    @Test
    void rejectsUnsupportedMethod() {
        assertEmptyFrameworkError(given().get("/auth/login").then(), 405);
    }

    @Test
    void rejectsIncompatibleAcceptHeader() {
        assertEmptyFrameworkError(given().contentType("application/json").accept("text/plain")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .post("/auth/login").then(), 406);
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertEmptyFrameworkError(given().contentType("text/plain").accept("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .post("/auth/login").then(), 415);
    }

    private void assertEmptyFrameworkError(ValidatableResponse response, int status) {
        var error = response.statusCode(status).extract().response();
        assertEquals(0, error.asByteArray().length);
        assertNull(error.getHeader("Content-Type"));
    }
}