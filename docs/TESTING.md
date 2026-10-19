# Pruebas del contrato HTTP

Fecha de ejecucion: 2026-10-19. Contrato:
[ENDPOINTS.md](ENDPOINTS.md). Ambito: solo `workshop-auth-backend`.

## Matriz: endpoint | caso | tipo | resultado esperado

Todos los casos de esta tabla fueron ejecutados y aprobados en la validacion
final. Una fila puede agrupar entradas comprobadas dentro de un mismo metodo;
el numero de filas no equivale al numero de pruebas JUnit.

Abreviaturas de clases y enlaces a su codigo completo:

- **AC**: [AuthCharacterizationTest](../src/test/java/com/bcp/workshop/auth/AuthCharacterizationTest.java).
- **AR**: [AuthResourceTest](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
- **CORS**: [CorsTest](../src/test/java/com/bcp/workshop/auth/CorsTest.java).
- **LP**: [LoginProtocolTest](../src/test/java/com/bcp/workshop/auth/LoginProtocolTest.java).
- **AF**: [AuthFailureHttpTest](../src/test/java/com/bcp/workshop/auth/AuthFailureHttpTest.java).
- **HR**: [HealthResourceTest](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java).
- **HD**: [HealthDownHttpTest](../src/test/java/com/bcp/workshop/auth/HealthDownHttpTest.java).

| Endpoint | Caso | Tipo | Resultado esperado |
|---|---|---|---|
| POST /auth/login | Cuenta student y contrasena validas, sin autenticacion previa; AR.logsInWithWorkshopUser y AC.preservesSuccessfulResponseAndIgnoresUnknownProperties | Camino feliz | 200; token no vacio, expiresIn=3600, usuario/rol/nombre correctos, debug fijo y conjunto exacto de campos. |
| POST /auth/login | Cuenta trainer, sin client; AC.clientIsOptionalAndTrainerCanLogIn | Camino feliz / borde | 200; rol TRAINER; client no obligatorio. |
| POST /auth/login | client explicitamente null; AC.acceptsExplicitNullClient | Borde | 200; usuario student. |
| POST /auth/login | Propiedad desconocida y client presente; AC.preservesSuccessfulResponseAndIgnoresUnknownProperties | Borde | 200; propiedad ignorada, sin campos adicionales en respuesta. |
| POST /auth/login | Sin cuerpo; AC.rejectsAbsentBodyWithTheSameNullRequestMessage | Borde / error | 400; cuerpo exacto {"message":"Request is null"}. |
| POST /auth/login | JSON null; AC.rejectsNullBody | Borde / error | 400; message=Request is null. |
| POST /auth/login | JSON incompleto; AC.malformedJsonUsesDocumentedErrorBody | Error | 400; message=Request is null. |
| POST /auth/login | Array en vez de objeto; AC.wrongJsonShapeUsesDocumentedErrorBody | Error | 400; message=Request is null. |
| POST /auth/login | username ausente, vacio o espacios, tambien con password vacio; AC.validatesUsernameBeforePassword | Borde / error | 400; message=Username is required, con precedencia de username. |
| POST /auth/login | Ambos campos null; AC.rejectsExplicitNullUsernameBeforePassword | Borde / error | 400; cuerpo exacto {"message":"Username is required"}. |
| POST /auth/login | password ausente, vacio o espacios; AC.rejectsMissingOrBlankPassword | Borde / error | 400; message=Password is required. |
| POST /auth/login | password null y username presente; AC.rejectsExplicitNullPassword | Borde / error | 400; cuerpo exacto {"message":"Password is required"}. |
| POST /auth/login | Credenciales incorrectas; AC.returnsGenericInvalidCredentialsAndPreservesLiteralWhitespace | Error | 401; cuerpo exacto {"message":"Invalid credentials"}. |
| POST /auth/login | Student, username con espacios o cuenta inexistente, usando la contrasena correcta de student; AC.doesNotNormalizeUsernamesWithAnOtherwiseCorrectPassword | Borde / error | 401 generico; no normalizar username. |
| POST /auth/login | Espacios alrededor de la contrasena conocida; AC.returnsGenericInvalidCredentialsAndPreservesLiteralWhitespace | Borde / error | 401; no recortar la contrasena para autenticar. |
| POST /auth/login | PersistenceFailure inducido; AF.persistenceFailurePreservesTheGenericUnauthorizedContract | Error | 401 JSON generico; no revelar detalle SQL. |
| POST /auth/login | Excepcion imprevista al autenticar; AF.unexpectedAuthenticationFailureReturnsOnlyTheGenericMessage | Error | 500 JSON exacto {"message":"Authentication failed"}. |
| POST /auth/login | Excepcion al emitir token tras autenticar; AF.tokenIssuanceFailureReturnsOnlyTheGenericMessage | Error | 500 JSON exacto, sin clase ni mensaje interno. |
| POST /auth/login | Origen no autorizado; CORS.unapprovedOriginCannotReadLoginFromBrowser | Error | 403, cuerpo vacio y sin Access-Control-Allow-Origin. |
| POST /auth/login | Accept: text/plain; LP.rejectsAnAcceptHeaderThatExcludesJson | Error | 406 y cuerpo vacio, no error JSON inventado. |
| POST /auth/login | Content-Type: text/plain; LP.rejectsUnsupportedContentTypeWithoutInventingAJsonError | Error | 415 y cuerpo vacio. |
| POST /auth/login | Preflight del origen autorizado; CORS.permitsOnlyConfiguredOriginForPreflight | Camino feliz de CORS | 200, origen permitido y POST entre los metodos anunciados; OPTIONS es infraestructura de la misma ruta, no un endpoint de negocio agregado. |
| POST /auth/login | Login sin Origin; CORS.loginWithoutOriginStillWorks | Borde | 200; CORS no exige Origin a clientes no navegador. |
| GET /q/health | GET sin cuerpo, Authorization ni cookies; HR.reportsHealthWithoutAuthenticationOrRequestBody | Camino feliz | 200 JSON exacto {"status":"UP","checks":[]}, Cache-Control: no-store. |
| GET /q/health | Conjunto vacio de checks; HR.emptyChecksDoNotImplyAnImplementedDatabaseProbe | Borde | 200 UP y checks vacio; no inventar una sonda SQLite. |
| GET /q/health | Accept: text/plain; HR.keepsJsonEvenWhenAcceptRequestsPlainText | Borde | 200 JSON, no 406. |
| GET /q/health | Origen no autorizado; HR.rejectsAnUnapprovedOriginWithAnEmptyBody | Error | 403 y cuerpo vacio, sin autorizacion CORS. |
| GET /q/health | Agregador DOWN con checks vacios; HD.returnsServiceUnavailableWhenTheProviderReportsDown | Error / borde | 503 JSON exacto {"status":"DOWN","checks":[]}, Cache-Control: no-store. |

La tabla cubre cada variante de error documentada: login 400/401/500 y
rechazos de infraestructura 403/406/415; salud 403/503. No se agrega un error
HTTP para fallos de transporte ni un limite de longitud no establecido.

## Codigo de las pruebas completadas

Los siguientes son los archivos nuevos completos. Los enlaces de la matriz
apuntan al codigo ejecutable de las pruebas existentes ampliadas; no hay
generacion dinamica de assertions ni codigo de prueba dentro de produccion.

### LoginProtocolTest.java

```java
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
```

### HealthResourceTest.java

```java
package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(AuthTestResource.class)
class HealthResourceTest {
    @Test
    void reportsHealthWithoutAuthenticationOrRequestBody() {
        given().accept("application/json")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .header("Cache-Control", equalTo("no-store"))
                .body("$", equalTo(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void emptyChecksDoNotImplyAnImplementedDatabaseProbe() {
        given().when().get("/q/health").then()
                .statusCode(200).body("status", equalTo("UP")).body("checks", empty());
    }

    @Test
    void keepsJsonEvenWhenAcceptRequestsPlainText() {
        given().accept("text/plain")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .body("$", equalTo(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void rejectsAnUnapprovedOriginWithAnEmptyBody() {
        given().header("Origin", "https://unapproved.example")
                .when().get("/q/health").then()
                .statusCode(403).body(isEmptyString())
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
```

### HealthDownHttpTest.java

```java
package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(HealthDownHttpTest.DownProfile.class)
@QuarkusTestResource(AuthTestResource.class)
class HealthDownHttpTest {
    @Test
    void returnsServiceUnavailableWhenTheProviderReportsDown() {
        given().accept("application/json")
                .when().get("/q/health").then()
                .statusCode(503).contentType("application/json")
                .header("Cache-Control", equalTo("no-store"))
                .body("$", equalTo(Map.of("status", "DOWN", "checks", List.of())));
    }

    public static class DownProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("io.smallrye.health.emptyChecksOutcome", "DOWN");
        }
    }
}
```

Este perfil cambia solo una configuracion soportada por el proveedor. La
respuesta HTTP la genera el handler real de SmallRye/Quarkus, no un Resource
falso. No agrega comprobaciones de base de datos ni cambia el default UP de
produccion.

### AuthFailureHttpTest.java

```java
package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(AuthFailureHttpTest.FailureProfile.class)
@QuarkusTestResource(AuthTestResource.class)
class AuthFailureHttpTest {
    @Test
    void persistenceFailurePreservesTheGenericUnauthorizedContract() {
        assertError("persistence-failure", 401, "Invalid credentials");
    }

    @Test
    void unexpectedAuthenticationFailureReturnsOnlyTheGenericMessage() {
        assertError("authentication-failure", 500, "Authentication failed");
    }

    @Test
    void tokenIssuanceFailureReturnsOnlyTheGenericMessage() {
        assertError("token-failure", 500, "Authentication failed");
    }

    private void assertError(String username, int status, String message) {
        given().contentType("application/json")
                .body(Map.of("username", username, "password", "synthetic-password"))
                .when().post("/auth/login").then()
                .statusCode(status).contentType("application/json")
                .body("$", equalTo(Map.of("message", message)));
    }

    public static class FailureProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingAuthService.class);
        }
    }

    @Alternative
    @ApplicationScoped
    public static class FailingAuthService extends AuthService {
        public FailingAuthService() {
            super(null, null, null);
        }

        @Override
        public Optional<UserRecord> authenticate(String username, String password) {
            return switch (username) {
                case "persistence-failure" ->
                        throw new PersistenceFailure("synthetic query failure", null);
                case "token-failure" ->
                        Optional.of(new UserRecord(1, username, "Synthetic User", "STUDENT"));
                default -> throw new IllegalStateException("synthetic authentication failure");
            };
        }

        @Override
        public String issueToken(UserRecord user) {
            throw new IllegalStateException("synthetic token issuance failure");
        }
    }
}
```

La alternativa CDI solo se habilita en `FailureProfile`, dentro de `src/test`.
Resource, serializacion HTTP y manejo de errores son reales; el servicio se
sustituye para inducir fallos de forma determinista sin romper SQLite ni
debilitar la firma. No demuestra que un fallo espontaneo ocurra en produccion.
El resto de pruebas HTTP usa el servicio, repositorio y seguridad reales.

### Ampliaciones a pruebas existentes

En [AuthCharacterizationTest.java](../src/test/java/com/bcp/workshop/auth/AuthCharacterizationTest.java)
se agregaron estos cinco metodos:

```java
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
void doesNotNormalizeUsernamesWithAnOtherwiseCorrectPassword() {
    for (String username : new String[]{"Student", " student ", "missing"}) {
        login(Map.of("username", username, "password", "Workshop123!"))
                .statusCode(401)
                .body("$", equalTo(Map.of("message", "Invalid credentials")));
    }
}
```

El ultimo metodo usa el helper `login` ya existente en esa clase. Se mantiene
el resto de las pruebas, incluida precedencia de validacion y JSON malformado.
En [CorsTest.java](../src/test/java/com/bcp/workshop/auth/CorsTest.java)
se reforzo la prueba existente para comprobar todo el error documentado:

```java
@Test
void unapprovedOriginCannotReadLoginFromBrowser() {
    given().header("Origin", "https://unapproved.example")
            .contentType("application/json")
            .body(Map.of("username", "student", "password", "Workshop123!"))
            .when().post("/auth/login").then()
            .statusCode(403).body(isEmptyString())
            .header("Access-Control-Allow-Origin", nullValue());
}
```

En [AuthTestResource.java](../src/test/java/com/bcp/workshop/auth/AuthTestResource.java)
se agrego `"quarkus.http.test-port", "0"` al mapa de configuracion devuelto por
`start()`. Se conserva SQLite temporal, semillas con hashes y clave sintetica
aleatoria. El puerto aleatorio debe estar en este recurso porque Quarkus
reinicia al cambiar perfiles; no basta con asumir que la propiedad de la
primera instancia seguira aplicada a todas las siguientes.

## Comandos y resultados realmente ejecutados

Directorio de todas las ejecuciones:

```text
C:\Users\Usuario\Documents\T60018-andres-koga\reqflow-workspace\workshop-auth-backend
```

Entorno de las ejecuciones:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

| Ejecucion | Comando exacto | Codigo de salida | Resultado real | Evidencia |
|---|---|---|---|---|
| Base antes de agregar casos | `mvn --batch-mode test '-Dquarkus.http.test-port=0'` | 0 | 41 pruebas; 0 fallos, 0 errores, 0 omitidas. | [testing-baseline.log](validation/testing-baseline.log) |
| Seleccion dirigida tras agregar casos | `mvn --batch-mode test '-Dquarkus.http.test-port=0' '-Dtest=AuthResourceTest,AuthCharacterizationTest,CorsTest,LoginProtocolTest,HealthResourceTest,HealthDownHttpTest,AuthFailureHttpTest'` | 0 | 27 pruebas; 0 fallos, 0 errores, 0 omitidas. | [testing-endpoints.log](validation/testing-endpoints.log) |
| Primera suite limpia completa | `mvn --batch-mode clean verify '-Dquarkus.http.test-port=0'` | 1 | 56 pruebas reportadas; 0 fallos de assertion, 1 error de arranque, 26 omitidas. No se completo verify/cobertura. | [testing-verify-first.log](validation/testing-verify-first.log) |
| Suite limpia final, con puerto 0 en el recurso | `mvn --batch-mode clean verify '-Dquarkus.http.test-port=0'` | 0 | **56 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS y gate JaCoCo aprobado.** | [testing-verify.log](validation/testing-verify.log) |

No se instalaron dependencias ni se cambiaron `pom.xml`, workflows, codigo de
produccion o contrato para conseguir estos resultados. La modificacion previa
de ENDPOINTS.md ya existia al comenzar esta tarea y no se reescribio.

### Resultado final por clase

| Clase | Pruebas | Fallos / errores / omitidas |
|---|---:|---|
| AuthCharacterizationTest | 13 | 0 / 0 / 0 |
| AuthCorrectionTest | 4 | 0 / 0 / 0 |
| AuthFailureHttpTest | 3 | 0 / 0 / 0 |
| AuthResourceTest | 1 | 0 / 0 / 0 |
| AuthServiceTest | 1 | 0 / 0 / 0 |
| CorsConfigurationTest | 1 | 0 / 0 / 0 |
| CorsTest | 3 | 0 / 0 / 0 |
| DependencyPolicyTest | 2 | 0 / 0 / 0 |
| DeserializationMapperTest | 1 | 0 / 0 / 0 |
| ErrorHandlingTest | 5 | 0 / 0 / 0 |
| HealthDownHttpTest | 1 | 0 / 0 / 0 |
| HealthResourceTest | 4 | 0 / 0 / 0 |
| LoginProtocolTest | 2 | 0 / 0 / 0 |
| PasswordHasherTest | 2 | 0 / 0 / 0 |
| PasswordMigrationTest | 2 | 0 / 0 / 0 |
| PersistenceCharacterizationTest | 1 | 0 / 0 / 0 |
| TokenCharacterizationTest | 1 | 0 / 0 / 0 |
| TokenServiceTest | 3 | 0 / 0 / 0 |
| UserRepositoryTest | 5 | 0 / 0 / 0 |
| UserResponseTest | 1 | 0 / 0 / 0 |
| **Total** | **56** | **0 / 0 / 0** |

Estos numeros se contrastaron con los XML generados en
`target/surefire-reports/TEST-*.xml`, no solo con la ausencia de excepciones.
Las pruebas HTTP son de integracion Quarkus; la suite tambien contiene pruebas
unitarias de capas/seguridad. No se denomina a las 56 exclusivamente unitarias.

## Cobertura: reporte y metodo

Meta: >=85% segun [REGLAS.md](../REGLAS.md). Se ejecuto `clean verify` para
eliminar clases y datos de ejecucion anteriores. JaCoCo 0.8.12 genera:

- `target/site/jacoco/index.html`: reporte navegable.
- `target/site/jacoco/jacoco.xml`: contadores procesables.
- `target/site/jacoco/jacoco.csv`: contadores por clase.
- [testing-coverage.csv](validation/testing-coverage.csv): copia persistente
  del CSV de esta ejecucion, no del refactor anterior.

Medicion de lineas del codigo de aplicacion:

```text
lineas cubiertas / (lineas cubiertas + lineas no cubiertas) * 100
= 259 / (259 + 6) * 100
= 97,74%
```

El gate existente de `pom.xml` usa `BUNDLE`, contador `LINE`, valor
`COVEREDRATIO`, minimo `0.85`; su ejecucion final indica
`All coverage checks have been met`. No se excluyeron clases para elevarlo.

**Alcance de la metrica:** esta tarea modifica pruebas y documentacion, no
lineas de produccion. JaCoCo mide las clases de aplicacion, no el porcentaje
de lineas de los propios tests; no se atribuye ficticiamente 97,74% de cobertura
a `src/test`. La suite ejercita el codigo de produccion existente y supera la
meta global; los tests se validan ejecutandolos, incluyendo cada perfil.

Para reproducir el calculo en PowerShell, despues de `verify`:

```powershell
$rows = Import-Csv .\target\site\jacoco\jacoco.csv
$covered = ($rows | Measure-Object LINE_COVERED -Sum).Sum
$missed = ($rows | Measure-Object LINE_MISSED -Sum).Sum
[Math]::Round(100 * $covered / ($covered + $missed), 2)
```

## Fallos, pendientes y pruebas propuestas

### Fallos realmente observados

- La primera suite completa fallo al reiniciar entre perfiles: intento usar
  el puerto 8081 ya ocupado (`QuarkusBindException`). Se registro sin ocultarlo.
  No era un fallo del cuerpo/estado esperado del endpoint. Se corrigio el
  aislamiento de configuracion en `AuthTestResource`, no una assertion.
- La ejecucion final no tiene fallos ni omisiones. Los mensajes ERROR de
  excepciones sinteticas en los logs son esperados por las pruebas de fallos;
  no deben confundirse con un resultado aprobado sin revisar Surefire.
- No se encontro un bug nuevo de produccion en los casos del contrato
  ejecutados. No se cambiaron expectativas para aceptar uno.

### Pendientes / limitaciones

- No se inventaron respuestas para limites de tamano, timeouts o proxies:
  ENDPOINTS.md los deja como pregunta abierta. Falta definir su contrato antes
  de exigir estados/cuerpos exactos en nuevas pruebas.
- Salud DOWN se prueba mediante la opcion real del proveedor exclusivamente
  en el perfil de pruebas. No existe una sonda SQLite de produccion y no se
  afirma que 503 detecte la caida real de esa base.
- Fallos 500 se inducen con una alternativa CDI, no destruyendo datos o
  provocando fallos criptograficos reales. Las pruebas unitarias existentes
  de repositorio y seguridad se conservan.
- CodeQL y Dependency Review remotos no fueron ejecutados en esta tarea;
  sus gates no se tocaron. Tampoco se prueban consumidores de otros componentes.

### Pruebas propuestas, no ejecutadas

- Una vez resuelta la pregunta abierta de limites/proxies, agregar los casos
  con el estado y cuerpo aprobados. No tienen resultado PASS ni cuentan entre
  las 56 pruebas.
- Si se incorpora un check real de dependencias al contrato de salud, probar
  UP/DOWN con esa dependencia aislada. No se agrega ni simula hoy ese endpoint
  o check como si existiera.

## Documentos consultados

- [ENDPOINTS.md](ENDPOINTS.md): rutas, entradas, estados, cuerpos y diferencias
  entre errores de aplicacion e infraestructura.
- [REGLAS.md](../REGLAS.md): meta de cobertura, aislamiento y Clean Code.
- [README.md](../README.md#pruebas-y-cobertura): Java 21, Maven, SQLite temporal,
  configuracion sintetica y ejecucion de pruebas.
- [pom.xml](../pom.xml): dependencias de prueba existentes, Surefire y JaCoCo.
- Fuentes y pruebas enlazadas en este documento, en particular
  [AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java),
  [AuthTestResource.java](../src/test/java/com/bcp/workshop/auth/AuthTestResource.java)
  y [TestDatabase.java](../src/test/java/com/bcp/workshop/auth/TestDatabase.java).
