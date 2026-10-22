package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Caracterización de POST /auth/login sobre el código actual (HU-01, sin cambios de producción).
@QuarkusTest
class AuthCharacterizationTest {

    private static final String LOGIN = "/auth/login";
    private static final String VALID_BODY = "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}";

    // Protege: spec auth-login "Respuesta de login exitoso" y contrato 200 de docs/ENDPOINTS.md.
    @Test
    void successfulLoginReturnsTokenExpiresInAndUserFields() {
        given()
                .contentType("application/json")
                .body(VALID_BODY)
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    // Protege: spec auth-login "displayName escapado en HTML".
    // Limitacion: los usuarios sembrados no tienen caracteres HTML, por lo que se fija la regla de escape
    // directamente sobre la funcion usada por AuthResource. No cubre el camino HTTP completo.
    @Test
    void displayNameEscapeRuleEscapesHtmlSpecialCharacters() {
        assertEquals("&lt;b&gt;Ana &amp; &quot;Bo&quot;&lt;/b&gt;",
                StringEscapeUtils.escapeHtml4("<b>Ana & \"Bo\"</b>"));
    }

    // Protege: spec auth-login "Validacion de entrada" (cuerpo nulo).
    @Test
    void nullBodyReturnsBadRequestRequestIsNull() {
        given()
                .contentType("application/json")
                .when().post(LOGIN)
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    // Protege: spec auth-login "Validacion de entrada" (username ausente o solo espacios).
    @Test
    void missingOrBlankUsernameReturnsBadRequest() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    // Protege: spec auth-login "Validacion de entrada" (password ausente o solo espacios).
    @Test
    void missingOrBlankPasswordReturnsBadRequest() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"   \"}")
                .when().post(LOGIN)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    // Protege: spec auth-login "Credenciales invalidas" (contrasena incorrecta).
    @Test
    void wrongPasswordReturnsUnauthorizedWithFixedMessage() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrecta\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    // Protege: spec auth-login "Credenciales invalidas" (usuario inexistente, mismo formato).
    @Test
    void unknownUserReturnsUnauthorizedWithSameMessageFormat() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"nadie\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user nadie or password"));
    }

    // Protege: spec auth-login "Campos desconocidos ignorados".
    @Test
    void unknownRequestFieldsAreIgnored() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"campoExtra\":\"x\"}")
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("user.username", is("student"));
    }
}
