# Contrato de endpoints

Contrato de la implementacion actual, verificado el 2026-10-19 con Quarkus
3.27.6. Base local predeterminada: `http://localhost:8080`. El host y puerto
del despliegue pueden cambiar; no estan incluidos en las rutas siguientes.
El servicio debe arrancar con la configuracion obligatoria del
[README](../README.md#ejecución). Un fallo de configuracion al arrancar no es
una respuesta HTTP de estos endpoints.

Los cuerpos JSON no garantizan orden de propiedades. Los ejemplos no contienen
secretos reales. Los valores de usuario corresponden a las semillas de
laboratorio; las contrasenas efectivas dependen de los hashes provisionados.

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de una cuenta del laboratorio y entrega un token
firmado que el cliente puede presentar a los servicios consumidores.

### 2. Autenticacion y headers

No requiere autenticacion previa: **no se exige `Authorization`** ni cookie.
Este endpoint no valida un Bearer recibido como sustituto de las credenciales.

```http
Content-Type: application/json
Accept: application/json
```

`Content-Type` identifica el cuerpo enviado. `Accept: application/json` es
recomendado; omitirlo tambien permite la respuesta JSON. En navegadores, el
`Origin` debe estar permitido por la configuracion CORS. `Origin` no es una
credencial ni lo debe inventar un cliente para autenticarse.

Fuente: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
anotaciones de ruta/consumo/produccion y metodo `login`;
[application.properties](../src/main/resources/application.properties).

### 3. Request

No tiene parametros de ruta ni query documentados. El cuerpo es un objeto JSON:

| Campo | Tipo | Obligatorio | Descripcion |
|---|---|---|---|
| `username` | string | Si | Nombre de cuenta. No puede faltar, ser `null`, vacio o contener solo caracteres eliminados por `String.trim()`. Se compara literalmente; no se recortan espacios ni se normalizan mayusculas para autenticar. |
| `password` | string | Si | Contrasena de la cuenta. Misma validacion de presencia que `username`; su valor original, incluidos espacios, se verifica contra el hash. |
| `client` | string o null | No | Se deserializa, pero no interviene en autenticacion, respuesta ni claims del token. Puede omitirse. |

Las propiedades desconocidas se ignoran. Se valida primero el cuerpo, despues
`username` y finalmente `password`; si ambos campos faltan, el mensaje es el
de `username`. No hay longitud maxima propia declarada para estos campos;
esto no elimina los limites HTTP/JSON del framework.

Ejemplo JSON completo de laboratorio, incluyendo el campo opcional:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "integration-example"
}
```

Solo autenticara si la cuenta fue provisionada con el hash de esa contrasena;
no existe una contrasena de produccion por defecto.

Ejemplo reproducible:

```bash
curl -i http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"username":"student","password":"Workshop123!","client":"integration-example"}'
```

Fuentes: [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java),
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java)
(`validationError`), [AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java)
y [AuthCharacterizationTest.java](../src/test/java/com/bcp/workshop/auth/AuthCharacterizationTest.java).

### 4. Response exitosa

**HTTP 200 OK**, `Content-Type: application/json` (puede incluir `charset=UTF-8`).

| Campo | Tipo | Descripcion |
|---|---|---|
| `token` | string | JWT firmado con HS256. Tratar como cadena opaca; no depender de su orden de claims o representacion interna. |
| `expiresIn` | integer | Vigencia en segundos: `3600`. Es una duracion, no una fecha ni milisegundos. |
| `user` | object | DTO publico del usuario autenticado. No contiene identificador de base, contrasena ni hash. |
| `user.username` | string | Nombre almacenado de la cuenta. |
| `user.displayName` | string | Nombre visible escapado con `StringEscapeUtils.escapeHtml4`. Por ejemplo, `<` se representa como `&lt;` en el valor, no como HTML ejecutable. |
| `user.role` | string | Rol almacenado. Las semillas usan `STUDENT` y `TRAINER`; el codigo no declara un enum que limite todos los valores posibles. |
| `debug` | string | Valor fijo de compatibilidad: `authenticated-with-legacy-flow`. No significa que siga usandose el token Base64 anterior. |

Ejemplo JSON completo para la cuenta del request anterior. Se incluye un JWT
sintetico completo, firmado exclusivamente para documentacion con una clave
aleatoria descartada; no es una credencial del entorno:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50Iiwicm9sZSI6IlNUVURFTlQiLCJpYXQiOjE3NjcyMjU2MDAsImV4cCI6MTc2NzIyOTIwMH0.U025toJ6XWPytwbvZNnpOXm7uwHsHu1qwDqc_t-LKqQ",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El token de ejemplo tiene `iat=1767225600` y `exp=1767229200` (2026-01-01,
00:00 y 01:00 UTC) y **ya esta expirado**. Coincide con los claims y algoritmo
del emisor; no debe autenticarse con el ni validarlo con una clave real.
Los tokens reales cambian con la clave y el momento de emision. Para obtener
una respuesta vigente, ejecutar el request reproducible anterior contra el
entorno provisionado.

Los claims emitidos son `sub` (username), `role`, `iat` y `exp`; las fechas son
NumericDate, segundos desde Unix epoch. `exp - iat` es `3600`. No se emite
`client`, ni se declaran `iss` o `aud` en este codigo.

Fuentes: [LoginResponse.java](../src/main/java/com/bcp/workshop/auth/LoginResponse.java),
[UserResponse.java](../src/main/java/com/bcp/workshop/auth/UserResponse.java),
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java)
(`success`) y [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java)
(`create`, `LIFETIME_SECONDS`).

### 5. Respuestas de error

**Errores JSON de la aplicacion:** el cuerpo contiene unicamente `message`.

| Estado | Cuando ocurre | Ejemplo completo del cuerpo |
|---|---|---|
| 400 Bad Request | Cuerpo ausente o JSON `null`; JSON malformado o estructura no deserializable, como `[]`. | `{"message":"Request is null"}` |
| 400 Bad Request | `username` falta, es `null`, vacio o solo espacios segun `trim()`. Se comprueba antes que `password`. | `{"message":"Username is required"}` |
| 400 Bad Request | Con `username` valido para la validacion de presencia, `password` falta, es `null`, vacio o solo espacios segun `trim()`. | `{"message":"Password is required"}` |
| 401 Unauthorized | No existe la cuenta o no coincide la contrasena. Tambien se usa ante `PersistenceFailure` por compatibilidad; el cliente no puede distinguirlos por el cuerpo. | `{"message":"Invalid credentials"}` |
| 500 Internal Server Error | Excepcion imprevista capturada durante autenticacion, emision del token o construccion de la respuesta. | `{"message":"Authentication failed"}` |

No se devuelve `exception`, stacktrace ni el username en los errores 401/500.
Este comportamiento reemplaza expresamente el error legacy, con aprobacion
registrada en [PLAN.md](../PLAN.md#aprobacion-humana-registrada).

**Rechazos anteriores al metodo de aplicacion:** no interpretar todo error
como JSON. En el paquete actual se observaron:

| Estado | Cuando ocurre | Ejemplo del cuerpo |
|---|---|---|
| 403 Forbidden | El filtro CORS rechaza un `Origin` no autorizado. | Cuerpo vacio: cero bytes; no `{}`, no `null` JSON. |
| 406 Not Acceptable | `Accept` no permite la respuesta JSON; por ejemplo `Accept: text/plain`. | Cuerpo vacio: cero bytes. |
| 415 Unsupported Media Type | El cuerpo se envia con un tipo no consumido; por ejemplo `Content-Type: text/plain`. | Cuerpo vacio: cero bytes. |

Ejemplo de rechazo sin cuerpo observado:

```http
HTTP/1.1 415 Unsupported Media Type
Content-Length: 0

```

Los limites de cuerpo/JSON y los proxies pueden producir rechazos adicionales
fuera del metodo. **Pregunta abierta:** no se ha fijado en el repositorio un
contrato de cuerpos para todos esos rechazos, incluidos limites de tamano;
no asumir que devuelven `message` ni inventar un JSON para ellos.

Fuentes: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
[LoginDeserializationExceptionMapper.java](../src/main/java/com/bcp/workshop/auth/LoginDeserializationExceptionMapper.java),
[LoginMappingExceptionMapper.java](../src/main/java/com/bcp/workshop/auth/LoginMappingExceptionMapper.java),
[ErrorHandlingTest.java](../src/test/java/com/bcp/workshop/auth/ErrorHandlingTest.java)
y las observaciones HTTP del paquete con configuracion sintetica aislada.

### 6. Notas de seguridad

- Las contrasenas se verifican mediante PBKDF2-HMAC-SHA256 con salt aleatorio;
  no se autentica comparando texto plano en SQL. El cliente sigue enviando la
  contrasena, por lo que un despliegue fuera del laboratorio requiere HTTPS.
- La firma HS256 requiere `AUTH_TOKEN_SECRET`, sin fallback embebido. Base64
  no cifra el payload: no incluir datos secretos en los claims.
- El verificador local comprueba firma, algoritmo permitido, claims requeridos
  y expiracion; rechaza cuando `now >= exp`. Emitir un token con `exp` **no
  garantiza que un consumidor externo lo valide**: debe verificarlo en cada
  uso. Este backend no tiene un endpoint protegido consumidor del token.
- **Pregunta abierta de integracion:** el repositorio no define el header
  exacto ni el mecanismo de autenticacion de los servicios consumidores.
  No asumir `Authorization: Bearer ...` como contrato de otro componente.
- CORS requiere origenes explicitos en `AUTH_CORS_ORIGINS`; los metodos
  anunciados por CORS no crean endpoints. Un cliente no navegador tampoco
  obtiene autorizacion adicional por cambiar u omitir `Origin`.
- Los errores internos se registran en el servidor. No registrar contrasenas,
  hashes, claves ni tokens en el cliente.

Fuentes adicionales: [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java),
[UserRepository.java](../src/main/java/com/bcp/workshop/auth/UserRepository.java),
[CorsConfiguration.java](../src/main/java/com/bcp/workshop/auth/CorsConfiguration.java)
y [README.md](../README.md).

## GET /q/health

### 1. Que hace

Permite a operadores y sondas consultar el estado de salud que publica la
instancia para decidir si responde como servicio.

### 2. Autenticacion y headers

No requiere autenticacion en la configuracion actual. **No exige
`Authorization`**, token ni cookie.

```http
Accept: application/json
```

No necesita `Content-Type` porque no se envia cuerpo. El handler devuelve
JSON; en la verificacion actual respondio JSON incluso con `Accept: text/plain`.
No extrapolar la negociacion 406 de login a este handler.

Fuente: extension `quarkus-smallrye-health` declarada en
[pom.xml](../pom.xml), configuracion de
[application.properties](../src/main/resources/application.properties)
y observacion del paquete sin credenciales HTTP.

### 3. Request

| Campo | Tipo | Obligatorio | Descripcion |
|---|---|---|---|
| Ninguno | No aplica | No | No hay campos de cuerpo, parametros de ruta ni query requeridos por este endpoint. |

**Ejemplo JSON de request: no aplica.** El request real no contiene cuerpo;
enviar `{}` o `null` como requisito inventaria una entrada inexistente.
Ejemplo completo del request real:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json

```

```bash
curl -i http://localhost:8080/q/health -H "Accept: application/json"
```

Fuente: `SmallRyeHealthHandler` de la extension; no hay Resource propio ni
parametros de entrada declarados para salud en `src/main`.

### 4. Response exitosa

**HTTP 200 OK**, `Content-Type: application/json; charset=UTF-8`.
El handler establece `Cache-Control: no-store`.

| Campo | Tipo | Descripcion |
|---|---|---|
| `status` | string | Estado agregado. En el arranque verificado con la configuracion actual: `UP`. |
| `checks` | array de objetos | Resultados de comprobaciones registradas. En este proyecto y configuracion actuales es `[]`; no hay checks propios de base de datos, autenticacion o consumidores. |

Ejemplo JSON real completo observado:

```json
{
  "status": "UP",
  "checks": []
}
```

No hay `token`, `message` ni `user` en esta respuesta. No se documentan campos
de objetos `checks[]` que el proyecto no registra ni devuelve actualmente.

Fuentes: handler de Quarkus 3.27.6, reporter de SmallRye Health 4.2.0 y
observacion HTTP del paquete; versiones resueltas en
[dependencies-after.txt](validation/dependencies-after.txt).

### 5. Respuestas de error

El codigo de negocio no declara errores propios para esta ruta. No aplicar
automaticamente el formato `{"message": ...}` de login a salud.

| Estado | Cuando ocurre | Ejemplo del cuerpo |
|---|---|---|
| 403 Forbidden | El filtro HTTP CORS rechaza un `Origin` no autorizado, antes del handler de salud. | Cuerpo vacio: cero bytes; no existe JSON de error propio de salud. |
| 503 Service Unavailable | El agregador de SmallRye Health informa `DOWN`. Es una capacidad del proveedor, no un check SQLite implementado por este repositorio. | Ejemplo minimo del proveedor con agregado `DOWN` y sin resultados individuales: `{"status":"DOWN","checks":[]}`. No es la respuesta observada con los valores predeterminados actuales. |

El handler del proveedor devuelve el payload agregado tambien para 503, con
el mismo formato de salud cuando no se habilita Problem Details. El proyecto
no habilita ese formato alternativo. No agregar `message` ni campos de un
check ficticio al ejemplo.

**Preguntas abiertas / limites de lo verificado:**

- No hay checks propios y el proveedor usa `UP` para el conjunto vacio por
  defecto. No se ha definido que comprobacion real debe provocar `DOWN`.
  El ejemplo 503 describe el caso soportado por el proveedor; no acredita
  una deteccion de fallo de SQLite ni una prueba de ese fallo en este proyecto.
- No existe un contrato propio para excepciones internas del handler,
  timeouts de proxy o fallos de transporte. Si el proceso no arranca o esta
  detenido, puede no existir respuesta HTTP ni cuerpo JSON alguno.

Fuente: `SmallRyeHealthHandlerBase` (`health.isDown()` -> 503 y
`reporter.reportHealth(...)`) y `SmallRyeHealthReporter`
(`emptyChecksOutcome`, predeterminado `UP`), enlazados al final.

### 6. Notas de seguridad

- `200` con `checks: []` confirma la respuesta del servicio, **no** que SQLite
  este disponible, que una contrasena autentique ni que otro servicio valide
  tokens. Para comprobar login hay que usar su endpoint con datos de prueba.
- El endpoint es publico en esta configuracion y revela estado operativo;
  controlar su exposicion en el despliegue. El proyecto no implementa una
  politica de autenticacion especial para salud.
- La politica CORS no sustituye controles de acceso de red y no cifra trafico.
- No anadir credenciales ni tokens a una sonda de salud: no son necesarios
  para el endpoint implementado.

## Documentos y fuentes consultados

- [README.md](../README.md): arranque, configuracion y limitaciones operativas.
- Version anterior de este documento: contraste con el contrato legacy.
- [pom.xml](../pom.xml) y
  [dependencies-after.txt](validation/dependencies-after.txt): proveedores y versiones.
- Codigo y pruebas enlazados en cada apartado, incluido
  [TokenServiceTest.java](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java)
  para expiracion y firma.
- [SmallRyeHealthHandler, Quarkus 3.27.6](https://github.com/quarkusio/quarkus/blob/3.27.6/extensions/smallrye-health/runtime/src/main/java/io/quarkus/smallrye/health/runtime/SmallRyeHealthHandler.java).
- [SmallRyeHealthHandlerBase, Quarkus 3.27.6](https://github.com/quarkusio/quarkus/blob/3.27.6/extensions/smallrye-health/runtime/src/main/java/io/quarkus/smallrye/health/runtime/SmallRyeHealthHandlerBase.java).
- [SmallRyeHealthReporter, SmallRye Health 4.2.0](https://github.com/smallrye/smallrye-health/blob/4.2.0/implementation/src/main/java/io/smallrye/health/SmallRyeHealthReporter.java).

Verificacion complementaria: empaquetado del codigo sin modificarlo y
peticiones HTTP sobre SQLite temporal con hashes y clave sinteticos. Se
observaron salud 200, login 200, JSON malformado 400, CORS 403, negociacion 406
y tipo de contenido 415; los errores de negocio restantes se contrastaron
con sus ramas y pruebas. La instancia se detuvo y la base temporal se elimino.
