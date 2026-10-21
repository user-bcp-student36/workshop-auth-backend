package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class AuthLogRedactionTest {
    private static final String PASSWORD = "Workshop123!";

    @ConfigProperty(name = "auth.token.secret")
    String secret;

    // Protege: spec auth-token "Login fallido no registra la contrasena" (contrasena incorrecta).
    @Test
    void failedLoginDoesNotLogPassword() {
        List<String> logs = captureLogs(() -> given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"clave-incorrecta-no-registrar\"}")
                .when().post("/auth/login")
                .then().statusCode(401));

        logs.forEach(line -> assertFalse(line.contains("clave-incorrecta-no-registrar"), line));
    }

    // Protege: spec auth-token "Login exitoso no registra el token" y "ningun log contiene el secreto".
    @Test
    void successfulLoginDoesNotLogTokenOrSecret() {
        String[] token = new String[1];
        List<String> logs = captureLogs(() -> token[0] = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"" + PASSWORD + "\"}")
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().jsonPath().getString("token"));

        logs.forEach(line -> {
            assertFalse(line.contains(token[0]), line);
            assertFalse(line.contains(secret), line);
            assertFalse(line.contains(PASSWORD), line);
        });
    }

    private static List<String> captureLogs(Runnable action) {
        List<String> captured = new CopyOnWriteArrayList<>();
        Logger root = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                StringBuilder line = new StringBuilder(String.valueOf(record.getMessage()));
                if (record.getThrown() != null) {
                    StringWriter writer = new StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(writer));
                    line.append(writer);
                }
                captured.add(line.toString());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        root.addHandler(handler);
        try {
            action.run();
        } finally {
            root.removeHandler(handler);
        }
        return captured;
    }
}
