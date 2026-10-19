package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CorsTest {
    @Test
    void permitsOnlyConfiguredOriginForPreflight() {
        given().header("Origin", "https://allowed.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .when().options("/auth/login").then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("https://allowed.example"))
                .header("Access-Control-Allow-Methods", containsString("POST"));
    }

    @Test
    void loginWithoutOriginStillWorks() {
        given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then().statusCode(200);
    }

    @Test
    void unapprovedOriginCannotReadLoginFromBrowser() {
        given().header("Origin", "https://unapproved.example")
                .contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then()
                .statusCode(403).body(isEmptyString())
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
