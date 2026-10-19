package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(TokenTestResource.class)
class AuthResourceTest {
    @BeforeEach
    void prepareFixtures() throws Exception {
        new LegacyDatabase().initialize();
        LegacyDatabaseTest.insertUser("html-user", "long-password-" + "x".repeat(90) + "\u00e9", "<Student & Trainer>", "STUDENT");
    }

    @Test
    void preservesSuccessfulResponseForBothWorkshopUsers() {
        assertSuccess("student", "Workshop123!", "Workshop Student", "STUDENT");
        assertSuccess("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    @Test
    void issuedLoginTokensAuthenticateInPythonConsumer() throws Exception {
        String python = System.getenv("REQFLOW_TEST_PYTHON");
        String apiRoot = System.getenv("REQFLOW_TEST_API_ROOT");
        org.junit.jupiter.api.Assumptions.assumeTrue(python != null && apiRoot != null,
                "Cross-repository test requires the consumer environment");
        ObjectMapper mapper = new ObjectMapper();
        for (String username : new String[]{"student", "trainer"}) {
            String password = username.equals("student") ? "Workshop123!" : "Trainer123!";
            Map<String, Object> login = given().contentType("application/json").body(credentials(username, password))
                    .post("/auth/login").then().statusCode(200).extract().jsonPath().getMap("");
            String token = (String) login.get("token");
            var claims = TokenTestResource.verify(token);
            String publicKey = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(TokenTestResource.KEYS.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            String exchange = mapper.writeValueAsString(Map.of(
                    "token", token, "publicKey", publicKey, "issuer", TokenTestResource.ISSUER,
                    "audience", TokenTestResource.AUDIENCE, "username", username,
                    "role", claims.getStringListClaimValue("groups").getFirst(),
                    "taskCount", username.equals("student") ? 2 : 1));
            Process consumer = new ProcessBuilder(python, "-m", "tests.verify_java_token")
                    .directory(Path.of(apiRoot).toFile()).redirectErrorStream(true).start();
            try {
                try (var input = consumer.getOutputStream()) {
                    input.write(exchange.getBytes(StandardCharsets.UTF_8));
                }
                assertTrue(consumer.waitFor(30, TimeUnit.SECONDS), "Python verification timed out");
                assertEquals(0, consumer.exitValue(), "Java login token must authenticate in FastAPI");
            } finally {
                consumer.destroyForcibly();
            }
            String fixture = System.getenv("REQFLOW_TEST_LOGIN_FIXTURE");
            if (fixture != null && username.equals("student")) {
                Files.writeString(Path.of(fixture), mapper.writeValueAsString(login));
            }
        }
    }

    @Test
    void escapesDisplayNameAndPreservesLongUnicodePassword() {
        assertSuccess("html-user", "long-password-" + "x".repeat(90) + "\u00e9", "&lt;Student &amp; Trainer&gt;", "STUDENT");
    }

    @Test
    void ignoresClientAndUnknownFields() {
        Map<String, Object> body = new HashMap<>(credentials("student", "Workshop123!"));
        body.put("client", "angular-workshop");
        body.put("unknown", true);
        given().contentType("application/json").body(body).post("/auth/login").then().statusCode(200);
    }

    @Test
    void preservesValidationOrderAndMessages() {
        for (String username : new String[]{null, "", "   ", "\t\r\n"}) {
            assertError(credentials(username, null), 400, "Username is required");
        }
        for (String password : new String[]{null, "", "   ", "\t\r\n"}) {
            assertError(credentials("student", password), 400, "Password is required");
        }
        try (Response response = new AuthResource().login(null)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Request is null"), response.getEntity());
        }
    }

    @Test
    void rejectsNullAndEmptyBodiesOverHttp() {
        for (String body : new String[]{"null", ""}) {
            var response = given().contentType("application/json").body(body).post("/auth/login")
                    .then().statusCode(400).contentType("application/json").extract().jsonPath();
            assertEquals(Map.of("message", "Request is null"), response.getMap(""));
        }
    }

    @Test
    void rejectsMissingRequiredFieldsInValidationOrder() {
        assertError(Map.of(), 400, "Username is required");
        assertError(Map.of("password", "Workshop123!"), 400, "Username is required");
        assertError(Map.of("username", "student"), 400, "Password is required");
    }

    @Test
    void rejectsInvalidCredentialsWithoutTrimmingThem() {
        assertError(credentials("missing", "Workshop123!"), 401, "Invalid user missing or password");
        assertError(credentials("student", "wrong"), 401, "Invalid user student or password");
        assertError(credentials(" student", "Workshop123!"), 401, "Invalid user  student or password");
        assertError(credentials("student", "Workshop123! "), 401, "Invalid user student or password");
    }

    @Test
    void distinguishesCredentialCase() {
        assertError(credentials("Student", "Workshop123!"), 401, "Invalid user Student or password");
        assertError(credentials("student", "workshop123!"), 401, "Invalid user student or password");
    }

    @Test
    void rejectsInvalidStoredHash() throws Exception {
        LegacyDatabaseTest.insertUser("rejected-hash-user", "test-password", "Test User", "STUDENT");
        try {
            LegacyDatabaseTest.execute("UPDATE legacy_users SET password_hash='invalid-hash' WHERE username='rejected-hash-user'");
            assertError(credentials("rejected-hash-user", "test-password"), 401,
                    "Invalid user rejected-hash-user or password");
        } finally {
            LegacyDatabaseTest.execute("DELETE FROM legacy_users WHERE username='rejected-hash-user'");
        }
    }

    @Test
    void preservesInternalErrorResponse() {
        for (String message : new String[]{"test failure", null}) {
            AuthResource resource = new AuthResource();
            resource.service = new AuthService(null, null, null) {
                @Override
                public LoginResponse login(String username, String password) {
                    throw new IllegalStateException(message);
                }
            };
            try (Response response = resource.login(request("student", "Workshop123!"))) {
                assertEquals(500, response.getStatus());
                assertEquals(Map.of("message", message == null ? "Unknown error" : message,
                        "exception", IllegalStateException.class.getName()), response.getEntity());
            }
        }
    }

    @Test
    void preservesUnauthorizedResponseForCaughtDatabaseFailure() throws Exception {
        LegacyDatabaseTest.withMissingTable(() ->
                assertError(credentials("student", "Workshop123!"), 401, "Invalid user student or password"));
    }

    @Test
    void reportsHealthWithoutAuthentication() {
        assertHealth(given().accept("application/json").get("/q/health").then());
    }

    @Test
    void ignoresInvalidAuthorizationForHealth() {
        assertHealth(given().accept("application/json")
                .header("Authorization", "Bearer test-only-not-a-token").get("/q/health").then());
    }

    @Test
    void preservesHealthWithPlainTextAcceptAndAdditionalQuery() {
        assertHealth(given().accept("text/plain").get("/q/health").then());
        assertHealth(given().accept("application/json").queryParam("client", "test-only")
                .get("/q/health").then());
    }

    private void assertHealth(ValidatableResponse response) {
        assertEquals(Map.of("status", "UP", "checks", java.util.List.of()),
                response.statusCode(200).contentType("application/json").extract().jsonPath().getMap(""));
    }

    private void assertSuccess(String username, String password, String displayName, String role) {
        var response = given().contentType("application/json").body(credentials(username, password))
                .post("/auth/login").then().statusCode(200).contentType("application/json").extract().jsonPath();
        Map<String, Object> body = response.getMap("");
        assertEquals(Set.of("token", "expiresIn", "user", "debug"), body.keySet());
        assertFalse(((String) body.get("token")).isBlank());
        try {
            var claims = TokenTestResource.verify((String) body.get("token"));
            assertEquals(username, claims.getSubject());
            assertEquals(java.util.List.of(role), claims.getStringListClaimValue("groups"));
            assertEquals(3600, claims.getExpirationTime().getValue() - claims.getIssuedAt().getValue());
        } catch (Exception error) {
            fail("Login must emit a verifiable JWT");
        }
        assertEquals(3600, body.get("expiresIn"));
        assertEquals("authenticated-with-legacy-flow", body.get("debug"));
        assertEquals(Map.of("username", username, "displayName", displayName, "role", role), body.get("user"));
    }

    private void assertError(Map<String, Object> body, int status, String message) {
        var response = given().contentType("application/json").body(body).post("/auth/login")
                .then().statusCode(status).contentType("application/json").extract().jsonPath();
        assertEquals(Map.of("message", message), response.getMap(""));
    }

    private Map<String, Object> credentials(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return body;
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.username = username;
        request.password = password;
        return request;
    }
}
