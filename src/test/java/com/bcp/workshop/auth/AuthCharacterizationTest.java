package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthCharacterizationTest {
    @Test
    void preservesSuccessfulResponseAndIgnoresUnknownProperties() {
        login(Map.of("username", "student", "password", "Workshop123!",
                "client", "ignored-client", "unknown", true))
                .statusCode(200)
                .body("keySet()", containsInAnyOrder("token", "expiresIn", "user", "debug"))
                .body("token", not(emptyOrNullString()))
                .body("expiresIn", equalTo(3600))
                .body("debug", equalTo("authenticated-with-legacy-flow"))
                .body("user", equalTo(Map.of("username", "student",
                        "displayName", "Workshop Student", "role", "STUDENT")));
    }

    @Test
    void clientIsOptionalAndTrainerCanLogIn() {
        login(Map.of("username", "trainer", "password", "Trainer123!"))
                .statusCode(200).body("user.role", equalTo("TRAINER"));
    }

    @Test
    void rejectsNullBody() {
        given().contentType("application/json").body("null")
                .when().post("/auth/login").then()
                .statusCode(400).body("message", equalTo("Request is null"));
    }

    @Test
    void rejectsAbsentBodyWithTheSameNullRequestMessage() {
        given().contentType("application/json")
                .when().post("/auth/login").then()
                .statusCode(400).body("$", equalTo(Map.of("message", "Request is null")));
    }

    @Test
    void rejectsExplicitNullUsernameBeforePassword() {
        given().contentType("application/json")
                .body("{\"username\":null,\"password\":null}")
                .when().post("/auth/login").then()
                .statusCode(400).body("$", equalTo(Map.of("message", "Username is required")));
    }

    @Test
    void rejectsExplicitNullPassword() {
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":null}")
                .when().post("/auth/login").then()
                .statusCode(400).body("$", equalTo(Map.of("message", "Password is required")));
    }

    @Test
    void acceptsExplicitNullClient() {
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}")
                .when().post("/auth/login").then()
                .statusCode(200).body("user.username", equalTo("student"));
    }

    @Test
    void validatesUsernameBeforePassword() {
        for (String username : new String[]{"", "   "}) {
            login(Map.of("username", username, "password", ""))
                    .statusCode(400).body("message", equalTo("Username is required"));
        }
        login(Map.of()).statusCode(400).body("message", equalTo("Username is required"));
    }

    @Test
    void rejectsMissingOrBlankPassword() {
        login(Map.of("username", "student"))
                .statusCode(400).body("message", equalTo("Password is required"));
        for (String password : new String[]{"", "   "}) {
            login(Map.of("username", "student", "password", password))
                    .statusCode(400).body("message", equalTo("Password is required"));
        }
    }

    @Test
    void returnsGenericInvalidCredentialsAndPreservesLiteralWhitespace() {
        for (String username : new String[]{"student", "Student", " student ", "missing"}) {
            login(Map.of("username", username, "password", "wrong"))
                    .statusCode(401)
                    .body("$", equalTo(Map.of("message", "Invalid credentials")));
        }
        login(Map.of("username", "student", "password", " Workshop123! "))
                .statusCode(401);
    }

    @Test
    void doesNotNormalizeUsernamesWithAnOtherwiseCorrectPassword() {
        for (String username : new String[]{"Student", " student ", "missing"}) {
            login(Map.of("username", username, "password", "Workshop123!"))
                    .statusCode(401)
                    .body("$", equalTo(Map.of("message", "Invalid credentials")));
        }
    }

    @Test
    void malformedJsonUsesDocumentedErrorBody() {
        given().contentType("application/json").body("{")
                .when().post("/auth/login").then()
                .statusCode(400).body("message", equalTo("Request is null"));
    }

    @Test
    void wrongJsonShapeUsesDocumentedErrorBody() {
        given().contentType("application/json").body("[]")
                .when().post("/auth/login").then()
                .statusCode(400).body("message", equalTo("Request is null"));
    }

    private ValidatableResponse login(Map<String, Object> request) {
        return given().contentType("application/json").body(request)
                .when().post("/auth/login").then();
    }
}
