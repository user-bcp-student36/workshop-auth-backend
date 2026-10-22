package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AuthLoginTokenTest {
    private static final String LOGIN = "/auth/login";

    @Inject
    AuthTokenService tokens;

    // Protege: spec auth-login "Client valido aparece en el token" y auth-token "expiresIn coincide con la vigencia".
    @Test
    void tokenClientClaimMatchesRequestAndExpiresInMatchesExpMinusIat() throws ParseException {
        Response response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().response();

        String token = response.jsonPath().getString("token");
        var parsed = tokens.verify(token);

        assertEquals("angular-workshop", parsed.getClaim("client"));
        assertEquals("student", parsed.getSubject());
        assertEquals(response.jsonPath().getLong("expiresIn"), parsed.getExpirationTime() - parsed.getIssuedAtTime());
    }

    // Protege: spec auth-login "Client ausente usa el valor por defecto".
    @Test
    void absentClientUsesDefaultClaim() throws ParseException {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");

        assertEquals("angular-workshop", tokens.verify(token).getClaim("client"));
    }

    // Protege: spec auth-login "Client no permitido es rechazado" (400 con message que menciona client).
    @Test
    void clientNotAllowedReturnsBadRequestAndNoToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"otro-cliente\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(400)
                .body("message", containsString("client"))
                .body("token", nullValue());
    }

    // Protege: spec auth-login "Credenciales invalidas" con client no permitido (la autenticacion va primero).
    @Test
    void wrongPasswordStillReturnsUnauthorizedBeforeClientCheck() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrecta\",\"client\":\"otro-cliente\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }
}
