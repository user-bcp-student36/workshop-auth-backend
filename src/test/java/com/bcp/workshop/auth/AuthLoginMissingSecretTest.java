package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;

@QuarkusTest
@TestProfile(MissingSecretProfile.class)
class AuthLoginMissingSecretTest {

    // Protege: spec auth-token "Sin variable de entorno no se emiten tokens" (500 sin campo token).
    @Test
    void validLoginWithoutSecretReturns500WithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("AUTH_TOKEN_SECRET is not configured"))
                .body("exception", is(IllegalStateException.class.getName()))
                .body("$", not(hasKey("token")));
    }
}
