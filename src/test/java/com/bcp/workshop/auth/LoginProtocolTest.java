package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isEmptyString;

@QuarkusTest
@QuarkusTestResource(AuthTestResource.class)
class LoginProtocolTest {
    @Test
    void rejectsUnsupportedContentTypeWithoutInventingAJsonError() {
        given().contentType("text/plain").body("not a JSON request")
                .when().post("/auth/login").then()
                .statusCode(415).body(isEmptyString());
    }

    @Test
    void rejectsAnAcceptHeaderThatExcludesJson() {
        given().contentType("application/json").accept("text/plain")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then()
                .statusCode(406).body(isEmptyString());
    }
}
