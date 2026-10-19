package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.config.ConfigProvider;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthCorrectionTest {
    @Test
    void credentialsCannotChangeTheSqlPredicate() {
        given().contentType("application/json")
                .body(Map.of("username", "student' --", "password", "wrong"))
                .when().post("/auth/login").then().statusCode(401);
    }

    @Test
    void activeSchemaContainsOnlyPasswordHashes() throws Exception {
        try (var connection = DriverManager.getConnection(
                ConfigProvider.getConfig().getValue("auth.db.url", String.class));
             var statement = connection.createStatement();
             var columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            var names = new HashSet<String>();
            while (columns.next()) {
                names.add(columns.getString("name"));
            }
            assertTrue(names.contains("password_hash"));
            assertFalse(names.contains("password"));
        }
    }

    @Test
    void tokenUsesSignedCompactFormat() {
        String token = given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then().statusCode(200)
                .extract().path("token");
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void unapprovedOriginReceivesNoCorsAuthorization() {
        String origin = given().header("Origin", "https://unapproved.example")
                .when().options("/auth/login").then().extract()
                .header("Access-Control-Allow-Origin");
        assertNull(origin);
    }
}
